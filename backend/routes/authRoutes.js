const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const adminController = require('../controllers/adminController');
const authenticateToken = require('../middleware/auth');
const checkBillingStatus = require('../middleware/billing');
const { attachBillingStatus } = require('../middleware/billing');
const { otpRateLimiter } = require('../middleware/rateLimiter');

// Public Auth Endpoints
router.get('/config/public', authController.getPublicConfig);
router.post('/check-contact', authController.checkContact);
router.post('/send-otp', otpRateLimiter, authController.sendOtp);
router.post('/send-otp-new', otpRateLimiter, authController.sendOtpNew);
router.post('/auth/register-send-otp', otpRateLimiter, authController.registerSendOtp);
router.post('/verify-otp', otpRateLimiter, authController.verifyOtp);
router.post('/check-device-trial', authController.checkDeviceTrial);
router.post('/check-device-login', authController.checkDeviceTrial);

// Aliases/Routes for site/device registration requested
router.post('/sites/add', authenticateToken, adminController.addSite);
router.post('/devices/register', authController.verifyOtp);

// Protected Auth Endpoints
router.post('/complete-profile', authenticateToken, authController.completeProfile);

// Parent-Child Control Hub Endpoints
// Reads stay open while suspended so the app can render its upsell state;
// device mutations are hard-gated (402) — they provision billable monitoring.
router.get('/v1/devices', authenticateToken, attachBillingStatus, authController.getChildDevices);
// Staff and owner callers share post-login device-management access.
// Controllers still scope by JWT userId + target deviceId (and account PIN where required).
router.post('/v1/devices/remote-update', authenticateToken, checkBillingStatus, authController.remoteUpdateDevice);
router.get('/v1/devices/my-config', authenticateToken, attachBillingStatus, authController.getMyDeviceConfig);

// Cross-Device Multi-Approval & RBAC Endpoints
router.get('/v1/devices/pending-approvals', authenticateToken, attachBillingStatus, authController.getPendingApprovals);
router.post('/v1/devices/approve-by-pin', authenticateToken, checkBillingStatus, authController.approveByPin);
router.post('/v1/devices/submit-role', authenticateToken, checkBillingStatus, authController.submitRole);
router.get('/v1/devices/check-approval-status', authenticateToken, attachBillingStatus, authController.checkApprovalStatus);
router.post('/v1/devices/mark-setup-completed', authenticateToken, checkBillingStatus, authController.markSetupCompleted);
router.post('/v1/devices/toggle-remote-role', authenticateToken, checkBillingStatus, authController.toggleRemoteRole);
// Device removal stays available while suspended — users must be able to clean up
// their own fleet without paying first.
router.post('/v1/devices/delete', authenticateToken, attachBillingStatus, authController.deleteDevice);

// Profile Endpoints — always reachable; the profile page is where the upsell lives.
router.get('/v1/profile', authenticateToken, attachBillingStatus, authController.getProfile);
router.post('/v1/profile/upload-avatar', authenticateToken, attachBillingStatus, authController.uploadAvatar);

module.exports = router;
