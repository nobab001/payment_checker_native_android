/**
 * @file PaymentContext v1 — FROZEN root shape.
 * @module payment/core/payment-context-v1
 *
 * RULE: Do NOT add new root-level fields after v1 freeze.
 * Extensions MUST go inside:
 *   - metadata
 *   - metadata.custom
 *   - metadata.extra
 */

const PAYMENT_CONTEXT_VERSION = '1.0';

/** @readonly Root-level keys allowed in PaymentContext v1 */
const PAYMENT_CONTEXT_V1_ROOT_KEYS = Object.freeze([
  'traceId',
  'source',
  'merchant',
  'website',
  'customer',
  'amount',
  'currency',
  'provider',
  'callbackUrl',
  'returnUrl',
  'successUrl',
  'cancelUrl',
  'ip',
  'device',
  'metadata',
  'http',
  'merchantConfig',
  'sessionToken',
]);

/**
 * @param {Object} ctx
 * @returns {string[]} warnings — unknown root keys
 */
function validatePaymentContextV1(ctx) {
  const warnings = [];
  if (!ctx || typeof ctx !== 'object') return ['context must be object'];
  for (const key of Object.keys(ctx)) {
    if (!PAYMENT_CONTEXT_V1_ROOT_KEYS.includes(key)) {
      warnings.push(`Non-frozen root key: ${key} — move to metadata.custom or metadata.extra`);
    }
  }
  return warnings;
}

module.exports = {
  PAYMENT_CONTEXT_VERSION,
  PAYMENT_CONTEXT_V1_ROOT_KEYS,
  validatePaymentContextV1,
};
