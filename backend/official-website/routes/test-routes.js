const express = require('express');
const rateLimit = require('express-rate-limit');
const ctrl = require('../controllers/test-controller');
const demoVisitor = require('../services/demo-visitor-service');

const router = express.Router();

const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 40,
  standardHeaders: true,
  legacyHeaders: false,
  message: { success: false, error: 'RATE_LIMIT', message: 'Too many requests' },
});

const payLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { success: false, error: 'RATE_LIMIT', message: 'Too many payment attempts' },
});

router.use(demoVisitor.attachDemoVisitor);

router.get('/status', ctrl.getStatus);
router.get('/me', ctrl.getMe);
router.post('/auth/start', authLimiter, ctrl.startDemoAuth);
router.post('/auth/new', authLimiter, ctrl.newDemoAuth);
router.post('/auth/logout', ctrl.logoutDemoAuth);
router.get('/workspace', demoVisitor.requireDemoVisitor, ctrl.getWorkspace);
router.patch('/settings', demoVisitor.requireDemoVisitor, ctrl.patchSettings);
router.post('/pay', payLimiter, demoVisitor.requireDemoVisitor, ctrl.startTestPayment);

// Legacy aliases (demoSessionId == visitor public_id)
router.post('/session', authLimiter, ctrl.createDemoSession);
router.get('/session/:id', ctrl.getDemoSession);
router.patch('/session/:id', ctrl.patchDemoSession);
router.get('/preview', ctrl.getPreviewUrl);

module.exports = router;
