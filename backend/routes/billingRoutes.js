const express = require('express');
const router = express.Router();
const billingController = require('../controllers/billingController');
const authenticateToken = require('../middleware/auth');

// Public route to view plans
router.get('/plans', billingController.listPlans);

// All subscription operations require JWT token validation
router.use(authenticateToken);

// POST /api/v1/subscription/fcm-token → Register FCM token
router.post('/subscription/fcm-token', billingController.updateFcmToken);

// POST /api/v1/subscription/purchase → Purchase subscription
router.post('/subscription/purchase', billingController.purchaseSubscription);

// POST /api/v1/subscription/purchase-addon → Purchase Custom Sender add-on
router.post('/subscription/purchase-addon', billingController.purchaseCustomSenderAddon);

module.exports = router;
