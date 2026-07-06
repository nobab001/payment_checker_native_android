/**
 * @file Merchant Callback Contract v1.0 — FROZEN.
 * @module payment/core/merchant-callback-v1
 *
 * Do not break this shape. Add fields only via v2 with version header.
 */

const { PAYMENT_STATUS } = require('./payment-status');

const MERCHANT_CALLBACK_V1_VERSION = '1.0';

/**
 * @typedef {Object} MerchantCallbackV1
 * @property {string} paymentId
 * @property {string} merchantId
 * @property {string} websiteId
 * @property {string} provider — canonical registry id e.g. bkash_live
 * @property {string} [providerTransactionId]
 * @property {string} [merchantTransactionId]
 * @property {number} amount
 * @property {keyof typeof PAYMENT_STATUS} status
 * @property {string} [type] — e.g. bkash_personal
 * @property {number} [commission]
 * @property {string} traceId
 * @property {string} timestamp — ISO-8601
 * @property {string} [orderId]
 * @property {string} [sessionId]
 * @property {string} currency
 */

/** Frozen JSON Schema (documentation + Phase-3B validation). */
const MERCHANT_CALLBACK_V1_SCHEMA = Object.freeze({
  $id: 'paychek://schemas/merchant-callback/v1.0',
  version: MERCHANT_CALLBACK_V1_VERSION,
  type: 'object',
  required: [
    'paymentId', 'merchantId', 'websiteId', 'provider',
    'amount', 'status', 'traceId', 'timestamp', 'currency',
  ],
  properties: {
    paymentId: { type: 'string' },
    merchantId: { type: 'string' },
    websiteId: { type: 'string' },
    provider: { type: 'string' },
    providerTransactionId: { type: ['string', 'null'] },
    merchantTransactionId: { type: ['string', 'null'] },
    amount: { type: 'number' },
    status: { type: 'string' },
    type: { type: ['string', 'null'] },
    commission: { type: 'number' },
    traceId: { type: 'string' },
    timestamp: { type: 'string', format: 'date-time' },
    orderId: { type: ['string', 'null'] },
    sessionId: { type: ['string', 'null'] },
    currency: { type: 'string' },
  },
  additionalProperties: false,
});

/**
 * @param {Partial<MerchantCallbackV1>} fields
 * @returns {MerchantCallbackV1}
 */
function buildMerchantCallbackV1(fields = {}) {
  return {
    paymentId: fields.paymentId || '',
    merchantId: String(fields.merchantId ?? ''),
    websiteId: String(fields.websiteId ?? ''),
    provider: fields.provider || '',
    providerTransactionId: fields.providerTransactionId || null,
    merchantTransactionId: fields.merchantTransactionId || null,
    amount: Number(fields.amount) || 0,
    status: fields.status || PAYMENT_STATUS.PENDING,
    type: fields.type || null,
    commission: fields.commission ?? 0,
    traceId: fields.traceId || '',
    timestamp: fields.timestamp || new Date().toISOString(),
    orderId: fields.orderId || null,
    sessionId: fields.sessionId || null,
    currency: fields.currency || 'BDT',
  };
}

/**
 * Lightweight validation (Phase-3B may use ajv).
 * @param {Object} payload
 * @returns {string[]} errors
 */
function validateMerchantCallbackV1(payload) {
  const errors = [];
  if (!payload || typeof payload !== 'object') return ['payload must be object'];
  for (const key of MERCHANT_CALLBACK_V1_SCHEMA.required) {
    if (payload[key] === undefined || payload[key] === null || payload[key] === '') {
      if (key !== 'commission' && key !== 'type') errors.push(`Missing: ${key}`);
    }
  }
  if (payload.amount != null && !(Number(payload.amount) >= 0)) {
    errors.push('amount must be >= 0');
  }
  return errors;
}

module.exports = {
  MERCHANT_CALLBACK_V1_VERSION,
  MERCHANT_CALLBACK_V1_SCHEMA,
  buildMerchantCallbackV1,
  validateMerchantCallbackV1,
};
