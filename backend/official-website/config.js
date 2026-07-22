/**
 * Official Website + Interactive Test Experience config.
 * Host merchant credentials come from env (read-only for visitors).
 */

function env(name, fallback = null) {
  const v = process.env[name];
  if (v) return v;
  return fallback;
}

function isLoopbackHost(host) {
  const h = String(host || '').toLowerCase();
  return /^127\.0\.0\.1(?::|$)/.test(h) || /^localhost(?::|$)/.test(h);
}

const config = {
  paychekApiKey:
    env('OFFICIAL_TEST_PAYCHEK_API_KEY') || env('DEMO_MERCHANT_PAYCHEK_API_KEY'),
  paychekApiSecret:
    env('OFFICIAL_TEST_PAYCHEK_API_SECRET') || env('DEMO_MERCHANT_PAYCHEK_API_SECRET'),
  publicBaseUrl:
    env('OFFICIAL_TEST_PUBLIC_URL') || env('DEMO_MERCHANT_PUBLIC_URL'),
  paychekApiBaseUrl: env('OFFICIAL_TEST_PAYCHEK_API_URL') || env('DEMO_MERCHANT_PAYCHEK_API_URL'),

  hostWebsiteId: (() => {
    const n = parseInt(env('OFFICIAL_TEST_HOST_WEBSITE_ID', ''), 10);
    return Number.isFinite(n) && n > 0 ? n : null;
  })(),

  minAmount: 10,
  maxAmount: 100,

  demoTtlMs: Number(env('OFFICIAL_TEST_DEMO_TTL_MS', String(24 * 60 * 60 * 1000))),
  sessionTtlMs: Number(env('OFFICIAL_TEST_SESSION_TTL_MS', String(2 * 60 * 60 * 1000))),

  demoMaxAccountsPerHour: Number(env('OFFICIAL_TEST_MAX_ACCOUNTS_PER_HOUR', '3')),
  demoMaxAccountsPerDay: Number(env('OFFICIAL_TEST_MAX_ACCOUNTS_PER_DAY', '20')),

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
    // Prefer LAN/public host — never send phones to 127.0.0.1
    if (host && !isLoopbackHost(host)) {
      return `${proto}://${host}`.replace(/\/$/, '');
    }
    if (this.publicBaseUrl) return this.publicBaseUrl.replace(/\/$/, '');
    if (host) return `${proto}://${host}`.replace(/\/$/, '');
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
