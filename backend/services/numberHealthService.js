/**
 * numberHealthService — Number-centric health (ONLINE / GRACE / OFFLINE / DISABLED / STALE).
 *
 * Hot path: Socket.IO connect + SMS ingest touch Redis last_seen.
 * HTTP heartbeat is package-tiered (Comm Policy v1.0): realtime packages every 2 min,
 * sparse packages every 10 min.
 *
 * Default thresholds (Gateway / Welcome):
 *   0–2 min   ONLINE
 *   2–3 min   GRACE
 *   3–5 min   OFFLINE
 *   5 min+    STALE (deactivate for gateway/welcome)
 */

const { getRedisClient } = require('./redisClient');
const commPolicy = require('./commPolicyService');

function deviceWatchdog() {
  return require('./deviceDisconnectWatchdog');
}

/** Legacy global defaults = Gateway/Welcome realtime tier (kept for exports). */
const THRESHOLDS = {
  ONLINE_MS: commPolicy.PROFILES.gateway.onlineMs,
  GRACE_MS: commPolicy.PROFILES.gateway.graceMs,
  OFFLINE_MS: commPolicy.PROFILES.gateway.offlineMs,
  STALE_MS: 24 * 60 * 60 * 1000,
};

const CHECKOUT_ELIGIBLE = new Set(['ONLINE', 'GRACE']);

const STATE_RANK = {
  ONLINE: 0,
  GRACE: 1,
  OFFLINE: 2,
  STALE: 3,
  DISABLED: 4,
};

const KEYS = {
  numberLastSeen: (userId, phone) => `paychek:nh:u:${userId}:n:${phone}:seen`,
  numberDevice: (userId, phone) => `paychek:nh:u:${userId}:n:${phone}:dev`,
  numberDisabled: (userId, phone) => `paychek:nh:u:${userId}:n:${phone}:dis`,
  deviceLastSeen: (userId, deviceId) => `paychek:nh:u:${userId}:d:${deviceId}:seen`,
  deviceDbFlush: (userId, deviceId) => `paychek:nh:u:${userId}:d:${deviceId}:dbflush`,
  deviceSocketLive: (userId, deviceId) => `paychek:nh:u:${userId}:d:${deviceId}:socket`,
};

/** In-memory debounce — brief socket flaps do not push numbers into GRACE. */
const pendingSocketDisconnect = new Map();
const SOCKET_DISCONNECT_DEBOUNCE_MS = 30 * 1000;

const DEVICE_DB_FLUSH_MS = 5 * 60 * 1000;
const REDIS_TTL_SEC = 48 * 60 * 60; // 48h — STALE still computable

/** Short-lived profile cache to avoid DB on every SMS touch. */
const profileCache = new Map();
const PROFILE_CACHE_MS = 60 * 1000;

async function getCachedProfile(userId) {
  const uid = String(userId);
  const hit = profileCache.get(uid);
  if (hit && Date.now() - hit.at < PROFILE_CACHE_MS) return hit.profile;
  const profile = await commPolicy.resolveCommProfile(uid);
  profileCache.set(uid, { at: Date.now(), profile });
  return profile;
}

function invalidateProfileCache(userId) {
  profileCache.delete(String(userId));
}

function normalizePhone(raw) {
  if (!raw || typeof raw !== 'string') return '';
  return raw.replace(/\D/g, '').slice(-11);
}

function computeState(lastSeenMs, isDisabled, now = Date.now(), profile = null) {
  const p = profile || commPolicy.PROFILES.gateway;
  if (isDisabled) return 'DISABLED';
  if (!lastSeenMs || lastSeenMs <= 0) return 'GRACE'; // migration: no heartbeat yet
  const age = now - lastSeenMs;
  if (age <= p.onlineMs) return 'ONLINE';
  if (age <= p.graceMs) return 'GRACE';
  if (age <= Math.max(p.offlineMs, THRESHOLDS.STALE_MS)) {
    // Before STALE wall-clock (24h), treat past offline as OFFLINE
    if (age <= p.offlineMs) return 'OFFLINE';
    if (age <= THRESHOLDS.STALE_MS) return 'OFFLINE';
  }
  return 'STALE';
}

function isCheckoutEligible(state) {
  return CHECKOUT_ELIGIBLE.has(state);
}

async function safePipeline(execFn) {
  try {
    const redis = getRedisClient();
    const pipe = redis.pipeline();
    execFn(pipe, redis);
    await pipe.exec();
    return true;
  } catch (err) {
    console.warn('[NumberHealth] Redis pipeline failed:', err.message);
    return false;
  }
}

async function getNumberMeta(userId, phone) {
  const uid = String(userId);
  const num = normalizePhone(phone);
  if (!num) return { lastSeenMs: 0, deviceId: '', isDisabled: false };

  try {
    const redis = getRedisClient();
    const [seen, dev, dis] = await redis.mget(
      KEYS.numberLastSeen(uid, num),
      KEYS.numberDevice(uid, num),
      KEYS.numberDisabled(uid, num),
    );
    return {
      lastSeenMs: seen ? parseInt(seen, 10) || 0 : 0,
      deviceId: dev || '',
      isDisabled: dis === '1',
    };
  } catch (err) {
    console.warn('[NumberHealth] Redis mget failed:', err.message);
    return { lastSeenMs: 0, deviceId: '', isDisabled: false };
  }
}

async function getNumberState(userId, phone, now = Date.now()) {
  const meta = await getNumberMeta(userId, phone);
  const profile = await getCachedProfile(userId);
  const state = computeState(meta.lastSeenMs, meta.isDisabled, now, profile);
  return {
    phone: normalizePhone(phone),
    state,
    lastSeenMs: meta.lastSeenMs,
    deviceId: meta.deviceId,
    ageMs: meta.lastSeenMs ? now - meta.lastSeenMs : null,
    checkoutEligible: isCheckoutEligible(state),
    profile: profile.id,
  };
}

async function isDeviceSocketLive(userId, deviceId) {
  if (!deviceId) return false;
  try {
    const redis = getRedisClient();
    return (await redis.get(KEYS.deviceSocketLive(String(userId), String(deviceId)))) === '1';
  } catch (_) {
    return false;
  }
}

/**
 * Lightweight proof-of-life — call when SMS is successfully ingested.
 */
async function touchNumberLive(userId, deviceId, phone) {
  const uid = String(userId);
  const num = normalizePhone(phone);
  const dev = String(deviceId || '');
  if (num.length !== 11) return;
  const nowStr = String(Date.now());
  await safePipeline((pipe) => {
    if (dev) {
      pipe.set(KEYS.deviceLastSeen(uid, dev), nowStr, 'EX', REDIS_TTL_SEC);
    }
    pipe.set(KEYS.numberLastSeen(uid, num), nowStr, 'EX', REDIS_TTL_SEC);
    if (dev) pipe.set(KEYS.numberDevice(uid, num), dev, 'EX', REDIS_TTL_SEC);
  });
  if (dev) {
    deviceWatchdog().isDeviceCurrentlyActive(uid, dev).then((active) => {
      if (active) deviceWatchdog().cancelDeviceWatch(uid, dev);
    }).catch(() => {});
  }
}

/**
 * Modal list / admin display — socket-live devices show ONLINE even if HTTP heartbeat lagged.
 */
async function getNumberStateForDisplay(userId, phone, deviceId, now = Date.now()) {
  const meta = await getNumberMeta(userId, phone);
  let lastSeenMs = meta.lastSeenMs;
  const profile = await getCachedProfile(userId);

  if (deviceId && (await isDeviceSocketLive(userId, deviceId))) {
    lastSeenMs = now;
  }

  const state = computeState(lastSeenMs, meta.isDisabled, now, profile);
  return {
    phone: normalizePhone(phone),
    state,
    lastSeenMs,
    deviceId: deviceId || meta.deviceId,
    ageMs: lastSeenMs ? now - lastSeenMs : null,
    checkoutEligible: isCheckoutEligible(state),
    profile: profile.id,
  };
}

/**
 * Device heartbeat — updates all active SIM numbers in one round-trip.
 * @param {Array<{sim_slot?: number, phone_number?: string}>} numbers
 */
async function recordHeartbeat(userId, deviceId, numbers = [], opts = {}) {
  const uid = String(userId);
  const dev = String(deviceId || '');
  const now = Date.now();
  const nowStr = String(now);

  const cleaned = numbers
    .map((n) => ({
      simSlot: Number(n.sim_slot || n.simSlot || 0),
      phone: normalizePhone(n.phone_number || n.phoneNumber || n.number || ''),
    }))
    .filter((n) => n.phone.length === 11);

  await safePipeline((pipe) => {
    pipe.set(KEYS.deviceLastSeen(uid, dev), nowStr, 'EX', REDIS_TTL_SEC);
    cleaned.forEach(({ phone }) => {
      pipe.set(KEYS.numberLastSeen(uid, phone), nowStr, 'EX', REDIS_TTL_SEC);
      pipe.set(KEYS.numberDevice(uid, phone), dev, 'EX', REDIS_TTL_SEC);
      pipe.del(KEYS.numberDisabled(uid, phone));
    });
  });

  // Throttled MySQL flush for admin "last seen"
  await maybeFlushDeviceToDb(uid, dev, now, opts.batteryPercent);

  deviceWatchdog().isDeviceCurrentlyActive(uid, dev).then((active) => {
    if (active) deviceWatchdog().cancelDeviceWatch(uid, dev);
  }).catch(() => {});

  const states = {};
  for (const { phone } of cleaned) {
    states[phone] = computeState(now, false, now);
  }
  return { updated: cleaned.map((n) => n.phone), states, serverTime: now };
}

function cancelPendingSocketDisconnect(userId, deviceId) {
  const key = `${userId}:${deviceId}`;
  const pending = pendingSocketDisconnect.get(key);
  if (pending) {
    clearTimeout(pending);
    pendingSocketDisconnect.delete(key);
  }
}

async function fetchActiveNumbersForDevice(userId, deviceId) {
  const uid = String(userId);
  const dev = String(deviceId);
  try {
    const prisma = require('../db/prisma');
    const bindings = await prisma.$queryRaw`
      SELECT phone_number, sim_slot
      FROM sim_slot_bindings
      WHERE user_id = ${uid}
        AND device_id = ${dev}
        AND is_active = 1
        AND phone_number IS NOT NULL
        AND phone_number != ''
    `;
    if (bindings.length) {
      return bindings.map((r) => ({
        sim_slot: Number(r.sim_slot) || 1,
        phone_number: normalizePhone(r.phone_number),
      })).filter((n) => n.phone_number.length === 11);
    }

    const methods = await prisma.gateway_methods.findMany({
      where: { user_id: uid, device_id: dev },
      select: { number: true, sim_slot: true },
    });
    const seen = new Set();
    const out = [];
    for (const m of methods) {
      const phone = normalizePhone(m.number);
      if (phone.length !== 11 || seen.has(phone)) continue;
      seen.add(phone);
      out.push({ sim_slot: m.sim_slot || 1, phone_number: phone });
    }
    return out;
  } catch (err) {
    console.warn('[NumberHealth] fetchActiveNumbersForDevice failed:', err.message);
    return [];
  }
}

function normalizeClientNumbers(numbers) {
  if (!Array.isArray(numbers)) return [];
  return numbers
    .map((n) => ({
      sim_slot: Number(n.sim_slot || n.simSlot || 0),
      phone_number: normalizePhone(n.phone_number || n.phoneNumber || n.number || ''),
    }))
    .filter((n) => n.phone_number.length === 11);
}

/**
 * Socket.IO connect — mark device + SIM numbers ONLINE; cancel disconnect watch.
 */
async function onDeviceSocketConnect(userId, deviceId, clientNumbers = null) {
  const uid = String(userId);
  const dev = String(deviceId || '');
  if (!uid || !dev) return { updated: [], states: {} };

  cancelPendingSocketDisconnect(uid, dev);
  deviceWatchdog().cancelDeviceWatch(uid, dev);

  const fromClient = normalizeClientNumbers(clientNumbers);
  const numbers = fromClient.length ? fromClient : await fetchActiveNumbersForDevice(uid, dev);

  try {
    const redis = getRedisClient();
    await redis.set(KEYS.deviceSocketLive(uid, dev), '1', 'EX', REDIS_TTL_SEC);
  } catch (_) { /* ignore */ }

  if (numbers.length) {
    await deviceWatchdog().reactivateDeviceBindings(uid, dev, numbers);
  }

  if (!numbers.length) {
    return { updated: [], states: {}, serverTime: Date.now() };
  }

  return recordHeartbeat(uid, dev, numbers);
}

/**
 * Socket.IO disconnect (debounced) — shift numbers into GRACE; start profile-aware watch.
 */
async function onDeviceSocketDisconnect(userId, deviceId) {
  const uid = String(userId);
  const dev = String(deviceId || '');
  if (!uid || !dev) return;

  const profile = await getCachedProfile(uid);
  const numbers = await fetchActiveNumbersForDevice(uid, dev);
  const graceAnchorMs = Date.now() - profile.onlineMs - 1000;
  const anchorStr = String(graceAnchorMs);

  await safePipeline((pipe) => {
    pipe.del(KEYS.deviceSocketLive(uid, dev));
    numbers.forEach(({ phone_number: phone }) => {
      pipe.set(KEYS.numberLastSeen(uid, phone), anchorStr, 'EX', REDIS_TTL_SEC);
      pipe.set(KEYS.numberDevice(uid, phone), dev, 'EX', REDIS_TTL_SEC);
    });
  });

  deviceWatchdog().scheduleDeviceWatch(uid, dev, profile);

  console.log(
    `[NumberHealth] Socket disconnect → GRACE + watchdog (${profile.id}) for device ${dev} (${numbers.length} number(s))`
  );
}

function scheduleSocketDisconnect(userId, deviceId) {
  const uid = String(userId);
  const dev = String(deviceId || '');
  const key = `${uid}:${dev}`;
  cancelPendingSocketDisconnect(uid, dev);
  const timer = setTimeout(() => {
    pendingSocketDisconnect.delete(key);
    onDeviceSocketDisconnect(uid, dev).catch((err) => {
      console.warn('[NumberHealth] socket disconnect handler failed:', err.message);
    });
  }, SOCKET_DISCONNECT_DEBOUNCE_MS);
  pendingSocketDisconnect.set(key, timer);
}

async function maybeFlushDeviceToDb(userId, deviceId, nowMs, batteryPercent) {
  if (!deviceId) return;
  const flushKey = KEYS.deviceDbFlush(userId, deviceId);
  try {
    const redis = getRedisClient();
    const lastFlush = await redis.get(flushKey);
    if (lastFlush && nowMs - (parseInt(lastFlush, 10) || 0) < DEVICE_DB_FLUSH_MS) {
      return;
    }
    await redis.set(flushKey, String(nowMs), 'EX', REDIS_TTL_SEC);

    const prisma = require('../db/prisma');
    const data = { last_seen_at: new Date(nowMs) };
    if (batteryPercent != null && Number.isFinite(Number(batteryPercent))) {
      data.last_battery_percent = Math.max(0, Math.min(100, Math.round(Number(batteryPercent))));
    }
    await prisma.registered_devices.updateMany({
      where: { user_id: Number(userId), device_id: String(deviceId) },
      data,
    });
  } catch (err) {
    console.warn('[NumberHealth] DB device flush failed:', err.message);
  }
}

async function setNumberDisabled(userId, phone, disabled = true) {
  const uid = String(userId);
  const num = normalizePhone(phone);
  if (!num) return;
  try {
    const redis = getRedisClient();
    if (disabled) {
      await redis.set(KEYS.numberDisabled(uid, num), '1', 'EX', REDIS_TTL_SEC);
    } else {
      await redis.del(KEYS.numberDisabled(uid, num));
    }
  } catch (err) {
    console.warn('[NumberHealth] setNumberDisabled failed:', err.message);
  }
}

/** Remove all Redis health keys for a deleted account number. */
async function purgeNumber(userId, phone) {
  const uid = String(userId);
  const num = normalizePhone(phone);
  if (!num) return;
  try {
    const redis = getRedisClient();
    await redis.del(
      KEYS.numberLastSeen(uid, num),
      KEYS.numberDevice(uid, num),
      KEYS.numberDisabled(uid, num),
    );
  } catch (err) {
    console.warn('[NumberHealth] purgeNumber failed:', err.message);
  }
}

async function getDeviceHealth(userId, deviceId) {
  const uid = String(userId);
  const dev = String(deviceId);
  const profile = await getCachedProfile(uid);
  try {
    const redis = getRedisClient();
    const seen = await redis.get(KEYS.deviceLastSeen(uid, dev));
    const lastSeenMs = seen ? parseInt(seen, 10) || 0 : 0;
    const state = computeState(lastSeenMs, false, Date.now(), profile);
    return {
      deviceId: dev,
      lastSeenMs,
      state,
      ageMs: lastSeenMs ? Date.now() - lastSeenMs : null,
      profile: profile.id,
    };
  } catch (err) {
    return { deviceId: dev, lastSeenMs: 0, state: 'GRACE', ageMs: null, profile: profile.id };
  }
}

/**
 * Filter + rank checkout rows by number health (failover: ONLINE before GRACE).
 */
async function applyHealthToCheckoutRows(userId, rows) {
  if (!rows?.length) return [];

  const uid = String(userId);
  const now = Date.now();
  const profile = await getCachedProfile(uid);
  const phones = [...new Set(rows.map((r) => normalizePhone(r.number)).filter(Boolean))];
  const deviceIds = [...new Set(
    rows.map((r) => String(r.device_id || r.deviceId || '')).filter(Boolean),
  )];

  const socketLiveByDevice = {};
  if (deviceIds.length) {
    try {
      const redis = getRedisClient();
      const socketResults = await redis.mget(
        ...deviceIds.map((d) => KEYS.deviceSocketLive(uid, d)),
      );
      deviceIds.forEach((d, i) => {
        socketLiveByDevice[d] = socketResults[i] === '1';
      });
    } catch (err) {
      console.warn('[NumberHealth] socket live batch read failed:', err.message);
    }
  }

  const metaByPhone = {};
  if (phones.length) {
    try {
      const redis = getRedisClient();
      const pipe = redis.pipeline();
      phones.forEach((p) => {
        pipe.get(KEYS.numberLastSeen(uid, p));
        pipe.get(KEYS.numberDisabled(uid, p));
      });
      const results = await pipe.exec();
      phones.forEach((p, idx) => {
        const seenRes = results[idx * 2];
        const disRes = results[idx * 2 + 1];
        const lastSeenMs = seenRes?.[1] ? parseInt(seenRes[1], 10) || 0 : 0;
        const isDisabled = disRes?.[1] === '1';
        metaByPhone[p] = { lastSeenMs, isDisabled };
      });
    } catch (err) {
      console.warn('[NumberHealth] batch meta read failed:', err.message);
      phones.forEach((p) => { metaByPhone[p] = { lastSeenMs: 0, isDisabled: false }; });
    }
  }

  const enriched = rows.map((row) => {
    const phone = normalizePhone(row.number);
    const meta = metaByPhone[phone] || { lastSeenMs: 0, isDisabled: false };
    const devId = String(row.device_id || row.deviceId || '');
    let lastSeenMs = meta.lastSeenMs;
    if (devId && socketLiveByDevice[devId]) {
      lastSeenMs = now;
    }
    const state = computeState(lastSeenMs, meta.isDisabled, now, profile);
    return {
      ...row,
      healthState: state,
      healthRank: STATE_RANK[state] ?? 99,
      checkoutEligible: isCheckoutEligible(state),
    };
  });

  const eligible = enriched.filter((r) => r.checkoutEligible);
  eligible.sort((a, b) => {
    if (a.healthRank !== b.healthRank) return a.healthRank - b.healthRank;
    return (Number(a.priority) || 0) - (Number(b.priority) || 0);
  });
  return eligible;
}

module.exports = {
  THRESHOLDS,
  STATE_RANK,
  normalizePhone,
  computeState,
  isCheckoutEligible,
  recordHeartbeat,
  onDeviceSocketConnect,
  onDeviceSocketDisconnect,
  scheduleSocketDisconnect,
  cancelPendingSocketDisconnect,
  fetchActiveNumbersForDevice,
  getNumberState,
  getNumberStateForDisplay,
  touchNumberLive,
  isDeviceSocketLive,
  getNumberMeta,
  setNumberDisabled,
  purgeNumber,
  getDeviceHealth,
  applyHealthToCheckoutRows,
  getCachedProfile,
  invalidateProfileCache,
};
