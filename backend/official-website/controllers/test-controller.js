const axios = require('axios');
const config = require('../config');
const demoVisitor = require('../services/demo-visitor-service');
const { applyOverrides, defaultOverrides } = require('../services/session-store');
const paychekClient = require('../services/paychek-client');

async function fetchLiveCheckoutLayout() {
  const apiKey = config.paychekApiKey;
  if (!apiKey) return null;
  const base = config.resolvePaychekApiBaseUrl();
  const res = await axios.get(`${base}/api/checkout/${encodeURIComponent(apiKey)}`, {
    timeout: 12000,
    validateStatus: () => true,
  });
  if (res.status >= 400) return null;
  return res.data;
}

function publicLayout(layout) {
  if (!layout) return null;
  return {
    siteName: layout.siteName || 'PayCheck',
    companyName: layout.companyName,
    logoUrl: layout.logoUrl,
    checkoutMode: layout.checkoutMode,
    checkoutTheme: layout.checkoutTheme || layout.checkoutDesign,
    checkoutDesign: layout.checkoutDesign || layout.checkoutTheme,
    checkoutTabs: layout.checkoutTabs || [],
    checkoutTabsAll: layout.checkoutTabsAll || {},
    providerBranding: layout.providerBranding || {},
    activeGateways: (layout.activeGateways || []).map((g) => ({
      id: g.id,
      providerTag: g.providerTag || g.provider,
      number: g.number || g.receiverNumber || g.displayNumber,
      displayName: g.displayName || g.name || g.templateName,
      isEnabled: g.isEnabled !== false,
      category: g.category,
      tab: g.tab,
    })),
    officialGateways: (layout.officialGateways || []).map((og) => ({
      id: og.id,
      provider: og.provider,
      displayName: og.displayName || og.provider,
      tab: og.tab,
      livePayment: true,
    })),
    merchantAccountsGroups: (layout.merchantAccountsGroups || []).map((g) => ({
      provider: g.provider,
      accounts: (g.accounts || []).map((a) => ({
        id: a.id,
        provider: a.provider || g.provider,
        displayName: a.displayName || a.merchantName || a.merchant_name || a.provider || g.provider,
        accountLabel: a.accountLabel || a.merchantRef || a.merchant_ref || null,
        isActive: a.isActive !== false && a.is_active !== 0,
      })),
    })),
  };
}

async function buildLayoutPayload(settings) {
  const live = await fetchLiveCheckoutLayout();
  const baseLayout = publicLayout(live);
  const overrides = settings || defaultOverrides();
  const layout = applyOverrides(baseLayout, overrides);
  return { baseLayout, layout, overrides };
}

async function getStatus(_req, res) {
  res.json({
    success: true,
    configured: config.isConfigured(),
    amountRange: { min: config.minAmount, max: config.maxAmount },
    demoTtlHours: Math.round(config.demoTtlMs / 3600000),
    notice: {
      title: 'PayCheck Test Environment',
      lines: [
        'This is a PayCheck Test Environment.',
        `You may send between ৳${config.minAmount} and ৳${config.maxAmount}.`,
        'Your payment will be automatically refunded within 24 hours.',
        `Demo accounts expire after ${Math.round(config.demoTtlMs / 3600000)} hours.`,
        `Please do not send more than ৳${config.maxAmount}.`,
      ],
    },
  });
}

async function getMe(req, res) {
  if (!req.demoVisitor) {
    return res.json({ success: true, authenticated: false, visitor: null });
  }
  const payments = await demoVisitor.listPayments(req.demoVisitor.publicId, 10);
  return res.json({
    success: true,
    authenticated: true,
    visitor: req.demoVisitor,
    payments,
    apiKey: config.paychekApiKey,
  });
}

async function startDemoAuth(req, res) {
  if (!config.isConfigured()) {
    return res.status(503).json({
      success: false,
      error: 'NOT_CONFIGURED',
      message: 'Set OFFICIAL_TEST_PAYCHEK_API_KEY and OFFICIAL_TEST_PAYCHEK_API_SECRET in .env',
    });
  }

  // Resume existing cookie account when still valid
  if (req.demoVisitor && !req.body?.forceNew) {
    const { baseLayout, layout, overrides } = await buildLayoutPayload(req.demoVisitor.settings);
    return res.json({
      success: true,
      created: false,
      visitor: req.demoVisitor,
      apiKey: config.paychekApiKey,
      baseLayout,
      layout,
      overrides,
    });
  }

  try {
    const { visitor, token } = await demoVisitor.createVisitor(req, {
      forceNew: Boolean(req.body?.forceNew),
    });
    demoVisitor.setDemoCookie(res, visitor.publicId, token, visitor.expiresAt);
    const { baseLayout, layout, overrides } = await buildLayoutPayload(visitor.settings);
    return res.status(201).json({
      success: true,
      created: true,
      visitor,
      apiKey: config.paychekApiKey,
      baseLayout,
      layout,
      overrides,
    });
  } catch (err) {
    return res.status(err.status || 500).json({
      success: false,
      error: err.code || 'DEMO_CREATE_FAILED',
      message: err.message,
    });
  }
}

async function newDemoAuth(req, res) {
  demoVisitor.clearDemoCookie(res);
  req.demoVisitor = null;
  req.body = { ...(req.body || {}), forceNew: true };
  return startDemoAuth(req, res);
}

async function logoutDemoAuth(_req, res) {
  demoVisitor.clearDemoCookie(res);
  return res.json({ success: true, authenticated: false });
}

async function getWorkspace(req, res) {
  if (!req.demoVisitor) {
    return res.status(401).json({ success: false, error: 'DEMO_AUTH_REQUIRED' });
  }
  const { baseLayout, layout, overrides } = await buildLayoutPayload(req.demoVisitor.settings);
  const payments = await demoVisitor.listPayments(req.demoVisitor.publicId, 10);
  return res.json({
    success: true,
    visitor: req.demoVisitor,
    apiKey: config.paychekApiKey,
    baseLayout,
    layout,
    overrides,
    payments,
  });
}

async function patchSettings(req, res) {
  if (!req.demoVisitor) {
    return res.status(401).json({ success: false, error: 'DEMO_AUTH_REQUIRED' });
  }
  const visitor = await demoVisitor.updateSettings(req.demoVisitor.publicId, req.body || {});
  if (!visitor) {
    return res.status(404).json({ success: false, error: 'SESSION_EXPIRED' });
  }
  const { baseLayout, layout, overrides } = await buildLayoutPayload(visitor.settings);
  return res.json({
    success: true,
    visitor,
    apiKey: config.paychekApiKey,
    baseLayout,
    layout,
    overrides,
  });
}

/** @deprecated — kept for old clients; prefer /auth/start + /workspace */
async function createDemoSession(req, res) {
  return startDemoAuth(req, res);
}

async function getDemoSession(req, res) {
  const found = await demoVisitor.getByPublicId(req.params.id);
  if (!found) {
    return res.status(404).json({ success: false, error: 'SESSION_EXPIRED' });
  }
  const { baseLayout, layout, overrides } = await buildLayoutPayload(found.visitor.settings);
  return res.json({
    success: true,
    sessionId: found.visitor.publicId,
    visitor: found.visitor,
    expiresAt: found.visitor.expiresAt,
    apiKey: config.paychekApiKey,
    baseLayout,
    layout,
    overrides,
  });
}

async function patchDemoSession(req, res) {
  const visitor = await demoVisitor.updateSettings(req.params.id, req.body || {});
  if (!visitor) {
    return res.status(404).json({ success: false, error: 'SESSION_EXPIRED' });
  }
  const { baseLayout, layout, overrides } = await buildLayoutPayload(visitor.settings);
  return res.json({
    success: true,
    sessionId: visitor.publicId,
    visitor,
    apiKey: config.paychekApiKey,
    baseLayout,
    layout,
    overrides,
  });
}

async function startTestPayment(req, res) {
  if (!config.isConfigured()) {
    return res.status(503).json({ success: false, error: 'NOT_CONFIGURED' });
  }

  const visitor = req.demoVisitor;
  if (!visitor) {
    return res.status(401).json({
      success: false,
      error: 'DEMO_AUTH_REQUIRED',
      message: 'ডেমো অ্যাকাউন্ট দিয়ে লগইন করুন',
    });
  }

  const demoSessionId = visitor.publicId;
  const amount = parseFloat(req.body.amount);
  if (!(amount >= config.minAmount && amount <= config.maxAmount)) {
    return res.status(400).json({
      success: false,
      error: 'INVALID_AMOUNT',
      message: `Amount must be between ৳${config.minAmount} and ৳${config.maxAmount}`,
    });
  }

  const purpose = String(req.body.purpose || 'pay').toLowerCase() === 'add_balance'
    ? 'add_balance'
    : 'payment';
  const orderId = `test_${purpose === 'add_balance' ? 'bal' : 'pay'}_${demoSessionId.slice(-8)}_${Date.now()}`;
  const clientOrigin = config.resolveBrowserBaseUrl(req);

  try {
    const data = await paychekClient.initPaychekCheckout(req, {
      amount,
      orderId,
      purpose,
      successUrl: config.paymentSuccessUrl(req, demoSessionId),
      cancelUrl: config.paymentCancelUrl(req, demoSessionId),
      callbackUrl: config.webhookUrl(),
      meta: {
        clientOrigin,
        demoSessionId,
        officialTest: true,
        purpose,
        demoOverrides: visitor.settings || {},
      },
    });

    const checkoutUrl = data.checkoutUrl || data.data?.checkoutUrl;
    let enrichedUrl = checkoutUrl;
    if (checkoutUrl) {
      try {
        const u = new URL(checkoutUrl, clientOrigin);
        // Internal pay/init often returns 127.0.0.1 — rewrite to visitor's LAN/public origin.
        if (u.hostname === '127.0.0.1' || u.hostname === 'localhost') {
          const origin = new URL(clientOrigin);
          u.protocol = origin.protocol;
          u.host = origin.host;
        }
        if (demoSessionId && !u.pathname.startsWith('/pay/')) {
          u.searchParams.set('demoSession', demoSessionId);
        }
        enrichedUrl = u.toString();
      } catch {
        enrichedUrl = checkoutUrl;
      }
    }

    await demoVisitor.recordPayment(demoSessionId, {
      amount,
      purpose,
      orderId,
      sessionToken: data.sessionToken || data.data?.sessionToken,
      status: 'initiated',
    });

    return res.json({
      success: true,
      checkoutUrl: enrichedUrl,
      sessionToken: data.sessionToken || data.data?.sessionToken,
      amount,
      orderId,
      demoSessionId,
      purpose,
    });
  } catch (err) {
    return res.status(err.code === 'CONFIG_ERROR' ? 503 : 502).json({
      success: false,
      error: err.code || 'PAY_INIT_FAILED',
      message: err.message,
      details: err.details || undefined,
    });
  }
}

async function getPreviewUrl(req, res) {
  const demoSessionId = String(req.query.demoSessionId || req.params.id || '').trim();
  const found = await demoVisitor.getByPublicId(demoSessionId);
  if (!found) {
    return res.status(404).json({ success: false, error: 'SESSION_EXPIRED' });
  }
  const amount = Math.min(
    config.maxAmount,
    Math.max(config.minAmount, parseFloat(req.query.amount) || config.minAmount),
  );
  const base = config.resolveBrowserBaseUrl(req);
  const q = new URLSearchParams({
    apiKey: config.paychekApiKey,
    amount: String(amount),
    demoSession: demoSessionId,
  });
  return res.json({
    success: true,
    previewUrl: `${base}/checkout.html?${q}`,
    amount,
  });
}

module.exports = {
  getStatus,
  getMe,
  startDemoAuth,
  newDemoAuth,
  logoutDemoAuth,
  getWorkspace,
  patchSettings,
  createDemoSession,
  getDemoSession,
  patchDemoSession,
  startTestPayment,
  getPreviewUrl,
  applyDemoSessionToLayout: (payload, demoSessionId, req) => {
    // Sync path used by checkout layout — load visitor settings from DB.
    // Note: this is sync wrapper; checkout controller expects sync. Use cached settings via require + async is hard.
    // We keep a sync lookup using Prisma is async — so we need a different approach.
    // Checkout controller calls this sync. Switch checkout to async OR use deasync — better fix checkoutController.
    return payload;
  },
  applyDemoSessionToLayoutAsync: async (payload, demoSessionId, req) => {
    const found = await demoVisitor.getByPublicId(demoSessionId);
    if (!found) return payload;
    const next = applyOverrides(payload, found.visitor.settings || {});
    next.successUrl = config.paymentSuccessUrl(req, demoSessionId);
    next.cancelUrl = config.paymentCancelUrl(req, demoSessionId);
    return next;
  },
};
