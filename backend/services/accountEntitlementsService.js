const prisma = require('../db/prisma');

const USER_PERM_COLUMNS = [
  'perm_custom_sender',
  'perm_template',
  'perm_website',
  'perm_device',
  'perm_smart_popup',
  'eff_max_devices',
  'eff_max_sites',
  'active_addon_plan_id',
];

const SUB_PLAN_EXTRA_COLUMNS = [
  'plan_category',
  'perm_template',
  'perm_website',
  'perm_device',
  'perm_smart_popup',
];

const ADDON_EXTRA_COLUMNS = [
  'max_devices',
  'perm_custom_sender',
  'perm_template',
  'perm_website',
  'perm_device',
  'perm_smart_popup',
];

let schemaReady = false;

const ALLOWED_TABLES = new Set(['users', 'subscription_plans', 'addon_plans']);

function assertSafeIdentifier(name, label) {
  if (!/^[a-z_][a-z0-9_]*$/i.test(name)) {
    throw new Error(`Invalid ${label}: ${name}`);
  }
}

async function ensureColumn(table, column, ddl) {
  assertSafeIdentifier(table, 'table');
  assertSafeIdentifier(column, 'column');
  if (!ALLOWED_TABLES.has(table)) {
    throw new Error(`Table not allowed for migration: ${table}`);
  }

  const rows = await prisma.$queryRawUnsafe(
    `SHOW COLUMNS FROM \`${table}\` LIKE '${column}'`
  );
  if (!rows.length) {
    await prisma.$executeRawUnsafe(`ALTER TABLE \`${table}\` ADD COLUMN ${ddl}`);
  }
}

async function ensureEntitlementSchema() {
  if (schemaReady) return;

  for (const col of USER_PERM_COLUMNS) {
    if (col.startsWith('eff_max_')) {
      await ensureColumn('users', col, `\`${col}\` INT NOT NULL DEFAULT 1`);
    } else if (col === 'active_addon_plan_id') {
      await ensureColumn('users', col, '`active_addon_plan_id` INT NULL DEFAULT NULL');
    } else {
      await ensureColumn('users', col, `\`${col}\` TINYINT NOT NULL DEFAULT 0`);
    }
  }

  await ensureColumn('subscription_plans', 'plan_category', "`plan_category` VARCHAR(32) NOT NULL DEFAULT 'payment_gateway'");
  await ensureColumn('subscription_plans', 'perm_template', '`perm_template` TINYINT NOT NULL DEFAULT 1');
  await ensureColumn('subscription_plans', 'perm_website', '`perm_website` TINYINT NOT NULL DEFAULT 1');
  await ensureColumn('subscription_plans', 'perm_device', '`perm_device` TINYINT NOT NULL DEFAULT 1');
  await ensureColumn('subscription_plans', 'perm_smart_popup', '`perm_smart_popup` TINYINT NOT NULL DEFAULT 0');

  await ensureColumn('addon_plans', 'max_devices', '`max_devices` INT NOT NULL DEFAULT 2');
  await ensureColumn('addon_plans', 'perm_custom_sender', '`perm_custom_sender` TINYINT NOT NULL DEFAULT 1');
  await ensureColumn('addon_plans', 'perm_template', '`perm_template` TINYINT NOT NULL DEFAULT 0');
  await ensureColumn('addon_plans', 'perm_website', '`perm_website` TINYINT NOT NULL DEFAULT 0');
  await ensureColumn('addon_plans', 'perm_device', '`perm_device` TINYINT NOT NULL DEFAULT 1');
  await ensureColumn('addon_plans', 'perm_smart_popup', '`perm_smart_popup` TINYINT NOT NULL DEFAULT 0');

  schemaReady = true;
}

function todayDateOnly() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

function isActiveDate(dateVal) {
  if (!dateVal) return false;
  const d = new Date(dateVal);
  d.setHours(0, 0, 0, 0);
  return d >= todayDateOnly();
}

async function getTrialEntitlements() {
  const keys = ['trial_max_devices', 'trial_max_sites', 'trial_allow_custom_sender'];
  const rows = await prisma.global_config.findMany({
    where: { config_key: { in: keys } },
  });
  const map = Object.fromEntries(rows.map((r) => [r.config_key, r.config_value]));
  return {
    perm_custom_sender: parseInt(map.trial_allow_custom_sender || '0', 10) === 1 ? 1 : 0,
    perm_template: 1,
    perm_website: 1,
    perm_device: 1,
    perm_smart_popup: 0,
    eff_max_devices: parseInt(map.trial_max_devices || '1', 10) || 1,
    eff_max_sites: parseInt(map.trial_max_sites || '1', 10) || 1,
  };
}

/**
 * Merge multiple entitlement snapshots:
 * - Boolean permissions: OR (any active plan grants it)
 * - Numeric limits: MAX (highest cap wins — subscription 3 + addon 5 => 5 devices, not 8)
 */
function mergeEntitlements(snapshots) {
  const base = {
    perm_custom_sender: 0,
    perm_template: 0,
    perm_website: 0,
    perm_device: 0,
    perm_smart_popup: 0,
    eff_max_devices: 0,
    eff_max_sites: 0,
  };
  for (const snap of snapshots) {
    if (!snap) continue;
    base.perm_custom_sender = Math.max(base.perm_custom_sender, Number(snap.perm_custom_sender || 0));
    base.perm_template = Math.max(base.perm_template, Number(snap.perm_template || 0));
    base.perm_website = Math.max(base.perm_website, Number(snap.perm_website || 0));
    base.perm_device = Math.max(base.perm_device, Number(snap.perm_device || 0));
    base.perm_smart_popup = Math.max(base.perm_smart_popup, Number(snap.perm_smart_popup || 0));
    base.eff_max_devices = Math.max(base.eff_max_devices, Number(snap.eff_max_devices || 0));
    base.eff_max_sites = Math.max(base.eff_max_sites, Number(snap.eff_max_sites || 0));
  }
  if (base.eff_max_devices < 1) base.eff_max_devices = 1;
  return base;
}

async function computeEntitlementsForUser(userId) {
  await ensureEntitlementSchema();

  const user = await prisma.users.findUnique({
    where: { id: Number(userId) },
    select: {
      role: true,
      is_paid: true,
      active_plan_name: true,
      expiry_date: true,
      has_custom_sender_addon: true,
      custom_sender_ends_at: true,
      active_addon_plan_id: true,
    },
  });
  if (!user) return null;
  if (user.role === 'admin') {
    return {
      perm_custom_sender: 1,
      perm_template: 1,
      perm_website: 1,
      perm_device: 1,
      perm_smart_popup: 1,
      eff_max_devices: 999,
      eff_max_sites: 999,
    };
  }

  const snapshots = [];

  if (user.active_plan_name === 'Trial Package') {
    snapshots.push(await getTrialEntitlements());
  } else if (user.is_paid && isActiveDate(user.expiry_date)) {
    // Raw SQL — Prisma schema may lag behind ALTER-added columns (e.g. perm_smart_popup)
    const planRows = await prisma.$queryRaw`
      SELECT is_custom_sender_allowed, perm_template, perm_website, perm_device, perm_smart_popup,
             max_devices, max_sites
      FROM subscription_plans
      WHERE plan_name = ${user.active_plan_name || ''}
      LIMIT 1
    `;
    const plan = planRows[0];
    if (plan) {
      snapshots.push({
        perm_custom_sender: Number(plan.is_custom_sender_allowed || 0),
        perm_template: Number(plan.perm_template ?? 1),
        perm_website: Number(plan.perm_website ?? 1),
        perm_device: Number(plan.perm_device ?? 1),
        perm_smart_popup: Number(plan.perm_smart_popup ?? 0),
        eff_max_devices: Number(plan.max_devices || 1),
        eff_max_sites: Number(plan.max_sites || 1),
      });
    }
  }

  if (user.has_custom_sender_addon === 1 && isActiveDate(user.custom_sender_ends_at)) {
    let addon = null;
    if (user.active_addon_plan_id) {
      const addonRows = await prisma.$queryRaw`
        SELECT max_devices, perm_custom_sender, perm_template, perm_website, perm_device, perm_smart_popup
        FROM addon_plans WHERE id = ${Number(user.active_addon_plan_id)} LIMIT 1
      `;
      addon = addonRows[0];
    }
    if (!addon) {
      const addonRows = await prisma.$queryRaw`
        SELECT max_devices, perm_custom_sender, perm_template, perm_website, perm_device, perm_smart_popup
        FROM addon_plans WHERE is_active = 1 ORDER BY max_devices DESC LIMIT 1
      `;
      addon = addonRows[0];
    }
    if (addon) {
      snapshots.push({
        perm_custom_sender: Number(addon.perm_custom_sender ?? 1),
        perm_template: Number(addon.perm_template ?? 0),
        perm_website: Number(addon.perm_website ?? 0),
        perm_device: Number(addon.perm_device ?? 1),
        perm_smart_popup: Number(addon.perm_smart_popup ?? 0),
        eff_max_devices: Number(addon.max_devices || 2),
        eff_max_sites: 0,
      });
    } else {
      snapshots.push({
        perm_custom_sender: 1,
        perm_template: 0,
        perm_website: 0,
        perm_device: 1,
        perm_smart_popup: 0,
        eff_max_devices: 2,
        eff_max_sites: 0,
      });
    }
  }

  if (!snapshots.length) {
    return {
      perm_custom_sender: 0,
      perm_template: 0,
      perm_website: 0,
      perm_device: 0,
      perm_smart_popup: 0,
      eff_max_devices: 0,
      eff_max_sites: 0,
    };
  }

  return mergeEntitlements(snapshots);
}

async function syncUserEntitlements(userId) {
  const ent = await computeEntitlementsForUser(userId);
  if (!ent) return null;
  await prisma.$executeRaw`
    UPDATE users
    SET perm_custom_sender = ${ent.perm_custom_sender},
        perm_template = ${ent.perm_template},
        perm_website = ${ent.perm_website},
        perm_device = ${ent.perm_device},
        perm_smart_popup = ${ent.perm_smart_popup},
        eff_max_devices = ${ent.eff_max_devices},
        eff_max_sites = ${ent.eff_max_sites}
    WHERE id = ${Number(userId)}
  `;
  return ent;
}

async function getUserEntitlements(userId, { refresh = false } = {}) {
  await ensureEntitlementSchema();
  if (refresh) {
    return syncUserEntitlements(userId);
  }
  const rows = await prisma.$queryRaw`
    SELECT perm_custom_sender, perm_template, perm_website, perm_device, perm_smart_popup,
           eff_max_devices, eff_max_sites
    FROM users WHERE id = ${Number(userId)} LIMIT 1
  `;
  if (!rows.length) return null;
  const row = rows[0];
  const stale = row.eff_max_devices === 0 && row.perm_device === 0;
  if (stale) {
    return syncUserEntitlements(userId);
  }
  return {
    perm_custom_sender: Number(row.perm_custom_sender || 0),
    perm_template: Number(row.perm_template || 0),
    perm_website: Number(row.perm_website || 0),
    perm_device: Number(row.perm_device || 0),
    perm_smart_popup: Number(row.perm_smart_popup || 0),
    eff_max_devices: Number(row.eff_max_devices || 0),
    eff_max_sites: Number(row.eff_max_sites || 0),
  };
}

function permissionDenied(res, code, message) {
  return res.status(403).json({ success: false, error: code, message });
}

async function requirePermission(userId, permissionKey) {
  const ent = await getUserEntitlements(userId);
  if (!ent) return { ok: false, message: 'User not found' };
  if (Number(ent[permissionKey] || 0) !== 1) {
    return { ok: false, message: 'এই ফিচারের জন্য আপনার প্যাকেজে পারমিশন নেই।' };
  }
  return { ok: true, entitlements: ent };
}

module.exports = {
  ensureEntitlementSchema,
  computeEntitlementsForUser,
  syncUserEntitlements,
  getUserEntitlements,
  mergeEntitlements,
  requirePermission,
  permissionDenied,
};
