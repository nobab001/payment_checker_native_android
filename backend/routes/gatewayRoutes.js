const express    = require('express');
const router     = express.Router();
const gw         = require('../controllers/gatewayController');
const auth       = require('../middleware/auth');
const billing    = require('../middleware/billing');

// GET  /api/gateway/methods           → সব মেথড লোড (priority অনুযায়ী)
router.get('/gateway/methods',               auth, billing, gw.getGatewayMethods);

// GET  /api/gateway/templates         → সব অ্যাক্টিভ টেমপ্লেট লোড
router.get('/gateway/templates',             auth, billing, gw.getTemplates);

// POST /api/gateway/methods           → নতুন মেথড যোগ করা
router.post('/gateway/methods',              auth, auth.restrictDevice, billing, gw.addGatewayMethod);

// PATCH /api/gateway/priority          → Drag & Drop পর priority ক্রম সেভ
router.patch('/gateway/priority',            auth, auth.restrictDevice, billing, gw.updatePriority);

// PATCH /api/gateway/methods/:id/toggle → চালু/বন্ধ করা
router.patch('/gateway/methods/:id/toggle',  auth, auth.restrictDevice, billing, gw.toggleMethod);

// PATCH /api/gateway/methods/:id        → নম্বর ও নাম আপডেট
router.patch('/gateway/methods/:id',         auth, auth.restrictDevice, billing, gw.updateMethod);

module.exports = router;
