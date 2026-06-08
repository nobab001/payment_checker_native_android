const express = require('express');
const router  = express.Router();
const pin     = require('../controllers/pinController');
const auth    = require('../middleware/auth');

// POST /api/pin/change           → পুরানো PIN দিয়ে নতুন PIN সেট করো (auth required)
router.post('/pin/change',          auth, pin.changePin);

// POST /api/pin/reset-send-otp   → ভুলে যাওয়া PIN রিসেটের জন্য OTP পাঠাও (no auth)
router.post('/pin/reset-send-otp',  pin.resetPinSendOtp);

// POST /api/pin/reset-verify     → OTP যাচাই করে নতুন PIN সেট করো (no auth)
router.post('/pin/reset-verify',    pin.resetPinVerify);

module.exports = router;
