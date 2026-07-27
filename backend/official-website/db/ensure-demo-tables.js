/**
 * Ensure demo_visitors / demo_payments exist (idempotent).
 */
const prisma = require('../../db/prisma');

async function ensureColumn(table, column, definition) {
  const rows = await prisma.$queryRawUnsafe(
    `SELECT COUNT(*) AS c FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?`,
    table,
    column,
  );
  const count = Number(rows?.[0]?.c || 0);
  if (count > 0) return;
  await prisma.$executeRawUnsafe(`ALTER TABLE \`${table}\` ADD COLUMN \`${column}\` ${definition}`);
}

async function ensureIndex(table, indexName, definition) {
  const rows = await prisma.$queryRawUnsafe(
    `SELECT COUNT(*) AS c FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?`,
    table,
    indexName,
  );
  const count = Number(rows?.[0]?.c || 0);
  if (count > 0) return;
  await prisma.$executeRawUnsafe(`ALTER TABLE \`${table}\` ADD INDEX \`${indexName}\` ${definition}`);
}

async function ensureDemoTables() {
  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS demo_visitors (
      id INT NOT NULL AUTO_INCREMENT,
      public_id VARCHAR(40) NOT NULL,
      display_name VARCHAR(64) NOT NULL,
      token_hash VARCHAR(64) NOT NULL,
      host_website_id INT NULL,
      settings_json LONGTEXT NULL,
      ip_hash VARCHAR(64) NULL,
      user_agent_hash VARCHAR(64) NULL,
      status VARCHAR(16) NOT NULL DEFAULT 'active',
      expires_at DATETIME NOT NULL,
      last_seen_at DATETIME NOT NULL,
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_demo_public_id (public_id),
      KEY idx_demo_visitors_expiry (status, expires_at),
      KEY idx_demo_visitors_ip (ip_hash, created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS demo_payments (
      id INT NOT NULL AUTO_INCREMENT,
      visitor_id INT NOT NULL,
      amount DECIMAL(12,2) NOT NULL,
      purpose VARCHAR(32) NOT NULL DEFAULT 'pay',
      order_id VARCHAR(128) NULL,
      session_token VARCHAR(128) NULL,
      status VARCHAR(32) NOT NULL DEFAULT 'initiated',
      trx_id VARCHAR(64) NULL,
      provider VARCHAR(64) NULL,
      sender_number VARCHAR(32) NULL,
      receiver_number VARCHAR(32) NULL,
      full_sms TEXT NULL,
      visitor_public_id VARCHAR(40) NULL,
      visitor_display_name VARCHAR(64) NULL,
      refund_status VARCHAR(16) NOT NULL DEFAULT 'none',
      refund_note VARCHAR(255) NULL,
      refunded_at DATETIME NULL,
      updated_at DATETIME NULL,
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      KEY idx_demo_payments_visitor (visitor_id, created_at),
      KEY idx_demo_payments_order (order_id),
      KEY idx_demo_payments_session (session_token),
      KEY idx_demo_payments_created (created_at),
      CONSTRAINT fk_demo_payment_visitor FOREIGN KEY (visitor_id)
        REFERENCES demo_visitors (id) ON DELETE CASCADE ON UPDATE RESTRICT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // Existing installs — add refund/history columns if missing
  await ensureColumn('demo_payments', 'trx_id', 'VARCHAR(64) NULL');
  await ensureColumn('demo_payments', 'provider', 'VARCHAR(64) NULL');
  await ensureColumn('demo_payments', 'sender_number', 'VARCHAR(32) NULL');
  await ensureColumn('demo_payments', 'receiver_number', 'VARCHAR(32) NULL');
  await ensureColumn('demo_payments', 'full_sms', 'TEXT NULL');
  await ensureColumn('demo_payments', 'visitor_public_id', 'VARCHAR(40) NULL');
  await ensureColumn('demo_payments', 'visitor_display_name', 'VARCHAR(64) NULL');
  await ensureColumn('demo_payments', 'refund_status', "VARCHAR(16) NOT NULL DEFAULT 'none'");
  await ensureColumn('demo_payments', 'refund_note', 'VARCHAR(255) NULL');
  await ensureColumn('demo_payments', 'refunded_at', 'DATETIME NULL');
  await ensureColumn('demo_payments', 'updated_at', 'DATETIME NULL');
  await ensureIndex('demo_payments', 'idx_demo_payments_order', '(order_id)');
  await ensureIndex('demo_payments', 'idx_demo_payments_session', '(session_token)');
  await ensureIndex('demo_payments', 'idx_demo_payments_created', '(created_at)');
}

module.exports = { ensureDemoTables };
