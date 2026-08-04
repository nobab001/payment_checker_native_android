/**
 * PayCheck Communication Policy v1.2
 * Package-tiered app↔server contact (heartbeat / miss-probe / deactivate).
 *
 * Heartbeat intervals (HTTP only — no Socket.IO presence):
 *   Trial/welcome | Gateway: 15 min
 *   Personal Business: 30 min
 *   Personal: 60 min
 *
 * Multi-package: effectiveHeartbeat = MIN(all currently active package intervals).
 * Template sync is separate: heartbeat may signal forceSync; device then fetches caches.
 */

const prisma = require('../db/prisma');
const { isUserOnTrial } = require('./subscriptionV3/trialFlagService');

/** @typedef {'welcome'|'personal'|'personal_business'|'gateway'} CommProfileId */

const MISS_PROBE = {
  intervalMs: 60 * 1000,
  maxAttempts: 5,
};

const PROFILES = {
  // online ≈ 2× hb, offline ≈ 3× hb (Doze-safe slack for one missed beat)
  welcome: {
    id: 'welcome',
    heartbeatSec: 900, // 15 min
    onlineMs: 33 * 60 * 1000,
    graceMs: 42 * 60 * 1000,
    offlineMs: 45 * 60 * 1000,
    useSocket: false,
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
  gateway: {
    id: 'gateway',
    heartbeatSec: 900, // 15 min
    onlineMs: 33 * 60 * 1000,
    graceMs: 42 * 60 * 1000,
    offlineMs: 45 * 60 * 1000,
    useSocket: false,
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
  personal_business: {
    id: 'personal_business',
    heartbeatSec: 1800, // 30 min
    onlineMs: 65 * 60 * 1000,
    graceMs: 85 * 60 * 1000,
    offlineMs: 90 * 60 * 1000,
    useSocket: false,
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
  personal: {
    id: 'personal',
    heartbeatSec: 3600, // 60 min
    onlineMs: 125 * 60 * 1000,
    graceMs: 170 * 60 * 1000,
    offlineMs: 180 * 60 * 1000,
    useSocket: false,
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
};

const DEFAULT_PROFILE = PROFILES.personal;

function normalizePlanCategory(raw) {
  const c = String(raw || '').trim().toLowerCase();
  if (c === 'personal_business') return 'personal_business';
  if (c === 'personal') return 'personal';
  if (c === 'welcome' || c === 'trial') return 'welcome';
  if (c === 'gateway' || c === 'payment_gateway' || c === '') return 'payment_gateway';
  return c;
}

function profileFromCategory(category) {
  const c = normalizePlanCategory(category);
  if (c === 'welcome') return PROFILES.welcome;
  if (c === 'personal_business') return PROFILES.personal_business;
  if (c === 'personal') return PROFILES.personal;
  return PROFILES.gateway;
}

function isActiveDate(dateVal) {
  if (!dateVal) return false;
  const d = new Date(dateVal);
  d.setHours(0, 0, 0, 0);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return d >= today;
}

/**
 * Pure: pick effective profile = MIN(heartbeatSec) among active category keys.
 * @param {string[]} categoryKeys e.g. ['payment_gateway','personal']
 * @returns {object} profile copy with activeCategories + heartbeatSec = min
 */
function resolveProfileFromActiveCategories(categoryKeys) {
  const keys = Array.isArray(categoryKeys) ? categoryKeys : [];
  if (!keys.length) {
    return { ...DEFAULT_PROFILE, activeCategories: [] };
  }

  let chosen = null;
  const activeCategories = [];
  for (const raw of keys) {
    const cat = normalizePlanCategory(raw);
    const profile = profileFromCategory(cat);
    activeCategories.push(profile.id);
    if (!chosen || profile.heartbeatSec < chosen.heartbeatSec) {
      chosen = profile;
    }
  }

  const uniqueCats = [...new Set(activeCategories)];
  return {
    ...chosen,
    heartbeatSec: chosen.heartbeatSec,
    activeCategories: uniqueCats,
  };
}

/**
 * Collect active package category keys for an account (not just active_plan_name).
 */
function collectActiveCategoryKeys({
  isAdmin = false,
  onTrial = false,
  subscriptionRows = [],
  hasCustomSenderAddon = false,
  customSenderEndsAt = null,
  legacyPaid = false,
  legacyExpiry = null,
  legacyPlanCategory = null,
} = {}) {
  const keys = [];

  if (isAdmin) {
    keys.push('payment_gateway');
    return keys;
  }

  if (onTrial) {
    keys.push('welcome');
  }

  const rows = Array.isArray(subscriptionRows) ? subscriptionRows : [];
  for (const row of rows) {
    const status = String(row.status || 'active').toLowerCase();
    if (status !== 'active') continue;
    if (!isActiveDate(row.expires_at ?? row.expiresAt)) continue;
    keys.push(normalizePlanCategory(row.category));
  }

  if (hasCustomSenderAddon && isActiveDate(customSenderEndsAt)) {
    keys.push('personal');
  }

  // Legacy mirror: paid user with no v3 subscription rows yet
  if (!keys.length && legacyPaid && isActiveDate(legacyExpiry) && legacyPlanCategory) {
    keys.push(normalizePlanCategory(legacyPlanCategory));
  }

  return keys;
}

/**
 * Resolve communication profile for a user account.
 * effectiveHeartbeat = MIN(all simultaneously active package intervals).
 */
async function resolveCommProfile(userId) {
  const uid = Number(userId);
  if (!uid) return { ...DEFAULT_PROFILE, activeCategories: [] };

  const user = await prisma.users.findUnique({
    where: { id: uid },
    select: {
      role: true,
      is_paid: true,
      active_plan_name: true,
      expiry_date: true,
      has_custom_sender_addon: true,
      custom_sender_ends_at: true,
    },
  });
  if (!user) return { ...DEFAULT_PROFILE, activeCategories: [] };

  let subscriptionRows = [];
  try {
    const { getUserSubscriptions } = require('./subscriptionV3/sharedExpiryService');
    subscriptionRows = await getUserSubscriptions(uid);
  } catch (_) {
    subscriptionRows = [];
  }

  let legacyPlanCategory = null;
  if (user.is_paid && isActiveDate(user.expiry_date) && user.active_plan_name) {
    const plan = await prisma.subscription_plans.findFirst({
      where: { plan_name: user.active_plan_name || '' },
      select: { plan_category: true },
    });
    legacyPlanCategory = plan?.plan_category || null;
  }

  const onTrial = await isUserOnTrial(uid);
  const keys = collectActiveCategoryKeys({
    isAdmin: user.role === 'admin',
    onTrial,
    subscriptionRows,
    hasCustomSenderAddon: user.has_custom_sender_addon === 1,
    customSenderEndsAt: user.custom_sender_ends_at,
    legacyPaid: !!user.is_paid,
    legacyExpiry: user.expiry_date,
    legacyPlanCategory,
  });

  if (!keys.length) {
    return { ...DEFAULT_PROFILE, activeCategories: [] };
  }

  return resolveProfileFromActiveCategories(keys);
}

function computeStateWithProfile(lastSeenMs, isDisabled, profile, now = Date.now()) {
  if (isDisabled) return 'DISABLED';
  if (!lastSeenMs || lastSeenMs <= 0) return 'GRACE';
  const age = now - lastSeenMs;
  if (age <= profile.onlineMs) return 'ONLINE';
  if (age <= profile.graceMs) return 'GRACE';
  if (age <= profile.offlineMs) return 'OFFLINE';
  return 'STALE';
}

function toClientPolicy(profile) {
  return {
    profile: profile.id,
    heartbeat: profile.heartbeatSec,
    use_socket: false,
    thresholds: {
      online_sec: Math.round(profile.onlineMs / 1000),
      grace_sec: Math.round(profile.graceMs / 1000),
      offline_sec: Math.round(profile.offlineMs / 1000),
      miss_probe_sec: Math.round(MISS_PROBE.intervalMs / 1000),
      miss_probe_max: MISS_PROBE.maxAttempts,
    },
  };
}

function shouldForceTemplateSync(clientLastSync, templateVersion) {
  const client = parseInt(clientLastSync || '0', 10) || 0;
  const tpl = Number(templateVersion) || 0;
  return tpl > 0 && client < tpl;
}

module.exports = {
  PROFILES,
  DEFAULT_PROFILE,
  MISS_PROBE,
  resolveCommProfile,
  resolveProfileFromActiveCategories,
  collectActiveCategoryKeys,
  computeStateWithProfile,
  toClientPolicy,
  normalizePlanCategory,
  profileFromCategory,
  isActiveDate,
  shouldForceTemplateSync,
};
