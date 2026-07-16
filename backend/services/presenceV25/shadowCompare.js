/**
 * Presence v2.5 Phase 3 — Shadow compare legacy vs v2.5.
 * Logs mismatches; increments false_offline_count. No checkout cutover here.
 */
const prisma = require('../../db/prisma');
const policyLoader = require('./policyLoader');
const store = require('./presenceStore');
const metrics = require('./metrics');
const commPolicy = require('../commPolicyService');

let shadowCache = { at: 0, value: false };
const SHADOW_CACHE_MS = 30_000;

async function isShadowModeEnabled() {
  try {
    if (shadowCache.at && Date.now() - shadowCache.at < SHADOW_CACHE_MS) {
      return shadowCache.value;
    }
    const row = await prisma.global_config.findUnique({
      where: { config_key: 'presence_v2_shadow_mode' },
    });
    shadowCache = { at: Date.now(), value: String(row?.config_value || '0') === '1' };
    return shadowCache.value;
  } catch (_) {
    return false;
  }
}

/**
 * Legacy "would show on checkout" — active binding + recent last_seen or socket.
 */
async function legacyDeviceAlive(userId, deviceId) {
  try {
    const watchdog = require('../deviceDisconnectWatchdog');
    return await watchdog.isDeviceCurrentlyActive(userId, deviceId);
  } catch (_) {
    return false;
  }
}

async function v2PredictedOnline(userId, deviceId, packageKey, policy, now = Date.now()) {
  const row = await prisma.registered_devices.findFirst({
    where: {
      user_id: Number(userId),
      device_id: String(deviceId),
      status: 'active',
    },
    select: { device_online: true },
  });
  const dbOnline = row ? Number(row.device_online) === 1 : false;

  const seenMs = await store.getSeenScore(userId, deviceId);
  const hbMs = (Number(policy?.heartbeat_interval_sec) || 300) * 1000;
  const steps = Array.isArray(policy?.probe_steps_json) ? policy.probe_steps_json : [];
  const probeMs = steps.reduce((a, b) => a + (Number(b) || 0) * 1000, 0);
  const deadlineMs = hbMs + probeMs;
  const redisFresh = seenMs > 0 && now - seenMs <= deadlineMs;

  const v2Enabled = await policyLoader.isPresenceV2Enabled(packageKey);
  // When v2 live: trust DB. When shadow-only: prefer Redis freshness prediction.
  if (v2Enabled) return dbOnline;
  return redisFresh || dbOnline;
}

/**
 * Sample up to `limit` active devices and compare legacy vs v2 prediction.
 */
async function runShadowCompare(limit = 50) {
  const shadow = await isShadowModeEnabled();
  if (!shadow && process.env.PRESENCE_V25_SHADOW !== '1') {
    return { compared: 0, mismatches: 0, shadow: false };
  }

  const rows = await prisma.$queryRaw`
    SELECT DISTINCT user_id, device_id
    FROM registered_devices
    WHERE status = 'active'
      AND device_id IS NOT NULL AND device_id != ''
    LIMIT ${limit}
  `;

  let compared = 0;
  let mismatches = 0;

  for (const row of rows || []) {
    const uid = String(row.user_id);
    const did = String(row.device_id);
    compared += 1;

    const profile = await commPolicy.resolveCommProfile(uid);
    const packageKey = profile?.id || 'personal';
    const policy = await policyLoader.getPolicy(packageKey);

    const legacy = await legacyDeviceAlive(uid, did);
    const v2 = await v2PredictedOnline(uid, did, packageKey, policy);

    if (legacy !== v2) {
      mismatches += 1;
      // false_offline: v2 says offline but legacy still considers alive
      if (!v2 && legacy) {
        metrics.inc('false_offline_count', 1);
      }
      console.log(
        `[PresenceV25] SHADOW_MISMATCH user=${uid} device=${did.slice(0, 12)}… `
        + `legacy=${legacy ? 1 : 0} v2=${v2 ? 1 : 0} package=${packageKey}`
      );
    } else if (process.env.PRESENCE_V25_VERBOSE === '1') {
      console.log(
        `[PresenceV25] SHADOW_OK user=${uid} device=${did.slice(0, 12)}… both=${legacy ? 1 : 0}`
      );
    }
  }

  if (mismatches > 0 || process.env.PRESENCE_V25_VERBOSE === '1') {
    console.log(
      `[PresenceV25] SHADOW_SUMMARY compared=${compared} mismatches=${mismatches}`
    );
  }

  return { compared, mismatches, shadow: true };
}

module.exports = {
  runShadowCompare,
  isShadowModeEnabled,
  legacyDeviceAlive,
  v2PredictedOnline,
};
