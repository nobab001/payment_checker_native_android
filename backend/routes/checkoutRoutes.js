const express = require('express');
const router = express.Router();
const checkoutController = require('../controllers/checkoutController');

// Public checkout endpoints (accessible by customers/shoppers without credentials)
router.get('/checkout/:apiKey', checkoutController.getCheckoutLayout);
router.post('/checkout/verify', checkoutController.verifyCheckoutPayment);

module.exports = router;
