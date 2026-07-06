/**
 * @file Frozen PayChek error code constants (v1).
 * @module payment/errors/error-codes
 *
 * Format: PAY_1xxx — do not renumber. Add new codes only at end of range.
 */

/** @readonly */
const PAY_ERROR_CODES = Object.freeze({
  INVALID_API_KEY: 'PAY_1001',
  PROVIDER_DISABLED: 'PAY_1002',
  PROVIDER_MAINTENANCE: 'PAY_1003',
  INVALID_SESSION: 'PAY_1004',
  INVALID_SIGNATURE: 'PAY_1005',
  DUPLICATE_CALLBACK: 'PAY_1006',
  PAYMENT_EXPIRED: 'PAY_1007',
  TRANSITION_BLOCKED: 'PAY_1008',
  MISSING_PARAMS: 'PAY_1009',
  INVALID_AMOUNT: 'PAY_1010',
  PROVIDER_NOT_CONFIGURED: 'PAY_1011',
  UNKNOWN_PROVIDER: 'PAY_1012',
  ADAPTER_NOT_FOUND: 'PAY_1013',
  REDIRECT_FAILED: 'PAY_1014',
  CALLBACK_NOT_IMPLEMENTED: 'PAY_1015',
  INTERNAL_ERROR: 'PAY_1999',
});

module.exports = { PAY_ERROR_CODES };
