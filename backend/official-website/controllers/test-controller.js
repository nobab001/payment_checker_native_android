const axios = require('axios');
const config = require('../config');
const sessionStore = require('../services/session-store');
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
  // Strip anything that looks sensitive; keep only UX fields for the Edit UI.
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
    officialGateways: layout.officialGateways || [],
  };
}

async function getStatus(_req, res) {
  res.json({
    success: true,
    configured: config.isConfigured(),
    amountRange: { min: config.minAmount, max: config.maxAmount },
    notice: {
      title: 'PayCheck Test Environment',
      lines: [
        'This is a PayCheck Test Environment.',
        `You may send between ৳${config.minAmount} and ৳${config.maxAmount}.`,
        'Your payment will be automatically refunded within 24 hours.',
        `Please do not send more than ৳${config.maxAmount}.`,
      ],
    },
  });
}

async function createDemoSession(_req, res) {
  if (!config.isConfigured()) {
    return res.status(503).json({
      success: false,
      error: 'NOT_CONFIGURED',
      message: 'Set OFFICIAL_TEST_PAYCHEK_API_KEY and OFFICIAL_TEST_PAYCHEK_API_SECRET in .env',
    });
  }
  const session = sessionStore.createSession();
  const live = await fetchLiveCheckoutLayout();
  const baseLayout = publicLayout(live);
  const layout = sessionStore.applyOverrides(baseLayout, session.overrides);
  return res.status(201).json({
    success: true,
    sessionId: session.id,
    expiresAt: session.expiresAt,
    apiKey: config.paychekApiKey,
    baseLayout,
    layout,
    overrides: session.overrides,
  });
}

async function getDemoSession(req, res) {
  const session = sessionStore.getSession(req.params.id);
  if (!session) {
    return res.status(404).json({ success: false, error: 'SESSION_EXPIRED' });
  }
  const live = await fetchLiveCheckoutLayout();
  const baseLayout = publicLayout(live);
  const layout = sessionStore.applyOverrides(baseLayout, session.overrides);
  return res.json({
    success: true,
    sessionId: session.id,
    expiresAt: session.expiresAt,
    apiKey: config.paychekApiKey,
    baseLayout,
    layout,
    overrides: session.overrides,
    lastPayment: session.lastPayment,
  });
}

async function patchDemoSession(req, res) {
  const session = sessionStore.updateOverrides(req.params.id, req.body || {});
  if (!session) {
    return res.status(404).json({ success: false, error: 'SESSION_EXPIRED' });
  }
  const live = await fetchLiveCheckoutLayout();
  const baseLayout = publicLayout(live);
  const layout = sessionStore.applyOverrides(baseLayout, session.overrides);
  return res.json({
    success: true,
    sessionId: session.id,
    expiresAt: session.expiresAt,
    apiKey: config.paychekApiKey,
    baseLayout,
    layout,
    overrides: session.overrides,
  });
}

async function startTestPayment(req, res) {
  if (!config.isConfigured()) {
    return res.status(503).json({ success: false, error: 'NOT_CONFIGURED' });
  }

  const demoSessionId = String(req.body.demoSessionId || req.body.sessionId || '').trim();
  const session = sessionStore.getSession(demoSessionId);
  if (!session) {
    return res.status(404).json({ success: false, error: 'SESSION_EXPIRED' });
  }

  const amount = parseFloat(req.body.amount);
  if (!(amount >= config.minAmount && amount <= config.maxAmount)) {
    return res.status(400).json({
      success: false,
      error: 'INVALID_AMOUNT',
      message: `Amount must be between ৳${config.minAmount} and ৳${config.maxAmount}`,
    });
  }

  const orderId = `test_${demoSessionId.slice(-10)}_${Date.now()}`;
  const clientOrigin = config.resolveBrowserBaseUrl(req);

  try {
    const data = await paychekClient.initPaychekCheckout(req, {
      amount,
      orderId,
      successUrl: config.paymentSuccessUrl(req, demoSessionId),
      cancelUrl: config.paymentCancelUrl(req, demoSessionId),
      callbackUrl: config.webhookUrl(),
      meta: {
        clientOrigin,
        demoSessionId,
        officialTest: true,
        demoOverrides: session.overrides || {},
      },
    });

    const checkoutUrl = data.checkoutUrl || data.data?.checkoutUrl;
    let enrichedUrl = checkoutUrl;
    if (checkoutUrl && demoSessionId) {
      try {
        const u = new URL(checkoutUrl, clientOrigin);
        // Prefer checkout.html path params when redirect is /pay/:token
        if (u.pathname.startsWith('/pay/')) {
          // keep /pay/:token — demo overlay applied via session meta next step
        } else {
          u.searchParams.set('demoSession', demoSessionId);
        }
        enrichedUrl = u.toString();
      } catch {
        enrichedUrl = checkoutUrl;
      }
    }

    // Store payment token on temp session for Result UI
    sessionStore.recordPayment(demoSessionId, {
      status: 'initiated',
      amount,
      orderId,
      sessionToken: data.sessionToken || data.data?.sessionToken,
      checkoutUrl: enrichedUrl,
      at: Date.now(),
    });

    return res.json({
      success: true,
      checkoutUrl: enrichedUrl,
      sessionToken: data.sessionToken || data.data?.sessionToken,
      amount,
      orderId,
      demoSessionId,
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

/**
 * Preview URL for iframe — real checkout.html with amount + demoSession overlay.
 * Does not create a payment session until user clicks Pay.
 */
async function getPreviewUrl(req, res) {
  const demoSessionId = String(req.query.demoSessionId || req.params.id || '').trim();
  const session = sessionStore.getSession(demoSessionId);
  if (!session) {
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
  createDemoSession,
  getDemoSession,
  patchDemoSession,
  startTestPayment,
  getPreviewUrl,
  applyDemoSessionToLayout: (payload, demoSessionId, req) => {
    const session = sessionStore.getSession(demoSessionId);
    if (!session) return payload;
    const next = sessionStore.applyOverrides(payload, session.overrides);
    // Live pay must return to LAN /test — not merchant DB URLs / ngrok.
    next.successUrl = config.paymentSuccessUrl(req, demoSessionId);
    next.cancelUrl = config.paymentCancelUrl(req, demoSessionId);
    return next;
  },
};
