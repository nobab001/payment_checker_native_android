const express = require('express');
const router  = express.Router();
const paymentController = require('../controllers/paymentController');
const authenticateToken = require('../middleware/auth');
const checkBillingStatus = require('../middleware/billing');
const apiRateLimiter = require('../middleware/rateLimiter');

// Isolated zero-overhead ping endpoint
router.get('/ping', (req, res) => res.status(200).send('OK'));

// Apply middleware individually to prevent leakage to other /api/v1 routes
router.post('/payment-sms-ingest', authenticateToken, checkBillingStatus, apiRateLimiter, paymentController.paymentSmsIngest);
router.post('/payment-sms-ingest/bulk', authenticateToken, checkBillingStatus, apiRateLimiter, paymentController.paymentSmsIngestBulk);
router.get('/sms-history', authenticateToken, checkBillingStatus, paymentController.getSmsHistory);
router.get('/dashboard/stats', authenticateToken, checkBillingStatus, paymentController.getDashboardStats);
router.post('/sms-history/:id/soldout', authenticateToken, checkBillingStatus, paymentController.markTransactionSoldOut);
router.get('/custom-archives', authenticateToken, checkBillingStatus, paymentController.getCustomArchives);

module.exports = router;
