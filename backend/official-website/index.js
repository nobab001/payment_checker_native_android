const path = require('path');
const express = require('express');
const config = require('./config');
const testRoutes = require('./routes/test-routes');
const webhookRoutes = require('./routes/webhook-routes');
const siteRoutes = require('./routes/site-routes');

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
  app.use('/api/official', siteRoutes);

  const publicDir = path.join(__dirname, '..', 'public');

  app.get('/test', (_req, res) => {
    res.sendFile(path.join(publicDir, 'test', 'index.html'));
  });

  app.get('/docs', (_req, res) => {
    res.sendFile(path.join(publicDir, 'docs', 'index.html'));
  });
  app.get('/docs/', (_req, res) => {
    res.redirect(302, '/docs');
  });

  app.get(['/features', '/solutions', '/pricing', '/documentation', '/resources', '/contact'], (req, res) => {
    // Marketing sections live on the homepage as anchors — docs is a full page.
    const map = {
      '/features': '/#features',
      '/solutions': '/#solutions',
      '/pricing': '/#pricing',
      '/documentation': '/docs',
      '/resources': '/#resources',
      '/contact': '/#contact',
    };
    res.redirect(302, map[req.path] || '/');
  });

  console.log('[OfficialWebsite] Test Experience at /test');
  console.log('[OfficialWebsite] Developer Docs at /docs');
  console.log('[OfficialWebsite] Public CMS at /api/official/site');
  if (!config.isConfigured()) {
    console.warn('[OfficialWebsite] ⚠  OFFICIAL_TEST_PAYCHEK_API_KEY/SECRET not set — Test pay disabled.');
  }
}

module.exports = { mountEarly, mount };
