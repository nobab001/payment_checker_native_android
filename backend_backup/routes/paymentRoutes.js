const express = require('express');
const router  = express.Router();
const paymentController = require('../controllers/paymentController');
const authenticateToken = require('../middleware/auth');
const checkBillingStatus = require('../middleware/billing');

// Apply middleware individually to prevent leakage to other /api/v1 routes
router.post('/payment-sms-ingest', authenticateToken, checkBillingStatus, paymentController.paymentSmsIngest);
router.get('/sms-history', authenticateToken, checkBillingStatus, paymentController.getSmsHistory);
router.get('/dashboard/stats', authenticateToken, paymentController.getDashboardStats);
router.post('/sms-history/:id/soldout', authenticateToken, checkBillingStatus, paymentController.markTransactionSoldOut);

module.exports = router;
