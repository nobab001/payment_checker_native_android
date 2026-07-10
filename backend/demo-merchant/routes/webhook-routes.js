const express = require('express');
const webhookService = require('../services/webhook-service');

const router = express.Router();

router.post('/paychek', async (req, res) => {
  try {
    const signature = req.headers['x-paychek-signature'];
    const rawBody = req.rawBody || Buffer.from(JSON.stringify(req.body || {}));

    const result = await webhookService.handlePaychekWebhook(rawBody, signature);
    return res.status(200).json({ success: true, ...result });
  } catch (err) {
    if (err.code === 'INVALID_SIGNATURE') {
      return res.status(401).json({ success: false, error: err.code });
    }
    if (err.code === 'INVALID_PAYLOAD') {
      return res.status(400).json({ success: false, error: err.code, message: err.message });
    }
    console.error('[DemoMerchant] webhook error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

module.exports = router;
