const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const adminController = require('../controllers/adminController');
const authenticateToken = require('../middleware/auth');

// Public Auth Endpoints
router.get('/config/public', authController.getPublicConfig);
router.post('/check-contact', authController.checkContact);
router.post('/send-otp', authController.sendOtp);
router.post('/send-otp-new', authController.sendOtpNew);
router.post('/auth/register-send-otp', authController.registerSendOtp);
router.post('/verify-otp', authController.verifyOtp);
router.post('/check-device-trial', authController.checkDeviceTrial);
router.post('/check-device-login', authController.checkDeviceTrial);

// Aliases/Routes for site/device registration requested
router.post('/sites/add', authenticateToken, adminController.addSite);
router.post('/devices/register', authController.verifyOtp);

// Protected Auth Endpoints
router.post('/complete-profile', authenticateToken, authController.completeProfile);

// Parent-Child Control Hub Endpoints
router.get('/v1/devices', authenticateToken, authController.getChildDevices);
router.post('/v1/devices/remote-update', authenticateToken, authController.remoteUpdateDevice);
router.get('/v1/devices/my-config', authenticateToken, authController.getMyDeviceConfig);

// Cross-Device Multi-Approval & RBAC Endpoints
router.get('/v1/devices/pending-approvals', authenticateToken, authController.getPendingApprovals);
router.post('/v1/devices/approve-by-pin', authenticateToken, authController.approveByPin);
router.post('/v1/devices/submit-role', authenticateToken, authController.submitRole);
router.get('/v1/devices/check-approval-status', authenticateToken, authController.checkApprovalStatus);
router.post('/v1/devices/toggle-remote-role', authenticateToken, authController.toggleRemoteRole);

// Profile Endpoints
router.get('/v1/profile', authenticateToken, authController.getProfile);
router.post('/v1/profile/upload-avatar', authenticateToken, authController.uploadAvatar);

module.exports = router;
