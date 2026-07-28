const express = require('express');
const router = express.Router();
const billingController = require('../controllers/billingController');
const authenticateToken = require('../middleware/auth');

// Public route to view plans
router.get('/plans', billingController.listPlans);
router.get('/addon-plans', billingController.listAddonPlans);

// PayChek merchant webhook (HMAC). Prefer early raw-body mount in app.js;
// this path remains as fallback when body was already parsed.
router.post('/subscription/payment-webhook', billingController.subscriptionPaymentWebhook);

// All subscription operations require JWT token validation
router.use(authenticateToken);

// POST /api/v1/subscription/fcm-token → Register FCM token
router.post('/subscription/fcm-token', billingController.updateFcmToken);

router.get('/billing/catalog', require('../controllers/subscriptionV3Controller').getCatalog);
router.post('/subscription/v3/quote', require('../controllers/subscriptionV3Controller').postQuote);
router.post('/subscription/v3/checkout-init', require('../controllers/subscriptionV3Controller').postCheckoutInit);
router.get('/subscription/history', require('../controllers/subscriptionV3Controller').getHistory);
router.post('/subscription/refund-request', require('../controllers/subscriptionV3Controller').postRefundRequest);

router.get('/subscription/quote', billingController.getSubscriptionQuote);
router.post('/subscription/checkout-init', billingController.initSubscriptionCheckout);
router.post('/subscription/addon-checkout-init', billingController.initAddonCheckout);
router.get('/subscription/checkout-status', billingController.getSubscriptionCheckoutStatus);

// Legacy free activate (admin/tests) — app Buy uses checkout-init
router.post('/subscription/purchase', billingController.purchaseSubscription);
router.post('/subscription/purchase-addon', billingController.purchaseCustomSenderAddon);

router.get('/account/entitlements', billingController.getAccountEntitlements);

module.exports = router;
