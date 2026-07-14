const path = require('path');
const express = require('express');
const config = require('./config');
const testRoutes = require('./routes/test-routes');
const webhookRoutes = require('./routes/webhook-routes');

/**
 * Mount webhook BEFORE express.json() for HMAC raw body.
 */
function mountEarly(app) {
  app.use(
    '/api/official/test/webhook',
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
  app.use('/api/official/test', testRoutes);

  const publicDir = path.join(__dirname, '..', 'public');

  app.get('/test', (_req, res) => {
    res.sendFile(path.join(publicDir, 'test', 'index.html'));
  });

  app.get(['/features', '/solutions', '/pricing', '/documentation', '/resources', '/contact'], (req, res) => {
    // Marketing sections live on the homepage as anchors
    const map = {
      '/features': '/#features',
      '/solutions': '/#solutions',
      '/pricing': '/#pricing',
      '/documentation': '/#documentation',
      '/resources': '/#resources',
      '/contact': '/#contact',
    };
    res.redirect(302, map[req.path] || '/');
  });

  console.log('[OfficialWebsite] Test Experience at /test');
  if (!config.isConfigured()) {
    console.warn('[OfficialWebsite] ⚠  OFFICIAL_TEST_PAYCHEK_API_KEY/SECRET not set — Test pay disabled.');
  }
}

module.exports = { mountEarly, mount };
