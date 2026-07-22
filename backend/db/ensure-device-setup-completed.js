/**
 * Ensure registered_devices.setup_completed exists.
 *
 * Marks whether Background Guard (Accessibility + Battery Unrestricted) was ever
 * completed on this physical device. Persists server-side so that after an app
 * reinstall (which wipes local prefs) the client can show a friendly one-tap
 * "re-enable Accessibility" prompt instead of the full first-time setup nag.
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

async function ensureDeviceSetupCompleted() {
  if (!(await columnExists('registered_devices', 'setup_completed'))) {
    await prisma.$executeRawUnsafe(`
      ALTER TABLE registered_devices
      ADD COLUMN setup_completed TINYINT(1) NOT NULL DEFAULT 0
      COMMENT 'Background Guard (accessibility+battery) once completed on this device'
    `);
  }
}

module.exports = { ensureDeviceSetupCompleted };
