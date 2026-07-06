/**
 * @file Payment + session lifecycle statuses.
 * @module payment/core/payment-status
 */

/** @readonly */
const PAYMENT_STATUS = Object.freeze({
  CREATED: 'CREATED',
  PENDING: 'PENDING',
  REDIRECTED: 'REDIRECTED',
  PROCESSING: 'PROCESSING',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED',
  EXPIRED: 'EXPIRED',
  TIMEOUT: 'TIMEOUT',
});

/** Maps legacy DB lowercase statuses → canonical PAYMENT_STATUS */
const LEGACY_STATUS_MAP = Object.freeze({
  created: PAYMENT_STATUS.CREATED,
  redirected: PAYMENT_STATUS.REDIRECTED,
  completed: PAYMENT_STATUS.SUCCESS,
  failed: PAYMENT_STATUS.FAILED,
  expired: PAYMENT_STATUS.EXPIRED,
  cancelled: PAYMENT_STATUS.CANCELLED,
});

function toCanonicalStatus(status) {
  const s = String(status || '').toUpperCase();
  if (PAYMENT_STATUS[s]) return PAYMENT_STATUS[s];
  return LEGACY_STATUS_MAP[String(status || '').toLowerCase()] || PAYMENT_STATUS.PENDING;
}

module.exports = {
  PAYMENT_STATUS,
  LEGACY_STATUS_MAP,
  toCanonicalStatus,
};
