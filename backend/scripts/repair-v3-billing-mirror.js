#!/usr/bin/env node
/**
 * Repair users.* billing mirror from active user_subscriptions (V3).
 * Fixes accounts where fulfill wrote V3 rows but mirrorUserBilling failed.
 */
const prisma = require('../db/prisma');
const { mirrorUserBilling } = require('../services/subscriptionV3/fulfillmentService');
const { reactivateUser } = require('../services/subscriptionStatusService');
const { bustEntitlementCache } = require('../services/permissionEngineService');

async function main() {
  const nameFilter = process.argv[2] || '';
  const users = nameFilter
    ? await prisma.$queryRaw`
        SELECT id, name, is_paid, active_plan_name, expiry_date, subscription_status
        FROM users
        WHERE name LIKE ${'%' + nameFilter + '%'}
        LIMIT 20
      `
    : await prisma.$queryRaw`
        SELECT u.id, u.name, u.is_paid, u.active_plan_name, u.expiry_date, u.subscription_status
        FROM users u
        INNER JOIN user_subscriptions s ON s.user_id = u.id AND s.status = 'active'
        WHERE u.active_plan_name = 'FREE_LEVEL' OR u.is_paid = 0 OR u.subscription_status = 'suspended'
        LIMIT 50
      `;

  console.log('candidates', users.length);
  for (const u of users) {
    const subs = await prisma.$queryRaw`
      SELECT category, package_sku, package_full_name, expires_at, status
      FROM user_subscriptions WHERE user_id = ${Number(u.id)} AND status = 'active'
    `;
    console.log('--- user', u.id, u.name, 'plan=', u.active_plan_name, 'paid=', u.is_paid, 'status=', u.subscription_status);
    console.log('    subs', JSON.stringify(subs));
    if (!subs.length) continue;
    await mirrorUserBilling(u.id);
    await reactivateUser(u.id);
    await bustEntitlementCache(u.id);
    const after = await prisma.$queryRaw`
      SELECT id, is_paid, active_plan_name, expiry_date, subscription_status
      FROM users WHERE id = ${Number(u.id)} LIMIT 1
    `;
    console.log('    AFTER', JSON.stringify(after[0]));
  }
  process.exit(0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
