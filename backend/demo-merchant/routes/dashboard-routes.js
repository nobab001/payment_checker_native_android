const express = require('express');
const dashboardService = require('../services/dashboard-service');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

router.get('/', requireAuth, async (req, res) => {
  try {
    const data = await dashboardService.getDashboard(req.demoUser.id);
    return res.json({ success: true, dashboard: data });
  } catch (err) {
    console.error('[DemoMerchant] dashboard error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

module.exports = router;
