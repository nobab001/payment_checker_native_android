const express = require('express');
const { getPaymentMetrics } = require('../controllers/paymentMetricsController');
const { getPaymentHealth } = require('../controllers/paymentHealthController');
const { authorizePaymentMetrics, metricsRateLimiter } = require('../middleware/paymentMetricsAuth');

const router = express.Router();
router.get('/health', getPaymentHealth);
router.get('/metrics', metricsRateLimiter, authorizePaymentMetrics, getPaymentMetrics);

module.exports = router;
