const prisma = require('../../db/prisma');

let ready = false;

function assertId(name) {
  if (!/^[a-z_][a-z0-9_]*$/i.test(name)) throw new Error(`Invalid identifier: ${name}`);
}

async function columnExists(table, column) {
  assertId(table);
  assertId(column);
  const rows = await prisma.$queryRawUnsafe(`SHOW COLUMNS FROM \`${table}\` LIKE '${column}'`);
  return rows.length > 0;
}

async function ensureColumn(table, column, ddl) {
  if (!(await columnExists(table, column))) {
    await prisma.$executeRawUnsafe(`ALTER TABLE \`${table}\` ADD COLUMN ${ddl}`);
  }
}

async function ensureSubscriptionV3Schema() {
  if (ready) return;

  // Extend subscription_plans for v3 catalog
  for (const [col, ddl] of [
    ['sku_key', '`sku_key` VARCHAR(64) NULL'],
    ['display_name', '`display_name` VARCHAR(120) NULL'],
    ['price_1m', '`price_1m` DECIMAL(10,2) NOT NULL DEFAULT 0.00'],
    ['price_6m', '`price_6m` DECIMAL(10,2) NOT NULL DEFAULT 0.00'],
    ['price_12m', '`price_12m` DECIMAL(10,2) NOT NULL DEFAULT 0.00'],
    ['website_limit_internal', '`website_limit_internal` INT NOT NULL DEFAULT 1'],
    ['device_limit_internal', '`device_limit_internal` INT NOT NULL DEFAULT 50'],
    ['refund_days', '`refund_days` INT NOT NULL DEFAULT 7'],
    ['catalog_status', "`catalog_status` VARCHAR(16) NOT NULL DEFAULT 'active'"],
    ['archived_at', '`archived_at` DATETIME NULL'],
    ['archived_by', '`archived_by` INT NULL'],
    ['is_visible', '`is_visible` TINYINT NOT NULL DEFAULT 1'],
  ]) {
    await ensureColumn('subscription_plans', col, ddl);
  }

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS subscription_addon_catalog (
      id INT NOT NULL AUTO_INCREMENT,
      addon_key VARCHAR(32) NOT NULL,
      display_name VARCHAR(120) NOT NULL,
      allowed_categories JSON NOT NULL,
      price_1m DECIMAL(10,2) NOT NULL DEFAULT 0.00,
      price_6m DECIMAL(10,2) NOT NULL DEFAULT 0.00,
      price_12m DECIMAL(10,2) NOT NULL DEFAULT 0.00,
      is_active TINYINT NOT NULL DEFAULT 1,
      sort_order INT NOT NULL DEFAULT 0,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_addon_key (addon_key)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  await ensureColumn('subscription_addon_catalog', 'info_text', '`info_text` TEXT NULL');

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS user_subscriptions (
      id INT NOT NULL AUTO_INCREMENT,
      user_id INT NOT NULL,
      category VARCHAR(32) NOT NULL,
      package_sku VARCHAR(64) NOT NULL,
      package_full_name VARCHAR(160) NOT NULL,
      website_limit_internal INT NOT NULL DEFAULT 0,
      device_limit_internal INT NOT NULL DEFAULT 50,
      duration_key VARCHAR(8) NOT NULL DEFAULT '12m',
      starts_at DATE NOT NULL,
      expires_at DATE NOT NULL,
      status VARCHAR(16) NOT NULL DEFAULT 'active',
      subscription_version VARCHAR(8) NOT NULL DEFAULT 'v3',
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_user_category (user_id, category),
      KEY idx_user_sub_expires (user_id, expires_at, status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  await ensureColumn('user_subscriptions', 'amount_paid', '`amount_paid` DECIMAL(10,2) NULL');
  await ensureColumn('user_subscriptions', 'paid_duration_days', '`paid_duration_days` INT NULL');
  await ensureColumn('user_subscriptions', 'list_price_paid', '`list_price_paid` DECIMAL(10,2) NULL');

  // Stable package codes — backfill empty sku_key once; never rewrite existing codes.
  await prisma.$executeRawUnsafe(`
    UPDATE subscription_plans
    SET sku_key = CONCAT('plan_', id)
    WHERE sku_key IS NULL OR sku_key = ''
  `).catch(() => {});
  const skuIdx = await prisma.$queryRawUnsafe(
    `SHOW INDEX FROM subscription_plans WHERE Key_name = 'uniq_plan_sku_key'`
  ).catch(() => []);
  if (!skuIdx.length) {
    await prisma.$executeRawUnsafe(`
      CREATE UNIQUE INDEX uniq_plan_sku_key ON subscription_plans (sku_key)
    `).catch(() => {});
  }

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS subscription_deferred (
      id INT NOT NULL AUTO_INCREMENT,
      user_id INT NOT NULL,
      category VARCHAR(32) NOT NULL,
      package_sku VARCHAR(64) NOT NULL,
      package_full_name VARCHAR(160) NOT NULL,
      duration_key VARCHAR(8) NOT NULL,
      duration_days INT NOT NULL,
      website_limit_internal INT NOT NULL DEFAULT 0,
      device_limit_internal INT NOT NULL DEFAULT 50,
      starts_at DATE NOT NULL,
      expires_at DATE NOT NULL,
      amount_paid DECIMAL(10,2) NOT NULL DEFAULT 0.00,
      list_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
      addons_json TEXT NULL,
      status VARCHAR(16) NOT NULL DEFAULT 'pending',
      purchase_invoice VARCHAR(32) NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      applied_at DATETIME NULL,
      PRIMARY KEY (id),
      KEY idx_deferred_pending (status, starts_at),
      KEY idx_deferred_user_cat (user_id, category, status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS user_subscription_addons (
      id INT NOT NULL AUTO_INCREMENT,
      user_id INT NOT NULL,
      addon_key VARCHAR(32) NOT NULL,
      expires_at DATE NOT NULL,
      status VARCHAR(16) NOT NULL DEFAULT 'active',
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_user_addon (user_id, addon_key),
      KEY idx_user_addon_exp (user_id, expires_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS subscription_quote_freeze (
      id INT NOT NULL AUTO_INCREMENT,
      quote_token VARCHAR(64) NOT NULL,
      user_id INT NOT NULL,
      quote_json LONGTEXT NOT NULL,
      payable_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
      expires_at DATETIME NOT NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_quote_token (quote_token),
      KEY idx_quote_user (user_id, expires_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS subscription_refund_requests (
      id INT NOT NULL AUTO_INCREMENT,
      user_id INT NOT NULL,
      purchase_id INT NOT NULL,
      invoice_no VARCHAR(32) NULL,
      status VARCHAR(16) NOT NULL DEFAULT 'pending',
      reason TEXT NULL,
      admin_id INT NULL,
      admin_note TEXT NULL,
      requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      resolved_at DATETIME NULL,
      PRIMARY KEY (id),
      KEY idx_refund_user (user_id, status),
      KEY idx_refund_pending (status, requested_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS subscription_audit_log (
      id INT NOT NULL AUTO_INCREMENT,
      user_id INT NULL,
      admin_id INT NULL,
      action VARCHAR(64) NOT NULL,
      old_package VARCHAR(160) NULL,
      new_package VARCHAR(160) NULL,
      reason TEXT NULL,
      ip_address VARCHAR(64) NULL,
      meta_json TEXT NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      KEY idx_audit_user (user_id, created_at),
      KEY idx_audit_action (action, created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS subscription_invoice_seq (
      seq_date CHAR(8) NOT NULL,
      last_seq INT NOT NULL DEFAULT 0,
      PRIMARY KEY (seq_date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  // Extend subscription_purchases if exists (from subscriptionBillingService)
  try {
    for (const [col, ddl] of [
      ['invoice_no', '`invoice_no` VARCHAR(32) NULL'],
      ['package_full_name', '`package_full_name` VARCHAR(160) NULL'],
      ['package_sku', '`package_sku` VARCHAR(64) NULL'],
      ['category', '`category` VARCHAR(32) NULL'],
      ['duration_key', '`duration_key` VARCHAR(8) NULL'],
      ['transaction_id', '`transaction_id` VARCHAR(128) NULL'],
      ['quote_token', '`quote_token` VARCHAR(64) NULL'],
      ['refund_status', '`refund_status` VARCHAR(16) NULL'],
      ['admin_marked', '`admin_marked` TINYINT NOT NULL DEFAULT 0'],
    ]) {
      await ensureColumn('subscription_purchases', col, ddl);
    }
    // Unique on transaction_id when set
    const idx = await prisma.$queryRawUnsafe(
      `SHOW INDEX FROM subscription_purchases WHERE Key_name = 'uniq_sub_purch_trx'`
    );
    if (!idx.length) {
      await prisma.$executeRawUnsafe(`
        CREATE UNIQUE INDEX uniq_sub_purch_trx ON subscription_purchases (transaction_id)
      `).catch(() => {});
    }
    const invIdx = await prisma.$queryRawUnsafe(
      `SHOW INDEX FROM subscription_purchases WHERE Key_name = 'uniq_sub_purch_invoice'`
    );
    if (!invIdx.length) {
      await prisma.$executeRawUnsafe(`
        CREATE UNIQUE INDEX uniq_sub_purch_invoice ON subscription_purchases (invoice_no)
      `).catch(() => {});
    }
  } catch (_) {
    // subscription_purchases created on first legacy billing use
  }

  // users.subscription_version + is_trial
  await ensureColumn('users', 'subscription_version', "`subscription_version` VARCHAR(8) NULL DEFAULT 'v3'");
  await ensureColumn('users', 'is_trial', '`is_trial` TINYINT NOT NULL DEFAULT 0');

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS subscription_extension_history (
      id INT NOT NULL AUTO_INCREMENT,
      user_id INT NOT NULL,
      extension_type VARCHAR(32) NOT NULL DEFAULT 'admin_extension',
      days_added INT NOT NULL,
      reason VARCHAR(120) NULL DEFAULT 'Manual Adjustment',
      admin_id INT NULL,
      admin_name VARCHAR(120) NULL,
      old_expiry DATE NULL,
      new_expiry DATE NOT NULL,
      mode VARCHAR(16) NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      KEY idx_ext_user_created (user_id, created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  ready = true;

  // Backfill off the critical path — never block auth/billing requests.
  setImmediate(() => {
    require('./trialFlagService').backfillIsTrialFlags().catch(() => {});
  });
}

module.exports = { ensureSubscriptionV3Schema, columnExists, ensureColumn };
