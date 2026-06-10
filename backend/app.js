// Force the process timezone to Bangladesh Standard Time
process.env.TZ = 'Asia/Dhaka';

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
    await query('UPDATE email_accounts SET sent_today = 0');
    console.log('[CRON] Reset daily email send limits successfully.');
  } catch (err) {
    console.error('[CRON] Error resetting daily email limits:', err);
  }
});

// Mount Calendar-Based Subscription Expiry Guard & FCM Reminder Scheduler
require('./cron/billingScheduler');

// Start listening for connections
app.listen(PORT, async () => {
  console.log(`=============================================`);
  console.log(` Payment Checker API Server running on port ${PORT}`);
  console.log(` Database Target: ${process.env.DB_HOST}:${process.env.DB_PORT || 3306}`);
  console.log(` Database Name  : ${process.env.DB_NAME}`);
  console.log(`=============================================`);

  try {
    // ─────────────────────────────────────────────────────────────────────────
    // Initialize anti-abuse table
    // ─────────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
    // Initialize global_billing_settings table (kept for admin config)
    // ─────────────────────────────────────────────────────────────────────────
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
    console.log('[DB] global_billing_settings table verified.');

    // ─────────────────────────────────────────────────────────────────────────
    // DROP deprecated credit_deduction_ledger table
    // ─────────────────────────────────────────────────────────────────────────
    await query(`DROP TABLE IF EXISTS \`credit_deduction_ledger\``);
    console.log('[DB] Dropped deprecated credit_deduction_ledger table (if existed).');

    // ─────────────────────────────────────────────────────────────────────────
    // Initialize subscription_plans table with duration_days (not credits_given)
    // ─────────────────────────────────────────────────────────────────────────
    await query(`
      CREATE TABLE IF NOT EXISTS \`subscription_plans\` (
        \`id\` INT AUTO_INCREMENT PRIMARY KEY,
        \`plan_name\` VARCHAR(50) NOT NULL UNIQUE,
        \`price\` DECIMAL(10,2) NOT NULL,
        \`max_sites\` INT NOT NULL,
        \`max_devices\` INT NOT NULL,
        \`duration_days\` INT DEFAULT 365,
        \`created_at\` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    `);

    // Migrate credits_given → duration_days if old column exists
    const creditsCol = await query("SHOW COLUMNS FROM `subscription_plans` LIKE 'credits_given'");
    if (creditsCol.length > 0) {
      // Add duration_days if it doesn't exist yet
      const durationCol = await query("SHOW COLUMNS FROM `subscription_plans` LIKE 'duration_days'");
      if (durationCol.length === 0) {
        await query("ALTER TABLE `subscription_plans` ADD COLUMN `duration_days` INT DEFAULT 365 AFTER `max_devices`");
      }
      // Copy values from credits_given to duration_days
      await query("UPDATE `subscription_plans` SET `duration_days` = `credits_given` WHERE `duration_days` IS NULL OR `duration_days` = 365");
      // Drop old column
      await query("ALTER TABLE `subscription_plans` DROP COLUMN `credits_given`");
      console.log('[DB] Migrated credits_given → duration_days in subscription_plans.');
    }

    // Seed default subscription plans
    const defaultPlans = [
      { name: 'Basic', price: 100.00, sites: 1, devices: 1, days: 365 },
      { name: 'Standard', price: 200.00, sites: 3, devices: 3, days: 365 },
      { name: 'Premium', price: 500.00, sites: 999, devices: 10, days: 365 }
    ];
    for (const plan of defaultPlans) {
      await query(
        'INSERT INTO subscription_plans (plan_name, price, max_sites, max_devices, duration_days) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE price = VALUES(price), max_sites = VALUES(max_sites), max_devices = VALUES(max_devices), duration_days = VALUES(duration_days)',
        [plan.name, plan.price, plan.sites, plan.devices, plan.days]
      );
    }
    console.log('[DB] Seeded default subscription plans (duration_days).');

    // ─────────────────────────────────────────────────────────────────────────
    // SCHEMA MIGRATION: Boolean Paid-Gate & Dynamic Plan Name Architecture
    // Drop: wallet_credits, account_level
    // Add:  is_paid, active_plan_name, expiry_date
    // ─────────────────────────────────────────────────────────────────────────

    // Step 1: Add new columns defensively (is_paid, active_plan_name, expiry_date)
    const isPaidCol = await query("SHOW COLUMNS FROM `users` LIKE 'is_paid'");
    if (isPaidCol.length === 0) {
      await query("ALTER TABLE `users` ADD COLUMN `is_paid` TINYINT(1) DEFAULT 0 AFTER `role`");
      console.log('[DB] ✅ Added is_paid column to users.');
    }

    const activePlanCol = await query("SHOW COLUMNS FROM `users` LIKE 'active_plan_name'");
    if (activePlanCol.length === 0) {
      await query("ALTER TABLE `users` ADD COLUMN `active_plan_name` VARCHAR(50) DEFAULT 'FREE_LEVEL' AFTER `is_paid`");
      console.log('[DB] ✅ Added active_plan_name column to users.');
    }

    const expiryDateCol = await query("SHOW COLUMNS FROM `users` LIKE 'expiry_date'");
    if (expiryDateCol.length === 0) {
      await query("ALTER TABLE `users` ADD COLUMN `expiry_date` DATE DEFAULT NULL AFTER `active_plan_name`");
      console.log('[DB] ✅ Added expiry_date column to users.');
    }

    // Step 2: Migrate existing data from old columns to new columns (one-time)
    const oldAccountLevelCol = await query("SHOW COLUMNS FROM `users` LIKE 'account_level'");
    const oldWalletCreditsCol = await query("SHOW COLUMNS FROM `users` LIKE 'wallet_credits'");

    if (oldAccountLevelCol.length > 0 && oldWalletCreditsCol.length > 0) {
      // Migrate paid users: if account_level != FREE_LEVEL → is_paid=1, active_plan_name=account_level, expiry_date=today+wallet_credits days
      await query(`
        UPDATE users 
        SET is_paid = 1, 
            active_plan_name = account_level, 
            expiry_date = DATE_ADD(CURRENT_DATE(), INTERVAL GREATEST(CAST(wallet_credits AS SIGNED), 1) DAY)
        WHERE account_level IS NOT NULL AND account_level != 'FREE_LEVEL' AND wallet_credits > 0
      `);
      console.log('[DB] ✅ Migrated existing paid users (account_level + wallet_credits → is_paid + active_plan_name + expiry_date).');
    }

    // Step 3: Drop deprecated columns safely
    if (oldWalletCreditsCol.length > 0) {
      await query("ALTER TABLE `users` DROP COLUMN `wallet_credits`");
      console.log('[DB] ✅ Dropped deprecated wallet_credits column from users.');
    }

    if (oldAccountLevelCol.length > 0) {
      await query("ALTER TABLE `users` DROP COLUMN `account_level`");
      console.log('[DB] ✅ Dropped deprecated account_level column from users.');
    }

    // Drop custom_daily_rate if it still exists
    const userCustomRateCol = await query("SHOW COLUMNS FROM `users` LIKE 'custom_daily_rate'");
    if (userCustomRateCol.length > 0) {
      await query("ALTER TABLE `users` DROP COLUMN `custom_daily_rate`");
      console.log('[DB] Dropped custom_daily_rate column from users.');
    }

    // Ensure fcm_token column exists
    const userFcmCol = await query("SHOW COLUMNS FROM `users` LIKE 'fcm_token'");
    if (userFcmCol.length === 0) {
      await query("ALTER TABLE `users` ADD COLUMN `fcm_token` VARCHAR(512) DEFAULT NULL AFTER `gmail_enabled`");
      console.log('[DB] Added fcm_token column to users.');
    }

    console.log('[DB] ═══════════════════════════════════════════');
    console.log('[DB] ✅ Boolean Paid-Gate Schema Migration Complete!');
    console.log('[DB] ═══════════════════════════════════════════');

    // ─────────────────────────────────────────────────────────────────────────
    // SCHEMA MIGRATION: Parent-Child Remote Control Hub for registered_devices
    // ─────────────────────────────────────────────────────────────────────────
    const addRegisteredDeviceColumn = async (colName, colDefinition) => {
      const cols = await query("SHOW COLUMNS FROM `registered_devices` LIKE ?", [colName]);
      if (cols.length === 0) {
        await query(`ALTER TABLE \`registered_devices\` ADD COLUMN \`${colName}\` ${colDefinition}`);
        console.log(`[DB] ✅ Added ${colName} column to registered_devices table.`);
      }
    };

    await addRegisteredDeviceColumn('custom_device_name', "VARCHAR(100) NOT NULL DEFAULT ''");
    await addRegisteredDeviceColumn('sim_one_number', "VARCHAR(15) DEFAULT NULL");
    await addRegisteredDeviceColumn('sim_one_active', "TINYINT(1) DEFAULT 1");
    await addRegisteredDeviceColumn('sim_two_number', "VARCHAR(15) DEFAULT NULL");
    await addRegisteredDeviceColumn('sim_two_active', "TINYINT(1) DEFAULT 1");
    await addRegisteredDeviceColumn('is_app_active', "TINYINT(1) DEFAULT 1");

    // Copy device_name / custom_name to custom_device_name where custom_device_name is empty
    await query("UPDATE `registered_devices` SET `custom_device_name` = IFNULL(NULLIF(custom_name, ''), device_name) WHERE `custom_device_name` = ''");
    console.log('[DB] ✅ Parent-Child Hub Schema Migration Complete!');


  } catch (dbErr) {
    console.error('[DB] Failed to initialize database setup:', dbErr);
  }
});
