/**
 * Ensure demo_visitors / demo_payments exist (idempotent).
 */
const prisma = require('../../db/prisma');

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
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      KEY idx_demo_payments_visitor (visitor_id, created_at),
      CONSTRAINT fk_demo_payment_visitor FOREIGN KEY (visitor_id)
        REFERENCES demo_visitors (id) ON DELETE CASCADE ON UPDATE RESTRICT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);
}

module.exports = { ensureDemoTables };
