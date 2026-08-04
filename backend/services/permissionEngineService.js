/**
 * Permission Engine — separate from Subscription (billing) engine.
 * Computes effective entitlements from user_subscriptions, addons, and trial state.
 */

const prisma = require('../db/prisma');
const { ensureSubscriptionV3Schema } = require('./subscriptionV3/schema');
const { isV3Enabled } = require('./subscriptionV3/configService');
const { getEffectiveExpiry, getUserSubscriptions, formatYmd, dateOnly } = require('./subscriptionV3/sharedExpiryService');
const { isUserOnTrial } = require('./subscriptionV3/trialFlagService');
const { getUserEntitlements: legacyGetEntitlements, syncUserEntitlements: legacySync } = require('./accountEntitlementsService');
const { getRedisClient } = require('./redisClient');

const CACHE_PREFIX = 'paychek:entitlements:';
const CACHE_TTL = 300;

async function safeRedisDel(key) {
  try {
    await getRedisClient().del(key);
  } catch (_) {}
}

async function bustEntitlementCache(userId) {
  await safeRedisDel(`${CACHE_PREFIX}${userId}`);
  try {
    require('./numberHealthService').invalidateProfileCache(userId);
  } catch (_) { /* optional */ }
}

async function getTrialEntitlements() {
  const rows = await prisma.global_config.findMany({
    where: { config_key: { in: ['trial_max_devices', 'trial_max_sites', 'trial_allow_custom_sender'] } },
  });
  const map = Object.fromEntries(rows.map((r) => [r.config_key, r.config_value]));
  return {
    perm_custom_sender: parseInt(map.trial_allow_custom_sender || '1', 10) ? 1 : 0,
    perm_template: 1,
    perm_website: 1,
    perm_device: 1,
    perm_smart_popup: 1,
    perm_manual_transaction: 0,
    perm_gateway: 1,
    perm_personal: 1,
    perm_personal_business: 1,
    eff_max_devices: parseInt(map.trial_max_devices || '9999', 10) || 9999,
    eff_max_sites: parseInt(map.trial_max_sites || '9999', 10) || 9999,
    is_trial: true,
  };
}

function zeroEntitlements() {
  return {
    perm_custom_sender: 0,
    perm_template: 0,
    perm_website: 0,
    perm_device: 0,
    perm_smart_popup: 0,
    perm_manual_transaction: 0,
    perm_gateway: 0,
    perm_personal: 0,
    perm_personal_business: 0,
    eff_max_devices: 0,
    eff_max_sites: 0,
    is_trial: false,
  };
}

async function computeV3Entitlements(userId) {
  await ensureSubscriptionV3Schema();

  const user = await prisma.users.findUnique({
    where: { id: Number(userId) },
    select: { role: true, active_plan_name: true, is_paid: true, expiry_date: true },
  });
  if (!user) return null;
  if (user.role === 'admin') {
    return {
      perm_custom_sender: 1,
      perm_template: 1,
      perm_website: 1,
      perm_device: 1,
      perm_smart_popup: 1,
      perm_manual_transaction: 1,
      perm_gateway: 1,
      perm_personal: 1,
      perm_personal_business: 1,
      eff_max_devices: 999,
      eff_max_sites: 999,
      is_trial: false,
    };
  }

  if (await isUserOnTrial(userId)) {
    const trialExp = user.expiry_date ? dateOnly(user.expiry_date) : null;
    const today = dateOnly();
    if (!trialExp || today <= trialExp) {
      return getTrialEntitlements();
    }
    return zeroEntitlements();
  }

  const subs = await getUserSubscriptions(userId);
  const addonRows = await prisma.$queryRaw`
    SELECT addon_key FROM user_subscription_addons
    WHERE user_id = ${Number(userId)} AND status = 'active'
  `;
  const addonSet = new Set(addonRows.map((r) => r.addon_key));

  const effectiveExp = await getEffectiveExpiry(userId);
  if (!subs.length && !addonSet.size) {
    return zeroEntitlements();
  }
  if (effectiveExp && new Date() > effectiveExp) {
    return zeroEntitlements();
  }

  const ent = {
    perm_custom_sender: 0,
    perm_template: 0,
    perm_website: 0,
    perm_device: 0,
    perm_smart_popup: 0,
    perm_manual_transaction: 0,
    perm_gateway: 0,
    perm_personal: 0,
    perm_personal_business: 0,
    eff_max_devices: 0,
    eff_max_sites: 0,
    is_trial: false,
    shared_expiry: subs.length ? formatYmd(await require('./subscriptionV3/sharedExpiryService').getSharedExpiry(userId)) : null,
  };

  for (const s of subs) {
    const exp = dateOnly(s.expires_at);
    const today = dateOnly();
    if (today > exp) continue;

    ent.eff_max_devices = Math.max(ent.eff_max_devices, Number(s.device_limit_internal || 50));
    ent.eff_max_sites = Math.max(ent.eff_max_sites, Number(s.website_limit_internal || 0));
    ent.perm_device = 1;
    ent.perm_template = 1;

    if (s.category === 'gateway') {
      ent.perm_gateway = 1;
      ent.perm_website = 1;
    }
    if (s.category === 'personal_business') {
      ent.perm_personal_business = 1;
      ent.perm_smart_popup = 1;
    }
    if (s.category === 'personal') {
      ent.perm_personal = 1;
      ent.perm_custom_sender = 1;
    }

    // Package-level optional perms (Manual Transaction, Smart Popup overrides)
    const sku = String(s.package_sku || '').trim();
    if (sku) {
      const planRows = await prisma.$queryRaw`
        SELECT perm_manual_transaction, perm_smart_popup
        FROM subscription_plans
        WHERE sku_key = ${sku} OR plan_name = ${sku}
        LIMIT 1
      `;
      const plan = planRows[0];
      if (plan) {
        if (Number(plan.perm_manual_transaction) === 1) ent.perm_manual_transaction = 1;
        if (Number(plan.perm_smart_popup) === 1) ent.perm_smart_popup = 1;
      }
    }
  }

  if (addonSet.has('smart_popup')) ent.perm_smart_popup = 1;
  if (addonSet.has('custom_sender')) ent.perm_custom_sender = 1;
  if (addonSet.has('manual_transaction')) ent.perm_manual_transaction = 1;
  if (addonSet.has('gateway_permission')) {
    ent.perm_gateway = 1;
    ent.perm_website = 1;
  }

  if (ent.eff_max_devices < 1 && ent.perm_device) ent.eff_max_devices = 50;
  return ent;
}

async function ensureSubscriptionFresh(userId) {
  if (!(await isV3Enabled())) {
    return legacySync(userId);
  }
  const ent = await computeV3Entitlements(userId);
  if (!ent) return null;
  await prisma.$executeRaw`
    UPDATE users SET
      perm_custom_sender = ${ent.perm_custom_sender},
      perm_template = ${ent.perm_template},
      perm_website = ${ent.perm_website},
      perm_device = ${ent.perm_device},
      perm_smart_popup = ${ent.perm_smart_popup},
      perm_manual_transaction = ${ent.perm_manual_transaction || 0},
      eff_max_devices = ${ent.eff_max_devices},
      eff_max_sites = ${ent.eff_max_sites}
    WHERE id = ${Number(userId)}
  `;
  return ent;
}

async function getEntitlements(userId, { refresh = false } = {}) {
  if (!(await isV3Enabled())) {
    return legacyGetEntitlements(userId, { refresh });
  }
  if (refresh) {
    await bustEntitlementCache(userId);
    return ensureSubscriptionFresh(userId);
  }
  try {
    const cached = await getRedisClient().get(`${CACHE_PREFIX}${userId}`);
    if (cached) return JSON.parse(cached);
  } catch (_) {}
  const ent = await computeV3Entitlements(userId);
  if (ent) {
    try {
      await getRedisClient().set(`${CACHE_PREFIX}${userId}`, JSON.stringify(ent), 'EX', CACHE_TTL);
    } catch (_) {}
  }
  return ent;
}

module.exports = {
  computeV3Entitlements,
  ensureSubscriptionFresh,
  getEntitlements,
  bustEntitlementCache,
};
