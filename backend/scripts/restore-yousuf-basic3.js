#!/usr/bin/env node
/**
 * Restore MD Yousuf (user 1) gateway → gw_basic_3 (Yearly Basic 3 style) + re-activate held sites.
 */
const prisma = require('../db/prisma');
const { mirrorUserBilling } = require('../services/subscriptionV3/fulfillmentService');
const { ensureSubscriptionFresh, bustEntitlementCache } = require('../services/permissionEngineService');

const USER_ID = 1;
const TARGET_SKU = 'gw_basic_3';
const FULL_NAME = 'Yearly Basic 3'; // previous display name before test downgrade

async function main() {
  const plans = await prisma.$queryRaw`
    SELECT plan_name, sku_key, display_name, website_limit_internal, max_sites,
           device_limit_internal, max_devices
    FROM subscription_plans WHERE sku_key = ${TARGET_SKU} LIMIT 1
  `;
  const pkg = plans[0];
  if (!pkg) {
    console.error('plan not found', TARGET_SKU);
    process.exit(1);
  }

  const sub = await prisma.$queryRaw`
    SELECT id FROM user_subscriptions
    WHERE user_id = ${USER_ID} AND category = 'gateway' AND status = 'active'
    LIMIT 1
  `;
  if (!sub.length) {
    console.error('no active gateway sub');
    process.exit(1);
  }

  const siteLimit = Number(pkg.website_limit_internal ?? pkg.max_sites ?? 3);
  const deviceLimit = Number(pkg.device_limit_internal ?? pkg.max_devices ?? 50);

  await prisma.$executeRaw`
    UPDATE user_subscriptions
    SET package_sku = ${TARGET_SKU},
        package_full_name = ${FULL_NAME},
        website_limit_internal = ${siteLimit},
        device_limit_internal = ${deviceLimit},
        updated_at = NOW()
    WHERE id = ${Number(sub[0].id)}
  `;

  // Restore previously held sites so account matches pre-test state.
  await prisma.$executeRaw`
    UPDATE gateway_layouts
    SET is_active = 1, updated_at = NOW()
    WHERE user_id = ${USER_ID}
  `;

  await bustEntitlementCache(USER_ID);
  await mirrorUserBilling(USER_ID);
  await ensureSubscriptionFresh(USER_ID);

  const afterUser = await prisma.$queryRaw`
    SELECT active_plan_name, eff_max_sites, is_paid, expiry_date FROM users WHERE id = ${USER_ID} LIMIT 1
  `;
  const afterSub = await prisma.$queryRaw`
    SELECT package_sku, package_full_name, website_limit_internal, expires_at, status
    FROM user_subscriptions
    WHERE user_id = ${USER_ID} AND category = 'gateway' AND status = 'active' LIMIT 1
  `;
  const afterSites = await prisma.$queryRaw`
    SELECT id, site_name, is_active FROM gateway_layouts
    WHERE user_id = ${USER_ID} ORDER BY created_at ASC, id ASC
  `;
  console.log('AFTER user', JSON.stringify(afterUser[0]));
  console.log('AFTER sub', JSON.stringify(afterSub[0]));
  console.log('AFTER sites', JSON.stringify(afterSites));
  process.exit(0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
