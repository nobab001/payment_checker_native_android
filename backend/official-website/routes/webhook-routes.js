const express = require('express');
const webhookService = require('../services/webhook-service');

const router = express.Router();

router.post('/paychek', async (req, res) => {
  try {
    const signature = req.headers['x-paychek-signature'];
    const rawBody = Buffer.isBuffer(req.rawBody)
      ? req.rawBody.toString('utf8')
      : typeof req.rawBody === 'string'
        ? req.rawBody
        : JSON.stringify(req.body || {});
    const result = await webhookService.handlePaychekWebhook(rawBody, signature);
    return res.json(result);
  } catch (err) {
    if (err.code === 'INVALID_SIGNATURE') {
      return res.status(401).json({ success: false, error: 'INVALID_SIGNATURE' });
    }
    console.error('[OfficialTestWebhook]', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

module.exports = router;
