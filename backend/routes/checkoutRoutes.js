const express = require('express');
const router = express.Router();
const checkoutController = require('../controllers/checkoutController');
const { otpRateLimiter } = require('../middleware/rateLimiter');

// Public checkout endpoints (accessible by customers/shoppers without credentials)
router.get('/checkout/:apiKey', checkoutController.getCheckoutLayout);
router.post('/checkout/verify', otpRateLimiter, checkoutController.verifyCheckoutPayment);
router.post('/transactions/claim-check', otpRateLimiter, checkoutController.claimCheckTransaction);

// Merchant Vibe Mode (public — customer-facing)
router.post('/checkout/:apiKey/vibe-init', checkoutController.vibeInit);
router.get('/checkout/vibe-status/:id', checkoutController.vibeStatus);
router.post('/checkout/:apiKey/live-init', checkoutController.liveInit);

module.exports = router;
