/**
 * Welcome / signup trial plan identity.
 * Display name lives in global_config.trial_plan_name (default: "Trial Package").
 * Entitlements for this plan still come from trial_max_* / trial_allow_custom_sender.
 */
const prisma = require('../db/prisma');

const DEFAULT_TRIAL_PLAN_NAME = 'Trial Package';
const CONFIG_KEY = 'trial_plan_name';

/** In-memory cache for sync callers (middleware). */
let cachedName = DEFAULT_TRIAL_PLAN_NAME;
let cacheReady = false;

async function refreshTrialPlanNameCache() {
  try {
    const row = await prisma.global_config.findUnique({
      where: { config_key: CONFIG_KEY },
    });
    const name = String(row?.config_value || '').trim();
    cachedName = name || DEFAULT_TRIAL_PLAN_NAME;
  } catch (_) {
    cachedName = DEFAULT_TRIAL_PLAN_NAME;
  }
  cacheReady = true;
  return cachedName;
}

function getTrialPlanNameCached() {
  return cachedName || DEFAULT_TRIAL_PLAN_NAME;
}

async function getTrialPlanName() {
  if (!cacheReady) {
    return refreshTrialPlanNameCache();
  }
  return cachedName;
}

/**
 * True when active_plan_name is the configured welcome trial (or legacy "Trial Package").
 */
function isTrialPlanName(name) {
  if (!name) return false;
  const n = String(name).trim();
  const configured = getTrialPlanNameCached();
  return n === configured || n === DEFAULT_TRIAL_PLAN_NAME;
}

async function isTrialPlanNameAsync(name) {
  await getTrialPlanName();
  return isTrialPlanName(name);
}

/**
 * Persist new trial display name and migrate users + optional subscription_plans row.
 */
async function setTrialPlanName(newName) {
  const trimmed = String(newName || '').trim();
  if (!trimmed) {
    throw new Error('trial_plan_name is required');
  }
  if (trimmed === 'FREE_LEVEL') {
    throw new Error('FREE_LEVEL is reserved');
  }

  const oldName = await getTrialPlanName();

  await prisma.global_config.upsert({
    where: { config_key: CONFIG_KEY },
    update: { config_value: trimmed },
    create: { config_key: CONFIG_KEY, config_value: trimmed },
  });

  cachedName = trimmed;
  cacheReady = true;

  if (oldName === trimmed) {
    return { oldName, newName: trimmed, migratedUsers: 0 };
  }

  const namesToMigrate = [...new Set([oldName, DEFAULT_TRIAL_PLAN_NAME])].filter(
    (n) => n && n !== trimmed
  );

  let migratedUsers = 0;
  if (namesToMigrate.length) {
    const result = await prisma.users.updateMany({
      where: { active_plan_name: { in: namesToMigrate } },
      data: { active_plan_name: trimmed },
    });
    migratedUsers = result.count;
  }

  // Keep catalog row in sync if a welcome-trial plan row exists under the old name
  for (const old of namesToMigrate) {
    try {
      await prisma.$executeRaw`
        UPDATE subscription_plans
        SET plan_name = ${trimmed}
        WHERE plan_name = ${old}
      `;
    } catch (_) {
      // Unique conflict if a paid plan already uses the new name — ignore
    }
  }

  return { oldName, newName: trimmed, migratedUsers };
}

// Warm cache on load (non-blocking)
refreshTrialPlanNameCache().catch(() => {});

module.exports = {
  DEFAULT_TRIAL_PLAN_NAME,
  CONFIG_KEY,
  getTrialPlanName,
  getTrialPlanNameCached,
  refreshTrialPlanNameCache,
  isTrialPlanName,
  isTrialPlanNameAsync,
  setTrialPlanName,
};
