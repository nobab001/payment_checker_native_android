/**
 * Ensure app_notifications + app_notification_reads tables exist.
 *
 * Admin-authored announcements delivered to the app over the existing HTTP
 * heartbeat (Comm Policy v1.2 — no socket, no FCM). `categories` targets the
 * three subscriptionV3 package categories; all three selected = broadcast to
 * every account, including trial / expired users who hold no active package.
 *
 * Raw-SQL tables (not Prisma models) so they can ship without regenerating the
 * Prisma client. All reads/writes go through $queryRaw* / $executeRawUnsafe.
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

async function ensureAppNotifications() {
  if (!(await tableExists('app_notifications'))) {
    await prisma.$executeRawUnsafe(`
      CREATE TABLE app_notifications (
        id         INT AUTO_INCREMENT PRIMARY KEY,
        title      VARCHAR(180) NOT NULL COMMENT 'headline shown in the popup + status bar',
        body       TEXT NOT NULL COMMENT 'details / full message',
        categories VARCHAR(128) NOT NULL
                   DEFAULT 'gateway,personal_business,personal'
                   COMMENT 'CSV of targeted subscriptionV3 categories',
        created_by INT NULL COMMENT 'admin users.id (NULL if account later removed)',
        is_active  TINYINT(1) NOT NULL DEFAULT 1,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_notif_active_created (is_active, created_at)
      )
    `);
  }

  if (!(await tableExists('app_notification_reads'))) {
    await prisma.$executeRawUnsafe(`
      CREATE TABLE app_notification_reads (
        id              INT AUTO_INCREMENT PRIMARY KEY,
        notification_id INT NOT NULL,
        user_id         INT NOT NULL,
        read_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uq_notif_user (notification_id, user_id),
        INDEX idx_notif_read_user (user_id),
        CONSTRAINT fk_notif_read_notif FOREIGN KEY (notification_id)
          REFERENCES app_notifications (id) ON DELETE CASCADE ON UPDATE RESTRICT
      )
    `);
  }
}

module.exports = { ensureAppNotifications };
