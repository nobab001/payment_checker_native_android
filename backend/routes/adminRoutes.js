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
router.post('/plans/reorder', billingController.reorderSubscriptionPlans);

router.get('/addon-plans', billingController.listAddonPlansAdmin);
router.post('/addon-plans', billingController.saveAddonPlan);
router.delete('/addon-plans/:id', billingController.deleteAddonPlan);
router.post('/addon-plans/reorder', billingController.reorderAddonPlans);
router.post('/billing-tab-order', billingController.saveBillingTabOrder);

// 1. App Configs (global_config)
router.get('/config', admin.getConfigs);
router.post('/config', admin.updateConfig);

// 2. Official SMS Templates
router.get('/sms-templates', admin.getSmsTemplates);
router.post('/sms-templates', admin.saveSmsTemplate);
router.post('/sms-templates/reorder', admin.reorderSmsTemplates);
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

// 5d. Website merchant permission control (payment-type / commission callbacks + purpose unlock)
router.get('/websites', admin.listAllWebsites);
router.post('/websites/:id/permissions', admin.setWebsitePermissions);

// 5d2. Global purpose help content (merchant ⓘ popups)
router.get('/purpose-help', admin.getPurposeHelp);
router.put('/purpose-help', admin.savePurposeHelp);
router.delete('/purpose-help/:key', admin.deletePurposeHelpKey);

// 5e. Global checkout design (tabs, icons, provider branding — all merchants)
router.get('/checkout-design', admin.getCheckoutDesignConfig);
router.post('/checkout-design', admin.saveCheckoutDesignConfig);
// Direct image upload for provider logos & checkout tab icons (base64 -> optimized file)
router.post('/upload-image', admin.uploadCheckoutImage);

// 5f. Official marketing website CMS (tabs + helpline)
router.get('/official-website', admin.getOfficialWebsiteCms);
router.put('/official-website', admin.saveOfficialWebsiteCms);

// 6. User and Device management list and toggle endpoints
router.get('/users', admin.listUsers);
router.post('/users/:id/block', admin.toggleUserBlock);
router.post('/devices/:id/trial', admin.updateDeviceTrial);

// Presence v2.5 metrics (Phase 3)
router.get('/presence-v25/metrics', async (req, res) => {
  try {
    const presenceV25 = require('../services/presenceV25');
    const snap = presenceV25.getPresenceMetrics();
    const shadow = await presenceV25.isShadowModeEnabled();
    const globalOn = await presenceV25.loadGlobalV2Enabled();
    const policies = await presenceV25.refreshAllPolicies();
    return res.json({
      success: true,
      dryRun: presenceV25.DRY_RUN,
      shadowMode: shadow,
      globalV2Enabled: globalOn,
      metrics: snap,
      policies: Object.fromEntries(
        Object.entries(policies).map(([k, p]) => [k, {
          heartbeat_interval_sec: p.heartbeat_interval_sec,
          presence_engine_version: p.presence_engine_version,
          offline_deadline_sec: p.offline_deadline_sec,
        }])
      ),
    });
  } catch (e) {
    return res.status(500).json({ success: false, error: e.message });
  }
});

module.exports = router;
