/**
 * PayCheck Communication Policy v1.1
 * Package-tiered app↔server contact (heartbeat / miss-probe / deactivate).
 *
 * Gateway + Trial: heartbeat every 10 min
 * Personal + Personal Business: heartbeat every 30 min
 * All packages on miss: probe every 1 min × 5, then is_active=0
 */

const prisma = require('../db/prisma');

/** @typedef {'welcome'|'personal'|'personal_business'|'gateway'} CommProfileId */

const MISS_PROBE = {
  intervalMs: 60 * 1000,
  maxAttempts: 5,
};

const PROFILES = {
  welcome: {
    id: 'welcome',
    heartbeatSec: 600, // 10 min
    onlineMs: 10 * 60 * 1000,
    graceMs: 10 * 60 * 1000,
    offlineMs: 10 * 60 * 1000, // miss deadline = one heartbeat window
    useSocket: false, // HTTP heartbeat only (Comm Policy v1.1)
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
  gateway: {
    id: 'gateway',
    heartbeatSec: 600,
    onlineMs: 10 * 60 * 1000,
    graceMs: 10 * 60 * 1000,
    offlineMs: 10 * 60 * 1000,
    useSocket: false, // HTTP heartbeat only
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
  personal: {
    id: 'personal',
    heartbeatSec: 1800, // 30 min
    onlineMs: 30 * 60 * 1000,
    graceMs: 30 * 60 * 1000,
    offlineMs: 30 * 60 * 1000,
    useSocket: false,
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
  personal_business: {
    id: 'personal_business',
    heartbeatSec: 1800,
    onlineMs: 30 * 60 * 1000,
    graceMs: 30 * 60 * 1000,
    offlineMs: 30 * 60 * 1000,
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
