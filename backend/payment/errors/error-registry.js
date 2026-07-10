/**
 * @file PayChek Error Registry — single source of truth for payment errors.
 * @module payment/errors/error-registry
 *
 * Used by PaymentEngine, Callback Engine, Merchant API, Admin Panel.
 * Legacy string messages preserved for backward-compatible API responses.
 */

const { PAY_ERROR_CODES } = require('./error-codes');

/**
 * @typedef {Object} ErrorRegistryEntry
 * @property {string} code — PAY_1xxx
 * @property {string} message — human-readable (API `error` field)
 * @property {number} httpStatus
 * @property {string} [legacyKey] — internal ProviderError code
 */

/** @type {Record<string, ErrorRegistryEntry>} */
const ERROR_REGISTRY = Object.freeze({
  [PAY_ERROR_CODES.INVALID_API_KEY]: {
    code: PAY_ERROR_CODES.INVALID_API_KEY,
    message: 'Invalid API Key',
    httpStatus: 404,
    legacyKey: 'INVALID_API_KEY',
  },
  [PAY_ERROR_CODES.PROVIDER_DISABLED]: {
    code: PAY_ERROR_CODES.PROVIDER_DISABLED,
    message: 'PROVIDER_NOT_CONFIGURED',
    httpStatus: 400,
    legacyKey: 'PROVIDER_DISABLED',
  },
  [PAY_ERROR_CODES.PROVIDER_MAINTENANCE]: {
    code: PAY_ERROR_CODES.PROVIDER_MAINTENANCE,
    message: 'PROVIDER_NOT_CONFIGURED',
    httpStatus: 400,
    legacyKey: 'PROVIDER_MAINTENANCE',
  },
  [PAY_ERROR_CODES.INVALID_SESSION]: {
    code: PAY_ERROR_CODES.INVALID_SESSION,
    message: 'SESSION_NOT_FOUND',
    httpStatus: 404,
    legacyKey: 'INVALID_SESSION',
  },
  [PAY_ERROR_CODES.INVALID_SIGNATURE]: {
    code: PAY_ERROR_CODES.INVALID_SIGNATURE,
    message: 'INVALID_SIGNATURE',
    httpStatus: 401,
    legacyKey: 'INVALID_SIGNATURE',
  },
  [PAY_ERROR_CODES.DUPLICATE_CALLBACK]: {
    code: PAY_ERROR_CODES.DUPLICATE_CALLBACK,
    message: 'DUPLICATE_CALLBACK',
    httpStatus: 409,
    legacyKey: 'DUPLICATE_CALLBACK',
  },
  [PAY_ERROR_CODES.PAYMENT_EXPIRED]: {
    code: PAY_ERROR_CODES.PAYMENT_EXPIRED,
    message: 'SESSION_EXPIRED',
    httpStatus: 410,
    legacyKey: 'SESSION_EXPIRED',
  },
  [PAY_ERROR_CODES.TRANSITION_BLOCKED]: {
    code: PAY_ERROR_CODES.TRANSITION_BLOCKED,
    message: 'INVALID_STATE_TRANSITION',
    httpStatus: 409,
    legacyKey: 'INVALID_STATE_TRANSITION',
  },
  [PAY_ERROR_CODES.MISSING_PARAMS]: {
    code: PAY_ERROR_CODES.MISSING_PARAMS,
    message: 'apiKey, provider, amount আবশ্যক।',
    httpStatus: 400,
    legacyKey: 'MISSING_PARAMS',
  },
  [PAY_ERROR_CODES.INVALID_AMOUNT]: {
    code: PAY_ERROR_CODES.INVALID_AMOUNT,
    message: 'INVALID_AMOUNT',
    httpStatus: 400,
    legacyKey: 'INVALID_AMOUNT',
  },
  [PAY_ERROR_CODES.PROVIDER_NOT_CONFIGURED]: {
    code: PAY_ERROR_CODES.PROVIDER_NOT_CONFIGURED,
    message: 'PROVIDER_NOT_CONFIGURED',
    httpStatus: 400,
    legacyKey: 'NOT_CONFIGURED',
  },
  [PAY_ERROR_CODES.UNKNOWN_PROVIDER]: {
    code: PAY_ERROR_CODES.UNKNOWN_PROVIDER,
    message: 'UNKNOWN_PROVIDER',
    httpStatus: 400,
    legacyKey: 'UNKNOWN_PROVIDER',
  },
  [PAY_ERROR_CODES.ADAPTER_NOT_FOUND]: {
    code: PAY_ERROR_CODES.ADAPTER_NOT_FOUND,
    message: 'ADAPTER_NOT_FOUND',
    httpStatus: 500,
    legacyKey: 'ADAPTER_NOT_FOUND',
  },
  [PAY_ERROR_CODES.REDIRECT_FAILED]: {
    code: PAY_ERROR_CODES.REDIRECT_FAILED,
    message: 'REDIRECT_FAILED',
    httpStatus: 500,
    legacyKey: 'REDIRECT_FAILED',
  },
  [PAY_ERROR_CODES.CALLBACK_NOT_IMPLEMENTED]: {
    code: PAY_ERROR_CODES.CALLBACK_NOT_IMPLEMENTED,
    message: 'CALLBACK_NOT_IMPLEMENTED',
    httpStatus: 501,
    legacyKey: 'CALLBACK_NOT_IMPLEMENTED',
  },
  [PAY_ERROR_CODES.INTERNAL_ERROR]: {
    code: PAY_ERROR_CODES.INTERNAL_ERROR,
    message: 'Internal Server Error',
    httpStatus: 500,
    legacyKey: 'INTERNAL_ERROR',
  },
});

/** legacyKey / PAY code / message → registry entry */
const LEGACY_INDEX = new Map();
for (const entry of Object.values(ERROR_REGISTRY)) {
  LEGACY_INDEX.set(entry.code, entry);
  if (entry.legacyKey) LEGACY_INDEX.set(entry.legacyKey, entry);
  LEGACY_INDEX.set(entry.message, entry);
}

/**
 * @param {string} key — PAY_1xxx, legacyKey, or message
 * @returns {ErrorRegistryEntry|null}
 */
function resolveError(key) {
  if (!key) return null;
  return LEGACY_INDEX.get(key) || ERROR_REGISTRY[key] || null;
}

/**
 * @param {string} payCode — PAY_1xxx
 * @returns {{ success: false, error: string, errorCode: string }}
 */
function toApiError(payCode) {
  const entry = ERROR_REGISTRY[payCode] || ERROR_REGISTRY[PAY_ERROR_CODES.INTERNAL_ERROR];
  return {
    success: false,
    error: entry.message,
    errorCode: entry.code,
  };
}

/**
 * Map ProviderError → API response shape (backward-compatible + errorCode).
 * @param {import('../shared/provider-errors').ProviderError} err
 */
function fromProviderError(err) {
  const key = typeof err === 'string'
    ? err
    : (err?.payCode || err?.code || err?.message);
  const entry = resolveError(key) || resolveError(err?.message);
  if (entry) {
    return { body: toApiError(entry.code), httpStatus: entry.httpStatus };
  }
  return {
    body: toApiError(PAY_ERROR_CODES.INTERNAL_ERROR),
    httpStatus: 500,
  };
}

class PaymentError extends Error {
  /**
   * @param {string} payCode — PAY_1xxx
   * @param {Object} [meta]
   */
  constructor(payCode, meta = {}) {
    const entry = ERROR_REGISTRY[payCode] || ERROR_REGISTRY[PAY_ERROR_CODES.INTERNAL_ERROR];
    super(entry.message);
    this.name = 'PaymentError';
    this.payCode = entry.code;
    this.httpStatus = entry.httpStatus;
    this.legacyKey = entry.legacyKey;
    this.meta = meta;
  }
}

module.exports = {
  PAY_ERROR_CODES,
  ERROR_REGISTRY,
  resolveError,
  toApiError,
  fromProviderError,
  PaymentError,
};
