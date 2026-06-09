const express = require('express');
const cors = require('cors');
const cron = require('node-cron');
const { query } = require('./db/connection');
require('dotenv').config();

const authRoutes        = require('./routes/authRoutes');
const paymentRoutes     = require('./routes/paymentRoutes');
const checkoutRoutes    = require('./routes/checkoutRoutes');
const gatewayRoutes     = require('./routes/gatewayRoutes');
const adminRoutes       = require('./routes/adminRoutes');
const credentialRoutes  = require('./routes/credentialRoutes');
const pinRoutes         = require('./routes/pinRoutes');

const app = express();
const PORT = process.env.PORT || 3000;

// Enable CORS for frontend API requests
app.use(cors());

// Serve static webpage assets (like public/checkout.html)
app.use(express.static('public'));

// Parse incoming JSON body payloads
app.use(express.json());

// Log incoming REST API requests
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
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
    await query('UPDATE email_accounts SET sent_today = 0');
    console.log('[CRON] Reset daily email send limits successfully.');
  } catch (err) {
    console.error('[CRON] Error resetting daily email limits:', err);
  }
});

// Start listening for connections
app.listen(PORT, async () => {
  console.log(`=============================================`);
  console.log(` Payment Checker API Server running on port ${PORT}`);
  console.log(` Database Target: ${process.env.DB_HOST}:${process.env.DB_PORT || 3306}`);
  console.log(` Database Name  : ${process.env.DB_NAME}`);
  console.log(`=============================================`);

  // Initialize anti-abuse table
  try {
    await query(`
      CREATE TABLE IF NOT EXISTS \`device_trial_logs\` (
        \`id\`                   INT           NOT NULL AUTO_INCREMENT,
        \`user_id\`              INT           DEFAULT NULL,
        \`android_id\`           VARCHAR(255)  NOT NULL,
        \`hardware_fingerprint\` VARCHAR(255)  NOT NULL,
        \`sim_slot_ids\`         VARCHAR(255)  NOT NULL,
        \`has_used_trial\`       TINYINT(1)    NOT NULL DEFAULT 1,
        \`created_at\`           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (\`id\`),
        UNIQUE KEY \`uniq_device_binding\` (\`android_id\`, \`hardware_fingerprint\`, \`sim_slot_ids\`),
        INDEX \`idx_android_id\` (\`android_id\`),
        INDEX \`idx_fingerprint\` (\`hardware_fingerprint\`),
        INDEX \`idx_sim_slots\` (\`sim_slot_ids\`)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    `);

    // Add user_id column if it does not exist
    const cols = await query("SHOW COLUMNS FROM `device_trial_logs` LIKE 'user_id'");
    if (cols.length === 0) {
      await query("ALTER TABLE `device_trial_logs` ADD COLUMN `user_id` INT DEFAULT NULL AFTER `id`");
      console.log('[DB] Added user_id column to device_trial_logs.');
    }
    console.log('[DB] device_trial_logs table verified/created.');

    // Sync primary phone & email from users to user_credentials
    await query(`
      INSERT INTO user_credentials (user_id, type, value, verified_at)
      SELECT id, 'phone', phone, NOW() FROM users 
      WHERE phone IS NOT NULL AND phone != '' AND phone NOT IN (SELECT value FROM user_credentials)
    `);
    await query(`
      INSERT INTO user_credentials (user_id, type, value, verified_at)
      SELECT id, 'email', email, NOW() FROM users 
      WHERE email IS NOT NULL AND email != '' AND email NOT IN (SELECT value FROM user_credentials)
    `);
    console.log('[DB] Synced existing users to user_credentials.');
  } catch (dbErr) {
    console.error('[DB] Failed to initialize database setup:', dbErr);
  }
});

