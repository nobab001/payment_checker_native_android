const path = require('path');
const express = require('express');
const apiRoutes = require('./routes');
const webhookRoutes = require('./routes/webhook-routes');
const config = require('./config');

/**
 * Mount webhook route BEFORE express.json() so raw body is available for HMAC verification.
 * Call this from app.js before app.use(express.json(...)).
 */
function mountEarly(app) {
  app.use(
    '/demo-merchant/api/webhooks',
    express.raw({ type: 'application/json' }),
    (req, _res, next) => {
      req.rawBody = req.body;
      try {
        req.body = JSON.parse(req.body.toString('utf8'));
      } catch {
        req.body = {};
      }
      next();
    },
    webhookRoutes,
  );
}

function mount(app) {
  app.use('/demo-merchant/api', apiRoutes);

  app.get('/demo-merchant/payment/success', (_req, res) => {
    res.sendFile(path.join(config.staticDir, 'payment-result.html'));
  });

  app.get('/demo-merchant/payment/cancel', (_req, res) => {
    res.sendFile(path.join(config.staticDir, 'payment-result.html'));
  });

  app.use('/demo-merchant', express.static(config.staticDir, {
    index: 'index.html',
  }));

  console.log('[DemoMerchant] mounted at /demo-merchant');
  if (!config.isConfigured()) {
    console.warn('[DemoMerchant] ⚠  PayCheck API key/secret not set — checkout disabled until configured.');
  }
}

module.exports = { mountEarly, mount };
