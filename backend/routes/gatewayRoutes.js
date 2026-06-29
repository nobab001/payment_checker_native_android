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
router.post('/gateway/methods',              auth, billing, gw.addGatewayMethod);

// PATCH /api/gateway/priority          → Drag & Drop পর priority ক্রম সেভ
router.patch('/gateway/priority',            auth, billing, gw.updatePriority);

// PATCH /api/gateway/methods/:id/toggle → চালু/বন্ধ করা
router.patch('/gateway/methods/:id/toggle',  auth, billing, gw.toggleMethod);

// PATCH /api/gateway/methods/:id        → নম্বর ও নাম আপডেট
router.patch('/gateway/methods/:id',         auth, billing, gw.updateMethod);

// POST /api/gateway/methods/:id/custom-templates → নতুন কাস্টম এসএমএস টেমপ্লেট
router.post('/gateway/methods/:id/custom-templates', auth, billing, gw.addCustomTemplate);

// GET /api/gateway/custom-sender/suggestions?q=gp
router.get('/gateway/custom-sender/suggestions', auth, billing, gw.getCustomSenderSuggestions);

// POST /api/gateway/custom-sender → নতুন কাস্টম প্লাস-আইকন সেন্ডার আইডি তৈরি
router.post('/gateway/custom-sender', auth, billing, gw.addCustomSender);

// DELETE /api/gateway/methods/:id → কাস্টম সেন্ডার আইডি ডিলিট করা
router.delete('/gateway/methods/:id', auth, billing, gw.deleteGatewayMethod);

// POST /api/gateway/sim-swap → সিম সোয়াপ / conflict lookup (legacy alias)
router.post('/gateway/sim-swap', auth, billing, gw.syncAndValidateSimSwap);

// POST /api/gateway/slot/lookup → নম্বর ইনপুটে conflict + cached profile
router.post('/gateway/slot/lookup', auth, billing, gw.lookupSlotNumber);

// POST /api/gateway/slot/force-shift → conflict approve — shift SIM to this device
router.post('/gateway/slot/force-shift', auth, billing, gw.forceShiftSlot);

// POST /api/gateway/slot/active → manual SIM toggle is_active state
router.post('/gateway/slot/active', auth, billing, gw.setSlotActive);

// POST /api/gateway/methods/bulk-sync → slot methods batch sync on SIM enable
router.post('/gateway/methods/bulk-sync', auth, billing, gw.bulkSyncSlotMethods);

module.exports = router;
