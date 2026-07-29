const prisma = require('../db/prisma');

const CACHE_TTL_MS = 15_000;
let cache = { value: false, at: 0 };

async function isMaintenanceModeOn() {
  const now = Date.now();
  if (now - cache.at < CACHE_TTL_MS) return cache.value;
  try {
    const row = await prisma.global_config.findUnique({
      where: { config_key: 'maintenance_mode' },
    });
    cache.value = String(row?.config_value || '').toLowerCase() === 'true';
  } catch {
    cache.value = false;
  }
  cache.at = now;
  return cache.value;
}

function bustMaintenanceCache() {
  cache.at = 0;
}

function isAdminBypassContact(contact) {
  const cleaned = String(contact || '').trim();
  const adminSecret = process.env.ADMIN_SECRET_USERNAME || 'admin';
  const adminEmail = (process.env.ADMIN_EMAIL || '').trim();
  if (cleaned === adminSecret) return true;
  if (adminEmail && cleaned === adminEmail) return true;
  return false;
}

module.exports = {
  isMaintenanceModeOn,
  bustMaintenanceCache,
  isAdminBypassContact,
};
