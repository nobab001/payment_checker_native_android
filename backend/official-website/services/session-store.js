/**
 * Temporary demo sessions — in-memory only.
 * Never persists visitor edits to gateway_layouts / merchant account.
 */

const crypto = require('crypto');
const config = require('../config');

/** @type {Map<string, { id: string, overrides: object, createdAt: number, expiresAt: number, lastPayment?: object }>} */
const sessions = new Map();

function sweep() {
  const now = Date.now();
  for (const [id, s] of sessions) {
    if (s.expiresAt <= now) sessions.delete(id);
  }
}

setInterval(sweep, 60_000).unref?.();

function defaultOverrides() {
  return {
    editorSurface: 'hybrid',
    checkoutMode: 'transaction',
    checkoutTheme: 'design-1',
  };
}

function createSession(initialOverrides = {}) {
  sweep();
  const id = `tds_${crypto.randomBytes(16).toString('hex')}`;
  const now = Date.now();
  const session = {
    id,
    overrides: sanitizeOverrides({ ...defaultOverrides(), ...initialOverrides }),
    createdAt: now,
    expiresAt: now + config.sessionTtlMs,
    lastPayment: null,
  };
  sessions.set(id, session);
  return clone(session);
}

function getSession(id) {
  if (!id) return null;
  sweep();
  const s = sessions.get(String(id));
  if (!s) return null;
  if (s.expiresAt <= Date.now()) {
    sessions.delete(id);
    return null;
  }
  // sliding TTL on touch
  s.expiresAt = Date.now() + config.sessionTtlMs;
  return clone(s);
}

function updateOverrides(id, patch) {
  const raw = sessions.get(String(id));
  if (!raw) return null;
  raw.overrides = sanitizeOverrides({ ...raw.overrides, ...patch });
  raw.expiresAt = Date.now() + config.sessionTtlMs;
  return clone(raw);
}

function recordPayment(id, paymentInfo) {
  const raw = sessions.get(String(id));
  if (!raw) return null;
  raw.lastPayment = paymentInfo;
  return clone(raw);
}

function deleteSession(id) {
  sessions.delete(String(id));
}

function clone(s) {
  return JSON.parse(JSON.stringify(s));
}

/**
 * Only allow known override keys — never secrets / history / wallet.
 */
function sanitizeOverrides(input = {}) {
  const out = {};
  if (!input || typeof input !== 'object') return out;

  const surface = input.editorSurface;
  if (surface === 'hybrid' || surface === 'live') {
    out.editorSurface = surface;
  }

  const mode = input.checkoutMode;
  if (typeof mode === 'string' && ['transaction', 'merchant_vibe', 'hybrid', 'live'].includes(mode)) {
    out.checkoutMode = mode;
  }

  // Live surface always forces live checkout; hybrid never stays on live.
  if (out.editorSurface === 'live') {
    out.checkoutMode = 'live';
  } else if (out.editorSurface === 'hybrid' && out.checkoutMode === 'live') {
    out.checkoutMode = 'transaction';
  }

  const theme = input.checkoutTheme || input.checkoutDesign;
  if (typeof theme === 'string' && /^design-[123]$/.test(theme)) {
    out.checkoutTheme = theme;
  }

  if (Array.isArray(input.disabledGatewayIds)) {
    out.disabledGatewayIds = input.disabledGatewayIds.map(String).slice(0, 200);
  }

  if (Array.isArray(input.enabledGatewayIds)) {
    out.enabledGatewayIds = input.enabledGatewayIds.map(String).slice(0, 200);
  }

  if (Array.isArray(input.gatewayOrder)) {
    out.gatewayOrder = input.gatewayOrder.map(String).slice(0, 200);
  }

  if (input.tabs && typeof input.tabs === 'object') {
    out.tabs = {};
    for (const [tabId, conf] of Object.entries(input.tabs)) {
      if (!tabId || typeof conf !== 'object') continue;
      out.tabs[String(tabId)] = {
        enabled: conf.enabled !== false,
      };
    }
  }

  if (typeof input.defaultProvider === 'string' && input.defaultProvider.length < 64) {
    out.defaultProvider = input.defaultProvider;
  }

  return out;
}

/**
 * Apply temporary overrides onto a checkout layout payload (mutates copy).
 */
function applyOverrides(layoutPayload, overrides) {
  if (!layoutPayload || !overrides) return layoutPayload;
  const payload = JSON.parse(JSON.stringify(layoutPayload));

  if (overrides.checkoutMode) {
    payload.checkoutMode = overrides.checkoutMode;
  }

  if (overrides.checkoutTheme) {
    payload.checkoutTheme = overrides.checkoutTheme;
    payload.checkoutDesign = overrides.checkoutTheme;
  }

  if (overrides.tabs && payload.checkoutTabsAll) {
    for (const [tabId, conf] of Object.entries(overrides.tabs)) {
      if (payload.checkoutTabsAll[tabId]) {
        payload.checkoutTabsAll[tabId] = {
          ...payload.checkoutTabsAll[tabId],
          enabled: conf.enabled !== false,
        };
      }
    }
    payload.checkoutTabs = Object.values(payload.checkoutTabsAll).filter((t) => t.enabled);
  }

  let gateways = Array.isArray(payload.activeGateways) ? payload.activeGateways : [];

  if (overrides.disabledGatewayIds?.length) {
    const disabled = new Set(overrides.disabledGatewayIds.map(String));
    gateways = gateways.filter((g) => !disabled.has(String(g.id)));
  }

  if (overrides.enabledGatewayIds?.length) {
    const enabled = new Set(overrides.enabledGatewayIds.map(String));
    gateways = gateways.filter((g) => enabled.has(String(g.id)));
  }

  if (overrides.gatewayOrder?.length) {
    const order = overrides.gatewayOrder.map(String);
    const rank = new Map(order.map((id, i) => [id, i]));
    gateways = [...gateways].sort((a, b) => {
      const ra = rank.has(String(a.id)) ? rank.get(String(a.id)) : 9999;
      const rb = rank.has(String(b.id)) ? rank.get(String(b.id)) : 9999;
      return ra - rb;
    });
  }

  if (overrides.defaultProvider) {
    const pref = overrides.defaultProvider.toLowerCase();
    gateways = [...gateways].sort((a, b) => {
      const ap = String(a.providerTag || a.provider || '').toLowerCase().includes(pref) ? 0 : 1;
      const bp = String(b.providerTag || b.provider || '').toLowerCase().includes(pref) ? 0 : 1;
      return ap - bp;
    });
  }

  payload.activeGateways = gateways;
  return payload;
}

module.exports = {
  createSession,
  getSession,
  updateOverrides,
  recordPayment,
  deleteSession,
  applyOverrides,
  sanitizeOverrides,
  defaultOverrides,
};
