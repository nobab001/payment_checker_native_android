/**
 * checkoutLayoutHelper.js — checkout tab config, global admin defaults, provider branding.
 */

const prisma = require('../db/prisma');

const GLOBAL_TABS_KEY = 'checkout_tabs_global';
const PROVIDER_BRANDING_KEY = 'checkout_provider_branding';

const DEFAULT_TABS = {
  send_money: { enabled: true, label: 'Send Money', icon: '💸', iconUrl: '', category: 'SEND_MONEY' },
  cash_out: { enabled: true, label: 'Cash Out', icon: '💵', iconUrl: '', category: 'CASH_OUT' },
  payment: { enabled: true, label: 'Payment', icon: '📱', iconUrl: '', category: 'PAYMENT' },
  bank: { enabled: false, label: 'Bank', icon: '🏦', iconUrl: '', category: 'BANK' },
  card: { enabled: true, label: 'Card Payment', icon: '💳', iconUrl: '', category: 'CARD' },
};

const DEFAULT_PROVIDER_BRANDING = {
  bkash: { displayName: 'bKash', logoUrl: '' },
  nagad: { displayName: 'Nagad', logoUrl: '' },
  rocket: { displayName: 'Rocket', logoUrl: '' },
  upay: { displayName: 'Upay', logoUrl: '' },
};

const VALID_DESIGNS = new Set(['design-1', 'design-2', 'design-3']);

function resolveDesign(theme) {
  const t = (theme || '').toLowerCase();
  if (t === 'design-4' || t === 'design-5') return 'design-3';
  if (VALID_DESIGNS.has(t)) return t;
  return 'design-1';
}

async function loadGlobalCheckoutDefaults() {
  try {
    const [tabsRow, brandRow] = await Promise.all([
      prisma.global_config.findUnique({ where: { config_key: GLOBAL_TABS_KEY } }),
      prisma.global_config.findUnique({ where: { config_key: PROVIDER_BRANDING_KEY } }),
    ]);
    let tabs = null;
    let providerBranding = null;
    if (tabsRow?.config_value) {
      try { tabs = JSON.parse(tabsRow.config_value); } catch (_) { /* */ }
    }
    if (brandRow?.config_value) {
      try { providerBranding = JSON.parse(brandRow.config_value); } catch (_) { /* */ }
    }
    return { tabs, providerBranding };
  } catch (_) {
    return { tabs: null, providerBranding: null };
  }
}

async function saveGlobalCheckoutDefaults(tabs, providerBranding) {
  if (tabs && typeof tabs === 'object') {
    await prisma.global_config.upsert({
      where: { config_key: GLOBAL_TABS_KEY },
      update: { config_value: JSON.stringify(tabs), updated_at: new Date() },
      create: { config_key: GLOBAL_TABS_KEY, config_value: JSON.stringify(tabs), updated_at: new Date() },
    });
  }
  if (providerBranding && typeof providerBranding === 'object') {
    await prisma.global_config.upsert({
      where: { config_key: PROVIDER_BRANDING_KEY },
      update: { config_value: JSON.stringify(providerBranding), updated_at: new Date() },
      create: { config_key: PROVIDER_BRANDING_KEY, config_value: JSON.stringify(providerBranding), updated_at: new Date() },
    });
  }
}

/** Parse layout_config + optional global admin defaults. */
function parseTabs(layoutConfigRaw, globalTabs) {
  let cfg = layoutConfigRaw;
  if (typeof cfg === 'string') {
    try { cfg = JSON.parse(cfg); } catch (_) { cfg = {}; }
  }
  if (!cfg || typeof cfg !== 'object') cfg = {};

  const src = cfg.tabs || cfg.checkout_tabs || {};
  const global = globalTabs || {};
  const tabs = {};

  for (const [key, def] of Object.entries(DEFAULT_TABS)) {
    const ov = src[key] || {};
    const gv = global[key] || {};
    tabs[key] = {
      id: key,
      label: ov.label || gv.label || def.label,
      icon: ov.icon || gv.icon || def.icon,
      iconUrl: ov.iconUrl || gv.iconUrl || def.iconUrl || '',
      category: ov.category || gv.category || def.category,
      enabled: ov.enabled !== undefined ? !!ov.enabled
        : (gv.enabled !== undefined ? !!gv.enabled : def.enabled),
    };
  }
  return tabs;
}

async function parseTabsForMerchant(layoutConfigRaw) {
  const { tabs: globalTabs } = await loadGlobalCheckoutDefaults();
  return parseTabs(layoutConfigRaw, globalTabs);
}

function mergeTabsIntoLayout(layoutConfigRaw, tabsInput, globalTabs) {
  let cfg = layoutConfigRaw;
  if (typeof cfg === 'string') {
    try { cfg = JSON.parse(cfg); } catch (_) { cfg = {}; }
  }
  if (!cfg || typeof cfg !== 'object') cfg = {};

  const global = globalTabs || {};
  const tabs = { ...(cfg.tabs || {}) };

  for (const [key, def] of Object.entries(DEFAULT_TABS)) {
    const ov = tabsInput[key];
    const gv = global[key] || {};
    if (ov !== undefined) {
      const enabled = typeof ov === 'boolean' ? ov : !!ov.enabled;
      const label = (typeof ov === 'object' && ov.label) ? ov.label : (tabs[key]?.label || gv.label || def.label);
      const icon = (typeof ov === 'object' && ov.icon) ? ov.icon : (tabs[key]?.icon || gv.icon || def.icon);
      const iconUrl = (typeof ov === 'object' && ov.iconUrl) ? ov.iconUrl : (tabs[key]?.iconUrl || gv.iconUrl || '');
      tabs[key] = { enabled, label, icon, iconUrl, category: def.category };
    } else if (!tabs[key]) {
      tabs[key] = {
        enabled: gv.enabled !== undefined ? !!gv.enabled : def.enabled,
        label: gv.label || def.label,
        icon: gv.icon || def.icon,
        iconUrl: gv.iconUrl || def.iconUrl || '',
        category: def.category,
      };
    }
  }
  return { ...cfg, tabs };
}

function resolveProviderBranding(globalBranding) {
  const merged = { ...DEFAULT_PROVIDER_BRANDING };
  if (globalBranding && typeof globalBranding === 'object') {
    for (const [k, v] of Object.entries(globalBranding)) {
      merged[k.toLowerCase()] = { ...merged[k.toLowerCase()], ...v };
    }
  }
  return merged;
}

function officialProviderTab(provider) {
  const p = (provider || '').toLowerCase();
  if (p === 'bank') return 'bank';
  if (p === 'card') return 'card';
  if (p.includes('merchant') || p === 'sslcommerz') return 'payment';
  return 'payment';
}

function enrichGatewayRow(g, category) {
  const checkoutData = require('./checkoutDataService');
  const cat = (category || g.category || 'SEND_MONEY').toUpperCase();
  const tab = checkoutData.categoryToTab(cat);
  const provider = (g.provider || '').trim();
  const display = g.display_name || g.provider;
  const kind = display.toLowerCase().includes('merchant') ? 'Merchant'
    : display.toLowerCase().includes('agent') ? 'Agent' : 'Personal';
  return {
    ...g,
    category: cat,
    tab,
    groupKey: `${provider}_${kind}`,
    groupLabel: `${provider} ${kind}`,
    kind,
  };
}

module.exports = {
  GLOBAL_TABS_KEY,
  PROVIDER_BRANDING_KEY,
  DEFAULT_TABS,
  DEFAULT_PROVIDER_BRANDING,
  VALID_DESIGNS,
  resolveDesign,
  loadGlobalCheckoutDefaults,
  saveGlobalCheckoutDefaults,
  parseTabs,
  parseTabsForMerchant,
  mergeTabsIntoLayout,
  resolveProviderBranding,
  officialProviderTab,
  enrichGatewayRow,
};
