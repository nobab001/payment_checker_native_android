/**
 * @file Provider Version Contract — FROZEN v1.
 * @module payment/core/provider-version-contract
 *
 * Version chain:
 *   Adapter v1.0 → Provider API vX.Y → Merchant Callback v1.0
 */

const MERCHANT_CALLBACK_VERSION = '1.0';
const ADAPTER_CONTRACT_VERSION = '1.0';

/**
 * @typedef {Object} ProviderVersionContract
 * @property {string} adapterVersion — PayChek adapter semver
 * @property {string} contractVersion — adapter interface contract
 * @property {string|null} providerApiVersion — upstream gateway API version
 * @property {string} merchantCallbackVersion — frozen merchant webhook schema
 */

/**
 * @param {import('../registry/provider-registry').ProviderRegistryEntry} entry
 * @returns {ProviderVersionContract}
 */
function buildVersionContract(entry) {
  return {
    adapterVersion: entry.adapterVersion || '1.0',
    contractVersion: entry.contractVersion || ADAPTER_CONTRACT_VERSION,
    providerApiVersion: entry.providerApiVersion ?? null,
    merchantCallbackVersion: entry.merchantCallbackVersion || MERCHANT_CALLBACK_VERSION,
  };
}

module.exports = {
  MERCHANT_CALLBACK_VERSION,
  ADAPTER_CONTRACT_VERSION,
  buildVersionContract,
};
