const express = require('express');
const ctrl = require('../controllers/test-controller');

const router = express.Router();

router.get('/status', ctrl.getStatus);
router.post('/session', ctrl.createDemoSession);
router.get('/session/:id', ctrl.getDemoSession);
router.patch('/session/:id', ctrl.patchDemoSession);
router.post('/pay', ctrl.startTestPayment);
router.get('/preview', ctrl.getPreviewUrl);

module.exports = router;
