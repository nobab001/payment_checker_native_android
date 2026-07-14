/**
 * PayCheck Communication Policy v1.0
 * Package-tiered app↔server contact (heartbeat / grace / offline).
 * Feature-permission logic stays elsewhere — this is only connection intensity.
 */

const prisma = require('../db/prisma');

/** @typedef {'welcome'|'personal'|'personal_business'|'gateway'} CommProfileId */

const PROFILES = {
  welcome: {
    id: 'welcome',
    heartbeatSec: 120,
    onlineMs: 2 * 60 * 1000,
    graceMs: 3 * 60 * 1000,
    offlineMs: 5 * 60 * 1000,
    useSocket: true,
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
  gateway: {
    id: 'gateway',
    heartbeatSec: 120,
    onlineMs: 2 * 60 * 1000,
    graceMs: 3 * 60 * 1000,
    offlineMs: 5 * 60 * 1000,
    useSocket: true,
    deactivateOnOffline: true,
    checkoutHideOnOffline: true,
  },
  personal: {
    id: 'personal',
    heartbeatSec: 600,
    onlineMs: 10 * 60 * 1000,
    graceMs: 15 * 60 * 1000,
    offlineMs: 20 * 60 * 1000,
    useSocket: false,
    deactivateOnOffline: false,
    checkoutHideOnOffline: false,
  },
  personal_business: {
    id: 'personal_business',
    heartbeatSec: 600,
    onlineMs: 10 * 60 * 1000,
    graceMs: 15 * 60 * 1000,
    offlineMs: 20 * 60 * 1000,
    useSocket: false,
    deactivateOnOffline: false,
    checkoutHideOnOffline: false,
  },
};

const DEFAULT_PROFILE = PROFILES.personal;

function normalizePlanCategory(raw) {
  const c = String(raw || '').trim().toLowerCase();
  if (c === 'personal_business') return 'personal_business';
  if (c === 'payment_gateway' || c === 'personal' || c === '') return 'payment_gateway';
  return c;
}

function profileFromCategory(category) {
  if (category === 'personal_business') return PROFILES.personal_business;
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
    },
  };
}

module.exports = {
  PROFILES,
  DEFAULT_PROFILE,
  resolveCommProfile,
  computeStateWithProfile,
  toClientPolicy,
  normalizePlanCategory,
};
