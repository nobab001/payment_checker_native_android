/**
 * After Socket.IO disconnect: check every 10 min, up to 3 times.
 * If device is still inactive → sim_slot_bindings.is_active = 0 for that device.
 * Watch ends after 3 checks or when device becomes active again.
 */

const prisma = require('../db/prisma');
const numberHealth = require('./numberHealthService');

const CHECK_INTERVAL_MS = 10 * 60 * 1000;
const MAX_CHECKS = 3;

/** @type {Map<string, { timeouts: NodeJS.Timeout[], checkCount: number }>} */
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

  if (await numberHealth.isDeviceSocketLive(uid, dev)) return true;

  const now = Date.now();
  const deviceHealth = await numberHealth.getDeviceHealth(uid, dev);
  if (
    deviceHealth.lastSeenMs
    && now - deviceHealth.lastSeenMs <= numberHealth.THRESHOLDS.ONLINE_MS
  ) {
    return true;
  }

  const numbers = await numberHealth.fetchActiveNumbersForDevice(uid, dev);
  for (const n of numbers) {
    const st = await numberHealth.getNumberState(uid, n.phone_number);
    if (st.state === 'ONLINE') return true;
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

async function runCheck(userId, deviceId, checkIndex) {
  const key = watchKey(userId, deviceId);
  const watch = activeWatches.get(key);
  if (!watch) return;

  const active = await isDeviceCurrentlyActive(userId, deviceId);
  if (active) {
    console.log(`[DeviceWatch] ${key} active on check ${checkIndex + 1}/${MAX_CHECKS} — OK, watch ended`);
    cancelDeviceWatch(userId, deviceId);
    return;
  }

  const checkNum = checkIndex + 1;
  watch.checkCount = checkNum;
  console.log(`[DeviceWatch] ${key} inactive — check ${checkNum}/${MAX_CHECKS}`);

  if (checkNum >= MAX_CHECKS) {
    await deactivateDeviceBindings(userId, deviceId);
    cancelDeviceWatch(userId, deviceId);
    return;
  }

  const timer = setTimeout(() => {
    runCheck(userId, deviceId, checkNum).catch((err) => {
      console.warn('[DeviceWatch] runCheck failed:', err.message);
      cancelDeviceWatch(userId, deviceId);
    });
  }, CHECK_INTERVAL_MS);
  watch.timeouts.push(timer);
}

/**
 * Start 3×10min inactive watch after socket disconnect.
 * First check runs after 10 minutes.
 */
function scheduleDeviceWatch(userId, deviceId) {
  const key = watchKey(userId, deviceId);
  cancelDeviceWatch(userId, deviceId);

  const watch = { timeouts: [], checkCount: 0 };
  activeWatches.set(key, watch);

  console.log(
    `[DeviceWatch] Scheduled ${MAX_CHECKS} checks every ${CHECK_INTERVAL_MS / 60000} min for ${key}`
  );

  const timer = setTimeout(() => {
    runCheck(userId, deviceId, 0).catch((err) => {
      console.warn('[DeviceWatch] first check failed:', err.message);
      cancelDeviceWatch(userId, deviceId);
    });
  }, CHECK_INTERVAL_MS);
  watch.timeouts.push(timer);
}

module.exports = {
  CHECK_INTERVAL_MS,
  MAX_CHECKS,
  scheduleDeviceWatch,
  cancelDeviceWatch,
  isDeviceCurrentlyActive,
  deactivateDeviceBindings,
  reactivateDeviceBindings,
};
