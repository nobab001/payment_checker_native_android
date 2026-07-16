/**
 * Redis presence store — Phase 1: read/write seen ZSET + probe/lock skeleton.
 * No MySQL updates in Phase 1.
 */
const { getRedisClient } = require('../redisClient');
const KEYS = require('./keys');

const LOCK_BUFFER_SEC = 120;
const DEFAULT_LOCK_TTL_SEC = 600;

function computeLockTtlSec(policy) {
  const offlineDeadline = Number(policy?.offline_deadline_sec) || 660;
  return offlineDeadline + LOCK_BUFFER_SEC;
}

async function touchSeen(userId, deviceId, scoreMs = Date.now()) {
  const redis = getRedisClient();
  const key = KEYS.seenZset(userId);
  const member = KEYS.seenMember(deviceId);
  await redis.zadd(key, scoreMs, member);
  return { key, member, scoreMs };
}

async function removeSeen(userId, deviceId) {
  const redis = getRedisClient();
  return redis.zrem(KEYS.seenZset(userId), KEYS.seenMember(deviceId));
}

async function getSeenScore(userId, deviceId) {
  const redis = getRedisClient();
  const score = await redis.zscore(KEYS.seenZset(userId), KEYS.seenMember(deviceId));
  return score ? Number(score) : 0;
}

async function findExpiredDevices(userId, beforeScoreMs, limit = 100) {
  const redis = getRedisClient();
  const key = KEYS.seenZset(userId);
  const members = await redis.zrangebyscore(key, '-inf', beforeScoreMs, 'LIMIT', 0, limit);
  return members.map(String);
}

async function acquireProbeLock(userId, deviceId, meta = {}) {
  const redis = getRedisClient();
  const key = KEYS.lockKey(userId, deviceId);
  const workerId = meta.workerId || `worker:${process.pid}`;
  const probeStage = Number(meta.probeStage) || 0;
  const ttlSec = Number(meta.ttlSec) || DEFAULT_LOCK_TTL_SEC;
  const payload = JSON.stringify({
    workerId,
    ts: Date.now(),
    probeStage,
  });
  const ok = await redis.set(key, payload, 'EX', ttlSec, 'NX');
  return ok === 'OK';
}

async function releaseProbeLock(userId, deviceId) {
  try {
    const redis = getRedisClient();
    await redis.del(KEYS.lockKey(userId, deviceId));
  } catch (_) { /* ignore */ }
}

async function getProbeState(userId, deviceId) {
  const redis = getRedisClient();
  return redis.hgetall(KEYS.probeHash(userId, deviceId));
}

async function setProbeState(userId, deviceId, fields) {
  const redis = getRedisClient();
  const key = KEYS.probeHash(userId, deviceId);
  if (!fields || !Object.keys(fields).length) return;
  await redis.hset(key, fields);
}

async function clearProbeState(userId, deviceId) {
  try {
    const redis = getRedisClient();
    await redis.del(KEYS.probeHash(userId, deviceId));
  } catch (_) { /* ignore */ }
}

function recoveryAtKey(userId, deviceId) {
  return `presence:recovery_at:${String(userId)}:${String(deviceId)}`;
}

async function markOfflineAt(userId, deviceId, atMs = Date.now()) {
  const redis = getRedisClient();
  await redis.set(recoveryAtKey(userId, deviceId), String(atMs), 'EX', 7 * 24 * 3600);
}

async function takeRecoveryMs(userId, deviceId) {
  const redis = getRedisClient();
  const key = recoveryAtKey(userId, deviceId);
  const raw = await redis.get(key);
  if (!raw) return null;
  await redis.del(key);
  const offlineAt = Number(raw);
  if (!offlineAt) return null;
  return Date.now() - offlineAt;
}

module.exports = {
  touchSeen,
  removeSeen,
  getSeenScore,
  findExpiredDevices,
  acquireProbeLock,
  releaseProbeLock,
  getProbeState,
  setProbeState,
  clearProbeState,
  computeLockTtlSec,
  markOfflineAt,
  takeRecoveryMs,
  LOCK_BUFFER_SEC,
  DEFAULT_LOCK_TTL_SEC,
};
