/**
 * @file Payment metrics endpoint authentication (Phase-3B.5).
 * @module middleware/paymentMetricsAuth
 *
 * Accepts:
 * - X-Payment-Metrics-Key or X-Admin-Api-Key matching PAYMENT_METRICS_API_KEY
 * - Bearer JWT with role=admin
 * Optional PAYMENT_METRICS_IP_WHITELIST (comma-separated).
 */

const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const rateLimit = require('express-rate-limit');
const prisma = require('../db/prisma');

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  throw new Error('JWT_SECRET environment variable is required and not set. Refusing to start.');
}

function timingSafeEqual(a, b) {
  if (!a || !b) return false;
  const ba = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ba.length !== bb.length) return false;
  return crypto.timingSafeEqual(ba, bb);
}

function isIpAllowed(req) {
  const raw = process.env.PAYMENT_METRICS_IP_WHITELIST || '';
  if (!raw.trim()) return true;
  const allowed = raw.split(',').map((s) => s.trim()).filter(Boolean);
  const ip = req.ip || req.socket?.remoteAddress || '';
  return allowed.some((entry) => entry === ip || ip.endsWith(entry));
}

async function authorizePaymentMetrics(req, res, next) {
  if (!isIpAllowed(req)) {
    return res.status(403).json({ success: false, error: 'IP_NOT_ALLOWED' });
  }

  const metricsKey = process.env.PAYMENT_METRICS_API_KEY;
  const headerKey = req.headers['x-payment-metrics-key']
    || req.headers['x-admin-api-key'];

  if (metricsKey && headerKey && timingSafeEqual(headerKey, metricsKey)) {
    req.metricsAuth = 'api_key';
    return next();
  }

  const authHeader = req.headers.authorization;
  if (authHeader?.startsWith('Bearer ')) {
    try {
      const token = authHeader.split(' ')[1];
      const decoded = jwt.verify(token, JWT_SECRET);
      if (decoded.role === 'admin') {
        req.user = decoded;
        req.metricsAuth = 'admin_jwt';
        return next();
      }
      const user = await prisma.users.findUnique({
        where: { id: decoded.userId },
        select: { role: true },
      });
      if (user?.role === 'admin') {
        req.user = decoded;
        req.metricsAuth = 'admin_jwt';
        return next();
      }
    } catch (_) { /* fall through */ }
  }

  return res.status(401).json({
    success: false,
    error: 'METRICS_UNAUTHORIZED',
    hint: 'Set X-Payment-Metrics-Key or Bearer admin JWT',
  });
}

const metricsRateLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: parseInt(process.env.PAYMENT_METRICS_RATE_LIMIT || '30', 10),
  standardHeaders: true,
  legacyHeaders: false,
  validate: { xForwardedForHeader: false, default: true },
  handler: (req, res) => {
    res.status(429).json({ success: false, error: 'METRICS_RATE_LIMITED' });
  },
});

module.exports = {
  authorizePaymentMetrics,
  metricsRateLimiter,
};
