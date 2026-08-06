const express = require('express');
const router  = express.Router();
const paymentController = require('../controllers/paymentController');
const authenticateToken = require('../middleware/auth');
const checkBillingStatus = require('../middleware/billing');
const { attachBillingStatus } = require('../middleware/billing');
const apiRateLimiter = require('../middleware/rateLimiter');

// Isolated zero-overhead ping endpoint
router.get('/ping', (req, res) => res.status(200).send('OK'));

// Apply middleware individually to prevent leakage to other /api/v1 routes
// Write/ingest paths: hard-gated — suspended accounts get 402.
router.post('/payment-sms-ingest', authenticateToken, checkBillingStatus, apiRateLimiter, paymentController.paymentSmsIngest);
router.post('/payment-sms-ingest/bulk', authenticateToken, checkBillingStatus, apiRateLimiter, paymentController.paymentSmsIngestBulk);
router.post('/sms-history/manual', authenticateToken, checkBillingStatus, apiRateLimiter, paymentController.createManualTransaction);
router.post('/sms-history/:id/soldout', authenticateToken, checkBillingStatus, paymentController.markTransactionSoldOut);

// Read-only paths: reachable while suspended so the app can render the lock/upsell
// screen with historical data instead of a network error.
router.get('/sms-history', authenticateToken, attachBillingStatus, paymentController.getSmsHistory);
router.get('/dashboard/stats', authenticateToken, attachBillingStatus, paymentController.getDashboardStats);
router.get('/custom-archives', authenticateToken, attachBillingStatus, paymentController.getCustomArchives);

module.exports = router;
