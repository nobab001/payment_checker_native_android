/**
 * websiteRoutes.js — API Integration v2 merchant/website management.
 * Mounted at /api/v1/websites. All routes require JWT auth.
 *
 * These endpoints operate on gateway_layouts (shared with the legacy checkout
 * flow) but never alter existing checkout/verify/claim-check behaviour.
 */
const express = require('express');
const router = express.Router();
const auth = require('../middleware/auth');
const websiteController = require('../controllers/websiteController');
const { logoUpload } = require('../middleware/websiteUploadMiddleware');

const authenticateToken = auth.authenticateToken || auth;

router.use(authenticateToken);

// Global checkout (must be before /:id)
router.get('/global-checkout', websiteController.getGlobalCheckout);
router.put('/global-checkout', websiteController.saveGlobalCheckout);

// Website CRUD
router.post('/', websiteController.createWebsite);
router.get('/', websiteController.listWebsites);
router.get('/:id', websiteController.getWebsite);
router.patch('/:id', websiteController.updateWebsiteSettings);
router.delete('/:id', websiteController.deleteWebsite);

// Secret rotation (returned once)
router.post('/:id/regenerate-secret', websiteController.regenerateSecret);

// Merchant branding logo (multipart upload — replaces legacy logo URL field)
router.post('/:id/branding/logo', logoUpload.single('logo'), websiteController.uploadWebsiteLogo);
router.delete('/:id/branding/logo', websiteController.deleteWebsiteLogo);

// Checkout-only number ordering / enable-disable
router.put('/:id/number-order', websiteController.updateNumberOrder);

// Commission management (locked until admin permission)
router.get('/:id/commissions', websiteController.listCommissions);
router.post('/:id/commissions', websiteController.upsertCommission);
router.delete('/:id/commissions/:commissionId', websiteController.deleteCommission);

// Campaign / Extra incentives (amount-range commission or charge; locked like commissions)
router.get('/:id/campaigns', websiteController.listCampaigns);
router.post('/:id/campaigns', websiteController.upsertCampaign);
router.delete('/:id/campaigns/:campaignId', websiteController.deleteCampaign);

// Live merchant accounts (multiple accounts per provider — credential vault)
router.get('/:id/merchant-accounts', websiteController.listMerchantAccounts);
router.post('/:id/merchant-accounts', websiteController.createMerchantAccount);
router.patch('/:id/merchant-accounts/:accountId', websiteController.updateMerchantAccount);
router.delete('/:id/merchant-accounts/:accountId', websiteController.deleteMerchantAccount);
router.post('/:id/merchant-accounts/:accountId/toggle', websiteController.toggleMerchantAccount);
router.post('/:id/merchant-accounts/:accountId/default', websiteController.setDefaultMerchantAccount);
router.post('/:id/merchant-accounts/:accountId/duplicate', websiteController.duplicateMerchantAccount);
router.post('/:id/merchant-accounts/:accountId/logo', logoUpload.single('logo'), websiteController.uploadMerchantAccountLogo);

module.exports = router;
