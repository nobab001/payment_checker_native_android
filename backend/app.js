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
const billingRoutes     = require('./routes/billingRoutes');

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
    await query('UPDATE email_accounts SET sent_today = 0');
    console.log('[CRON] Reset daily email send limits successfully.');
  } catch (err) {
    console.error('[CRON] Error resetting daily email limits:', err);
  }
});

// 12:01 AM Cron: Deduct daily maintenance rate (1 credit) from active non-free subscription tier users
cron.schedule('1 0 * * *', async () => {
  try {
    // 1. Deduct 1 credit from all users who have account_level != 'FREE_LEVEL' and are active
    await query(`
      UPDATE users 
      SET wallet_credits = wallet_credits - 1 
      WHERE blocked = 0 AND profile_complete = 1 AND account_level != 'FREE_LEVEL'
    `);

    // 2. Auto-Downgrade Engine: For any user whose wallet_credits <= 0, reset to FREE_LEVEL and zero credits
    await query(`
      UPDATE users
      SET account_level = 'FREE_LEVEL', wallet_credits = 0
      WHERE blocked = 0 AND profile_complete = 1 AND account_level != 'FREE_LEVEL' AND wallet_credits <= 0
    `);

    console.log('[CRON] SaaS Unified Credit Aging Engine: Deducted 1 credit and ran auto-downgrade engine.');
  } catch (err) {
    console.error('[CRON] Error in midnight SaaS credit aging cron job:', err);
  }
});

// 11:00 AM Cron: Notify users with credits < ৳10 using Mock Firebase push alerts
cron.schedule('0 11 * * *', async () => {
  try {
    const lowBalanceUsers = await query(
      `SELECT id, name, wallet_credits, fcm_token FROM users 
       WHERE blocked = 0 AND profile_complete = 1 AND wallet_credits < 10.00 AND fcm_token IS NOT NULL AND fcm_token != ''`
    );

    for (const u of lowBalanceUsers) {
      console.log(`[Mock FCM Notification] Sent to user ${u.id} (Token: ${u.fcm_token.substring(0, 15)}...): ` +
        `"আপনার ওয়ালেট ব্যালেন্স ৳${u.wallet_credits}। ওটিপি এবং ট্র্যাকিং সার্ভিস সচল রাখতে অনুগ্রহ করে রিচার্জ সম্পন্ন করুন।"`);
    }
    console.log(`[CRON] Dispatched low balance push alerts to ${lowBalanceUsers.length} users.`);
  } catch (err) {
    console.error('[CRON] Error in morning low balance alert:', err);
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

    // Initialize global_billing_settings table
    await query(`
      CREATE TABLE IF NOT EXISTS \`global_billing_settings\` (
        \`id\`           INT          NOT NULL AUTO_INCREMENT,
        \`setting_key\`   VARCHAR(255) NOT NULL,
        \`setting_value\` VARCHAR(255) NOT NULL,
        \`description\`   VARCHAR(255) DEFAULT NULL,
        PRIMARY KEY (\`id\`),
        UNIQUE KEY \`uniq_setting_key\` (\`setting_key\`)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    `);

    const defaultSettings = [
      { key: 'default_signup_bonus', val: '30.00', desc: 'Default signup bonus credits for new users' },
      { key: 'daily_maintenance_rate', val: '0.50', desc: 'Global daily maintenance cost deducted at midnight' },
      { key: 'one_time_site_fee', val: '10.00', desc: 'One-time cost to add a website/layout' },
      { key: 'one_time_device_fee', val: '5.00', desc: 'One-time cost to add a child device' }
    ];

    for (const setting of defaultSettings) {
      await query(
        'INSERT INTO global_billing_settings (setting_key, setting_value, description) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE description = VALUES(description)',
        [setting.key, setting.val, setting.desc]
      );
    }
    console.log('[DB] global_billing_settings verified and populated.');

    // Initialize subscription_plans table
    await query(`
      CREATE TABLE IF NOT EXISTS \`subscription_plans\` (
        \`id\` INT AUTO_INCREMENT PRIMARY KEY,
        \`plan_name\` VARCHAR(50) NOT NULL UNIQUE,
        \`price\` DECIMAL(10,2) NOT NULL,
        \`max_sites\` INT NOT NULL,
        \`max_devices\` INT NOT NULL,
        \`credits_given\` INT DEFAULT 365,
        \`created_at\` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    `);

    const defaultPlans = [
      { name: 'Basic', price: 100.00, sites: 1, devices: 1, credits: 365 },
      { name: 'Standard', price: 200.00, sites: 3, devices: 3, credits: 365 },
      { name: 'Premium', price: 500.00, sites: 999, devices: 10, credits: 365 }
    ];
    for (const plan of defaultPlans) {
      await query(
        'INSERT INTO subscription_plans (plan_name, price, max_sites, max_devices, credits_given) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE price = VALUES(price), max_sites = VALUES(max_sites), max_devices = VALUES(max_devices), credits_given = VALUES(credits_given)',
        [plan.name, plan.price, plan.sites, plan.devices, plan.credits]
      );
    }
    console.log('[DB] Seeded default subscription plans.');

    // Add account_level and wallet_credits columns to users table if they don't exist
    const userLevelCol = await query("SHOW COLUMNS FROM `users` LIKE 'account_level'");
    if (userLevelCol.length === 0) {
      await query("ALTER TABLE `users` ADD COLUMN `account_level` VARCHAR(30) DEFAULT 'FREE_LEVEL' AFTER `role`");
      console.log('[DB] Added account_level column to users.');
    }
    const userWalletCol = await query("SHOW COLUMNS FROM `users` LIKE 'wallet_credits'");
    if (userWalletCol.length === 0) {
      await query("ALTER TABLE `users` ADD COLUMN `wallet_credits` INT DEFAULT 0 AFTER `account_level`");
      console.log('[DB] Added wallet_credits column to users.');
    } else {
      if (userWalletCol[0].Type.toLowerCase() !== 'int') {
        await query("ALTER TABLE `users` MODIFY COLUMN `wallet_credits` INT DEFAULT 0");
        console.log('[DB] Altered wallet_credits column in users to INT.');
      }
    }
    const userCustomRateCol = await query("SHOW COLUMNS FROM `users` LIKE 'custom_daily_rate'");
    if (userCustomRateCol.length > 0) {
      await query("ALTER TABLE `users` DROP COLUMN `custom_daily_rate`");
      console.log('[DB] Dropped custom_daily_rate column from users.');
    }
    const userFcmCol = await query("SHOW COLUMNS FROM `users` LIKE 'fcm_token'");
    if (userFcmCol.length === 0) {
      await query("ALTER TABLE `users` ADD COLUMN `fcm_token` VARCHAR(512) DEFAULT NULL AFTER `gmail_enabled`");
      console.log('[DB] Added fcm_token column to users.');
    }
  } catch (dbErr) {
    console.error('[DB] Failed to initialize database setup:', dbErr);
  }
});

