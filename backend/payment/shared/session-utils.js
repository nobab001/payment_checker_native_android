/**
 * @file Session expiry helpers (no DB writes).
 */

const { PAYMENT_STATUS } = require('../core/payment-status');

const OPEN_STATUSES = new Set([
  PAYMENT_STATUS.CREATED,
  PAYMENT_STATUS.REDIRECTED,
  PAYMENT_STATUS.PENDING,
  PAYMENT_STATUS.PROCESSING,
]);

/**
 * @param {{ status: string, expiresAt?: Date|string }} session
 */
function isSessionExpired(session) {
  if (!session) return true;
  if (!OPEN_STATUSES.has(session.status)) return false;
  if (!session.expiresAt) return false;
  return new Date(session.expiresAt) < new Date();
}

function browserBaseUrlFromRequest(req) {
  const proto = req.headers['x-forwarded-proto'] || req.protocol || 'http';
  const host = req.headers['x-forwarded-host'] || req.get('host');
  return `${proto}://${host}`.replace(/\/$/, '');
}

/**
 * Public base for out-of-band gateway callbacks (bKash etc).
 * Prefer PUBLIC_BASE_URL — do NOT use this for browser /pay redirects (ngrok interstitial).
 */
function baseUrlFromRequest(req) {
  if (process.env.PUBLIC_BASE_URL) return process.env.PUBLIC_BASE_URL.replace(/\/$/, '');
  return browserBaseUrlFromRequest(req);
}

/** True for RFC1918 / loopback hosts — bKash cannot callback these. */
function isPrivateOrLocalHost(hostname) {
  const h = String(hostname || '').toLowerCase();
  if (!h || h === 'localhost' || h.endsWith('.local')) return true;
  if (h === '127.0.0.1' || h === '::1' || h === '0.0.0.0') return true;
  if (/^10\.\d+\.\d+\.\d+$/.test(h)) return true;
  if (/^192\.168\.\d+\.\d+$/.test(h)) return true;
  if (/^172\.(1[6-9]|2\d|3[0-1])\.\d+\.\d+$/.test(h)) return true;
  return false;
}

/**
 * Prefer PUBLIC_BASE_URL, then absolute merchant callback base, then request host.
 * Rejects private LAN hosts for external gateway callbacks.
 */
function resolvePublicBaseUrl(req, { merchantCallbackUrl } = {}) {
  if (process.env.PUBLIC_BASE_URL) {
    return process.env.PUBLIC_BASE_URL.replace(/\/$/, '');
  }
  if (merchantCallbackUrl && /^https?:\/\//i.test(merchantCallbackUrl)) {
    try {
      const u = new URL(merchantCallbackUrl);
      if (!isPrivateOrLocalHost(u.hostname)) {
        return `${u.protocol}//${u.host}`;
      }
    } catch (_) { /* ignore */ }
  }
  const fromReq = browserBaseUrlFromRequest(req);
  try {
    const u = new URL(fromReq);
    if (isPrivateOrLocalHost(u.hostname)) {
      return null;
    }
  } catch (_) {
    return null;
  }
  return fromReq;
}

module.exports = {
  isSessionExpired,
  baseUrlFromRequest,
  browserBaseUrlFromRequest,
  isPrivateOrLocalHost,
  resolvePublicBaseUrl,
};
