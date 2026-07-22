/**
 * Ensure website purpose lock columns on gateway_layouts.
 *   purpose_locked     — 1 after merchant confirms purpose
 *   purpose_locked_at  — when locked
 *   purpose_selected   — 1 once merchant has explicitly chosen (legacy sites may be 0)
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

async function ensureWebsitePurposeLockColumns() {
  if (!(await columnExists('gateway_layouts', 'purpose_selected'))) {
    await prisma.$executeRawUnsafe(`
      ALTER TABLE gateway_layouts
      ADD COLUMN purpose_selected TINYINT NOT NULL DEFAULT 0
      COMMENT '1 = merchant explicitly chose purpose'
      AFTER website_purpose
    `);
  }
  if (!(await columnExists('gateway_layouts', 'purpose_locked'))) {
    await prisma.$executeRawUnsafe(`
      ALTER TABLE gateway_layouts
      ADD COLUMN purpose_locked TINYINT NOT NULL DEFAULT 0
      COMMENT '1 = purpose locked; only super-admin may unlock'
      AFTER purpose_selected
    `);
  }
  if (!(await columnExists('gateway_layouts', 'purpose_locked_at'))) {
    await prisma.$executeRawUnsafe(`
      ALTER TABLE gateway_layouts
      ADD COLUMN purpose_locked_at DATETIME NULL
      COMMENT 'When purpose was locked'
      AFTER purpose_locked
    `);
  }
}

module.exports = { ensureWebsitePurposeLockColumns };
