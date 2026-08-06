const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');

const DEFAULT_ADDONS = [
  {
    key: 'gateway_permission',
    name: 'গেটওয়ে পারমিশন',
    cats: ['personal_business', 'personal'],
    p1: 100,
    p6: 250,
    p12: 500,
    info: 'পার্সোনাল প্যাকেজে পেমেন্ট গেটওয়ে/API ফিচার চালু করতে এই অ্যাড-অন প্রয়োজন।',
  },
  {
    key: 'custom_sender',
    name: 'কাস্টম সেন্ডার আইডি',
    cats: ['gateway', 'personal_business', 'personal'],
    p1: 80,
    p6: 200,
    p12: 400,
    info: 'আলাদা এসএমএস কাস্টম সেন্ডার আইডি ব্যবহার করতে এই অ্যাড-অন সক্রিয় করুন।',
  },
  {
    key: 'smart_popup',
    name: 'স্মার্ট পপআপ',
    cats: ['gateway', 'personal'],
    p1: 50,
    p6: 125,
    p12: 250,
    info: 'স্মার্ট পপআপ স্ক্যান ও সোল্ড আউট ফিচার চালু করতে এই অ্যাড-অন প্রয়োজন।',
  },
];

function mapAddonRow(r) {
  return {
    id: Number(r.id),
    addon_key: r.addon_key,
    display_name: r.display_name,
    allowed_categories: typeof r.allowed_categories === 'string'
      ? JSON.parse(r.allowed_categories)
      : r.allowed_categories,
    price_1m: Number(r.price_1m),
    price_6m: Number(r.price_6m),
    price_12m: Number(r.price_12m),
    info_text: r.info_text || null,
    is_active: Number(r.is_active ?? 1) === 1,
    sort_order: Number(r.sort_order ?? 0),
  };
}

async function ensureDefaultAddons() {
  await ensureSubscriptionV3Schema();
  let order = 1;
  for (const a of DEFAULT_ADDONS) {
    await prisma.$executeRaw`
      INSERT INTO subscription_addon_catalog (
        addon_key, display_name, allowed_categories, price_1m, price_6m, price_12m,
        info_text, is_active, sort_order
      ) VALUES (
        ${a.key}, ${a.name}, ${JSON.stringify(a.cats)}, ${a.p1}, ${a.p6}, ${a.p12},
        ${a.info}, 1, ${order++}
      )
      ON DUPLICATE KEY UPDATE
        display_name = IF(display_name IS NULL OR display_name = '', VALUES(display_name), display_name)
    `;
  }
}

async function listAllAddonCatalogAdmin() {
  await ensureDefaultAddons();
  const rows = await prisma.$queryRaw`
    SELECT id, addon_key, display_name, allowed_categories, price_1m, price_6m, price_12m,
           info_text, is_active, sort_order
    FROM subscription_addon_catalog
    ORDER BY sort_order ASC, id ASC
  `;
  return rows.map(mapAddonRow);
}

async function updateAddonCatalog(addonKey, payload) {
  await ensureDefaultAddons();
  const key = String(addonKey || '').trim();
  if (!key) return { error: 'INVALID_KEY' };

  const displayName = String(payload.display_name || '').trim();
  if (!displayName) return { error: 'DISPLAY_NAME_REQUIRED' };

  const p1 = Number(payload.price_1m ?? 0);
  const p6 = Number(payload.price_6m ?? 0);
  const p12 = Number(payload.price_12m ?? 0);
  const infoText = payload.info_text != null ? String(payload.info_text) : null;
  const isActive = payload.is_active === false || payload.is_active === 0 ? 0 : 1;

  const existing = await prisma.$queryRaw`
    SELECT id FROM subscription_addon_catalog WHERE addon_key = ${key} LIMIT 1
  `;
  if (!existing.length) return { error: 'NOT_FOUND' };

  await prisma.$executeRaw`
    UPDATE subscription_addon_catalog
    SET display_name = ${displayName},
        price_1m = ${p1},
        price_6m = ${p6},
        price_12m = ${p12},
        info_text = ${infoText},
        is_active = ${isActive}
    WHERE addon_key = ${key}
  `;

  const rows = await prisma.$queryRaw`
    SELECT id, addon_key, display_name, allowed_categories, price_1m, price_6m, price_12m,
           info_text, is_active, sort_order
    FROM subscription_addon_catalog
    WHERE addon_key = ${key}
    LIMIT 1
  `;
  return { addon: mapAddonRow(rows[0]) };
}

module.exports = {
  ensureDefaultAddons,
  listAllAddonCatalogAdmin,
  updateAddonCatalog,
  mapAddonRow,
};
