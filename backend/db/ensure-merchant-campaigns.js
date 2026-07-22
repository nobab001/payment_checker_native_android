/**
 * Ensure merchant_campaigns table exists.
 *
 * Campaign / Extra incentives sit alongside the per-type merchant_commissions.
 * A campaign gives a commission (cashback) OR deducts a charge on transactions
 * that fall inside an amount range [min_amount, max_amount], scoped to a single
 * payment type (template) or ALL types (payment_type = '').
 *
 * Raw-SQL table (not a Prisma model) so it can be added without regenerating the
 * Prisma client. All reads/writes go through $queryRawUnsafe / $executeRawUnsafe.
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

async function ensureMerchantCampaigns() {
  if (!(await tableExists('merchant_campaigns'))) {
    await prisma.$executeRawUnsafe(`
      CREATE TABLE merchant_campaigns (
        id           INT AUTO_INCREMENT PRIMARY KEY,
        website_id   INT NOT NULL,
        payment_type VARCHAR(64)  NOT NULL DEFAULT '' COMMENT 'normalized token; empty = ALL types',
        label        VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'friendly template name for display',
        min_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
        max_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '0 = no upper limit',
        mode         VARCHAR(16) NOT NULL DEFAULT 'commission' COMMENT 'commission | charge',
        value_type   VARCHAR(16) NOT NULL DEFAULT 'flat' COMMENT 'percentage | flat',
        value        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
        is_active    TINYINT(1) NOT NULL DEFAULT 1,
        created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_campaign_website (website_id),
        INDEX idx_campaign_website_active (website_id, is_active),
        CONSTRAINT fk_campaign_website FOREIGN KEY (website_id)
          REFERENCES gateway_layouts (id) ON DELETE CASCADE ON UPDATE RESTRICT
      )
    `);
  }
}

module.exports = { ensureMerchantCampaigns };
