/**
 * Device disconnect / heartbeat-miss watchdog (Comm Policy v1.1).
 *
 * All packages: after one missed heartbeat window → probe every 1 min × 5,
 * then set sim_slot_bindings.is_active = 0 (hide from checkout).
 */

const prisma = require('../db/prisma');
const numberHealth = require('./numberHealthService');
const commPolicy = require('./commPolicyService');

/** @type {Map<string, { timeouts: NodeJS.Timeout[], profileId: string, phase: string }>} */
const activeWatches = new Map();

function watchKey(userId, deviceId) {
  return `${String(userId)}:${String(deviceId)}`;
}

function cancelDeviceWatch(userId, deviceId, { quiet = false } = {}) {
  const key = watchKey(userId, deviceId);
  const watch = activeWatches.get(key);
  if (!watch) return;
  watch.timeouts.forEach((t) => clearTimeout(t));
  activeWatches.delete(key);
  if (!quiet) console.log(`[DeviceWatch] Stopped watch for ${key}`);
}

function hasActiveWatch(userId, deviceId) {
  return activeWatches.has(watchKey(userId, deviceId));
}

/**
 * True only with fresh proof-of-life (socket or last_seen within online window).
 * Zero/missing last_seen is NOT active (avoids GRACE-migration false positives).
 */
async function isDeviceCurrentlyActive(userId, deviceId) {
  const uid = String(userId);
  const dev = String(deviceId);
  const profile = await numberHealth.getCachedProfile(uid);
  const onlineMs = Math.max(60_000, Number(profile.onlineMs || profile.heartbeatSec * 1000 || 600_000));

  if (await numberHealth.isDeviceSocketLive(uid, dev)) return true;

  const now = Date.now();
  const deviceHealth = await numberHealth.getDeviceHealth(uid, dev);
  if (deviceHealth.lastSeenMs > 0 && now - deviceHealth.lastSeenMs <= onlineMs) {
    return true;
  }

  const numbers = await numberHealth.fetchActiveNumbersForDevice(uid, dev);
  for (const n of numbers) {
    const meta = await numberHealth.getNumberMeta(uid, n.phone_number);
    if (meta.lastSeenMs > 0 && now - meta.lastSeenMs <= onlineMs) {
      return true;
    }
  }
  return false;
}

async function deactivateDeviceBindings(userId, deviceId) {
  const uid = String(userId);
  const dev = String(deviceId);
  const result = await prisma.$executeRaw`
    UPDATE sim_slot_bindings
    SET is_active = 0, updated_at = CURRENT_TIMESTAMP
    WHERE user_id = ${uid}
      AND device_id = ${dev}
      AND is_active = 1
  `;
  console.log(
    `[DeviceWatch] Deactivated sim_slot_bindings for ${uid}:${dev} (rows=${result})`
  );
  return result;
}

async function reactivateDeviceBindings(userId, deviceId, numbers = null) {
  const uid = String(userId);
  const dev = String(deviceId);
  const list = Array.isArray(numbers) && numbers.length
    ? numbers
    : await numberHealth.fetchActiveNumbersForDevice(uid, dev);

  for (const n of list) {
    const slot = Number(n.sim_slot || n.simSlot || 1);
    const phone = numberHealth.normalizePhone(n.phone_number || n.phoneNumber || '');
    if (phone.length !== 11) continue;
    // Presence-only reactivate: never override merchant_enabled=0
    await prisma.$executeRaw`
      INSERT INTO sim_slot_bindings (user_id, device_id, sim_slot, phone_number, is_active, merchant_enabled)
      VALUES (${uid}, ${dev}, ${slot}, ${phone}, 1, 1)
      ON DUPLICATE KEY UPDATE
        phone_number = VALUES(phone_number),
        is_active = IF(merchant_enabled = 1, 1, is_active),
        updated_at = CURRENT_TIMESTAMP
    `;
  }
}

/**
 * After heartbeat window missed: probe every 1 min, up to 5 times, then deactivate.
 */
function beginMissProbes(userId, deviceId, profile) {
  const key = watchKey(userId, deviceId);
  if (activeWatches.get(key)?.phase === 'miss_probe') {
    return; // already probing — do not reset
  }

  cancelDeviceWatch(userId, deviceId, { quiet: true });

  const maxAttempts = commPolicy.MISS_PROBE.maxAttempts;
  const intervalMs = commPolicy.MISS_PROBE.intervalMs;
  const watch = { timeouts: [], profileId: profile.id, phase: 'miss_probe' };
  activeWatches.set(key, watch);

  console.log(
    `[DeviceWatch] Miss probes ${key} (${profile.id}) — `
    + `${maxAttempts}×${Math.round(intervalMs / 1000)}s → is_active=0`
  );

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const timer = setTimeout(() => {
      runMissProbe(userId, deviceId, attempt, maxAttempts).catch((err) => {
        console.warn('[DeviceWatch] miss probe failed:', err.message);
        cancelDeviceWatch(userId, deviceId, { quiet: true });
      });
    }, attempt * intervalMs);
    watch.timeouts.push(timer);
  }
}

async function runMissProbe(userId, deviceId, attempt, maxAttempts) {
  const key = watchKey(userId, deviceId);
  const watch = activeWatches.get(key);
  if (!watch || watch.phase !== 'miss_probe') return;

  const active = await isDeviceCurrentlyActive(userId, deviceId);
  if (active) {
    console.log(`[DeviceWatch] ${key} probe ${attempt}/${maxAttempts} — ONLINE again`);
    cancelDeviceWatch(userId, deviceId, { quiet: true });
    const profile = await numberHealth.getCachedProfile(userId);
    scheduleDeviceWatch(userId, deviceId, profile);
    return;
  }

  console.log(`[DeviceWatch] ${key} probe ${attempt}/${maxAttempts} — no heartbeat`);

  if (attempt >= maxAttempts) {
    console.log(`[DeviceWatch] ${key} ${maxAttempts} probes failed — is_active=0`);
    await deactivateDeviceBindings(userId, deviceId);
    cancelDeviceWatch(userId, deviceId, { quiet: true });
  }
}

/**
 * Arm after successful heartbeat / socket connect.
 * Waits remaining time in heartbeat window; if silent → miss probes.
 * v2.5 packages: no-op (presence worker owns offline).
 */
function scheduleDeviceWatch(userId, deviceId, profileHint = null) {
  const profile = profileHint || commPolicy.PROFILES.gateway;

  (async () => {
    try {
      const presenceV25 = require('./presenceV25');
      if (await presenceV25.isPresenceV2Enabled(profile.id || 'personal')) {
        return;
      }
    } catch (_) { /* fall through to legacy */ }

    const key = watchKey(userId, deviceId);
    const existing = activeWatches.get(key);
    // Don't interrupt an in-flight miss probe
    if (existing?.phase === 'miss_probe') return;

    cancelDeviceWatch(userId, deviceId, { quiet: true });

    const heartbeatMs = Math.max(60_000, Number(profile.heartbeatSec || 600) * 1000);

    const watch = { timeouts: [], profileId: profile.id, phase: 'await_heartbeat' };
    activeWatches.set(key, watch);

    numberHealth.getDeviceHealth(userId, deviceId).then((health) => {
      if (!activeWatches.has(key)) return;
      const age = health.lastSeenMs > 0 ? Date.now() - health.lastSeenMs : heartbeatMs + 1;
      const untilMiss = Math.max(0, heartbeatMs - age);

      if (untilMiss <= 0) {
        // Already past window — go straight to probes (do not re-arm at 0s)
        beginMissProbes(userId, deviceId, profile);
        return;
      }

      console.log(
        `[DeviceWatch] Armed ${key} (${profile.id}) — miss check in ${Math.round(untilMiss / 1000)}s`
      );

      const armTimer = setTimeout(() => {
        if (!activeWatches.has(key)) return;
        isDeviceCurrentlyActive(userId, deviceId).then((active) => {
          if (!activeWatches.has(key)) return;
          if (active) {
            // Fresh again — schedule full next window (never 0s loop)
            scheduleDeviceWatch(userId, deviceId, profile);
            return;
          }
          beginMissProbes(userId, deviceId, profile);
        }).catch(() => {
          beginMissProbes(userId, deviceId, profile);
        });
      }, untilMiss);

      const current = activeWatches.get(key);
      if (current) current.timeouts.push(armTimer);
    }).catch(() => {
      if (!activeWatches.has(key)) return;
      beginMissProbes(userId, deviceId, profile);
    });
  })();
}

/**
 * Cron: only start probes for stale active bindings that have no watch yet.
 * Does NOT re-arm healthy devices every minute (that caused log spam).
 */
async function sweepStaleActiveBindings() {
  let rows = [];
  try {
    rows = await prisma.$queryRaw`
      SELECT DISTINCT user_id, device_id
      FROM sim_slot_bindings
      WHERE is_active = 1
        AND device_id IS NOT NULL
        AND device_id != ''
      LIMIT 500
    `;
  } catch (err) {
    console.warn('[DeviceWatch] sweep query failed:', err.message);
    return { checked: 0, armed: 0 };
  }

  let armed = 0;
  const presenceV25 = require('./presenceV25');
  const commPolicy = require('./commPolicyService');

  for (const row of rows) {
    const uid = String(row.user_id);
    const dev = String(row.device_id);
    if (hasActiveWatch(uid, dev)) continue;

    try {
      // Skip orphaned bindings for deleted accounts
      const userRows = await prisma.$queryRaw`
        SELECT id FROM users WHERE id = ${Number(uid) || 0} LIMIT 1
      `;
      if (!userRows?.length) {
        await deactivateDeviceBindings(uid, dev);
        console.log(`[DeviceWatch] Orphan bindings cleared for deleted user ${uid}:${dev.slice(0, 12)}…`);
        continue;
      }

      // Phase 4: v2.5 presence owns online/offline — skip legacy watchdog
      try {
        const profile = await commPolicy.resolveCommProfile(uid);
        if (await presenceV25.isPresenceV2Enabled(profile?.id || 'personal')) {
          continue;
        }
      } catch (_) { /* fall through to legacy */ }

      const profile = await numberHealth.getCachedProfile(uid);
      const health = await numberHealth.getDeviceHealth(uid, dev);
      const heartbeatMs = Math.max(60_000, Number(profile.heartbeatSec || 600) * 1000);
      const age = health.lastSeenMs > 0 ? Date.now() - health.lastSeenMs : heartbeatMs + 1;

      if (age > heartbeatMs) {
        // Confirm not actually online before probing
        const active = await isDeviceCurrentlyActive(uid, dev);
        if (active) {
          scheduleDeviceWatch(uid, dev, profile);
          armed += 1;
          continue;
        }
        console.log(
          `[DeviceWatch] Sweep miss ${uid}:${dev} age=${Math.round(age / 1000)}s`
        );
        beginMissProbes(uid, dev, profile);
        armed += 1;
      }
      // Inside window + no watch: leave alone until heartbeat/socket arms it
    } catch (err) {
      console.warn(`[DeviceWatch] sweep item failed ${uid}:${dev}:`, err.message);
    }
  }

  return { checked: rows.length, armed };
}

module.exports = {
  scheduleDeviceWatch,
  cancelDeviceWatch,
  hasActiveWatch,
  beginMissProbes,
  isDeviceCurrentlyActive,
  deactivateDeviceBindings,
  reactivateDeviceBindings,
  sweepStaleActiveBindings,
};
