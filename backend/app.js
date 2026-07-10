// Force the process timezone to Bangladesh Standard Time
process.env.TZ = 'Asia/Dhaka';

const express = require('express');
const http = require('http');
const path = require('path');
const { Server } = require('socket.io');
const cors = require('cors');
const cron = require('node-cron');
const prisma = require('./db/prisma');
require('dotenv').config();

const authRoutes        = require('./routes/authRoutes');
const paymentRoutes     = require('./routes/paymentRoutes');
const checkoutRoutes    = require('./routes/checkoutRoutes');
const gatewayRoutes     = require('./routes/gatewayRoutes');
const adminRoutes       = require('./routes/adminRoutes');
const credentialRoutes  = require('./routes/credentialRoutes');
const pinRoutes         = require('./routes/pinRoutes');
const billingRoutes     = require('./routes/billingRoutes');
const numberHealth      = require('./services/numberHealthService');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*' }
});

// One live socket per device room — duplicate connects from service restarts are dropped.
const deviceSocketIds = new Map();

// Setup Socket.IO Multi-Device Isolation + number health (ONLINE on connect, GRACE on disconnect)
io.on('connection', (socket) => {
  const userId = socket.handshake.query.userId;
  const deviceId = socket.handshake.query.deviceId;

  if (!userId || !deviceId) {
    console.log('[Socket.IO] Anonymous connection dropped (missing userId or deviceId)');
    socket.disconnect();
    return;
  }

  const roomName = `${userId}:${deviceId}`;

  const previousSocketId = deviceSocketIds.get(roomName);
  if (previousSocketId && previousSocketId !== socket.id) {
    const stale = io.sockets.sockets.get(previousSocketId);
    if (stale) {
      console.log(`[Socket.IO] Replacing stale socket for ${roomName}`);
      stale.disconnect(true);
    }
  }
  deviceSocketIds.set(roomName, socket.id);

  socket.join(roomName);
  console.log(`[Socket.IO] Device connected and locked to room: ${roomName} (socket=${socket.id})`);

  numberHealth.onDeviceSocketConnect(userId, deviceId).catch((err) => {
    console.warn('[Socket.IO] connect health update failed:', err.message);
  });

  socket.on('device_numbers', (payload) => {
    const numbers = payload?.numbers || payload;
    numberHealth.onDeviceSocketConnect(userId, deviceId, numbers).catch((err) => {
      console.warn('[Socket.IO] device_numbers health update failed:', err.message);
    });
  });

  socket.on('disconnect', () => {
    if (deviceSocketIds.get(roomName) === socket.id) {
      deviceSocketIds.delete(roomName);
    }
    console.log(`[Socket.IO] Device disconnected from room: ${roomName} (socket=${socket.id})`);
    numberHealth.scheduleSocketDisconnect(userId, deviceId);
  });
});

// Make io accessible globally via app
app.set('io', io);

const PORT = process.env.PORT || 3000;

// Enable CORS for frontend API requests
app.use(cors());

// Demo Merchant webhook (raw body for PayCheck HMAC) — must mount before express.json
const demoMerchant = require('./demo-merchant');
demoMerchant.mountEarly(app);

// Serve static webpage assets (like public/checkout.html).
// Uploaded logos/icons use content-hashed filenames, so they can be cached
// aggressively & immutably; the service worker + browser reuse them from cache.
app.use(express.static('public', {
  setHeaders: (res, filePath) => {
    const rel = filePath.split(path.sep).join('/');
    if (rel.includes('/uploads/')) {
      res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
    } else if (filePath.endsWith('sw.js')) {
      // The service worker itself must always be revalidated so updates ship.
      res.setHeader('Cache-Control', 'no-cache');
    }
  },
}));

// Parse incoming JSON body payloads (raised limit to allow base64 image uploads)
app.use(express.json({ limit: '12mb' }));

// Helper to get formatted Bangladesh Standard Time (BST) timestamp
const getBdTimestamp = () => {
  const options = { timeZone: 'Asia/Dhaka', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false };
  const formatter = new Intl.DateTimeFormat('en-US', options);
  const [{ value: m }, , { value: d }, , { value: y }, , { value: h }, , { value: min }, , { value: s }] = formatter.formatToParts(new Date());
  return `${y}-${m}-${d} ${h}:${min}:${s}`;
};

// Log incoming REST API requests with Bangladesh Time (BST)
app.use((req, res, next) => {
  console.log(`[${getBdTimestamp()}] ${req.method} ${req.url}`);
  next();
});

// Root check route
app.get('/health', (req, res) => {
  res.json({ status: 'UP', env: process.env.APP_ENV || 'unknown', timestamp: new Date().toISOString() });
});

// Alias for load balancers / staging probes
app.get('/payment/health', (req, res) => {
  res.redirect(307, '/api/v1/payment/health');
});

// Mount Authentication & Device Trial Routes
app.use('/api', authRoutes);
app.use('/api', paymentRoutes);
app.use('/api', checkoutRoutes);
app.use('/api', gatewayRoutes);
app.use('/api', credentialRoutes);
app.use('/api', pinRoutes);
app.use('/api/admin', adminRoutes);
// ── Phase 6: Payment Flow routing ──────────────────────────────────────────
// IMPORTANT: mount the payment-flow API BEFORE billingRoutes. billingRoutes is
// mounted at /api/v1 and applies a router-level JWT guard to every /api/v1/*
// path; the merchant payment APIs authenticate via X-API-Key (not JWT), so they
// must be matched first.
const paymentFlowController = require('./controllers/paymentFlowController');
const callbackRateLimiter = require('./middleware/callbackRateLimiter');
app.use('/api/v1/pay', require('./routes/paymentFlowRoutes'));           // merchant S2S: init + status
app.use('/api/v1/payment', require('./routes/paymentMetricsRoutes'));   // health / metrics
app.get('/pay/:token', paymentFlowController.redirectPayment);
app.post('/api/payment/bkash/callback', callbackRateLimiter, paymentFlowController.bkashCallback);
app.all('/api/pay/:token/gateway-callback', paymentFlowController.officialGatewayCallback);

app.use('/api/v1', billingRoutes);
app.use('/api/v1/websites', require('./routes/websiteRoutes'));

// Demo Merchant application (v1 test harness — consumes PayCheck, does not modify payment/)
demoMerchant.mount(app);

// General 404 Route handler
app.use((req, res) => {
  res.status(404).json({ error: 'Endpoint Not Found' });
});

// Error handling middleware
app.use((err, req, res, next) => {
  console.error('Express Error Handler caught:', err);
  res.status(500).json({ error: 'Internal Server Error' });
});

// Schedule a daily midnight task to reset email limits
cron.schedule('0 0 * * *', async () => {
  try {
    await prisma.smtp_gateways.updateMany({
      data: { sent_today: 0 }
    });
    console.log('[CRON] Reset daily email send limits successfully.');
  } catch (err) {
    console.error('[CRON] Error resetting daily email limits:', err);
  }
});

// Mount Calendar-Based Subscription Expiry Guard & FCM Reminder Scheduler
require('./cron/billingScheduler');

// Phase-3B.5: expire stale payment sessions every 5 minutes
const { runSessionCleanup } = require('./payment/workers/session-cleanup-worker');
cron.schedule('*/5 * * * *', () => {
  runSessionCleanup().catch((err) => console.error('[CRON] session cleanup:', err.message));
});

// Phase-3B.5: merchant callback outbox worker every 15 seconds
const { runOutboxWorker } = require('./payment/workers/outbox-worker');
cron.schedule('*/15 * * * * *', () => {
  runOutboxWorker().catch((err) => console.error('[CRON] outbox worker:', err.message));
});

// Mount BullMQ Background Worker for SMS Processing
require('./workers/smsWorker');

// Start listening for connections
server.listen(PORT, async () => {
  console.log(`=============================================`);
  console.log(` Payment Checker API Server running on port ${PORT}`);
  console.log(` Database Target: ${process.env.DB_HOST}:${process.env.DB_PORT || 3306}`);
  console.log(` Database Name  : ${process.env.DB_NAME}`);
  console.log(`=============================================`);

  try {
    // ─────────────────────────────────────────────────────────────────────────
    // Check if gateway_methods table exists, if not, wait for prisma db push
    // We assume Prisma schema handles all DDL migrations from now on
    // ─────────────────────────────────────────────────────────────────────────
    try {
      await prisma.$queryRaw`DESCRIBE gateway_methods`;
      console.log('[DB] ✅ gateway_methods table verified via Prisma.');
    } catch (err) {
      console.warn('[DB] ⚠  gateway_methods check failed. Make sure to run `npx prisma db push` or `npx prisma migrate deploy`.');
    }

    console.log('[DB] ✅ Database check complete!');

    try {
      const { migrateAllUserEntitlements } = require('./scripts/migrateUserEntitlements');
      await migrateAllUserEntitlements();
    } catch (entErr) {
      console.warn('[Entitlements] Startup migration skipped:', entErr.message);
    }

  } catch (dbErr) {
    console.error('[DB] Failed to initialize database setup:', dbErr);
  }
});


