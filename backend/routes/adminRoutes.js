const express = require('express');
const router = express.Router();
const admin = require('../controllers/adminController');

// Force verifyAdmin middleware on all subroutes
router.use(admin.verifyAdmin);

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

// 6. User and Device management list and toggle endpoints
router.get('/users', admin.listUsers);
router.post('/users/:id/block', admin.toggleUserBlock);
router.post('/devices/:id/trial', admin.updateDeviceTrial);

module.exports = router;
