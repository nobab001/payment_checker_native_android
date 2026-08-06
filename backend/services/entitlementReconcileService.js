/**
 * After entitlements change (purchase / trial end / downgrade), revoke
 * resource access that exceeds the new plan — without deleting user data.
 *
 * Policy:
 *  - No perm_custom_sender → disable ALL (*) and other archive (is_parseable=0) methods
 *  - No perm_template → disable official template methods (template_id set, parseable)
 *  - Active websites > eff_max_sites → keep oldest N active; hold the rest (is_active=0)
 */

const prisma = require('../db/prisma');

async function reconcileGatewayMethods(userId, ent) {
  const uid = String(userId);
  const allowCustom = Number(ent?.perm_custom_sender || 0) === 1;
  const allowTemplate = Number(ent?.perm_template || 0) === 1;

  if (!allowCustom) {
    // Archive / ALL lives on joined sms_templates (is_parseable=0 or sender_id=* / provider ALL)
    await prisma.$executeRaw`
      UPDATE gateway_methods gm
      LEFT JOIN sms_templates t ON t.id = gm.template_id
      SET gm.is_enabled = 0, gm.updated_at = NOW()
      WHERE gm.user_id = ${uid}
        AND gm.is_enabled = 1
        AND (
          COALESCE(t.is_parseable, 1) = 0
          OR LOWER(TRIM(COALESCE(t.sender_id, ''))) IN ('*', 'all')
          OR LOWER(TRIM(COALESCE(gm.provider, ''))) IN ('*', 'all')
        )
    `;
  }

  if (!allowTemplate) {
    await prisma.$executeRaw`
      UPDATE gateway_methods gm
      LEFT JOIN sms_templates t ON t.id = gm.template_id
      SET gm.is_enabled = 0, gm.updated_at = NOW()
      WHERE gm.user_id = ${uid}
        AND gm.is_enabled = 1
        AND gm.template_id IS NOT NULL
        AND COALESCE(t.is_parseable, 1) = 1
    `;
  }
}

/**
 * Keep the earliest-created websites within the limit; hold excess.
 * Does not delete rows — merchant can upgrade or delete to free slots.
 */
async function reconcileWebsites(userId, ent) {
  const uid = Number(userId);
  const maxSites = Math.max(0, Number(ent?.eff_max_sites || 0));
  const allowWebsite = Number(ent?.perm_website || 0) === 1;

  const sites = await prisma.gateway_layouts.findMany({
    where: { user_id: uid },
    orderBy: [{ created_at: 'asc' }, { id: 'asc' }],
    select: { id: true, is_active: true },
  });

  if (!sites.length) return { held: 0, kept: 0 };

  if (!allowWebsite || maxSites < 1) {
    const ids = sites.filter((s) => s.is_active).map((s) => s.id);
    if (ids.length) {
      await prisma.gateway_layouts.updateMany({
        where: { id: { in: ids } },
        data: { is_active: 0, updated_at: new Date() },
      });
    }
    return { held: ids.length, kept: 0 };
  }

  const keepIds = new Set(sites.slice(0, maxSites).map((s) => s.id));
  const toHold = sites.filter((s) => s.is_active && !keepIds.has(s.id)).map((s) => s.id);

  if (toHold.length) {
    await prisma.gateway_layouts.updateMany({
      where: { id: { in: toHold } },
      data: { is_active: 0, updated_at: new Date() },
    });
  }

  return { held: toHold.length, kept: Math.min(sites.length, maxSites) };
}

async function reconcileEntitlementResources(userId, entOverride = null) {
  let ent = entOverride;
  if (!ent) {
    // Do NOT refresh here — avoid recursion with ensureSubscriptionFresh.
    const { getEntitlements } = require('./permissionEngineService');
    ent = await getEntitlements(userId, { refresh: false });
  }
  if (!ent) return { ok: false };

  await reconcileGatewayMethods(userId, ent);
  const sites = await reconcileWebsites(userId, ent);

  console.log(
    `[EntitlementReconcile] user=${userId} custom=${ent.perm_custom_sender} ` +
      `template=${ent.perm_template} maxSites=${ent.eff_max_sites} heldSites=${sites.held}`
  );
  return { ok: true, sites };
}

module.exports = {
  reconcileEntitlementResources,
  reconcileGatewayMethods,
  reconcileWebsites,
};
