const express = require('express');
const router = express.Router();
const paymentController = require('../controllers/paymentController');
const authenticateToken = require('../middleware/auth');

// Ingest parsed payment telemetry (secured by user session token)
router.post('/payment-sms-ingest', authenticateToken, paymentController.paymentSmsIngest);

module.exports = router;
