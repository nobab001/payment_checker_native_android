const prisma = require('../db/prisma');
const { getRedisClient } = require('./redisClient');

const TTL_SECONDS = 3600;

const KEYS = {
  GLOBAL_TEMPLATE_VERSION: 'paychek:global:template_version',
  ACTIVE_TEMPLATES: 'paychek:cache:active_templates',
  OFFICIAL_TEMPLATES_ALL: 'paychek:cache:official_templates_all',
  deviceSync: (userId, deviceId) => `paychek:sync:device:${userId}:${deviceId}`,
  userCustomVersion: (userId) => `paychek:sync:user_custom:${userId}`,
  userHistoryVersion: (userId) => `paychek:sync:history:${userId}`,
};

let broadcastTimer = null;
let pendingBroadcastVersion = 0;

async function safeRedisGet(key) {
  try {
    return await getRedisClient().get(key);
  } catch (err) {
    console.warn('[DataSyncCache] Redis GET failed:', err.message);
    return null;
  }
}

async function safeRedisSet(key, value, ttlSeconds = TTL_SECONDS) {
  try {
    if (ttlSeconds > 0) {
      await getRedisClient().set(key, value, 'EX', ttlSeconds);
    } else {
      await getRedisClient().set(key, value);
    }
    return true;
  } catch (err) {
    console.warn('[DataSyncCache] Redis SET failed:', err.message);
    return false;
  }
}

async function safeRedisDel(...keys) {
  try {
    if (keys.length > 0) {
      await getRedisClient().del(...keys);
    }
  } catch (err) {
    console.warn('[DataSyncCache] Redis DEL failed:', err.message);
  }
}

async function invalidateTemplateListCaches() {
  await safeRedisDel(KEYS.ACTIVE_TEMPLATES, KEYS.OFFICIAL_TEMPLATES_ALL);
}

async function getGlobalTemplateVersion() {
  const cached = await safeRedisGet(KEYS.GLOBAL_TEMPLATE_VERSION);
  if (cached) {
    return parseInt(cached, 10) || 0;
  }

  const config = await prisma.global_config.findUnique({
    where: { config_key: 'templates_last_updated' },
  });
  const version = config ? parseInt(config.config_value, 10) || 0 : 0;
  await safeRedisSet(KEYS.GLOBAL_TEMPLATE_VERSION, String(version), TTL_SECONDS);
  return version;
}

async function bumpGlobalTemplateVersion() {
  const version = Date.now();

  await prisma.global_config.upsert({
    where: { config_key: 'templates_last_updated' },
    update: { config_value: String(version), updated_at: new Date() },
    create: { config_key: 'templates_last_updated', config_value: String(version), updated_at: new Date() },
  });

  await safeRedisSet(KEYS.GLOBAL_TEMPLATE_VERSION, String(version), 0);
  await invalidateTemplateListCaches();
  return version;
}

async function bumpDeviceSyncVersion(userId, deviceId) {
  if (!userId || !deviceId) return Date.now();
  const version = Date.now();
  await safeRedisSet(KEYS.deviceSync(String(userId), String(deviceId)), String(version), 0);
  return version;
}

async function bumpUserCustomTemplateVersion(userId) {
  if (!userId) return Date.now();
  const version = Date.now();
  await safeRedisSet(KEYS.userCustomVersion(String(userId)), String(version), 0);
  await invalidateTemplateListCaches();
  return version;
}

async function bumpUserHistoryVersion(userId) {
  if (!userId) return Date.now();
  const version = Date.now();
  await safeRedisSet(KEYS.userHistoryVersion(String(userId)), String(version), 0);
  return version;
}

async function getUserHistoryVersion(userId) {
  if (!userId) return 0;

  const cached = await safeRedisGet(KEYS.userHistoryVersion(String(userId)));
  if (cached) {
    return parseInt(cached, 10) || 0;
  }

  const latestCreated = await prisma.sms_history.findFirst({
    where: { user_id: Number(userId) },
    orderBy: { created_at: 'desc' },
    select: { created_at: true },
  });
  const latestUsed = await prisma.sms_history.findFirst({
    where: { user_id: Number(userId), used_at: { not: null } },
    orderBy: { used_at: 'desc' },
    select: { used_at: true },
  });

  const createdMs = latestCreated ? new Date(latestCreated.created_at).getTime() : 0;
  const usedMs = latestUsed?.used_at ? new Date(latestUsed.used_at).getTime() : 0;
  let version = Math.max(createdMs, usedMs);

  if (version === 0) {
    const userRow = await prisma.users.findUnique({
      where: { id: Number(userId) },
      select: { created_at: true },
    });
    version = userRow?.created_at ? new Date(userRow.created_at).getTime() : Date.now();
  }

  if (version > 0) {
    await safeRedisSet(KEYS.userHistoryVersion(String(userId)), String(version), TTL_SECONDS);
  }
  return version;
}

async function getDeviceSyncVersion(userId, deviceId) {
  const globalVersion = await getGlobalTemplateVersion();

  let deviceVersion = 0;
  if (deviceId) {
    const cachedDevice = await safeRedisGet(KEYS.deviceSync(String(userId), String(deviceId)));
    if (cachedDevice) {
      deviceVersion = parseInt(cachedDevice, 10) || 0;
    } else {
      const lastMethod = await prisma.gateway_methods.findFirst({
        where: { user_id: String(userId), device_id: String(deviceId) },
        orderBy: { updated_at: 'desc' },
        select: { updated_at: true },
      });
      deviceVersion = lastMethod ? new Date(lastMethod.updated_at).getTime() : 0;
      if (deviceVersion > 0) {
        await safeRedisSet(KEYS.deviceSync(String(userId), String(deviceId)), String(deviceVersion), TTL_SECONDS);
      }
    }
  }

  const cachedUserCustom = await safeRedisGet(KEYS.userCustomVersion(String(userId)));
  const userCustomVersion = cachedUserCustom ? parseInt(cachedUserCustom, 10) || 0 : 0;

  return Math.max(globalVersion, deviceVersion, userCustomVersion);
}

async function getActiveTemplatesForDashboard() {
  const cached = await safeRedisGet(KEYS.ACTIVE_TEMPLATES);
  if (cached) {
    return JSON.parse(cached);
  }

  const rows = await prisma.sms_templates.findMany({
    where: { is_active: 1 },
  });

  await safeRedisSet(KEYS.ACTIVE_TEMPLATES, JSON.stringify(rows), TTL_SECONDS);
  return rows;
}

async function getActiveTemplatesForParsing() {
  return getActiveTemplatesForDashboard();
}

async function getOfficialTemplatesForAdmin() {
  const cached = await safeRedisGet(KEYS.OFFICIAL_TEMPLATES_ALL);
  if (cached) {
    return JSON.parse(cached);
  }

  const rows = await prisma.sms_templates.findMany({
    where: { is_official: 1 },
  });

  await safeRedisSet(KEYS.OFFICIAL_TEMPLATES_ALL, JSON.stringify(rows), TTL_SECONDS);
  return rows;
}

function scheduleTemplateSyncBroadcast(io, version) {
  pendingBroadcastVersion = version;
  if (broadcastTimer) {
    clearTimeout(broadcastTimer);
  }

  broadcastTimer = setTimeout(() => {
    if (io) {
      io.emit('force_template_sync', {
        version: pendingBroadcastVersion,
        timestamp: Date.now(),
      });
      console.log(`[DataSyncCache] Debounced force_template_sync broadcast (version=${pendingBroadcastVersion})`);
    }
    broadcastTimer = null;
  }, 3000);
}

function isClientSyncCurrent(clientLastSync, serverVersion) {
  const client = parseInt(clientLastSync, 10) || 0;
  const server = parseInt(serverVersion, 10) || 0;
  return client > 0 && server > 0 && client >= server;
}

module.exports = {
  bumpGlobalTemplateVersion,
  bumpDeviceSyncVersion,
  bumpUserCustomTemplateVersion,
  bumpUserHistoryVersion,
  getGlobalTemplateVersion,
  getDeviceSyncVersion,
  getUserHistoryVersion,
  getActiveTemplatesForDashboard,
  getActiveTemplatesForParsing,
  getOfficialTemplatesForAdmin,
  invalidateTemplateListCaches,
  scheduleTemplateSyncBroadcast,
  isClientSyncCurrent,
};
