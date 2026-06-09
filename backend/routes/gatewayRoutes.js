const express    = require('express');
const router     = express.Router();
const gw         = require('../controllers/gatewayController');
const auth       = require('../middleware/auth');
const billing    = require('../middleware/billing');

// Require authentication and valid billing status for all gateway operations
router.use(auth, billing);

// GET  /api/gateway/methods           → সব মেথড লোড (priority অনুযায়ী)
router.get('/gateway/methods',               gw.getGatewayMethods);

// PATCH /api/gateway/priority          → Drag & Drop পর priority ক্রম সেভ
router.patch('/gateway/priority',            gw.updatePriority);

// PATCH /api/gateway/methods/:id/toggle → চালু/বন্ধ করা
router.patch('/gateway/methods/:id/toggle',  gw.toggleMethod);

// PATCH /api/gateway/methods/:id        → নম্বর ও নাম আপডেট
router.patch('/gateway/methods/:id',         gw.updateMethod);

module.exports = router;
