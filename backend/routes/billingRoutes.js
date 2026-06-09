const express = require('express');
const router = express.Router();
const billingController = require('../controllers/billingController');
const authenticateToken = require('../middleware/auth');

// All subscription operations require JWT token validation
router.use(authenticateToken);

// POST /api/v1/subscription/recharge → Wallet recharge
router.post('/subscription/recharge', billingController.recharge);

// POST /api/v1/subscription/fcm-token → Register FCM token
router.post('/subscription/fcm-token', billingController.updateFcmToken);

module.exports = router;
