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

// Compute commission + charge for a payment_type using merchant_commissions.
async function computeCommission(websiteId, paymentType, amount) {
  const rule = await prisma.merchant_commissions.findFirst({
    where: { website_id: websiteId, payment_type: paymentType, is_active: 1 },
  });
  if (!rule) return { commission: 0, charge: 0 };

  const amt = Number(amount) || 0;
  const commission = rule.commission_type === 'percentage'
    ? +(amt * (Number(rule.commission_value) / 100)).toFixed(2)
    : Number(rule.commission_value);
  const charge = rule.charge_type === 'percentage'
    ? +(amt * (Number(rule.charge_value) / 100)).toFixed(2)
    : Number(rule.charge_value);
  return { commission, charge };
}

/**
 * Build the callback payload for a website + verified transaction.
 * @param website  gateway_layouts row (must include permission + preference flags)
 * @param history  sms_history row (amount, trx_id, provider_tag, sender_number, ...)
 * @param status   e.g. 'verified'
 */
async function buildPayload(website, history, status = 'verified') {
  const amount = Number(history.amount);
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

  // payment_type: included only if admin allows AND merchant opted in.
  if (website.allow_payment_type_callback && website.receive_payment_type) {
    payload.payment_type = normalizePaymentType(history.provider_tag, history.template_name || '');
  }

  // commission: included only if admin allows AND merchant opted in.
  if (website.allow_commission_callback && website.receive_commission) {
    const paymentType = payload.payment_type || normalizePaymentType(history.provider_tag, '');
    const { commission, charge } = await computeCommission(website.id, paymentType, amount);
    payload.commission = commission;
    payload.charge = charge;
  }

  return payload;
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

/**
 * Convenience: build payload for a website+history and dispatch to both the
 * callback_url and webhook_url (whichever are set). Uses the website's stored
 * api_secret for signing.
 */
async function sendMerchantCallback(website, history, status = 'verified') {
  const payload = await buildPayload(website, history, status);
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
      detail: { url, trxId: payload.trxId, amount: payload.amount, result },
    });
  }
  return { payload, results };
}

module.exports = {
  normalizePaymentType,
  computeCommission,
  buildPayload,
  dispatchWebhook,
  sendMerchantCallback,
};
