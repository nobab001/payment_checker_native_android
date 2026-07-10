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

function baseUrlFromRequest(req) {
  if (process.env.PUBLIC_BASE_URL) return process.env.PUBLIC_BASE_URL.replace(/\/$/, '');
  const proto = req.headers['x-forwarded-proto'] || req.protocol || 'http';
  return `${proto}://${req.get('host')}`;
}

module.exports = {
  isSessionExpired,
  baseUrlFromRequest,
};
