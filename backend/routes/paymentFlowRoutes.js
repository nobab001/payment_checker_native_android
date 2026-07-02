const express = require('express');
const router = express.Router();
const paymentFlow = require('../controllers/paymentFlowController');
const apiRateLimiter = require('../middleware/rateLimiter');

// Merchant server-to-server payment session APIs (authenticated via X-API-Key).
router.post('/init', apiRateLimiter, paymentFlow.initPayment);
router.get('/:token/status', paymentFlow.paymentStatus);

module.exports = router;
