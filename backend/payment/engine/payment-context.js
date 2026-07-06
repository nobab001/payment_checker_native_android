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
  const baseUrl = process.env.PUBLIC_BASE_URL
    || `${req.headers['x-forwarded-proto'] || req.protocol}://${req.get('host')}`;

  return {
    traceId: createTraceId(),
    source: 'checkout-live-init',
    merchant: { apiKey: req.params.apiKey },
    amount: parseFloat(req.body?.amount),
    currency: 'BDT',
    provider: { raw: req.body?.provider, id: null },
    ip: req.ip,
    http: { baseUrl, protocol: req.protocol, host: req.get('host') },
    metadata: { custom: {}, extra: {} },
  };
}

module.exports = {
  PAYMENT_CONTEXT_VERSION,
  buildContextFromLiveInitRequest,
};
