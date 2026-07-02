/**
 * merchantCache.js — Redis-backed cache for merchant (gateway_layouts) lookups
 * by api_key (Phase 8).
 *
 * Rationale: the checkout / payment-init hot paths look up the merchant row on
 * every request. This adds a short-TTL cache with explicit invalidation on any
 * website mutation. It degrades gracefully: if Redis is unavailable the DB is
 * always queried, so correctness is never sacrificed for the cache.
 *
 * IMPORTANT: values are cached per (api_key + select signature). Callers pass a
 * loader that performs the actual Prisma query, so this module never bakes in a
 * specific column set (avoids stale/partial rows across call sites).
 */

const { getRedisClient } = require('./redisClient');

const redis = getRedisClient();
const DEFAULT_TTL_SEC = 30;
const PREFIX = 'paychek:merchant:apikey:';

function keyFor(apiKey, tag) {
  return `${PREFIX}${tag}:${apiKey}`;
}

/**
 * Get a merchant row by api_key, using cache when available.
 * @param {string} apiKey
 * @param {string} tag        cache namespace tag identifying the select signature
 * @param {() => Promise<object|null>} loader  performs the DB query on miss
 * @param {number} [ttl]
 */
async function getByApiKey(apiKey, tag, loader, ttl = DEFAULT_TTL_SEC) {
  if (!apiKey) return loader();
  const cacheKey = keyFor(apiKey, tag);
  try {
    const cached = await redis.get(cacheKey);
    if (cached) return JSON.parse(cached);
  } catch (_) { /* cache read failed — fall through to DB */ }

  const row = await loader();
  if (row) {
    try { await redis.set(cacheKey, JSON.stringify(row), 'EX', ttl); } catch (_) { /* ignore */ }
  }
  return row;
}

/** Invalidate every cached variant for an api_key (call on any website mutation). */
async function invalidate(apiKey) {
  if (!apiKey) return;
  try {
    const keys = await redis.keys(`${PREFIX}*:${apiKey}`);
    if (keys && keys.length) await redis.del(...keys);
  } catch (_) { /* best-effort */ }
}

module.exports = { getByApiKey, invalidate };
