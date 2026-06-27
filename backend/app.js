// Force the process timezone to Bangladesh Standard Time
process.env.TZ = 'Asia/Dhaka';

const express = require('express');
const http = require('http');
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

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*' }
});

// Setup Socket.IO Multi-Device Isolation
io.on('connection', (socket) => {
  const userId = socket.handshake.query.userId;
  const deviceId = socket.handshake.query.deviceId;
  
  if (userId && deviceId) {
    const roomName = `${userId}:${deviceId}`;
    socket.join(roomName);
    console.log(`[Socket.IO] Device connected and locked to room: ${roomName}`);
    
    socket.on('disconnect', () => {
      console.log(`[Socket.IO] Device disconnected from room: ${roomName}`);
    });
  } else {
    console.log(`[Socket.IO] Anonymous connection dropped (missing userId or deviceId)`);
    socket.disconnect();
  }
});

// Make io accessible globally via app
app.set('io', io);

const PORT = process.env.PORT || 3000;

// Enable CORS for frontend API requests
app.use(cors());

// Serve static webpage assets (like public/checkout.html)
app.use(express.static('public'));

// Parse incoming JSON body payloads
app.use(express.json());

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
  res.json({ status: 'UP', timestamp: new Date() });
});

// Mount Authentication & Device Trial Routes
app.use('/api', authRoutes);
app.use('/api', paymentRoutes);
app.use('/api', checkoutRoutes);
app.use('/api', gatewayRoutes);
app.use('/api', credentialRoutes);
app.use('/api', pinRoutes);
app.use('/api/admin', adminRoutes);
app.use('/api/v1', billingRoutes);

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

    // Check if initial seeding has already been performed
    const seedCheck = await prisma.global_config.findUnique({
      where: { config_key: 'db_seeded' }
    });

    if (!seedCheck || seedCheck.config_value !== 'true') {
      // Seed default subscription plans
      const defaultPlans = [
        { name: 'Basic', price: 100.00, sites: 1, devices: 1, days: 365 },
        { name: 'Standard', price: 200.00, sites: 3, devices: 3, days: 365 },
        { name: 'Premium', price: 500.00, sites: 999, devices: 10, days: 365 }
      ];
      for (const plan of defaultPlans) {
        await prisma.subscription_plans.upsert({
          where: { plan_name: plan.name },
          update: { price: plan.price, max_sites: plan.sites, max_devices: plan.devices, duration_days: plan.days },
          create: { plan_name: plan.name, price: plan.price, max_sites: plan.sites, max_devices: plan.devices, duration_days: plan.days }
        });
      }
      console.log('[DB] Seeded default subscription plans.');

      // Delete legacy default SMS templates IDs 1 to 10 before seeding new ones
      await prisma.checkout_view_templates.deleteMany({
        where: { sms_template_id: { in: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] } }
      });
      await prisma.sms_templates.deleteMany({
        where: { id: { in: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] } }
      });

      // We will compile the bracket formats for the DB
      const { generateCustomRegex } = require('./controllers/adminController');

      // Seed default SMS templates for bKash Personal (Receive Money and Cash In combined)
      const defaultSmsTemplates = [
        { 
          id: 1, 
          name: 'bKash Personal', 
          sender: 'bKash', 
          kw: '',
          regex: generateCustomRegex('You have received Tk {amount} from {sender}. Fee Tk 0.00. Balance Tk {random}. TrxID {trxid} at {random}|||Cash In Tk {amount} from {sender} successful. Fee Tk 0.00. Balance Tk {random}. TrxID {trxid} at {random}. Download App: https://bKa.sh/8app')
        }
      ];

      for (const t of defaultSmsTemplates) {
        await prisma.sms_templates.upsert({
          where: { id: t.id },
          update: { template_name: t.name, sender_id: t.sender, matching_keyword: t.kw, regex_pattern: t.regex },
          create: { id: t.id, template_name: t.name, sender_id: t.sender, matching_keyword: t.kw, regex_pattern: t.regex, is_official: 1, is_active: 1 }
        });
      }
      console.log('[DB] Seeding official SMS templates complete.');

      // Seed default checkout view templates
      const defaultCheckoutViews = [
        { id: 1, single: 'নিচের বিকাশ পার্সোনাল নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।', multi: 'নিচের যেকোনো একটি সক্রিয় বিকাশ নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।' }
      ];

      for (const cv of defaultCheckoutViews) {
        await prisma.checkout_view_templates.upsert({
          where: { sms_template_id: cv.id },
          update: { single_number_instruction: cv.single, multiple_number_instruction: cv.multi },
          create: { sms_template_id: cv.id, single_number_instruction: cv.single, multiple_number_instruction: cv.multi }
        });
      }
      console.log('[DB] Seeding checkout view templates complete.');

      // Mark database as seeded
      await prisma.global_config.upsert({
        where: { config_key: 'db_seeded' },
        update: { config_value: 'true' },
        create: { config_key: 'db_seeded', config_value: 'true' }
      });
      console.log('[DB] Initial seeding marked as completed.');
    } else {
      console.log('[DB] Initial seeding skipped (already initialized).');
    }
    console.log('[DB] ✅ Prisma setup complete!');

  } catch (dbErr) {
    console.error('[DB] Failed to initialize database setup:', dbErr);
  }
});


