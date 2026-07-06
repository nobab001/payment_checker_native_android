/**
 * @file Normalized merchant callback payload — all providers converge here (Phase-3B).
 * @module payment/core/callback-schema
 *
 * Phase-3A: schema + stub builder only. No signature / delivery logic.
 */

const { PAYMENT_STATUS } = require('./payment-status');

/**
 * @typedef {Object} NormalizedCallbackPayload
 * @property {string} paymentId
 * @property {string} provider
 * @property {string} providerId
 * @property {'SIM'|'LIVE'|'BANK'|'CARD'} providerType
 * @property {keyof typeof PAYMENT_STATUS} status
 * @property {number} amount
 * @property {string} currency
 * @property {string} [transactionId]
 * @property {string} [providerTransactionId]
 * @property {string} [providerReference]
 * @property {'PAYMENT'|'REFUND'|'CHARGE'} [type]
 * @property {number} [commission]
 * @property {number} [charge]
 * @property {string} [orderId]
 * @property {string} [sessionId]
 * @property {string} [completedAt]
 */

/**
 * Build a normalized callback payload (stub — no provider-specific mapping yet).
 * @param {Partial<NormalizedCallbackPayload>} fields
 * @returns {NormalizedCallbackPayload}
 */
function buildNormalizedCallback(fields = {}) {
  return {
    paymentId: fields.paymentId || '',
    provider: fields.provider || '',
    providerId: fields.providerId || '',
    providerType: fields.providerType || 'LIVE',
    status: fields.status || PAYMENT_STATUS.PENDING,
    amount: Number(fields.amount) || 0,
    currency: fields.currency || 'BDT',
    transactionId: fields.transactionId || null,
    providerTransactionId: fields.providerTransactionId || null,
    providerReference: fields.providerReference || null,
    type: fields.type || 'PAYMENT',
    commission: fields.commission ?? 0,
    charge: fields.charge ?? 0,
    orderId: fields.orderId || null,
    sessionId: fields.sessionId || null,
    completedAt: fields.completedAt || null,
  };
}

/** JSON Schema shape (documentation contract — validate in Phase-3B). */
const NORMALIZED_CALLBACK_SCHEMA = Object.freeze({
  $id: 'paychek://schemas/normalized-callback/v1',
  type: 'object',
  required: ['paymentId', 'provider', 'providerId', 'providerType', 'status', 'amount', 'currency'],
  properties: {
    paymentId: { type: 'string' },
    provider: { type: 'string' },
    providerId: { type: 'string' },
    providerType: { enum: ['SIM', 'LIVE', 'BANK', 'CARD'] },
    status: { type: 'string' },
    amount: { type: 'number' },
    currency: { type: 'string' },
    transactionId: { type: ['string', 'null'] },
    providerTransactionId: { type: ['string', 'null'] },
    providerReference: { type: ['string', 'null'] },
    type: { enum: ['PAYMENT', 'REFUND', 'CHARGE'] },
    commission: { type: 'number' },
    charge: { type: 'number' },
    orderId: { type: ['string', 'null'] },
    sessionId: { type: ['string', 'null'] },
    completedAt: { type: ['string', 'null'] },
  },
});

module.exports = {
  buildNormalizedCallback,
  NORMALIZED_CALLBACK_SCHEMA,
};
