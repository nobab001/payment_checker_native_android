const express = require('express');
const router = express.Router();
const admin = require('../controllers/adminController');
const authenticateToken = require('../middleware/auth');
const billingController = require('../controllers/billingController');

// Website creation route for standard users (uses standard JWT authentication)
router.post('/sites/add', authenticateToken, admin.addSite);

// Force verifyAdmin middleware on all subroutes
router.use(admin.verifyAdmin);

// Admin Billing configurations
router.post('/users/:id/manual-grace', admin.manualGrace);
router.post('/plans', billingController.createPlan);
router.get('/plans', billingController.listPlans);
router.get('/subscription-plans', billingController.listPlans);
router.delete('/plans/:id', billingController.deletePlan);

router.get('/addon-plans', billingController.listAddonPlansAdmin);
router.post('/addon-plans', billingController.saveAddonPlan);
router.delete('/addon-plans/:id', billingController.deleteAddonPlan);

// 1. App Configs (global_config)
router.get('/config', admin.getConfigs);
router.post('/config', admin.updateConfig);

// 2. Official SMS Templates
router.get('/sms-templates', admin.getSmsTemplates);
router.post('/sms-templates', admin.saveSmsTemplate);
router.delete('/sms-templates/:id', admin.deleteSmsTemplate);

// 3. Checkout View Templates
router.get('/checkout-templates', admin.getCheckoutTemplates);
router.post('/checkout-templates', admin.saveCheckoutTemplate);

// 4. SMTP (Email Round-Robin) Profiles
router.get('/email-accounts', admin.getEmailAccounts);
router.post('/email-accounts', admin.saveEmailAccount);
router.delete('/email-accounts/:id', admin.deleteEmailAccount);

// 5. SMS Gateway Configuration settings
router.get('/sms-settings', admin.getSmsSettings);
router.post('/sms-settings', admin.saveSmsSettings);
router.delete('/sms-settings/:id', admin.deleteSmsSettings);   // ← NEW: delete SMS provider

// 5b. Email Account SMTP Counter Management
router.post('/email-accounts/:id/reset', admin.resetEmailCounter);       // ← NEW: reset one counter
router.post('/email-accounts/reset-all', admin.resetAllEmailCounters);   // ← NEW: reset all counters

// 5c. OTP Message Format Manager
router.get('/otp-format', admin.getOtpFormat);
router.post('/otp-format/update', admin.updateOtpFormat);

// 5d. Website merchant permission control (payment-type / commission callbacks)
router.get('/websites', admin.listAllWebsites);
router.post('/websites/:id/permissions', admin.setWebsitePermissions);

// 5e. Global checkout design (tabs, icons, provider branding — all merchants)
router.get('/checkout-design', admin.getCheckoutDesignConfig);
router.post('/checkout-design', admin.saveCheckoutDesignConfig);

// 6. User and Device management list and toggle endpoints
router.get('/users', admin.listUsers);
router.post('/users/:id/block', admin.toggleUserBlock);
router.post('/devices/:id/trial', admin.updateDeviceTrial);

module.exports = router;
