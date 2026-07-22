/**
 * merchantCallback.js — unified webhook/callback dispatcher for PayCheck merchants.
 *
 * Responsibilities (shared across checkout verify, B2B claim-check and Vibe match):
 *   - Build the callback payload from a verified sms_history row.
 *   - Conditionally enrich with payment_type / commission fields, gated by the
 *     admin permission flags AND the merchant preference on the website.
 *   - HMAC-sign the body (X-Paychek-Signature) and POST with bounded retries.
 *
 * Idempotency: sms_history.is_used + used_by_merchant_id already guarantees a
 * transaction is claimed once. This dispatcher only sends; callers ensure a
 * transaction is dispatched a single time.
 */

const crypto = require('crypto');
const axios = require('axios');
const prisma = require('../db/prisma');
const audit = require('./auditLog');

// Map a raw provider tag / template name to a normalized payment_type token.
function normalizePaymentType(providerTag = '', templateName = '') {
  const s = `${providerTag} ${templateName}`.toLowerCase();
  const provider =
    s.includes('bkash') ? 'bkash' :
    s.includes('nagad') ? 'nagad' :
    s.includes('rocket') ? 'rocket' :
    s.includes('upay') ? 'upay' :
    s.includes('card') ? 'card' :
    s.includes('bank') ? 'bank' : 'other';

  if (provider === 'card' || provider === 'bank') return provider;

  const kind =
    s.includes('merchant') ? 'merchant' :
    s.includes('agent') ? 'agent' :
    'personal';
  return `${provider}_${kind}`;
}

/**
 * Resolve all payment_type keys that should match a verified SMS.
 * Supports legacy tokens (bkash_personal) and per-template keys (tpl_<id>).
 * provider_tag is often the exact sms_templates.template_name (see smsWorker).
 */
async function resolveIncentiveKeys(paymentType, providerTag = '') {
  const keys = new Set();
  if (paymentType) keys.add(String(paymentType));
  const tag = (providerTag || '').trim();
  if (tag) {
    keys.add(tag);
    keys.add(normalizePaymentType(tag, ''));
    try {
      const tpls = await prisma.sms_templates.findMany({
        where: { template_name: tag, is_active: 1 },
        select: { id: true },
        take: 10,
      });
      for (const t of tpls) keys.add(`tpl_${t.id}`);
    } catch (_) { /* ignore */ }
  }
  return [...keys];
}

function applyMoneyRule(amt, type, value) {
  return type === 'percentage'
    ? +(amt * (Number(value) / 100)).toFixed(2)
    : Number(value) || 0;
}

// Compute commission + charge using merchant_commissions (token or tpl_<id>).
async function computeCommission(websiteId, paymentType, amount, providerTag = '') {
  const keys = await resolveIncentiveKeys(paymentType, providerTag);
  if (!keys.length) return { commission: 0, charge: 0 };

  const rules = await prisma.merchant_commissions.findMany({
    where: { website_id: websiteId, is_active: 1, payment_type: { in: keys } },
  });
  if (!rules.length) return { commission: 0, charge: 0 };

  // Prefer an exact per-template rule over a coarse legacy token.
  const rule = rules.find((r) => String(r.payment_type).startsWith('tpl_')) || rules[0];
  const amt = Number(amount) || 0;
  return {
    commission: applyMoneyRule(amt, rule.commission_type, rule.commission_value),
    charge: applyMoneyRule(amt, rule.charge_type, rule.charge_value),
  };
}

// Campaign matches: payment_type in resolved keys OR '' (ALL), plus amount range.
async function computeCampaigns(websiteId, paymentType, amount, providerTag = '') {
  const amt = Number(amount) || 0;
  const keys = await resolveIncentiveKeys(paymentType, providerTag);
  let rows = [];
  try {
    if (!keys.length) {
      rows = await prisma.$queryRawUnsafe(
        `SELECT * FROM merchant_campaigns
          WHERE website_id = ? AND is_active = 1 AND payment_type = ''
            AND ? >= min_amount AND (max_amount = 0 OR ? <= max_amount)`,
        websiteId, amt, amt,
      );
    } else {
      rows = await prisma.$queryRawUnsafe(
        `SELECT * FROM merchant_campaigns
          WHERE website_id = ? AND is_active = 1
            AND (payment_type = '' OR payment_type IN (${keys.map(() => '?').join(',')}))
            AND ? >= min_amount
            AND (max_amount = 0 OR ? <= max_amount)`,
        websiteId, ...keys, amt, amt,
      );
    }
  } catch (_) {
    return { commission: 0, charge: 0 };
  }
  let commission = 0;
  let charge = 0;
  for (const c of rows) {
    const v = applyMoneyRule(amt, c.value_type, c.value);
    if (c.mode === 'charge') charge += v; else commission += v;
  }
  return { commission: +commission.toFixed(2), charge: +charge.toFixed(2) };
}

// Total incentives = base per-type commission/charge + all matching campaigns.
async function computeIncentives(websiteId, paymentType, amount, providerTag = '') {
  const base = await computeCommission(websiteId, paymentType, amount, providerTag);
  const camp = await computeCampaigns(websiteId, paymentType, amount, providerTag);
  return {
    commission: +(base.commission + camp.commission).toFixed(2),
    charge: +(base.charge + camp.charge).toFixed(2),
  };
}

/**
 * Build the callback payload for a website + verified transaction.
 * Additive purpose-aware fields (v2) — legacy keys (trxId, amount, status) kept.
 *
 * @param website  gateway_layouts row (must include permission + preference flags)
 * @param history  sms_history row
 * @param status   e.g. 'verified' | 'SUCCESS'
 * @param extras   optional settlement / purpose enrichment
 */
async function buildPayload(website, history, status = 'verified', extras = {}) {
  const amount = Number(history.amount);
  const purpose = extras.purpose || extras.sessionPurpose || null;
  const payload = {
    trxId: history.trx_id,
    amount,
    provider: history.provider_tag,
    sender: history.sender_number || null,
    smsTimestamp: history.sms_timestamp,
    merchantId: website.merchant_id || website.id,
    status,
    timestamp: new Date(),
  };

  if (purpose) payload.purpose = purpose;

  // payment_type: included only if admin allows AND merchant opted in.
  if (website.allow_payment_type_callback && website.receive_payment_type) {
    payload.payment_type = normalizePaymentType(history.provider_tag, history.template_name || '');
  }

  const paymentType = payload.payment_type || normalizePaymentType(history.provider_tag, '');
  const incentives = await computeIncentives(
    website.id, paymentType, extras.checkoutAmount ?? amount, history.provider_tag || '',
  );

  // commission/charge raw: only if admin allows AND merchant opted in.
  if (website.allow_commission_callback && website.receive_commission) {
    payload.commission = incentives.commission;
    payload.charge = incentives.charge;
  }

  // ── Purpose-aware additive fields (always when purpose known) ───────────
  if (purpose === 'add_balance') {
    const checkoutAmount = Number(extras.checkoutAmount ?? amount);
    const net = +(incentives.commission - incentives.charge).toFixed(2);
    payload.checkoutAmount = checkoutAmount;
    payload.receivedAmount = Number(extras.receivedAmount ?? amount);
    payload.walletCredit = Number(
      extras.walletCredit ?? +(Math.max(0, checkoutAmount + net)).toFixed(2),
    );
  } else if (purpose === 'payment') {
    payload.orderAmount = Number(extras.orderAmount ?? extras.checkoutAmount ?? amount);
    payload.expectedPayable = Number(extras.expectedPayable ?? amount);
    payload.receivedAmount = Number(extras.receivedAmount ?? amount);
    if (extras.overPaid != null) payload.overPaid = Number(extras.overPaid);
    if (Array.isArray(extras.transactions)) payload.transactions = extras.transactions;
  }

  return payload;
}

/**
 * Dispatch with optional purpose/settlement extras (additive).
 */
async function sendMerchantCallback(website, history, status = 'verified', extras = {}) {
  const payload = await buildPayload(website, history, status, extras);
  const targets = [];
  if (website.callback_url) targets.push(website.callback_url);
  if (website.webhook_url && website.webhook_url !== website.callback_url) targets.push(website.webhook_url);
  const results = [];
  for (const url of targets) {
    const result = await dispatchWebhook(url, payload, website.api_secret);
    results.push(result);
    audit.log({
      eventType: result.delivered ? 'callback.delivered' : 'callback.failed',
      entityType: 'website', entityId: website.merchant_id || website.id,
      websiteId: website.id, userId: website.user_id,
      status: result.delivered ? 'success' : 'failed',
      detail: { url, trxId: payload.trxId, amount: payload.amount, purpose: payload.purpose, result },
    });
  }
  return { payload, results };
}

function signBody(rawBody, secret) {
  if (!secret) return null;
  return crypto.createHmac('sha256', secret).update(rawBody).digest('hex');
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * POST the payload to a URL with HMAC signature and bounded exponential retry.
 * Fire-and-forget friendly; never throws to the caller.
 */
async function dispatchWebhook(url, payload, secret, { retries = 3 } = {}) {
  if (!url) return { delivered: false, reason: 'NO_URL' };

  const rawBody = JSON.stringify(payload);
  const signature = signBody(rawBody, secret);
  const headers = { 'Content-Type': 'application/json' };
  if (signature) headers['X-Paychek-Signature'] = signature;

  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const res = await axios.post(url, rawBody, { headers, timeout: 10000 });
      console.log(`[CALLBACK] Delivered to ${url} (status ${res.status}, attempt ${attempt + 1})`);
      return { delivered: true, status: res.status };
    } catch (err) {
      const status = err.response?.status;
      console.error(`[CALLBACK] Attempt ${attempt + 1} failed for ${url}: ${status || err.message}`);
      // Do not retry on clear client errors (4xx except 429).
      if (status && status >= 400 && status < 500 && status !== 429) {
        return { delivered: false, status };
      }
      if (attempt < retries) await sleep(300 * Math.pow(2, attempt));
    }
  }
  return { delivered: false, reason: 'MAX_RETRIES' };
}

module.exports = {
  normalizePaymentType,
  computeCommission,
  computeCampaigns,
  computeIncentives,
  buildPayload,
  dispatchWebhook,
  sendMerchantCallback,
};
