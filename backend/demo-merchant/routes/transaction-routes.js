const express = require('express');
const transactionService = require('../services/transaction-service');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

router.get('/', requireAuth, async (req, res) => {
  try {
    const txnType = req.query.type || null;
    const limit = parseInt(req.query.limit, 10) || 50;
    const transactions = await transactionService.listTransactions(req.demoUser.id, {
      txnType,
      limit,
    });
    return res.json({ success: true, transactions });
  } catch (err) {
    console.error('[DemoMerchant] transactions error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

module.exports = router;
