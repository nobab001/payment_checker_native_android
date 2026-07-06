/**
 * @file Shared payment engine errors.
 * @module payment/shared/provider-errors
 */

const PROVIDER_ERROR_CODES = Object.freeze({
  UNKNOWN_PROVIDER: 'UNKNOWN_PROVIDER',
  ADAPTER_NOT_FOUND: 'ADAPTER_NOT_FOUND',
  NOT_CONFIGURED: 'NOT_CONFIGURED',
  SESSION_EXPIRED: 'SESSION_EXPIRED',
  INVALID_AMOUNT: 'INVALID_AMOUNT',
  REDIRECT_FAILED: 'REDIRECT_FAILED',
  CALLBACK_NOT_IMPLEMENTED: 'CALLBACK_NOT_IMPLEMENTED',
  NORMALIZE_NOT_IMPLEMENTED: 'NORMALIZE_NOT_IMPLEMENTED',
  PROVIDER_DISABLED: 'PROVIDER_DISABLED',
  PROVIDER_MAINTENANCE: 'PROVIDER_MAINTENANCE',
  INVALID_STATE_TRANSITION: 'INVALID_STATE_TRANSITION',
});

class ProviderError extends Error {
  /**
   * @param {string} code
   * @param {string} message
   * @param {Object} [meta]
   */
  constructor(code, message, meta = {}) {
    super(message);
    this.name = 'ProviderError';
    this.code = code;
    this.meta = meta;
  }
}

module.exports = {
  PROVIDER_ERROR_CODES,
  ProviderError,
};
