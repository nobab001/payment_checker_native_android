const express = require('express');
const walletService = require('../services/wallet-service');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

router.get('/balance', requireAuth, async (req, res) => {
  try {
    const balance = await walletService.getBalance(req.demoUser.id);
    return res.json({ success: true, balance });
  } catch (err) {
    console.error('[DemoMerchant] wallet balance error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.get('/history', requireAuth, async (req, res) => {
  try {
    const limit = parseInt(req.query.limit, 10) || 50;
    const history = await walletService.getLedger(req.demoUser.id, { limit });
    return res.json({ success: true, history });
  } catch (err) {
    console.error('[DemoMerchant] wallet history error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

module.exports = router;
