/**
 * paymentFlowController.js — Phase 6: Payment Flow routing.
 *
 * Flow:
 *   Merchant server ──POST /api/v1/pay/init──▶ PayCheck creates a payment_session
 *     (amount recorded server-side, tamper-proof) and returns a checkoutUrl.
 *   Customer browser ──GET /pay/:token──▶ PayCheck routes based on channel:
 *       • paycheck  → PayCheck hosted checkout (transaction / merchant_vibe)
 *       • official  → 302 redirect to the original gateway page (bKash/Nagad/
 *                     Rocket Merchant, SSLCommerz, Card, Bank). On completion the
 *                     gateway calls /api/pay/:token/gateway-callback which updates
 *                     the DB and fires the merchant callback, then returns the
 *                     customer to success_url / cancel_url.
 *
 * Security & robustness:
 *   - Merchant authenticates with X-API-Key. If X-Signature is provided it is
 *     verified as HMAC-SHA256(raw body, api_secret) in constant time.
 *   - Amount is trusted only from the server session, never from the redirect URL.
 *   - Sessions expire; expiry is enforced lazily on read.
 *   - Callbacks are idempotent (a session completes exactly once).
 */

const crypto = require('crypto');
const prisma = require('../db/prisma');
const merchantCallback = require('../services/merchantCallback');
const merchantCache = require('../services/merchantCache');
const audit = require('../services/auditLog');

const OFFICIAL_PROVIDERS = new Set([
  'bkash_merchant', 'nagad_merchant', 'rocket_merchant', 'sslcommerz', 'card', 'bank',
]);

const MERCHANT_FLAGS_SELECT = {
  id: true, user_id: true, merchant_id: true, api_key: true, api_secret: true,
  redirect_url: true, success_url: true, cancel_url: true, callback_url: true, webhook_url: true,
  checkout_mode: true,
  allow_payment_type_callback: true, allow_commission_callback: true,
  receive_payment_type: true, receive_commission: true,
};

const genToken = () => `ps_${crypto.randomBytes(24).toString('hex')}`;

function timingSafeEqual(a, b) {
  const ba = Buffer.from(String(a || ''));
  const bb = Buffer.from(String(b || ''));
  if (ba.length !== bb.length) return false;
  return crypto.timingSafeEqual(ba, bb);
}

function baseUrl(req) {
  if (process.env.PUBLIC_BASE_URL) return process.env.PUBLIC_BASE_URL.replace(/\/$/, '');
  const proto = req.headers['x-forwarded-proto'] || req.protocol || 'http';
  return `${proto}://${req.get('host')}`;
}

function applyTemplate(tpl, vars) {
  return tpl.replace(/\{(\w+)\}/g, (_, k) => (vars[k] != null ? encodeURIComponent(vars[k]) : ''));
}

function isExpired(session) {
  return session.status === 'created' || session.status === 'redirected'
    ? session.expires_at < new Date()
    : false;
}

/**
 * POST /api/v1/pay/init
 * Headers: X-API-Key (required), X-Signature (optional HMAC of raw body)
 * Body: { amount, orderId?, channel?('paycheck'|'official'), provider?, customerNumber?,
 *         successUrl?, cancelUrl?, callbackUrl?, webhookUrl?, expiresInSec?, meta? }
 */
async function initPayment(req, res) {
  try {
    const apiKey = req.headers['x-api-key'] || req.body.apiKey;
    if (!apiKey) {
      return res.status(401).json({ success: false, error: 'MISSING_API_KEY' });
    }

    const website = await merchantCache.getByApiKey(apiKey, 'flags', () =>
      prisma.gateway_layouts.findFirst({
        where: { api_key: apiKey, is_active: 1 },
        select: MERCHANT_FLAGS_SELECT,
      })
    );
    if (!website) {
      return res.status(403).json({ success: false, error: 'INVALID_API_KEY' });
    }

    // Optional HMAC verification of the raw JSON body.
    const signature = req.headers['x-signature'];
    if (signature) {
      const raw = JSON.stringify(req.body || {});
      const expected = crypto.createHmac('sha256', website.api_secret || '').update(raw).digest('hex');
      if (!timingSafeEqual(signature, expected)) {
        return res.status(401).json({ success: false, error: 'INVALID_SIGNATURE' });
      }
    }

    const amount = parseFloat(req.body.amount);
    if (!(amount > 0)) {
      return res.status(400).json({ success: false, error: 'INVALID_AMOUNT' });
    }

    let channel = (req.body.channel || 'paycheck').toLowerCase();
    let officialProvider = null;

    if (channel === 'official') {
      officialProvider = (req.body.provider || '').toLowerCase();
      if (!OFFICIAL_PROVIDERS.has(officialProvider)) {
        return res.status(400).json({ success: false, error: 'INVALID_OFFICIAL_PROVIDER' });
      }
      const gw = await prisma.website_official_gateways.findFirst({
        where: { website_id: website.id, provider: officialProvider, is_active: 1 },
        select: { id: true },
      });
      if (!gw) {
        return res.status(400).json({ success: false, error: 'OFFICIAL_PROVIDER_NOT_CONFIGURED' });
      }
    } else {
      channel = 'paycheck';
    }

    const expiresInSec = Math.min(Math.max(parseInt(req.body.expiresInSec, 10) || 1800, 120), 86400);
    const token = genToken();

    const session = await prisma.payment_sessions.create({
      data: {
        session_token: token,
        website_id: website.id,
        user_id: website.user_id,
        order_id: req.body.orderId ? String(req.body.orderId).slice(0, 191) : null,
        amount,
        currency: (req.body.currency || 'BDT').toUpperCase().slice(0, 8),
        channel,
        official_provider: officialProvider,
        customer_number: req.body.customerNumber ? String(req.body.customerNumber).slice(0, 20) : null,
        status: 'created',
        // Per-session overrides fall back to the website defaults at dispatch time.
        success_url: req.body.successUrl || website.success_url || website.redirect_url || null,
        cancel_url: req.body.cancelUrl || website.cancel_url || null,
        callback_url: req.body.callbackUrl || website.callback_url || null,
        webhook_url: req.body.webhookUrl || website.webhook_url || null,
        meta_json: req.body.meta ? JSON.stringify(req.body.meta) : null,
        expires_at: new Date(Date.now() + expiresInSec * 1000),
      },
    });

    audit.log({
      eventType: 'payment.init', entityType: 'payment_session', entityId: token,
      websiteId: website.id, userId: website.user_id, ip: req.ip, status: 'created',
      detail: { amount, channel, officialProvider, orderId: session.order_id },
    });

    const checkoutUrl = `${baseUrl(req)}/pay/${token}`;
    return res.status(201).json({
      success: true,
      sessionToken: token,
      checkoutUrl,
      channel,
      amount,
      expiresAt: session.expires_at,
    });
  } catch (error) {
    console.error('[PAY INIT] error:', error);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
}

/**
 * GET /pay/:token  (top-level, browser-facing)
 * Routes the customer to the correct destination based on the session channel.
 */
async function redirectPayment(req, res) {
  try {
    const { token } = req.params;
    const session = await prisma.payment_sessions.findUnique({ where: { session_token: token } });
    if (!session) return res.status(404).send('Payment session not found.');

    if (isExpired(session)) {
      await prisma.payment_sessions.update({ where: { id: session.id }, data: { status: 'expired' } })
        .catch(() => {});
      const back = session.cancel_url || '/';
      return res.redirect(302, back);
    }

    if (session.status === 'completed' && session.success_url) {
      return res.redirect(302, session.success_url);
    }

    // ── PayCheck hosted checkout ──────────────────────────────────────────
    if (session.channel === 'paycheck') {
      const website = await prisma.gateway_layouts.findUnique({
        where: { id: session.website_id }, select: { api_key: true },
      });
      if (!website) return res.status(404).send('Merchant not found.');
      const q = new URLSearchParams({
        apiKey: website.api_key,
        amount: String(Number(session.amount)),
        session: token,
      });
      return res.redirect(302, `/checkout.html?${q.toString()}`);
    }

    // ── Official gateway redirect ─────────────────────────────────────────
    const gw = await prisma.website_official_gateways.findFirst({
      where: { website_id: session.website_id, provider: session.official_provider, is_active: 1 },
    });
    if (!gw) {
      const back = session.cancel_url || '/';
      return res.redirect(302, back);
    }

    const callbackUrl = `${baseUrl(req)}/api/pay/${token}/gateway-callback`;
    const redirectUrl = applyTemplate(gw.redirect_url_template, {
      amount: Number(session.amount),
      order_id: session.order_id || '',
      token,
      callback_url: callbackUrl,
      currency: session.currency,
      customer_number: session.customer_number || '',
    });

    await prisma.payment_sessions.update({ where: { id: session.id }, data: { status: 'redirected' } })
      .catch(() => {});

    return res.redirect(302, redirectUrl);
  } catch (error) {
    console.error('[PAY REDIRECT] error:', error);
    return res.status(500).send('Internal Server Error');
  }
}

/**
 * ALL /api/pay/:token/gateway-callback
 * Called by the official gateway after payment authentication. Marks the session
 * completed/failed, fires the merchant callback, and returns the customer to the
 * merchant's success / cancel URL.
 * Params/body accepted: { status('success'|'failed'), trxId, provider }
 */
async function officialGatewayCallback(req, res) {
  try {
    const { token } = req.params;
    const src = { ...req.query, ...req.body };
    const outcome = (src.status || 'success').toLowerCase();
    const trxId = src.trxId || src.trx_id || src.transactionId || null;

    const session = await prisma.payment_sessions.findUnique({ where: { session_token: token } });
    if (!session) return res.status(404).json({ success: false, error: 'SESSION_NOT_FOUND' });

    // Idempotency: a completed session never re-fires callbacks.
    if (session.status === 'completed') {
      return res.redirect(302, session.success_url || '/');
    }

    if (outcome !== 'success') {
      await prisma.payment_sessions.update({
        where: { id: session.id }, data: { status: 'failed', completed_at: new Date() },
      });
      audit.log({
        eventType: 'payment.failed', entityType: 'payment_session', entityId: token,
        websiteId: session.website_id, userId: session.user_id, ip: req.ip, status: 'failed',
        detail: { provider: session.official_provider, trxId },
      });
      return res.redirect(302, session.cancel_url || '/');
    }

    await prisma.payment_sessions.update({
      where: { id: session.id },
      data: { status: 'completed', trx_id: trxId, completed_at: new Date() },
    });
    audit.log({
      eventType: 'payment.completed', entityType: 'payment_session', entityId: token,
      websiteId: session.website_id, userId: session.user_id, ip: req.ip, status: 'success',
      detail: { provider: session.official_provider, trxId, amount: Number(session.amount) },
    });

    const website = await prisma.gateway_layouts.findUnique({
      where: { id: session.website_id }, select: MERCHANT_FLAGS_SELECT,
    });
    if (website) {
      // Synthetic history-like record for the unified callback dispatcher.
      const synthetic = {
        amount: Number(session.amount),
        trx_id: trxId,
        provider_tag: session.official_provider,
        sender_number: session.customer_number || null,
        sms_timestamp: new Date(),
        template_name: session.official_provider,
      };
      // Honour per-session callback/webhook overrides.
      const target = {
        ...website,
        callback_url: session.callback_url || website.callback_url,
        webhook_url: session.webhook_url || website.webhook_url,
      };
      merchantCallback.sendMerchantCallback(target, synthetic, 'completed')
        .catch((e) => console.error('[PAY CALLBACK] merchant callback error:', e.message));
    }

    const success = session.success_url
      ? `${session.success_url}${session.success_url.includes('?') ? '&' : '?'}trxId=${encodeURIComponent(trxId || '')}&amount=${encodeURIComponent(Number(session.amount))}&status=success`
      : '/';
    return res.redirect(302, success);
  } catch (error) {
    console.error('[PAY CALLBACK] error:', error);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
}

/**
 * GET /api/v1/pay/:token/status  — merchant polling (server-to-server).
 * Requires X-API-Key belonging to the session's website.
 */
async function paymentStatus(req, res) {
  try {
    const { token } = req.params;
    const apiKey = req.headers['x-api-key'] || req.query.apiKey;
    const session = await prisma.payment_sessions.findUnique({ where: { session_token: token } });
    if (!session) return res.status(404).json({ success: false, error: 'SESSION_NOT_FOUND' });

    const website = await prisma.gateway_layouts.findUnique({
      where: { id: session.website_id }, select: { api_key: true },
    });
    if (!website || !timingSafeEqual(apiKey, website.api_key)) {
      return res.status(403).json({ success: false, error: 'FORBIDDEN' });
    }

    let status = session.status;
    if (isExpired(session)) {
      status = 'expired';
      await prisma.payment_sessions.update({ where: { id: session.id }, data: { status } }).catch(() => {});
    }

    return res.json({
      success: true,
      status,
      channel: session.channel,
      amount: Number(session.amount),
      orderId: session.order_id,
      trxId: session.trx_id,
      completedAt: session.completed_at,
      expiresAt: session.expires_at,
    });
  } catch (error) {
    console.error('[PAY STATUS] error:', error);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
}

/**
 * completeSessionByToken — invoked by the PayCheck checkout verify path when a
 * paycheck session token is present. Marks the session completed and records the
 * matched transaction. Never throws.
 */
async function completeSessionByToken(token, { trxId, historyId } = {}) {
  if (!token) return;
  try {
    await prisma.payment_sessions.updateMany({
      where: { session_token: token, status: { in: ['created', 'redirected'] } },
      data: { status: 'completed', trx_id: trxId || null, matched_history_id: historyId || null, completed_at: new Date() },
    });
  } catch (e) {
    console.error('[PAY COMPLETE] error:', e.message);
  }
}

module.exports = {
  initPayment,
  redirectPayment,
  officialGatewayCallback,
  paymentStatus,
  completeSessionByToken,
  OFFICIAL_PROVIDERS,
};
