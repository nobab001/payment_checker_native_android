/**
 * @file Provider Registry cache — Redis-backed capability lookup.
 * @module payment/registry/provider-cache
 *
 * Registry → Redis → PaymentEngine. Degrades to in-memory registry on Redis miss/fail.
 */

const { getRedisClient } = require('../../services/redisClient');
const { PROVIDER_REGISTRY, getRegistryEntry, listRegistryEntries } = require('./provider-registry');

const PREFIX = 'paychek:registry:';
const ALL_KEY = `${PREFIX}all`;
const DEFAULT_TTL_SEC = 300;

/** @type {typeof PROVIDER_REGISTRY|null} */
let memorySnapshot = null;

async function safeRedisGet(key) {
  try {
    return await getRedisClient().get(key);
  } catch (_) {
    return null;
  }
}

async function safeRedisSet(key, value, ttl = DEFAULT_TTL_SEC) {
  try {
    await getRedisClient().set(key, value, 'EX', ttl);
    return true;
  } catch (_) {
    return false;
  }
}

async function warmCache() {
  const json = JSON.stringify(PROVIDER_REGISTRY);
  memorySnapshot = PROVIDER_REGISTRY;
  await safeRedisSet(ALL_KEY, json);
}

/**
 * @param {string} canonicalId
 */
async function getCachedProviderEntry(canonicalId) {
  if (!canonicalId) return null;

  const itemKey = `${PREFIX}provider:${canonicalId}`;
  const cachedItem = await safeRedisGet(itemKey);
  if (cachedItem) {
    try { return JSON.parse(cachedItem); } catch (_) { /* fall through */ }
  }

  const entry = getRegistryEntry(canonicalId);
  if (entry) {
    await safeRedisSet(itemKey, JSON.stringify(entry));
  }
  return entry;
}

async function getCachedRegistryAll() {
  const cached = await safeRedisGet(ALL_KEY);
  if (cached) {
    try { return JSON.parse(cached); } catch (_) { /* fall through */ }
  }

  if (!memorySnapshot) {
    memorySnapshot = Object.fromEntries(
      listRegistryEntries().map((e) => [e.id, e]),
    );
  }
  await warmCache();
  return memorySnapshot;
}

async function invalidateProviderCache(canonicalId) {
  try {
    if (canonicalId) {
      await getRedisClient().del(`${PREFIX}provider:${canonicalId}`);
    }
    await getRedisClient().del(ALL_KEY);
  } catch (_) { /* best-effort */ }
  memorySnapshot = null;
}

module.exports = {
  warmCache,
  getCachedProviderEntry,
  getCachedRegistryAll,
  invalidateProviderCache,
};
