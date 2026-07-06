/**
 * @file Resolve any provider alias → canonical registry id.
 * @module payment/registry/provider-alias
 *
 * Bridges frontend (bkash_live) ↔ legacy DB (bkash_merchant) without migrations.
 */

const { PROVIDER_REGISTRY } = require('./provider-registry');

/** @type {Map<string, string>} alias → canonicalId */
const ALIAS_INDEX = new Map();

for (const entry of Object.values(PROVIDER_REGISTRY)) {
  ALIAS_INDEX.set(entry.id.toLowerCase(), entry.id);
  for (const alias of entry.aliases || []) {
    ALIAS_INDEX.set(String(alias).toLowerCase(), entry.id);
  }
  if (entry.dbProviderKey) {
    ALIAS_INDEX.set(String(entry.dbProviderKey).toLowerCase(), entry.id);
  }
}

/**
 * @param {string} raw — any alias, db key, or canonical id
 * @returns {string|null} canonical id
 */
function resolveProviderId(raw) {
  if (!raw) return null;
  return ALIAS_INDEX.get(String(raw).trim().toLowerCase()) || null;
}

/**
 * @param {string} raw
 * @returns {import('./provider-registry').ProviderRegistryEntry|null}
 */
function resolveProviderEntry(raw) {
  const id = resolveProviderId(raw);
  return id ? PROVIDER_REGISTRY[id] : null;
}

/** Legacy DB column value for website_official_gateways.provider */
function toDbProviderKey(canonicalId) {
  const entry = PROVIDER_REGISTRY[canonicalId];
  return entry?.dbProviderKey || canonicalId;
}

module.exports = {
  resolveProviderId,
  resolveProviderEntry,
  toDbProviderKey,
  ALIAS_INDEX,
};
