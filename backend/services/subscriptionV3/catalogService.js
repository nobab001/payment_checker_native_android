const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');
const { CATEGORIES, DURATION_DAYS, DURATION_LABELS, UNLIMITED_WEBSITE_SENTINEL } = require('./constants');

function mapPlanCategory(row) {
  const c = String(row.plan_category || '').trim();
  if (c === 'personal_business') return CATEGORIES.PERSONAL_BUSINESS;
  if (c === 'personal' || c === 'personal_custom_center') return CATEGORIES.PERSONAL;
  // Admin stores payment_gateway; V3 UI key is gateway
  return CATEGORIES.GATEWAY;
}

function mapPlanRow(row) {
  const category = mapPlanCategory(row);
  const sku = row.sku_key || `legacy_${row.id}`;
  const displayName = row.display_name || row.plan_name;
  return {
    id: Number(row.id),
    sku_key: sku,
    display_name: displayName,
    plan_name: row.plan_name,
    category,
    website_limit_internal: Number(row.website_limit_internal ?? row.max_sites ?? 1),
    device_limit_internal: Number(row.device_limit_internal ?? row.max_devices ?? 50),
    price_1m: Number(row.price_1m || row.price || 0),
    price_6m: Number(row.price_6m || 0),
    price_12m: Number(row.price_12m || row.price || 0),
    refund_days: Number(row.refund_days ?? 7),
    is_visible: Number(row.is_visible ?? 1) === 1,
    catalog_status: row.catalog_status || 'active',
    sort_order: Number(row.sort_order ?? 0),
    perm_template: Number(row.perm_template ?? 1),
    perm_website: Number(row.perm_website ?? 1),
    perm_device: Number(row.perm_device ?? 1),
    perm_smart_popup: Number(row.perm_smart_popup ?? 0),
    perm_manual_transaction: Number(row.perm_manual_transaction ?? 0),
    is_custom_sender_allowed: Number(row.is_custom_sender_allowed ?? 0),
    features_json: row.features_json,
  };
}

function packageFullName(pkg, durationKey) {
  const dur = DURATION_LABELS[durationKey] || durationKey;
  return `${dur} ${pkg.display_name}`;
}

function priceForDuration(pkg, durationKey) {
  if (durationKey === '1m') return pkg.price_1m;
  if (durationKey === '6m') return pkg.price_6m;
  if (durationKey === '12m') return pkg.price_12m;
  return pkg.price_12m;
}

function discountPercent(pkg, durationKey) {
  if (durationKey === '1m') return 0;
  const months = durationKey === '6m' ? 6 : 12;
  const full = pkg.price_1m * months;
  const actual = priceForDuration(pkg, durationKey);
  if (full <= 0 || actual <= 0 || actual >= full) return 0;
  return Math.round(100 * (1 - actual / full));
}

function websiteDisplayLabel(limit) {
  if (limit >= UNLIMITED_WEBSITE_SENTINEL || limit <= 0) return 'Unlimited Websites';
  return `${limit} Website${limit > 1 ? 's' : ''}`;
}

async function listActiveCatalog({ includeHidden = false } = {}) {
  await ensureSubscriptionV3Schema();
  const rows = await prisma.$queryRaw`
    SELECT id, plan_name, sku_key, display_name, plan_category, max_sites, max_devices,
           price, price_1m, price_6m, price_12m, website_limit_internal, device_limit_internal,
           refund_days, catalog_status, is_visible, sort_order, perm_template, perm_website,
           perm_device, perm_smart_popup, perm_manual_transaction, is_custom_sender_allowed, features_json
    FROM subscription_plans
    WHERE catalog_status = 'active' OR catalog_status IS NULL
    ORDER BY plan_category, sort_order ASC, id ASC
  `;
  return rows
    .map(mapPlanRow)
    .filter((p) => p.catalog_status !== 'archived')
    .filter((p) => includeHidden || p.is_visible);
}

async function getPackageBySku(skuKey) {
  await ensureSubscriptionV3Schema();
  const rows = await prisma.$queryRaw`
    SELECT id, plan_name, sku_key, display_name, plan_category, max_sites, max_devices,
           price, price_1m, price_6m, price_12m, website_limit_internal, device_limit_internal,
           refund_days, catalog_status, is_visible, sort_order, perm_template, perm_website,
           perm_device, perm_smart_popup, perm_manual_transaction, is_custom_sender_allowed, features_json
    FROM subscription_plans
    WHERE sku_key = ${skuKey} OR plan_name = ${skuKey}
    LIMIT 1
  `;
  return rows[0] ? mapPlanRow(rows[0]) : null;
}

async function listAddonCatalog() {
  await ensureSubscriptionV3Schema();
  const rows = await prisma.$queryRaw`
    SELECT id, addon_key, display_name, allowed_categories, price_1m, price_6m, price_12m,
           is_active, sort_order
    FROM subscription_addon_catalog
    WHERE is_active = 1
    ORDER BY sort_order ASC, id ASC
  `;
  return rows.map((r) => ({
    addon_key: r.addon_key,
    display_name: r.display_name,
    allowed_categories: typeof r.allowed_categories === 'string'
      ? JSON.parse(r.allowed_categories)
      : r.allowed_categories,
    price_1m: Number(r.price_1m),
    price_6m: Number(r.price_6m),
    price_12m: Number(r.price_12m),
  }));
}

async function archivePackage(planId, adminId) {
  await ensureSubscriptionV3Schema();
  await prisma.$executeRaw`
    UPDATE subscription_plans
    SET catalog_status = 'archived', is_visible = 0, archived_at = NOW(), archived_by = ${Number(adminId)}
    WHERE id = ${Number(planId)}
  `;
}

async function reorderPackages(items) {
  await ensureSubscriptionV3Schema();
  for (const it of items) {
    await prisma.$executeRaw`
      UPDATE subscription_plans SET sort_order = ${Number(it.sort_order)} WHERE id = ${Number(it.id)}
    `;
  }
}

module.exports = {
  mapPlanRow,
  packageFullName,
  priceForDuration,
  discountPercent,
  websiteDisplayLabel,
  durationDays: (key) => DURATION_DAYS[key] || 365,
  listActiveCatalog,
  getPackageBySku,
  listAddonCatalog,
  archivePackage,
  reorderPackages,
};
