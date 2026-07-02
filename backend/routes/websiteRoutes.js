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

const authenticateToken = auth.authenticateToken || auth;

router.use(authenticateToken);

// Website CRUD
router.post('/', websiteController.createWebsite);
router.get('/', websiteController.listWebsites);
router.get('/:id', websiteController.getWebsite);
router.patch('/:id', websiteController.updateWebsiteSettings);
router.delete('/:id', websiteController.deleteWebsite);

// Secret rotation (returned once)
router.post('/:id/regenerate-secret', websiteController.regenerateSecret);

// Checkout-only number ordering / enable-disable
router.put('/:id/number-order', websiteController.updateNumberOrder);

// Commission management (locked until admin permission)
router.get('/:id/commissions', websiteController.listCommissions);
router.post('/:id/commissions', websiteController.upsertCommission);
router.delete('/:id/commissions/:commissionId', websiteController.deleteCommission);

// Official payment gateway configuration (Phase 6 — redirect-based channels)
router.get('/:id/official-gateways', websiteController.listOfficialGateways);
router.post('/:id/official-gateways', websiteController.upsertOfficialGateway);
router.delete('/:id/official-gateways/:gatewayId', websiteController.deleteOfficialGateway);

module.exports = router;
