/**
 * @file Idempotency Manager — duplicate callback / webhook protection.
 * @module payment/idempotency/idempotency-manager
 *
 * Redis-backed with in-memory fallback. Phase-3B wires gateway callbacks here.
 */

const { getRedisClient } = require('../../services/redisClient');

const PREFIX = 'paychek:idempotency:';
const DEFAULT_LOCK_TTL_SEC = 300;
const DEFAULT_COMPLETE_TTL_SEC = 86400;

/** @type {Map<string, { status: string, result?: Object, at: number }>} */
const memoryStore = new Map();

function keyFor(idempotencyKey) {
  return `${PREFIX}${idempotencyKey}`;
}

function memoryGet(key) {
  const row = memoryStore.get(key);
  if (!row) return null;
  return row;
}

/**
 * Check if key was already processed or is in-flight.
 * @param {string} idempotencyKey
 * @returns {Promise<{ exists: boolean, status: string|null, result: Object|null }>}
 */
async function check(idempotencyKey) {
  if (!idempotencyKey) {
    return { exists: false, status: null, result: null };
  }

  const redisKey = keyFor(idempotencyKey);
  try {
    const raw = await getRedisClient().get(redisKey);
    if (raw) {
      const parsed = JSON.parse(raw);
      return {
        exists: true,
        status: parsed.status || null,
        result: parsed.result || null,
      };
    }
  } catch (_) {
    const mem = memoryGet(redisKey);
    if (mem) {
      return { exists: true, status: mem.status, result: mem.result || null };
    }
  }

  return { exists: false, status: null, result: null };
}

/**
 * Acquire processing lock (SET NX). Returns false if already locked/completed.
 * @param {string} idempotencyKey
 * @param {number} [ttlSec]
 * @returns {Promise<{ acquired: boolean }>}
 */
async function lock(idempotencyKey, ttlSec = DEFAULT_LOCK_TTL_SEC) {
  if (!idempotencyKey) return { acquired: false };

  const redisKey = keyFor(idempotencyKey);
  const payload = JSON.stringify({ status: 'processing', at: Date.now() });

  try {
    const ok = await getRedisClient().set(redisKey, payload, 'NX', 'EX', ttlSec);
    return { acquired: ok === 'OK' };
  } catch (_) {
    const existing = memoryGet(redisKey);
    if (existing) return { acquired: false };
    memoryStore.set(redisKey, { status: 'processing', at: Date.now() });
    return { acquired: true };
  }
}

/**
 * Mark idempotency key complete (stores result for replay).
 * @param {string} idempotencyKey
 * @param {Object} [result]
 * @param {number} [ttlSec]
 */
async function complete(idempotencyKey, result = {}, ttlSec = DEFAULT_COMPLETE_TTL_SEC) {
  if (!idempotencyKey) return;

  const redisKey = keyFor(idempotencyKey);
  const payload = JSON.stringify({ status: 'completed', result, at: Date.now() });

  try {
    await getRedisClient().set(redisKey, payload, 'EX', ttlSec);
  } catch (_) {
    memoryStore.set(redisKey, { status: 'completed', result, at: Date.now() });
  }
}

module.exports = {
  check,
  lock,
  complete,
};
