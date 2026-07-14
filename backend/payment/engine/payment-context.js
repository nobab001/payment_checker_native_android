/**
 * @file PaymentContext — FROZEN v1 root shape. See core/payment-context-v1.js.
 * Extensions: metadata | metadata.custom | metadata.extra only.
 */

const { createTraceId } = require('../logging/trace-logger');
const { PAYMENT_CONTEXT_VERSION } = require('../core/payment-context-v1');

/**
 * @typedef {Object} PaymentContext
 * @property {string} traceId
 * @property {string} source
 * @property {Object} merchant
 * @property {Object} [website]
 * @property {Object} [customer]
 * @property {number} amount
 * @property {string} currency
 * @property {Object} provider
 * @property {string} [callbackUrl]
 * @property {string} [returnUrl]
 * @property {string} [successUrl]
 * @property {string} [cancelUrl]
 * @property {string} [ip]
 * @property {Object} [device]
 * @property {Object} [metadata] — extensions via metadata.custom | metadata.extra
 * @property {Object} [http]
 * @property {Object} [merchantConfig]
 * @property {string} [sessionToken]
 */

function buildContextFromLiveInitRequest(req) {
  const { browserBaseUrlFromRequest } = require('../shared/session-utils');
  // Browser must stay on the same origin as checkout (LAN / current host).
  // PUBLIC_BASE_URL is only for gateway callbacks — ngrok free interstitial breaks /pay.
  const browserBase = browserBaseUrlFromRequest(req);

  const successUrl = req.body?.successUrl || req.body?.success_url || null;
  const cancelUrl = req.body?.cancelUrl || req.body?.cancel_url || null;
  const demoSessionId = req.body?.demoSessionId || req.body?.demoSession || null;

  return {
    traceId: createTraceId(),
    source: 'checkout-live-init',
    merchant: { apiKey: req.params.apiKey },
    amount: parseFloat(req.body?.amount),
    currency: 'BDT',
    provider: { raw: req.body?.provider, id: null },
    merchantAccountId: req.body?.merchantAccountId
      ?? req.body?.merchant_account_id
      ?? null,
    successUrl: typeof successUrl === 'string' ? successUrl : null,
    cancelUrl: typeof cancelUrl === 'string' ? cancelUrl : null,
    ip: req.ip,
    http: {
      baseUrl: browserBase,
      protocol: req.protocol,
      host: req.get('host'),
    },
    metadata: {
      custom: {},
      extra: demoSessionId ? { demoSessionId: String(demoSessionId) } : {},
    },
  };
}

module.exports = {
  PAYMENT_CONTEXT_VERSION,
  buildContextFromLiveInitRequest,
};
