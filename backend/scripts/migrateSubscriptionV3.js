/**
 * One-time migration: wipe legacy plans, seed v3 catalog, reset admin to Trial.
 * Run: node scripts/migrateSubscriptionV3.js
 */
const prisma = require('../db/prisma');
const { ensureSubscriptionV3Schema } = require('../services/subscriptionV3/schema');
const { DEFAULTS, setConfig } = require('../services/subscriptionV3/configService');
const { CATEGORIES, UNLIMITED_WEBSITE_SENTINEL } = require('../services/subscriptionV3/constants');
const { getTrialPlanName } = require('../services/trialPlanService');
const { syncUserEntitlements } = require('../services/accountEntitlementsService');

const GATEWAY_PACKAGES = [
  { sku: 'gw_basic_1', name: 'Basic 1', sites: 1, p1: 100, p6: 250, p12: 495 },
  { sku: 'gw_basic_2', name: 'Basic 2', sites: 2, p1: 150, p6: 375, p12: 750 },
  { sku: 'gw_basic_3', name: 'Basic 3', sites: 3, p1: 200, p6: 500, p12: 990 },
  { sku: 'gw_basic_5', name: 'Basic 5', sites: 5, p1: 300, p6: 750, p12: 1490 },
  { sku: 'gw_unlimited', name: 'Unlimited', sites: UNLIMITED_WEBSITE_SENTINEL, p1: 500, p6: 1250, p12: 2490 },
];

async function upsertConfig() {
  for (const [k, v] of Object.entries(DEFAULTS)) {
    await setConfig(k, v);
  }
  await setConfig('subscription_version', 'v3');
}

async function wipeLegacy() {
  console.log('Wiping legacy subscription_plans and addon_plans...');
  await prisma.$executeRaw`DELETE FROM addon_plans`;
  await prisma.$executeRaw`DELETE FROM subscription_plans`;
}

async function seedGateway(sortStart = 1) {
  let order = sortStart;
  for (const g of GATEWAY_PACKAGES) {
    await prisma.$executeRaw`
      INSERT INTO subscription_plans (
        plan_name, sku_key, display_name, plan_category, max_sites, max_devices,
        website_limit_internal, device_limit_internal, price, price_1m, price_6m, price_12m,
        duration_days, is_custom_sender_allowed, perm_template, perm_website, perm_device,
        perm_smart_popup, refund_days, catalog_status, is_visible, sort_order
      ) VALUES (
        ${g.name}, ${g.sku}, ${g.name}, 'payment_gateway',
        ${g.sites >= UNLIMITED_WEBSITE_SENTINEL ? 1 : g.sites}, 50,
        ${g.sites}, 50,
        ${g.p12}, ${g.p1}, ${g.p6}, ${g.p12},
        365, 0, 1, 1, 1, 0, 7, 'active', 1, ${order++}
      )
    `;
  }
}

async function seedSingleCategory(category, sku, displayName, sortOrder, prices, flags) {
  const planCategory = category === CATEGORIES.PERSONAL_BUSINESS ? 'personal_business' : 'personal';
  await prisma.$executeRaw`
    INSERT INTO subscription_plans (
      plan_name, sku_key, display_name, plan_category, max_sites, max_devices,
      website_limit_internal, device_limit_internal, price, price_1m, price_6m, price_12m,
      duration_days, is_custom_sender_allowed, perm_template, perm_website, perm_device,
      perm_smart_popup, refund_days, catalog_status, is_visible, sort_order
    ) VALUES (
      ${displayName}, ${sku}, ${displayName}, ${planCategory},
      0, 50, 0, 50,
      ${prices.p12}, ${prices.p1}, ${prices.p6}, ${prices.p12},
      365, ${flags.customSender ? 1 : 0}, 1, ${flags.website ? 1 : 0}, 1,
      ${flags.smartPopup ? 1 : 0}, 7, 'active', 1, ${sortOrder}
    )
  `;
}

async function seedAddons() {
  const addons = [
    { key: 'smart_popup', name: 'Smart Popup', cats: ['gateway', 'personal'], p1: 50, p6: 125, p12: 250 },
    { key: 'custom_sender', name: 'Custom Sender ID', cats: ['gateway', 'personal_business', 'personal'], p1: 80, p6: 200, p12: 400 },
    { key: 'gateway_permission', name: 'Gateway Permission', cats: ['personal_business', 'personal'], p1: 100, p6: 250, p12: 500 },
  ];
  let order = 1;
  for (const a of addons) {
    await prisma.$executeRaw`
      INSERT INTO subscription_addon_catalog (
        addon_key, display_name, allowed_categories, price_1m, price_6m, price_12m, is_active, sort_order
      ) VALUES (
        ${a.key}, ${a.name}, ${JSON.stringify(a.cats)}, ${a.p1}, ${a.p6}, ${a.p12}, 1, ${order++}
      )
      ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
    `;
  }
}

async function resetAdminToTrial() {
  const trialName = await getTrialPlanName();
  const trialDays = parseInt(DEFAULTS.trial_days, 10) || 7;
  const admins = await prisma.users.findMany({ where: { role: 'admin' }, select: { id: true } });
  if (!admins.length) {
    console.warn('No admin user found — skip trial reset');
    return;
  }
  const exp = new Date();
  exp.setDate(exp.getDate() + trialDays);
  for (const a of admins) {
    await prisma.users.update({
      where: { id: a.id },
      data: {
        is_paid: 1,
        active_plan_name: trialName,
        expiry_date: exp,
        subscription_version: 'v3',
        has_custom_sender_addon: 0,
        custom_sender_ends_at: null,
      },
    });
    await prisma.$executeRaw`UPDATE users SET is_trial = 1 WHERE id = ${a.id}`;
    await prisma.$executeRaw`DELETE FROM user_subscriptions WHERE user_id = ${a.id}`;
    await prisma.$executeRaw`DELETE FROM user_subscription_addons WHERE user_id = ${a.id}`;
    await syncUserEntitlements(a.id);
    console.log(`Admin user ${a.id} → Trial (${trialDays} days)`);
  }
}

async function main() {
  console.log('=== Subscription V3 Migration ===');
  await ensureSubscriptionV3Schema();
  await upsertConfig();
  await wipeLegacy();
  await seedGateway();
  await seedSingleCategory(CATEGORIES.PERSONAL_BUSINESS, 'pb_main', 'Personal Business', 1, { p1: 120, p6: 300, p12: 600 }, { smartPopup: true, website: false, customSender: false });
  await seedSingleCategory(CATEGORIES.PERSONAL, 'personal_main', 'Personal', 1, { p1: 100, p6: 250, p12: 500 }, { smartPopup: false, website: false, customSender: true });
  await seedAddons();
  await resetAdminToTrial();
  console.log('=== Migration complete ===');
  process.exit(0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
