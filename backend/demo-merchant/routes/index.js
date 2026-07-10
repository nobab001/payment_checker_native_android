const express = require('express');
const authRoutes = require('./auth-routes');
const dashboardRoutes = require('./dashboard-routes');
const walletRoutes = require('./wallet-routes');
const productRoutes = require('./product-routes');
const orderRoutes = require('./order-routes');
const transactionRoutes = require('./transaction-routes');
const paymentRoutes = require('./payment-routes');
const adminRoutes = require('./admin-routes');

const router = express.Router();

router.get('/health', (_req, res) => {
  res.json({ success: true, service: 'demo-merchant', version: '1.0.0' });
});

router.use('/auth', authRoutes);
router.use('/dashboard', dashboardRoutes);
router.use('/wallet', walletRoutes);
router.use('/products', productRoutes);
router.use('/orders', orderRoutes);
router.use('/transactions', transactionRoutes);
router.use('/payments', paymentRoutes);
router.use('/admin', adminRoutes);

module.exports = router;
