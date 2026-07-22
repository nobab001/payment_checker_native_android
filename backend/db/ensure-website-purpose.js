/**
 * Ensure gateway_layouts.website_purpose column exists.
 * Values: add_balance | payment | both (default add_balance).
 */
const prisma = require('../db/prisma');

async function columnExists(table, column) {
  const rows = await prisma.$queryRawUnsafe(
    `SELECT COUNT(*) AS c FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?`,
    table,
    column,
  );
  return Number(rows[0]?.c || 0) > 0;
}

async function ensureWebsitePurposeColumn() {
  if (!(await columnExists('gateway_layouts', 'website_purpose'))) {
    await prisma.$executeRawUnsafe(`
      ALTER TABLE gateway_layouts
      ADD COLUMN website_purpose VARCHAR(32) NOT NULL DEFAULT 'add_balance'
      COMMENT 'add_balance | payment | both'
      AFTER commission_enabled
    `);
  }
}

module.exports = { ensureWebsitePurposeColumn };
