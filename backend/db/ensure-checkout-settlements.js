/**
 * Ensure checkout_settlements table for Payment-mode multi-Trx settlement.
 */
const prisma = require('../db/prisma');

async function tableExists(table) {
  const rows = await prisma.$queryRawUnsafe(
    `SELECT COUNT(*) AS c FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?`,
    table,
  );
  return Number(rows[0]?.c || 0) > 0;
}

async function ensureCheckoutSettlementsTable() {
  if (await tableExists('checkout_settlements')) return;
  await prisma.$executeRawUnsafe(`
    CREATE TABLE checkout_settlements (
      id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
      settlement_key VARCHAR(120) NOT NULL,
      website_id INT NOT NULL,
      session_token VARCHAR(80) NULL,
      purpose VARCHAR(32) NOT NULL DEFAULT 'payment',
      order_amount DECIMAL(12,2) NOT NULL,
      expected_payable DECIMAL(12,2) NOT NULL,
      received_sum DECIMAL(12,2) NOT NULL DEFAULT 0,
      status VARCHAR(20) NOT NULL DEFAULT 'open',
      parts_json LONGTEXT NULL,
      provider_tag VARCHAR(191) NULL,
      completed_at DATETIME NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      UNIQUE KEY uniq_settlement_key (settlement_key),
      KEY idx_settlement_website_status (website_id, status),
      KEY idx_settlement_session (session_token)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);
}

module.exports = { ensureCheckoutSettlementsTable };
