/**
 * PayCheck Communication Policy v1.1
 * Package-tiered app↔server contact (heartbeat / miss-probe / deactivate).
 *
 * Trial/Gateway: heartbeat every 5 min (aligned with comm_policy / AppConfig)
 * Personal: 30 min | Personal Business: 15 min client / 30 min legacy profile
 * Legacy miss: probe every 1 min × 5, then is_active=0
 * Trial (welcome) v2.5: presence worker owns offline (not DeviceWatch)
 */

const prisma = require('../db/prisma');

/** @typedef {'welcome'|'personal'|'personal_business'|'gateway'} CommProfileId */

const MISS_PROBE = {
  intervalMs: 60 * 1000,
  maxAttempts: 5,
};

const PROFILES = {
  // NOTE on thresholds: online/grace/offline MUST be comfortably larger than the
  // heartbeat interval, otherwise a single late/jittered heartbeat (common in the
  // background under Android Doze / OEM battery management) instantly flips a live
  // device to OFFLINE. Rule of thumb: online ≈ 2× hb (survives one missed beat),
  // offline ≈ 3× hb.
  welcome: {
    id: 'welcome',
    heartbeatSec: 300, // 5 min — matches presence v2.5 / AppConfig
    onlineMs: 11 * 60 * 1000,  // ~2× hb — stays ONLINE across one missed beat
    graceMs: 14 * 60 * 1000,
    offlineMs: 15 * 60 * 1000, // ~3× hb
    useSocket: false, // HTTP heartbeat only (Comm Policy v1.1)
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
  gateway: {
    id: 'gateway',
    heartbeatSec: 300, // 5 min
    onlineMs: 11 * 60 * 1000,
    graceMs: 14 * 60 * 1000,
    offlineMs: 15 * 60 * 1000,
    useSocket: false, // HTTP heartbeat only
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
  personal: {
    id: 'personal',
    heartbeatSec: 1800, // 30 min
    onlineMs: 65 * 60 * 1000,  // ~2× hb
    graceMs: 85 * 60 * 1000,
    offlineMs: 90 * 60 * 1000, // ~3× hb
    useSocket: false,
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
  personal_business: {
    id: 'personal_business',
    heartbeatSec: 900, // 15 min (aligned with app CommPolicyStore)
    onlineMs: 33 * 60 * 1000,  // ~2× hb
    graceMs: 42 * 60 * 1000,
    offlineMs: 45 * 60 * 1000, // ~3× hb
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
  if (c === 'payment_gateway' || c === '') return 'payment_gateway';
  return c;
}

function profileFromCategory(category) {
  if (category === 'personal_business') return PROFILES.personal_business;
  if (category === 'personal') return PROFILES.personal;
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
 * Resolve communication profile for a user account.
 * Priority: Trial → paid subscription category → custom-sender addon only → sparse personal.
 */
async function resolveCommProfile(userId) {
  const uid = Number(userId);
  if (!uid) return { ...DEFAULT_PROFILE };

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
  if (!user) return { ...DEFAULT_PROFILE };

  if (user.role === 'admin') {
    return { ...PROFILES.gateway };
  }

  if (user.active_plan_name === 'Trial Package') {
    return { ...PROFILES.welcome };
  }

  if (user.is_paid && isActiveDate(user.expiry_date)) {
    const plan = await prisma.subscription_plans.findFirst({
      where: { plan_name: user.active_plan_name || '' },
      select: { plan_category: true },
    });
    const category = normalizePlanCategory(plan?.plan_category);
    return { ...profileFromCategory(category) };
  }

  if (user.has_custom_sender_addon === 1 && isActiveDate(user.custom_sender_ends_at)) {
    return { ...PROFILES.personal };
  }

  return { ...DEFAULT_PROFILE };
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
    use_socket: profile.useSocket,
    thresholds: {
      online_sec: Math.round(profile.onlineMs / 1000),
      grace_sec: Math.round(profile.graceMs / 1000),
      offline_sec: Math.round(profile.offlineMs / 1000),
      miss_probe_sec: Math.round(MISS_PROBE.intervalMs / 1000),
      miss_probe_max: MISS_PROBE.maxAttempts,
    },
  };
}

module.exports = {
  PROFILES,
  DEFAULT_PROFILE,
  MISS_PROBE,
  resolveCommProfile,
  computeStateWithProfile,
  toClientPolicy,
  normalizePlanCategory,
};
