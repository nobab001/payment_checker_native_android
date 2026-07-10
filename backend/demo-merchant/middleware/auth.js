const jwt = require('jsonwebtoken');
const config = require('../config');
const authService = require('../services/auth-service');
const { ROLES } = require('../services/roles');

function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;

  if (!token) {
    return res.status(401).json({ success: false, error: 'UNAUTHORIZED' });
  }

  try {
    const payload = authService.verifyToken(token);
    if (payload.scope !== 'demo_merchant') {
      return res.status(401).json({ success: false, error: 'INVALID_TOKEN_SCOPE' });
    }
    req.demoUser = {
      id: payload.sub,
      email: payload.email,
      role: payload.role || ROLES.USER,
    };
    return next();
  } catch (err) {
    if (err instanceof jwt.TokenExpiredError) {
      return res.status(401).json({ success: false, error: 'TOKEN_EXPIRED' });
    }
    return res.status(401).json({ success: false, error: 'INVALID_TOKEN' });
  }
}

function requireAdmin(req, res, next) {
  if (req.demoUser?.role !== ROLES.ADMIN) {
    return res.status(403).json({ success: false, error: 'ADMIN_REQUIRED' });
  }
  return next();
}

function requireConfigured(req, res, next) {
  if (!config.isConfigured()) {
    return res.status(503).json({
      success: false,
      error: 'DEMO_MERCHANT_NOT_CONFIGURED',
      message: 'Set DEMO_MERCHANT_PAYCHEK_API_KEY and DEMO_MERCHANT_PAYCHEK_API_SECRET in environment.',
    });
  }
  return next();
}

module.exports = { requireAuth, requireAdmin, requireConfigured };
