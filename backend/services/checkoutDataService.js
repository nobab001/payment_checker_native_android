/**
 * checkoutDataService.js — secure checkout gateway fetcher.
 * Only official, active, parseable templates may appear on customer checkout.
 */

const prisma = require('../db/prisma');
const layoutHelper = require('./checkoutLayoutHelper');

const CATEGORY_TAB = {
  SEND_MONEY: 'send_money',
  CASH_OUT: 'cash_out',
  PAYMENT: 'payment',
  BANK: 'bank',
  CARD: 'card',
};

const TAB_CATEGORY = Object.fromEntries(
  Object.entries(CATEGORY_TAB).map(([cat, tab]) => [tab, cat])
);

/** Infer category from template_name when DB column is unset (backward compatible). */
function inferCategory(template) {
  if (template?.category) {
    const c = String(template.category).toUpperCase();
    if (CATEGORY_TAB[c]) return c;
  }
  const name = (template?.template_name || '').toLowerCase();
  if (name.includes('cash out') || name.includes('cashout') || name.includes('cash in') || name.includes('agent')) {
    return 'CASH_OUT';
  }
  if (name.includes('merchant') || name.includes('payment')) return 'PAYMENT';
  if (name.includes('bank')) return 'BANK';
  if (name.includes('card')) return 'CARD';
  return 'SEND_MONEY';
}

function categoryToTab(category) {
  return CATEGORY_TAB[(category || 'SEND_MONEY').toUpperCase()] || 'send_money';
}

/**
 * Fetch gateway rows for checkout — strict security filters:
 *   gateway_methods.is_enabled = 1
 *   sms_templates.is_active = 1
 *   sms_templates.is_parseable = 1
 *   checkout_view_templates must exist (official checkout instructions)
 */
async function fetchSecureCheckoutRows(userId) {
  const uid = String(userId);
  const rows = await prisma.$queryRaw`
    SELECT gm.id, gm.sim_slot, gm.provider, gm.number, gm.display_name, gm.device_id, gm.priority,
           t.id AS template_id, t.template_name, t.category, t.is_active AS tpl_active, t.is_parseable,
           cvt.single_number_instruction, cvt.multiple_number_instruction,
           rd.device_name
      FROM gateway_methods gm
      INNER JOIN sms_templates t ON gm.template_id = t.id
      LEFT JOIN checkout_view_templates cvt ON cvt.sms_template_id = t.id
      LEFT JOIN registered_devices rd
        ON CONVERT(gm.device_id USING utf8mb4) COLLATE utf8mb4_unicode_ci
         = CONVERT(rd.device_id USING utf8mb4) COLLATE utf8mb4_unicode_ci
       AND rd.user_id = CAST(gm.user_id AS UNSIGNED)
     WHERE gm.user_id = ${uid}
       AND gm.is_enabled = 1
       AND gm.number IS NOT NULL AND gm.number != ''
       AND t.is_active = 1
       AND t.is_parseable = 1
  ORDER BY gm.priority ASC, gm.sim_slot ASC
  `;
  return rows;
}

function formatRow(g, providerCounts) {
  const category = inferCategory({
    category: g.category,
    template_name: g.template_name,
  });
  const tab = categoryToTab(category);
  const provider = (g.provider || '').trim();
  const display = g.display_name || provider;
  const kind = display.toLowerCase().includes('merchant') ? 'Merchant'
    : display.toLowerCase().includes('agent') ? 'Agent' : 'Personal';
  const count = providerCounts[`${tab}|${provider}`] || 1;
  const fallbackSingle = `${display} নম্বরে টাকা পাঠান`;
  const fallbackMulti = `${display} নম্বারগুলোর যেকোনো একটিতে টাকা পাঠান`;
  const instruction = count > 1
    ? (g.multiple_number_instruction || fallbackMulti)
    : (g.single_number_instruction || fallbackSingle);

  return {
    id: Number(g.id) || g.id,
    methodId: Number(g.id) || g.id,
    simSlot: g.sim_slot,
    provider,
    number: g.number,
    deviceId: g.device_id,
    deviceName: g.device_name || 'Main Phone',
    displayName: display,
    templateId: g.template_id,
    category,
    tab,
    groupKey: `${provider}_${kind}`,
    groupLabel: `${provider} ${kind}`,
    kind,
    instruction,
    position: Number.MAX_SAFE_INTEGER,
    enabled: true,
  };
}

function applyNumberOrderOverrides(gateways, numberOrderJson, options = {}) {
  const excludeDisabled = options.excludeDisabled === true;
  if (!numberOrderJson) return gateways;

  let overrides = [];
  try { overrides = JSON.parse(numberOrderJson); } catch (_) { return gateways; }

  const orderMap = new Map();
  overrides.forEach((o, idx) => {
    const key = o.methodId != null ? `id:${o.methodId}` : `num:${o.provider}|${o.number}`;
    orderMap.set(key, { position: o.position != null ? o.position : idx, enabled: o.enabled !== false });
  });

  const lookup = (g) => orderMap.get(`id:${g.id}`) || orderMap.get(`id:${g.methodId}`)
    || orderMap.get(`num:${g.provider}|${g.number}`);

  const mapped = gateways.map((g) => {
    const ov = lookup(g);
    return {
      ...g,
      position: ov ? ov.position : g.position,
      enabled: ov ? ov.enabled : true,
    };
  }).sort((a, b) => a.position - b.position);

  if (!excludeDisabled) return mapped;
  return mapped.filter((g) => g.enabled !== false);
}

/**
 * Full secure checkout payload for a merchant user.
 * Returns flat list + grouped-by-category map for tab rendering.
 */
async function buildSecureCheckoutData(userId, numberOrderJson = null, options = {}) {
  const rows = await fetchSecureCheckoutRows(userId);

  const providerCounts = {};
  rows.forEach((g) => {
    const cat = inferCategory({ category: g.category, template_name: g.template_name });
    const tab = categoryToTab(cat);
    const p = g.provider;
    const key = `${tab}|${p}`;
    providerCounts[key] = (providerCounts[key] || 0) + 1;
  });

  let gateways = rows.map((g) => formatRow(g, providerCounts));
  gateways = applyNumberOrderOverrides(gateways, numberOrderJson, options);

  const gatewaysByCategory = {
    SEND_MONEY: [],
    CASH_OUT: [],
    PAYMENT: [],
    BANK: [],
    CARD: [],
  };
  gateways.forEach((g) => {
    const cat = (g.category || 'SEND_MONEY').toUpperCase();
    if (gatewaysByCategory[cat]) gatewaysByCategory[cat].push(g);
    else gatewaysByCategory.SEND_MONEY.push(g);
  });

  const activeNumbers = gateways.map((g, idx) => ({
    methodId: g.methodId,
    provider: g.provider,
    number: g.number,
    simSlot: g.simSlot,
    deviceId: g.deviceId,
    displayName: g.displayName,
    templateId: g.templateId,
    category: g.category,
    tab: g.tab,
    enabled: g.enabled !== false,
    position: g.position != null ? g.position : idx,
  }));

  const customerGateways = options.excludeDisabled
    ? gateways.filter((g) => g.enabled !== false)
    : gateways;

  return { gateways: customerGateways, gatewaysByCategory, activeNumbers };
}

module.exports = {
  CATEGORY_TAB,
  TAB_CATEGORY,
  inferCategory,
  categoryToTab,
  fetchSecureCheckoutRows,
  buildSecureCheckoutData,
};
