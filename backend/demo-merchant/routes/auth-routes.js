const express = require('express');
const authService = require('../services/auth-service');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

router.post('/register', async (req, res) => {
  try {
    const { email, password, fullName } = req.body || {};
    const result = await authService.register({ email, password, fullName });
    return res.status(201).json({ success: true, ...result });
  } catch (err) {
    if (err.code === 'EMAIL_EXISTS') {
      return res.status(409).json({ success: false, error: err.code, message: err.message });
    }
    if (err.code === 'VALIDATION_ERROR') {
      return res.status(400).json({ success: false, error: err.code, message: err.message });
    }
    console.error('[DemoMerchant] register error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body || {};
    const result = await authService.login({ email, password });
    return res.json({ success: true, ...result });
  } catch (err) {
    if (err.code === 'INVALID_CREDENTIALS') {
      return res.status(401).json({ success: false, error: err.code, message: err.message });
    }
    console.error('[DemoMerchant] login error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.post('/logout', requireAuth, (_req, res) => {
  return res.json({ success: true, message: 'Logged out. Discard client token.' });
});

router.post('/forgot-password', async (req, res) => {
  try {
    const { email, newPassword } = req.body || {};
    const result = await authService.resetPassword({ email, newPassword });
    return res.json({ success: true, message: 'Password updated. You can log in now.', ...result });
  } catch (err) {
    if (err.code === 'NOT_FOUND') {
      return res.status(404).json({ success: false, error: err.code, message: err.message });
    }
    if (err.code === 'VALIDATION_ERROR') {
      return res.status(400).json({ success: false, error: err.code, message: err.message });
    }
    console.error('[DemoMerchant] forgot-password error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.get('/session', requireAuth, async (req, res) => {
  try {
    const user = await authService.getSession(req.demoUser.id);
    return res.json({ success: true, user });
  } catch (err) {
    if (err.code === 'NOT_FOUND') {
      return res.status(404).json({ success: false, error: err.code });
    }
    console.error('[DemoMerchant] session error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

module.exports = router;
