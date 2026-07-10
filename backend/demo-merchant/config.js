/**
 * Demo Merchant configuration — reads from environment.
 * This app consumes PayCheck; it does not implement payment logic.
 */

const path = require('path');

function requireEnv(name, fallback = null) {
  const value = process.env[name];
  if (value) return value;
  if (fallback != null) return fallback;
  return null;
}

const config = {
  jwtSecret: requireEnv('DEMO_MERCHANT_JWT_SECRET', 'demo-merchant-dev-secret-change-me'),
  jwtExpiresIn: requireEnv('DEMO_MERCHANT_JWT_EXPIRES', '7d'),

  /** PayCheck merchant API key (gateway_layouts.api_key) */
  paychekApiKey: requireEnv('DEMO_MERCHANT_PAYCHEK_API_KEY'),

  /** PayCheck merchant API secret for webhook signature verification */
  paychekApiSecret: requireEnv('DEMO_MERCHANT_PAYCHEK_API_SECRET'),

  /**
   * Public base URL reachable by PayCheck callbacks (no trailing slash).
   * Example: http://192.168.1.10:3000
   */
  publicBaseUrl: requireEnv('DEMO_MERCHANT_PUBLIC_URL'),

  /** Optional override when PayCheck API is on a different host than callbacks */
  paychekApiBaseUrl: requireEnv('DEMO_MERCHANT_PAYCHEK_API_URL'),

  staticDir: path.join(__dirname, 'public'),

  isConfigured() {
    return Boolean(this.paychekApiKey && this.paychekApiSecret);
  },

  resolvePublicBaseUrl(req) {
    if (this.publicBaseUrl) return this.publicBaseUrl.replace(/\/$/, '');
    const proto = req.headers['x-forwarded-proto'] || req.protocol || 'http';
    const host = req.headers['x-forwarded-host'] || req.get('host');
    return `${proto}://${host}`;
  },

  /**
   * Base URL for server-to-server PayCheck API calls (init, status).
   * Always use loopback when colocated — calling the LAN IP from the same Node process
   * can hang on Windows (hairpin) and surfaces as ECONNABORTED / INTERNAL_ERROR.
   */
  resolvePaychekApiBaseUrl(_req) {
    if (this.paychekApiBaseUrl) return this.paychekApiBaseUrl.replace(/\/$/, '');
    const port = Number(process.env.PORT) || 3000;
    return `http://127.0.0.1:${port}`;
  },

  /**
   * Browser-facing base URL — prefer the host the client actually used.
   * Avoids redirecting to a stale DEMO_MERCHANT_PUBLIC_URL (e.g. wrong LAN IP).
   */
  resolveBrowserBaseUrl(req) {
    const proto = req?.headers?.['x-forwarded-proto'] || req?.protocol || 'http';
    const host = req?.headers?.['x-forwarded-host'] || req?.get?.('host');
    if (host) return `${proto}://${host}`.replace(/\/$/, '');
    if (this.publicBaseUrl) return this.publicBaseUrl.replace(/\/$/, '');
    const port = Number(process.env.PORT) || 3000;
    return `http://127.0.0.1:${port}`;
  },

  /**
   * Webhook URL stored on payment_sessions — loopback when colocated on same Node app.
   */
  webhookUrl(_req) {
    const port = Number(process.env.PORT) || 3000;
    return `http://127.0.0.1:${port}/demo-merchant/api/webhooks/paychek`;
  },

  paymentSuccessUrl(req, orderNumber) {
    const q = new URLSearchParams({ order: orderNumber });
    return `${this.resolveBrowserBaseUrl(req)}/demo-merchant/payment/success?${q}`;
  },

  paymentCancelUrl(req, orderNumber) {
    const q = new URLSearchParams({ order: orderNumber });
    return `${this.resolveBrowserBaseUrl(req)}/demo-merchant/payment/cancel?${q}`;
  },
};

module.exports = config;
