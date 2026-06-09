const express = require('express');
const router  = express.Router();
const paymentController = require('../controllers/paymentController');
const authenticateToken = require('../middleware/auth');
const checkBillingStatus = require('../middleware/billing');

// Require authentication and valid billing status for all payment/dashboard actions
router.use(authenticateToken, checkBillingStatus);

// ─────────────────────────────────────────────────────────────────────────────
// POST /api/payment-sms-ingest
// Android SMS Monitor → পার্স করা পেমেন্ট ডেটা সংরক্ষণ করে
// INSERT IGNORE দিয়ে ডুপ্লিকেট ব্লক করা হয়, is_used স্ট্যাটাস রিসেট হয় না
// ─────────────────────────────────────────────────────────────────────────────
router.post('/payment-sms-ingest', paymentController.paymentSmsIngest);

// ─────────────────────────────────────────────────────────────────────────────
// GET /api/sms-history
// পেজিনেটেড ট্রানজেকশন লিস্ট
// Query params: ?page=1&limit=20&provider=bKash
// ─────────────────────────────────────────────────────────────────────────────
router.get('/sms-history', paymentController.getSmsHistory);

// ─────────────────────────────────────────────────────────────────────────────
// GET /api/dashboard/stats
// Dashboard statistics — মোট আয়, আজকের আয়, SOLDOUT/UNUSED গণনা
// ─────────────────────────────────────────────────────────────────────────────
router.get('/dashboard/stats', paymentController.getDashboardStats);

module.exports = router;
