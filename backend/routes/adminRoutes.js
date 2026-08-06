const express = require('express');
const router = express.Router();
const prisma = require('../db/prisma');
const admin = require('../controllers/adminController');
const authenticateToken = require('../middleware/auth');
const billingController = require('../controllers/billingController');

// Website creation route for standard users (uses standard JWT authentication)
router.post('/sites/add', authenticateToken, admin.addSite);

// Force verifyAdmin middleware on all subroutes
router.use(admin.verifyAdmin);

// Admin Billing configurations
router.post('/users/:id/manual-grace', admin.manualGrace);
router.post('/users/:id/extend-subscription', admin.extendSubscription);
router.post('/plans', billingController.createPlan);
router.get('/plans', billingController.listPlans);
router.get('/subscription-plans', billingController.listPlans);
router.post('/plans/:id/archive', require('../controllers/subscriptionV3Controller').adminArchivePackage);
router.get('/subscription/refunds/pending', require('../controllers/subscriptionV3Controller').adminListPendingRefunds);
router.post('/subscription/refunds/:id/resolve', require('../controllers/subscriptionV3Controller').adminResolveRefund);
router.get('/subscription/v3/settings', require('../controllers/subscriptionV3Controller').adminGetSettings);
router.post('/subscription/v3/settings', require('../controllers/subscriptionV3Controller').adminUpdateSettings);
router.get('/subscription/v3/addon-catalog', require('../controllers/subscriptionV3Controller').adminListAddonCatalog);
router.put('/subscription/v3/addon-catalog/:addonKey', require('../controllers/subscriptionV3Controller').adminUpdateAddonCatalog);

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

router.get('/global-blocked-senders', admin.getGlobalBlockedSendersAdmin);
router.put('/global-blocked-senders', admin.saveGlobalBlockedSendersAdmin);

// 3. Checkout View Templates
router.get('/checkout-templates', admin.getCheckoutTemplates);
router.post('/checkout-templates', admin.saveCheckoutTemplate);
router.delete('/checkout-templates/:id', admin.deleteCheckoutTemplate);

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
router.post('/official-website/publish', admin.publishOfficialWebsiteCms);
router.post('/official-website/rollback', admin.rollbackOfficialWebsiteCms);

// 5g. Official Test sandbox payments (refund history)
router.get('/demo-payments', admin.listDemoPayments);
router.patch('/demo-payments/:id/refund', admin.updateDemoPaymentRefund);

// 6. User and Device management list and toggle endpoints
router.get('/users', admin.listUsers);
router.post('/users/:id/block', admin.toggleUserBlock);
router.post('/devices/:id/trial', admin.updateDeviceTrial);

// 7. App notifications — admin authored, delivered over the device heartbeat
const notify = require('../controllers/notificationController');
router.get('/notifications', notify.adminListNotifications);
router.post('/notifications', notify.adminCreateNotification);
router.delete('/notifications/:id', notify.adminDeleteNotification);

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

// ==========================================
// OFFICIAL WEBSITE REVIEWS MODERATION CRUD
// ==========================================
router.get('/official/reviews', async (req, res) => {
  try {
    const { status, limit = 100, offset = 0 } = req.query;
    const where = {};
    if (status && status !== 'all') {
      where.status = status;
    }
    const total = await prisma.official_reviews.count({ where });
    const reviews = await prisma.official_reviews.findMany({
      where,
      orderBy: { created_at: 'desc' },
      take: parseInt(limit, 10),
      skip: parseInt(offset, 10),
    });
    return res.json({ success: true, total, reviews });
  } catch (err) {
    console.error('Admin get official reviews error:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

router.put('/official/reviews/:id', express.json(), async (req, res) => {
  try {
    const { status, admin_reply, rating, comment, helpful_count } = req.body;
    const updateData = { updated_at: new Date() };
    if (status) updateData.status = status;
    if (admin_reply !== undefined) updateData.admin_reply = admin_reply;
    if (rating !== undefined) updateData.rating = parseInt(rating, 10);
    if (comment !== undefined) updateData.comment = comment;
    if (helpful_count !== undefined) updateData.helpful_count = parseInt(helpful_count, 10);

    const review = await prisma.official_reviews.update({
      where: { id: parseInt(req.params.id, 10) },
      data: updateData
    });
    return res.json({ success: true, review });
  } catch (err) {
    console.error('Admin update review error:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

router.delete('/official/reviews/:id', async (req, res) => {
  try {
    await prisma.official_reviews.delete({
      where: { id: parseInt(req.params.id, 10) }
    });
    return res.json({ success: true });
  } catch (err) {
    console.error('Admin delete review error:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

// ==========================================
// OFFICIAL WEBSITE TRUSTED COMPANIES CRUD
// ==========================================
router.get('/official/companies', async (req, res) => {
  try {
    const companies = await prisma.official_companies.findMany({
      orderBy: { priority: 'asc' }
    });
    return res.json({ success: true, companies });
  } catch (err) {
    console.error('Admin get companies error:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

router.post('/official/companies', express.json(), async (req, res) => {
  try {
    const { name, logo_url, website_url, industry, country, merchant_since, is_verified, priority } = req.body;
    if (!name || !logo_url) {
      return res.status(400).json({ success: false, error: 'Name and logo_url are required.' });
    }
    const company = await prisma.official_companies.create({
      data: {
        name,
        logo_url,
        website_url: website_url || null,
        industry: industry || null,
        country: country || null,
        merchant_since: merchant_since || null,
        is_verified: is_verified !== false && is_verified !== 0 ? 1 : 0,
        priority: parseInt(priority, 10) || 0
      }
    });
    return res.json({ success: true, company });
  } catch (err) {
    console.error('Admin create company error:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

router.put('/official/companies/:id', express.json(), async (req, res) => {
  try {
    const { name, logo_url, website_url, industry, country, merchant_since, is_verified, priority } = req.body;
    const updateData = {};
    if (name !== undefined) updateData.name = name;
    if (logo_url !== undefined) updateData.logo_url = logo_url;
    if (website_url !== undefined) updateData.website_url = website_url;
    if (industry !== undefined) updateData.industry = industry;
    if (country !== undefined) updateData.country = country;
    if (merchant_since !== undefined) updateData.merchant_since = merchant_since;
    if (is_verified !== undefined) updateData.is_verified = is_verified ? 1 : 0;
    if (priority !== undefined) updateData.priority = parseInt(priority, 10) || 0;

    const company = await prisma.official_companies.update({
      where: { id: parseInt(req.params.id, 10) },
      data: updateData
    });
    return res.json({ success: true, company });
  } catch (err) {
    console.error('Admin update company error:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

router.delete('/official/companies/:id', async (req, res) => {
  try {
    await prisma.official_companies.delete({
      where: { id: parseInt(req.params.id, 10) }
    });
    return res.json({ success: true });
  } catch (err) {
    console.error('Admin delete company error:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

module.exports = router;
