/**
 * Official Website + Interactive Test Experience config.
 * Uses a real merchant gateway_layouts row (API key/secret from env).
 * Visitors never write to that merchant account.
 */

function env(name, fallback = null) {
  const v = process.env[name];
  if (v) return v;
  return fallback;
}

const config = {
  /** Prefer new names; accept legacy DEMO_MERCHANT_* during migration */
  paychekApiKey:
    env('OFFICIAL_TEST_PAYCHEK_API_KEY') || env('DEMO_MERCHANT_PAYCHEK_API_KEY'),
  paychekApiSecret:
    env('OFFICIAL_TEST_PAYCHEK_API_SECRET') || env('DEMO_MERCHANT_PAYCHEK_API_SECRET'),
  publicBaseUrl:
    env('OFFICIAL_TEST_PUBLIC_URL') || env('DEMO_MERCHANT_PUBLIC_URL'),
  paychekApiBaseUrl: env('OFFICIAL_TEST_PAYCHEK_API_URL') || env('DEMO_MERCHANT_PAYCHEK_API_URL'),

  minAmount: 10,
  maxAmount: 100,
  sessionTtlMs: Number(env('OFFICIAL_TEST_SESSION_TTL_MS', String(2 * 60 * 60 * 1000))),

  isConfigured() {
    return Boolean(this.paychekApiKey && this.paychekApiSecret);
  },

  resolvePaychekApiBaseUrl() {
    if (this.paychekApiBaseUrl) return this.paychekApiBaseUrl.replace(/\/$/, '');
    const port = Number(process.env.PORT) || 3000;
    return `http://127.0.0.1:${port}`;
  },

  resolveBrowserBaseUrl(req) {
    const proto = req?.headers?.['x-forwarded-proto'] || req?.protocol || 'http';
    const host = req?.headers?.['x-forwarded-host'] || req?.get?.('host');
    if (host) return `${proto}://${host}`.replace(/\/$/, '');
    if (this.publicBaseUrl) return this.publicBaseUrl.replace(/\/$/, '');
    const port = Number(process.env.PORT) || 3000;
    return `http://127.0.0.1:${port}`;
  },

  webhookUrl() {
    const port = Number(process.env.PORT) || 3000;
    return `http://127.0.0.1:${port}/api/official/test/webhook/paychek`;
  },

  paymentSuccessUrl(req, demoSessionId) {
    const q = new URLSearchParams({ demoSession: demoSessionId || '', status: 'success' });
    return `${this.resolveBrowserBaseUrl(req)}/test?${q}#result`;
  },

  paymentCancelUrl(req, demoSessionId) {
    const q = new URLSearchParams({ demoSession: demoSessionId || '', status: 'cancel' });
    return `${this.resolveBrowserBaseUrl(req)}/test?${q}#result`;
  },
};

module.exports = config;
