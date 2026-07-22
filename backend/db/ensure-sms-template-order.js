/**
 * Ensure sms_templates.display_order exists (admin/global sort for checkout + ready-made).
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

async function ensureSmsTemplateDisplayOrder() {
  if (!(await columnExists('sms_templates', 'display_order'))) {
    await prisma.$executeRawUnsafe(`
      ALTER TABLE sms_templates
      ADD COLUMN display_order INT NOT NULL DEFAULT 0
      COMMENT 'Admin global sort: lower = higher on checkout/ready-made'
      AFTER is_parseable
    `);
    // Seed order by id within each parseable group
    await prisma.$executeRawUnsafe(`
      UPDATE sms_templates t
      JOIN (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY is_parseable ORDER BY id ASC) AS rn
        FROM sms_templates
        WHERE is_official = 1
      ) x ON x.id = t.id
      SET t.display_order = x.rn
    `);
  }
}

module.exports = { ensureSmsTemplateDisplayOrder };
