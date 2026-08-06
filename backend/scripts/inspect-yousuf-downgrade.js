#!/usr/bin/env node
/**
 * Inspect MD Yousuf + gateway catalog (smallest package) for safe downgrade test.
 */
const prisma = require('../db/prisma');

async function main() {
  const users = await prisma.$queryRaw`
    SELECT id, name, is_paid, active_plan_name, expiry_date, subscription_status,
           perm_website, eff_max_sites
    FROM users
    WHERE name LIKE '%Yousuf%' OR name LIKE '%Yousuf%' OR id = 1
    LIMIT 5
  `;
  console.log('USERS', JSON.stringify(users, null, 2));

  for (const u of users) {
    const subs = await prisma.$queryRaw`
      SELECT id, category, package_sku, package_full_name, website_limit_internal,
             device_limit_internal, starts_at, expires_at, status, amount_paid
      FROM user_subscriptions WHERE user_id = ${Number(u.id)}
      ORDER BY id DESC
    `;
    console.log('SUBS', u.id, JSON.stringify(subs, null, 2));
    const sites = await prisma.$queryRaw`
      SELECT id, site_name, site_url, is_active, created_at
      FROM gateway_layouts WHERE user_id = ${Number(u.id)}
      ORDER BY created_at ASC, id ASC
    `;
    console.log('SITES', u.id, JSON.stringify(sites, null, 2));
  }

  const plans = await prisma.$queryRaw`
    SELECT id, plan_name, sku_key, display_name, plan_category,
           max_sites, website_limit_internal, price, price_1m, price_12m, sort_order
    FROM subscription_plans
    WHERE plan_category IN ('gateway', 'payment_gateway')
       OR sku_key LIKE 'gw_%'
    ORDER BY COALESCE(website_limit_internal, max_sites, 999) ASC,
             COALESCE(price_1m, price, 999999) ASC,
             id ASC
  `;
  console.log('GATEWAY_PLANS', JSON.stringify(plans, null, 2));
  process.exit(0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
