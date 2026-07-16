/**
 * Presence v2.5 Phase 2 — Entry points:
 * - markDeviceAlive(): called on successful server communication events only.
 * - markDeviceOffline(): called ONLY by the Phase 2 worker.
 *
 * Logging + in-memory counters are implemented here to keep Phase 2 observable.
 */
const prisma = require('../../db/prisma');
const commPolicy = require('../commPolicyService');
const policyLoader = require('./policyLoader');
const store = require('./presenceStore');
const metrics = require('./metrics');

const DRY_RUN = process.env.PRESENCE_V25_DRY_RUN === '1';

const RESOLVE_CACHE_MS = 60 * 1000;
const packageKeyCache = new Map(); // userId -> { at, packageKey }

async function resolvePackageKeyForUser(userId) {
  const uid = String(userId);
  const hit = packageKeyCache.get(uid);
  if (hit && Date.now() - hit.at < RESOLVE_CACHE_MS) return hit.packageKey;
  const profile = await commPolicy.resolveCommProfile(uid);
  const packageKey = profile?.id || 'personal';
  packageKeyCache.set(uid, { at: Date.now(), packageKey });
  return packageKey;
}

async function getCurrentOnlineState(userId, deviceId) {
  // Only active devices matter for DB flips (checkout will also depend on active bindings).
  const row = await prisma.registered_devices.findFirst({
    where: {
      user_id: Number(userId),
      device_id: String(deviceId),
      status: 'active',
    },
    select: { device_online: true },
  });
  return row ? Number(row.device_online) : null;
}

/**
 * markDeviceAlive(userId, deviceId, meta?)
 * meta.source examples: HB_SUCCESS, SMS_SUCCESS, LOGIN_SUCCESS
 */
async function markDeviceAlive(userId, deviceId, meta = {}) {
  const { source = 'HB_SUCCESS', packageKey: providedPackageKey } = meta;
  if (userId === undefined || userId === null) return;
  if (!deviceId) return;

  const packageKey = providedPackageKey || (await resolvePackageKeyForUser(userId));
  const enabled = await policyLoader.isPresenceV2Enabled(packageKey);
  const shadow = await (async () => {
    try {
      return await require('./shadowCompare').isShadowModeEnabled();
    } catch (_) {
      return false;
    }
  })();

  // Phase 3 shadow: still refresh Redis seen so compare has data, even when engine=1.
  if (!enabled && !shadow) return;

  metrics.inc('alive_calls', 1);
  metrics.incAliveSource(source);
  console.log(`[PresenceV25] ${source} user=${userId} device=${deviceId} package=${packageKey}`);

  await store.releaseProbeLock(userId, deviceId);
  await store.touchSeen(userId, deviceId, Date.now());

  const probe = await store.getProbeState(userId, deviceId);
  const hadProbeStage = probe && probe.stage !== undefined && String(probe.stage) !== '';
  if (hadProbeStage) {
    await store.clearProbeState(userId, deviceId);
    metrics.inc('probe_cancelled', 1);
    console.log(
      `[PresenceV25] PROBE_CANCEL user=${userId} device=${deviceId} stage=${probe.stage}`
    );
  }

  const recoveryMs = await store.takeRecoveryMs(userId, deviceId);
  if (recoveryMs != null) {
    metrics.recordRecoveryMs(recoveryMs);
    console.log(
      `[PresenceV25] RECOVERY_MS user=${userId} device=${deviceId} ms=${recoveryMs}`
    );
  }

  // Shadow-only: Redis updated; skip DB flips until engine version=2.
  if (!enabled) return;

  if (DRY_RUN) {
    const cur = await getCurrentOnlineState(userId, deviceId);
    if (cur === 0 || cur === null) {
      metrics.inc('state_flip_online', 1);
      console.log(
        `[PresenceV25] DEVICE_ONLINE user=${userId} device=${deviceId} dry=1`
      );
    }
    return;
  }

  const r = await prisma.registered_devices.updateMany({
    where: {
      user_id: Number(userId),
      device_id: String(deviceId),
      status: 'active',
      device_online: 0,
    },
    data: { device_online: 1 },
  });

  if (r.count > 0) {
    metrics.inc('state_flip_online', 1);
    console.log(`[PresenceV25] DEVICE_ONLINE user=${userId} device=${deviceId}`);
  }

  // Reactivate merchant-enabled SIM slots for checkout (legacy is_active bridge).
  await prisma.$executeRaw`
    UPDATE sim_slot_bindings
    SET is_active = 1, updated_at = CURRENT_TIMESTAMP
    WHERE user_id = ${String(userId)}
      AND device_id = ${String(deviceId)}
      AND merchant_enabled = 1
      AND is_active = 0
  `;
}

/**
 * markDeviceOffline(userId, deviceId, meta?)
 * meta.reason example: PROBE_STAGE3
 *
 * IMPORTANT: only Phase 2 worker should call this.
 */
async function markDeviceOffline(userId, deviceId, meta = {}) {
  const { reason = 'PROBE_STAGE3' } = meta;
  if (userId === undefined || userId === null) return;
  if (!deviceId) return;

  metrics.inc('offline_calls', 1);

  if (DRY_RUN) {
    const cur = await getCurrentOnlineState(userId, deviceId);
    if (cur === 1) {
      metrics.inc('state_flip_offline', 1);
      await store.markOfflineAt(userId, deviceId);
      console.log(
        `[PresenceV25] DEVICE_OFFLINE user=${userId} device=${deviceId} dry=1 reason=${reason}`
      );
    }
    return;
  }

  const r = await prisma.registered_devices.updateMany({
    where: {
      user_id: Number(userId),
      device_id: String(deviceId),
      status: 'active',
      device_online: 1,
    },
    data: { device_online: 0 },
  });

  if (r.count > 0) {
    metrics.inc('state_flip_offline', 1);
    await store.markOfflineAt(userId, deviceId);
    console.log(
      `[PresenceV25] DEVICE_OFFLINE user=${userId} device=${deviceId} reason=${reason}`
    );
  }

  // Hide from checkout while merchant_enabled stays (merchant toggle untouched).
  await prisma.$executeRaw`
    UPDATE sim_slot_bindings
    SET is_active = 0, updated_at = CURRENT_TIMESTAMP
    WHERE user_id = ${String(userId)}
      AND device_id = ${String(deviceId)}
      AND is_active = 1
  `;
}

function getMetrics() {
  return metrics.snapshot();
}

module.exports = {
  markDeviceAlive,
  markDeviceOffline,
  getMetrics,
  DRY_RUN,
};

