#!/usr/bin/env node
/** One-shot: refresh entitlements + reconcile resources for a user (or all mismatched). */
const { ensureSubscriptionFresh, bustEntitlementCache } = require('../services/permissionEngineService');
const { reconcileEntitlementResources } = require('../services/entitlementReconcileService');
const prisma = require('../db/prisma');

async function main() {
  const arg = process.argv[2];
  let ids = [];
  if (arg && /^\d+$/.test(arg)) {
    ids = [Number(arg)];
  } else if (arg) {
    const rows = await prisma.$queryRaw`
      SELECT id FROM users WHERE name LIKE ${'%' + arg + '%'} LIMIT 20
    `;
    ids = rows.map((r) => Number(r.id));
  } else {
    const rows = await prisma.$queryRaw`
      SELECT DISTINCT u.id
      FROM users u
      INNER JOIN user_subscriptions s ON s.user_id = u.id AND s.status = 'active'
      LIMIT 100
    `;
    ids = rows.map((r) => Number(r.id));
  }
  console.log('reconciling users', ids.length, ids);
  for (const id of ids) {
    await bustEntitlementCache(id);
    const ent = await ensureSubscriptionFresh(id);
    const r = await reconcileEntitlementResources(id, ent);
    console.log('user', id, 'ent', {
      custom: ent?.perm_custom_sender,
      template: ent?.perm_template,
      sites: ent?.eff_max_sites,
    }, 'reconcile', r);
  }
  process.exit(0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
