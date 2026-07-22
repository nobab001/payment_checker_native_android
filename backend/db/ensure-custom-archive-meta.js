/**
 * Ensure custom_sms_archives has sim_slot / sim_number / sender_id for home archive cards.
 */
const prisma = require('./prisma');

async function columnExists(table, column) {
  const rows = await prisma.$queryRawUnsafe(
    `SELECT COUNT(*) AS c FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?`,
    table,
    column,
  );
  return Number(rows[0]?.c || 0) > 0;
}

async function ensureCustomArchiveMetaColumns() {
  if (!(await columnExists('custom_sms_archives', 'sim_slot'))) {
    await prisma.$executeRawUnsafe(`
      ALTER TABLE custom_sms_archives
      ADD COLUMN sim_slot TINYINT NULL
      COMMENT 'SIM slot 1 or 2 at ingest'
      AFTER device_id
    `);
  }
  if (!(await columnExists('custom_sms_archives', 'sim_number'))) {
    await prisma.$executeRawUnsafe(`
      ALTER TABLE custom_sms_archives
      ADD COLUMN sim_number VARCHAR(32) NULL
      COMMENT 'Phone number of the SIM slot'
      AFTER sim_slot
    `);
  }
  if (!(await columnExists('custom_sms_archives', 'sender_id'))) {
    await prisma.$executeRawUnsafe(`
      ALTER TABLE custom_sms_archives
      ADD COLUMN sender_id VARCHAR(128) NULL
      COMMENT 'Template / SMS sender ID'
      AFTER provider_tag
    `);
  }
}

module.exports = { ensureCustomArchiveMetaColumns };
