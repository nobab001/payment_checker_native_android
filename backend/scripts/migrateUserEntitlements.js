/**
 * One-time / startup heal: recompute cached permission columns for all users.
 */
const prisma = require('../db/prisma');
const { syncUserEntitlements, ensureEntitlementSchema } = require('../services/accountEntitlementsService');

async function migrateAllUserEntitlements() {
  await ensureEntitlementSchema();
  const users = await prisma.users.findMany({
    where: { role: { not: 'admin' } },
    select: { id: true },
  });
  let ok = 0;
  let fail = 0;
  for (const u of users) {
    try {
      await syncUserEntitlements(u.id);
      ok += 1;
    } catch (err) {
      fail += 1;
      console.warn(`[Entitlements] sync failed user=${u.id}:`, err.message);
    }
  }
  console.log(`[Entitlements] Migration complete: ${ok} synced, ${fail} failed (${users.length} total).`);
}

module.exports = { migrateAllUserEntitlements };
