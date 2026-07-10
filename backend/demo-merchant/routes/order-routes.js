const express = require('express');
const orderService = require('../services/order-service');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

router.get('/', requireAuth, async (req, res) => {
  try {
    const status = req.query.status || null;
    const limit = parseInt(req.query.limit, 10) || 50;
    const orders = await orderService.listOrders(req.demoUser.id, { status, limit });
    return res.json({ success: true, orders });
  } catch (err) {
    console.error('[DemoMerchant] orders error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.get('/:orderNumber', requireAuth, async (req, res) => {
  try {
    const order = await orderService.getOrderByNumber(req.params.orderNumber, req.demoUser.id);
    if (!order) {
      return res.status(404).json({ success: false, error: 'NOT_FOUND' });
    }
    return res.json({ success: true, order: orderService.formatOrder(order) });
  } catch (err) {
    console.error('[DemoMerchant] order detail error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

module.exports = router;
