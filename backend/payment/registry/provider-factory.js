/**
 * @file Provider Factory — resolve adapter instance by canonical or alias id.
 * @module payment/registry/provider-factory
 *
 * No if-else chains. Registry adapter field → dynamic require.
 */

const { resolveProviderId } = require('./provider-alias');
const { getRegistryEntry } = require('./provider-registry');
const { ProviderError, PROVIDER_ERROR_CODES } = require('../shared/provider-errors');

/** @type {Map<string, import('../providers/base-provider')>} */
const ADAPTER_CACHE = new Map();

const ADAPTER_MODULES = Object.freeze({
  'bkash-live': () => require('../providers/bkash-live'),
  'nagad-live': () => require('../providers/nagad-live'),
  'rocket-live': () => require('../providers/rocket-live'),
  sslcommerz: () => require('../providers/sslcommerz'),
  surjopay: () => require('../providers/surjopay'),
  portwallet: () => require('../providers/portwallet'),
  bank: () => require('../providers/bank'),
  card: () => require('../providers/card'),
});

/**
 * @param {string} providerIdOrAlias
 * @returns {import('../providers/base-provider')}
 */
function getProvider(providerIdOrAlias) {
  const canonicalId = resolveProviderId(providerIdOrAlias);
  if (!canonicalId) {
    throw new ProviderError(PROVIDER_ERROR_CODES.UNKNOWN_PROVIDER, `Unknown provider: ${providerIdOrAlias}`);
  }

  if (ADAPTER_CACHE.has(canonicalId)) {
    return ADAPTER_CACHE.get(canonicalId);
  }

  const entry = getRegistryEntry(canonicalId);
  const loader = ADAPTER_MODULES[entry.adapter];
  if (!loader) {
    throw new ProviderError(
      PROVIDER_ERROR_CODES.ADAPTER_NOT_FOUND,
      `No adapter loader for: ${entry.adapter}`,
    );
  }

  const AdapterClass = loader();
  const instance = new AdapterClass(entry);
  ADAPTER_CACHE.set(canonicalId, instance);
  return instance;
}

function clearAdapterCache() {
  ADAPTER_CACHE.clear();
}

module.exports = {
  getProvider,
  clearAdapterCache,
};
