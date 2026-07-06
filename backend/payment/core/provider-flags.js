/**
 * @file Provider capability flags — registry, UI metadata, admin, backend gates.
 * @module payment/core/provider-flags
 */

/**
 * @typedef {Object} ProviderSupports
 * @property {boolean} [redirect]
 * @property {boolean} [callback]
 * @property {boolean} [webhook]
 * @property {boolean} [otp]
 * @property {boolean} [pin]
 * @property {boolean} [commission]
 * @property {boolean} [typeCallback]
 * @property {boolean} [refund]
 * @property {boolean} [capture]
 * @property {boolean} [recurring]
 * @property {boolean} [polling]
 */

/** Default capabilities for redirect-only official gateways (Phase-3A baseline). */
const DEFAULT_SUPPORTS = Object.freeze({
  redirect: true,
  callback: true,
  webhook: false,
  otp: false,
  pin: false,
  commission: false,
  typeCallback: false,
  refund: false,
  capture: false,
  recurring: false,
  polling: false,
});

/**
 * @param {Partial<ProviderSupports>} overrides
 * @returns {ProviderSupports}
 */
function buildSupports(overrides = {}) {
  return { ...DEFAULT_SUPPORTS, ...overrides };
}

module.exports = {
  DEFAULT_SUPPORTS,
  buildSupports,
};
