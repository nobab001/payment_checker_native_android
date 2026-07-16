/**
 * comm_policy loader — MySQL source of truth, Redis cache.
 */
const prisma = require('../../db/prisma');
const { getRedisClient } = require('../redisClient');
const KEYS = require('./keys');

const CACHE_TTL_SEC = 300;
const memoryCache = new Map();
let globalV2Cache = { at: 0, value: false };
const GLOBAL_V2_CACHE_MS = 30 * 1000;

function parseProbeSteps(raw) {
  if (Array.isArray(raw)) return raw.map((n) => Number(n)).filter((n) => n > 0);
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed.map((n) => Number(n)).filter((n) => n > 0) : [];
    } catch (_) {
      return [];
    }
  }
  return [];
}

function deriveOfflineDeadlineSec(policy) {
  const hb = Number(policy.heartbeat_interval_sec) || 300;
  const steps = parseProbeSteps(policy.probe_steps_json);
  return hb + steps.reduce((a, b) => a + b, 0);
}

function normalizePolicy(row) {
  const probeSteps = parseProbeSteps(row.probe_steps_json);
  const policy = {
    package_key: row.package_key,
    heartbeat_interval_sec: Number(row.heartbeat_interval_sec) || 300,
    probe_steps_json: probeSteps,
    worker_sweep_sec: Number(row.worker_sweep_sec) || 30,
    sync_interval_sec: Number(row.sync_interval_sec) || 1800,
    jitter_sec: Number(row.jitter_sec) || 30,
    presence_engine_version: Number(row.presence_engine_version) || 1,
    offline_deadline_sec: 0,
  };
  policy.offline_deadline_sec = deriveOfflineDeadlineSec({
    heartbeat_interval_sec: policy.heartbeat_interval_sec,
    probe_steps_json: probeSteps,
  });
  return policy;
}

async function loadGlobalV2Enabled() {
  try {
    if (globalV2Cache.at && Date.now() - globalV2Cache.at < GLOBAL_V2_CACHE_MS) {
      return globalV2Cache.value;
    }
    const row = await prisma.global_config.findUnique({
      where: { config_key: 'presence_v2_global_enabled' },
    });
    globalV2Cache = { at: Date.now(), value: String(row?.config_value || '0') === '1' };
    return globalV2Cache.value;
  } catch (_) {
    return false;
  }
}

async function fetchPolicyFromDb(packageKey) {
  const rows = await prisma.$queryRaw`
    SELECT package_key, heartbeat_interval_sec, probe_steps_json,
           worker_sweep_sec, sync_interval_sec, jitter_sec, presence_engine_version
    FROM comm_policy
    WHERE package_key = ${String(packageKey)} AND is_active = 1
    LIMIT 1
  `;
  if (!rows?.length) return null;
  return normalizePolicy(rows[0]);
}

async function cachePolicy(packageKey, policy) {
  try {
    const redis = getRedisClient();
    await redis.set(KEYS.policyCache(packageKey), JSON.stringify(policy), 'EX', CACHE_TTL_SEC);
  } catch (err) {
    console.warn('[PresenceV25] policy cache write failed:', err.message);
  }
}

async function getPolicy(packageKey) {
  const key = String(packageKey || 'personal');
  const hit = memoryCache.get(key);
  if (hit && Date.now() - hit.at < CACHE_TTL_SEC * 1000) {
    return hit.policy;
  }

  try {
    const redis = getRedisClient();
    const cached = await redis.get(KEYS.policyCache(key));
    if (cached) {
      const policy = JSON.parse(cached);
      memoryCache.set(key, { at: Date.now(), policy });
      return policy;
    }
  } catch (_) { /* fall through */ }

  const policy = await fetchPolicyFromDb(key);
  if (policy) {
    memoryCache.set(key, { at: Date.now(), policy });
    await cachePolicy(key, policy);
  }
  return policy;
}

async function refreshAllPolicies() {
  const rows = await prisma.$queryRaw`
    SELECT package_key, heartbeat_interval_sec, probe_steps_json,
           worker_sweep_sec, sync_interval_sec, jitter_sec, presence_engine_version
    FROM comm_policy WHERE is_active = 1
  `;
  const out = {};
  for (const row of rows || []) {
    const p = normalizePolicy(row);
    out[p.package_key] = p;
    memoryCache.set(p.package_key, { at: Date.now(), policy: p });
    await cachePolicy(p.package_key, p);
  }
  return out;
}

/**
 * v2.5 engine active for this package when global flag OR package version = 2.
 * Phase 1: worker may run in dry-run even when false (logging only).
 */
async function isPresenceV2Enabled(packageKey) {
  const globalOn = await loadGlobalV2Enabled();
  if (globalOn) return true;
  const policy = await getPolicy(packageKey);
  return Number(policy?.presence_engine_version) === 2;
}

module.exports = {
  getPolicy,
  refreshAllPolicies,
  isPresenceV2Enabled,
  loadGlobalV2Enabled,
  deriveOfflineDeadlineSec,
  parseProbeSteps,
  normalizePolicy,
};
