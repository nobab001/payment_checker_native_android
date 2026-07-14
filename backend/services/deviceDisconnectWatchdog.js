/**
 * Device disconnect / heartbeat-miss watchdog (Comm Policy v1.0).
 *
 * Gateway / Welcome: inactive past offline threshold → is_active = 0 (checkout hide).
 * Personal tiers: monitoring status only — do not force-deactivate bindings.
 */

const prisma = require('../db/prisma');
const numberHealth = require('./numberHealthService');
const commPolicy = require('./commPolicyService');

/** @type {Map<string, { timeouts: NodeJS.Timeout[], profileId: string }>} */
const activeWatches = new Map();

function watchKey(userId, deviceId) {
  return `${String(userId)}:${String(deviceId)}`;
}

function cancelDeviceWatch(userId, deviceId) {
  const key = watchKey(userId, deviceId);
  const watch = activeWatches.get(key);
  if (!watch) return;
  watch.timeouts.forEach((t) => clearTimeout(t));
  activeWatches.delete(key);
  console.log(`[DeviceWatch] Stopped watch for ${key}`);
}

async function isDeviceCurrentlyActive(userId, deviceId) {
  const uid = String(userId);
  const dev = String(deviceId);
  const profile = await numberHealth.getCachedProfile(uid);

  if (await numberHealth.isDeviceSocketLive(uid, dev)) return true;

  const now = Date.now();
  const deviceHealth = await numberHealth.getDeviceHealth(uid, dev);
  if (
    deviceHealth.lastSeenMs
    && now - deviceHealth.lastSeenMs <= profile.onlineMs
  ) {
    return true;
  }

  const numbers = await numberHealth.fetchActiveNumbersForDevice(uid, dev);
  for (const n of numbers) {
    const st = await numberHealth.getNumberState(uid, n.phone_number);
    if (st.state === 'ONLINE' || st.state === 'GRACE') return true;
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

/**
 * Re-enable bindings when device reconnects (undo watchdog deactivation).
 */
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
    await prisma.$executeRaw`
      INSERT INTO sim_slot_bindings (user_id, device_id, sim_slot, phone_number, is_active)
      VALUES (${uid}, ${dev}, ${slot}, ${phone}, 1)
      ON DUPLICATE KEY UPDATE
        phone_number = VALUES(phone_number),
        is_active = 1,
        updated_at = CURRENT_TIMESTAMP
    `;
  }
}

async function runOfflineCheck(userId, deviceId) {
  const key = watchKey(userId, deviceId);
  const watch = activeWatches.get(key);
  if (!watch) return;

  const profile = await numberHealth.getCachedProfile(userId);
  if (!profile.deactivateOnOffline) {
    console.log(`[DeviceWatch] ${key} profile=${profile.id} — skip deactivate`);
    cancelDeviceWatch(userId, deviceId);
    return;
  }

  const active = await isDeviceCurrentlyActive(userId, deviceId);
  if (active) {
    console.log(`[DeviceWatch] ${key} active before offline deadline — OK`);
    cancelDeviceWatch(userId, deviceId);
    return;
  }

  console.log(`[DeviceWatch] ${key} OFFLINE past ${profile.offlineMs}ms — is_active=0`);
  await deactivateDeviceBindings(userId, deviceId);
  cancelDeviceWatch(userId, deviceId);
}

/**
 * Schedule profile-aware offline check after disconnect / heartbeat miss.
 * @param {object} [profileHint] optional pre-resolved profile
 */
function scheduleDeviceWatch(userId, deviceId, profileHint = null) {
  const key = watchKey(userId, deviceId);
  cancelDeviceWatch(userId, deviceId);

  const profile = profileHint || commPolicy.PROFILES.gateway;
  if (!profile.deactivateOnOffline) {
    console.log(`[DeviceWatch] Skip watch for ${key} (profile=${profile.id})`);
    return;
  }

  const watch = { timeouts: [], profileId: profile.id };
  activeWatches.set(key, watch);

  const delay = profile.offlineMs;
  console.log(
    `[DeviceWatch] Scheduled offline check in ${Math.round(delay / 1000)}s for ${key} (${profile.id})`
  );

  const timer = setTimeout(() => {
    runOfflineCheck(userId, deviceId).catch((err) => {
      console.warn('[DeviceWatch] offline check failed:', err.message);
      cancelDeviceWatch(userId, deviceId);
    });
  }, delay);
  watch.timeouts.push(timer);
}

module.exports = {
  scheduleDeviceWatch,
  cancelDeviceWatch,
  isDeviceCurrentlyActive,
  deactivateDeviceBindings,
  reactivateDeviceBindings,
};
