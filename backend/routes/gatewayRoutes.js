const express    = require('express');
const router     = express.Router();
const gw         = require('../controllers/gatewayController');
const hb         = require('../controllers/heartbeatController');
const auth       = require('../middleware/auth');
const checkBillingStatus = require('../middleware/billing');
const { attachBillingStatus } = require('../middleware/billing');

// Heartbeat must stay reachable for suspended accounts so the server can return
// { action: "STOP_MONITORING" } and the client shuts down the FGS remotely.
router.post('/gateway/heartbeat',              auth, attachBillingStatus, hb.postHeartbeat);

// GET  /api/gateway/numbers/health?numbers=017..,018..
router.get('/gateway/numbers/health',          auth, checkBillingStatus, hb.getNumbersHealth);

// GET  /api/gateway/methods           → সব মেথড লোড (priority অনুযায়ী)
router.get('/gateway/methods',               auth, checkBillingStatus, gw.getGatewayMethods);

// GET  /api/gateway/templates         → সব অ্যাক্টিভ টেমপ্লেট লোড
router.get('/gateway/templates',             auth, checkBillingStatus, gw.getTemplates);

// POST /api/gateway/methods           → নতুন মেথড যোগ করা
router.post('/gateway/methods',              auth, checkBillingStatus, gw.addGatewayMethod);

// PATCH /api/gateway/priority          → Drag & Drop পর priority ক্রম সেভ
router.patch('/gateway/priority',            auth, checkBillingStatus, gw.updatePriority);

// PATCH /api/gateway/methods/:id/toggle → চালু/বন্ধ করা
router.patch('/gateway/methods/:id/toggle',  auth, checkBillingStatus, gw.toggleMethod);

// PATCH /api/gateway/methods/:id        → নম্বর ও নাম আপডেট
router.patch('/gateway/methods/:id',         auth, checkBillingStatus, gw.updateMethod);

// POST /api/gateway/methods/:id/custom-templates → নতুন কাস্টম এসএমএস টেমপ্লেট
router.post('/gateway/methods/:id/custom-templates', auth, checkBillingStatus, gw.addCustomTemplate);

// GET /api/gateway/custom-sender/suggestions?q=gp
router.get('/gateway/custom-sender/suggestions', auth, checkBillingStatus, gw.getCustomSenderSuggestions);

// POST /api/gateway/custom-sender → নতুন কাস্টম প্লাস-আইকন সেন্ডার আইডি তৈরি
router.post('/gateway/custom-sender', auth, checkBillingStatus, gw.addCustomSender);

// DELETE /api/gateway/methods/:id → কাস্টম সেন্ডার আইডি ডিলিট করা
router.delete('/gateway/methods/:id', auth, checkBillingStatus, gw.deleteGatewayMethod);

// POST /api/gateway/sim-swap → সিম সোয়াপ / conflict lookup (legacy alias)
router.post('/gateway/sim-swap', auth, checkBillingStatus, gw.syncAndValidateSimSwap);

// POST /api/gateway/slot/lookup → নম্বর ইনপুটে conflict + cached profile
router.post('/gateway/slot/lookup', auth, checkBillingStatus, gw.lookupSlotNumber);

// POST /api/gateway/slot/force-shift → conflict approve — shift SIM to this device
router.post('/gateway/slot/force-shift', auth, checkBillingStatus, gw.forceShiftSlot);

// POST /api/gateway/slot/active → manual SIM toggle is_active state
router.post('/gateway/slot/active', auth, checkBillingStatus, gw.setSlotActive);

// POST /api/gateway/methods/bulk-sync → slot methods batch sync on SIM enable
router.post('/gateway/methods/bulk-sync', auth, checkBillingStatus, gw.bulkSyncSlotMethods);

// GET  /api/gateway/account-numbers → all active account SIM numbers
router.get('/gateway/account-numbers', auth, checkBillingStatus, gw.listAccountActiveNumbers);

// DELETE /api/gateway/account-numbers → remove number from entire account
router.delete('/gateway/account-numbers', auth, checkBillingStatus, gw.deleteAccountNumber);

module.exports = router;
