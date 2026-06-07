const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const authenticateToken = require('../middleware/auth');

// Public Auth Endpoints
router.post('/check-contact', authController.checkContact);
router.post('/send-otp', authController.sendOtp);
router.post('/send-otp-new', authController.sendOtpNew);
router.post('/auth/register-send-otp', authController.registerSendOtp);
router.post('/verify-otp', authController.verifyOtp);
router.post('/check-device-trial', authController.checkDeviceTrial);

// Protected Auth Endpoints
router.post('/complete-profile', authenticateToken, authController.completeProfile);

module.exports = router;
