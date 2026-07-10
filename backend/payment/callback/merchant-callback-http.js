/**
 * @file Merchant callback HTTP delivery (no outbox dependency).
 * @module payment/callback/merchant-callback-http
 */

const crypto = require('crypto');
const axios = require('axios');
const { runWithRetry } = require('../retry/retry-engine');
const { DEFAULT_MERCHANT_CALLBACK_POLICY } = require('../retry/retry-policy');
const { emitPaymentEvent, PAYMENT_EVENTS } = require('../events/event-bus');
const { recordLatency } = require('../monitoring/payment-monitor');

function signBody(rawBody, secret) {
  if (!secret) return null;
  return crypto.createHmac('sha256', secret).update(rawBody).digest('hex');
}

async function deliverHttpMerchantCallback({
  url, payload, website, traceId, sessionToken, retryPolicy,
}) {
  const started = Date.now();
  const outcome = await runWithRetry({
    id: `mcb:${sessionToken}:${url}`,
    traceId,
    type: 'merchant_callback',
    policy: retryPolicy || DEFAULT_MERCHANT_CALLBACK_POLICY,
    execute: async () => {
      const rawBody = JSON.stringify(payload);
      const signature = signBody(rawBody, website?.api_secret);
      const headers = { 'Content-Type': 'application/json' };
      if (signature) headers['X-Paychek-Signature'] = signature;

      try {
        const res = await axios.post(url, rawBody, { headers, timeout: 10000 });
        const ok = res.status >= 200 && res.status < 300;
        return { ok, retryable: !ok, reason: ok ? null : `HTTP_${res.status}` };
      } catch (err) {
        const status = err.response?.status;
        const retryable = !status || status >= 500 || status === 429;
        return { ok: false, retryable, reason: status ? `HTTP_${status}` : err.code || 'NETWORK_ERROR' };
      }
    },
  });

  recordLatency('merchant_callback', Date.now() - started, { traceId, url });

  if (outcome.ok) {
    emitPaymentEvent(PAYMENT_EVENTS.MERCHANT_CALLBACK_SENT, {
      traceId,
      sessionToken,
      url,
      status: payload.status,
    });
  }

  return outcome;
}

module.exports = { deliverHttpMerchantCallback, signBody };
