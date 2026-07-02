/**
 * checkoutLayoutHelper.js — shared parsing for checkout tab config stored in
 * gateway_layouts.layout_config (JSON). Backward compatible: missing keys default
 * to enabled.
 */

const DEFAULT_TABS = {
  send_money: { enabled: true, label: 'Send Money' },
  cash_out: { enabled: true, label: 'Cash Out' },
  payment: { enabled: true, label: 'Payment' },
  bank: { enabled: false, label: 'Bank' },
  card: { enabled: true, label: 'Card Payment' },
};

const VALID_DESIGNS = new Set(['design-1', 'design-2', 'design-3']);

/** Normalize checkout_theme to one of design-1|design-2|design-3. */
function resolveDesign(theme) {
  const t = (theme || '').toLowerCase();
  if (VALID_DESIGNS.has(t)) return t;
  // Legacy themes map to design-1
  return 'design-1';
}

/** Parse layout_config JSON (string or object) and merge tab overrides. */
function parseTabs(layoutConfigRaw) {
  let cfg = layoutConfigRaw;
  if (typeof cfg === 'string') {
    try { cfg = JSON.parse(cfg); } catch (_) { cfg = {}; }
  }
  if (!cfg || typeof cfg !== 'object') cfg = {};

  const src = cfg.tabs || cfg.checkout_tabs || {};
  const tabs = {};
  for (const [key, def] of Object.entries(DEFAULT_TABS)) {
    const ov = src[key];
    tabs[key] = {
      id: key,
      label: (ov && ov.label) || def.label,
      enabled: ov && ov.enabled !== undefined ? !!ov.enabled : def.enabled,
    };
  }
  return tabs;
}

/** Build layout_config object preserving unknown keys. */
function mergeTabsIntoLayout(layoutConfigRaw, tabsInput) {
  let cfg = layoutConfigRaw;
  if (typeof cfg === 'string') {
    try { cfg = JSON.parse(cfg); } catch (_) { cfg = {}; }
  }
  if (!cfg || typeof cfg !== 'object') cfg = {};

  const tabs = { ...(cfg.tabs || {}) };
  for (const [key, def] of Object.entries(DEFAULT_TABS)) {
    const ov = tabsInput[key];
    if (ov !== undefined) {
      const enabled = typeof ov === 'boolean' ? ov : !!ov.enabled;
      tabs[key] = { enabled, label: def.label };
    } else if (!tabs[key]) {
      tabs[key] = { enabled: def.enabled, label: def.label };
    }
  }
  return { ...cfg, tabs };
}

/** Map official gateway provider slug → checkout tab id. */
function officialProviderTab(provider) {
  const p = (provider || '').toLowerCase();
  if (p === 'bank') return 'bank';
  if (p === 'card') return 'card';
  if (p.includes('merchant') || p === 'sslcommerz') return 'payment';
  return 'payment';
}

/** Group synced SIM gateways under send_money (Personal). */
function enrichGatewayRow(g) {
  const provider = (g.provider || '').trim();
  const display = g.display_name || provider;
  const kind = display.toLowerCase().includes('merchant') ? 'Merchant'
    : display.toLowerCase().includes('agent') ? 'Agent' : 'Personal';
  return {
    ...g,
    tab: 'send_money',
    groupKey: `${provider}_${kind}`,
    groupLabel: `${provider} ${kind}`,
    kind,
  };
}

module.exports = {
  DEFAULT_TABS,
  VALID_DESIGNS,
  resolveDesign,
  parseTabs,
  mergeTabsIntoLayout,
  officialProviderTab,
  enrichGatewayRow,
};
