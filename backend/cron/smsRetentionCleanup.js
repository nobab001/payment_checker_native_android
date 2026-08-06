'use strict';

/**
 * SMS Retention Cleanup — low-load night job helpers.
 *
 * sms_history:
 *   Delete sold-out rows (is_used = 1) whose age is ≥ HISTORY_SOLD_OUT_DAYS
 *   (prefer used_at, else sms_timestamp, else created_at).
 *
 * custom_sms_archives:
 *   Delete rows older than ARCHIVE_MAX_AGE_DAYS (rolling 15-day window).
 *   No per-user row cap.
 *
 * Load control: small delete batches + short sleep between users / batches.
 */

const prisma = require('../db/prisma');

const HISTORY_SOLD_OUT_DAYS = 30;
/** Rolling window: keep last 15 days of custom archives; day 16+ cut nightly. */
const ARCHIVE_MAX_AGE_DAYS = 15;

/** Rows deleted per DELETE statement — keeps locks short */
const DELETE_BATCH_SIZE = 400;
/** Pause between batches / users (ms) */
const COOLDOWN_MS = 75;
/** Safety cap per nightly run (0 = unlimited) */
const MAX_DELETES_PER_RUN = Number(process.env.SMS_RETENTION_MAX_DELETES || 50000);

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function daysAgo(days) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d;
}

/**
 * Delete sold-out sms_history older than HISTORY_SOLD_OUT_DAYS.
 * Uses raw SQL so COALESCE(used_at, sms_timestamp, created_at) works efficiently.
 */
async function cleanupSoldOutSmsHistory(stats) {
  const cutoff = daysAgo(HISTORY_SOLD_OUT_DAYS);
  let deletedThisPhase = 0;

  // eslint-disable-next-line no-constant-condition
  while (true) {
    if (MAX_DELETES_PER_RUN > 0 && stats.totalDeleted >= MAX_DELETES_PER_RUN) {
      stats.capped = true;
      break;
    }

    const remainingBudget =
      MAX_DELETES_PER_RUN > 0
        ? Math.min(DELETE_BATCH_SIZE, MAX_DELETES_PER_RUN - stats.totalDeleted)
        : DELETE_BATCH_SIZE;

    const batch = await prisma.$queryRawUnsafe(
      `SELECT id FROM sms_history
       WHERE is_used = 1
         AND COALESCE(used_at, sms_timestamp, created_at) < ?
       ORDER BY id ASC
       LIMIT ?`,
      cutoff,
      remainingBudget
    );

    if (!batch.length) break;

    const ids = batch.map((r) => Number(r.id)).filter((id) => Number.isFinite(id));
    if (!ids.length) break;

    const result = await prisma.sms_history.deleteMany({
      where: { id: { in: ids } },
    });
    const n = result.count || 0;
    deletedThisPhase += n;
    stats.historyDeleted += n;
    stats.totalDeleted += n;

    await sleep(COOLDOWN_MS);
    if (n < remainingBudget) break;
  }

  return deletedThisPhase;
}

/**
 * Delete archive rows older than ARCHIVE_MAX_AGE_DAYS (all users, batched).
 * No per-user row cap — retention is time-based (rolling 15 days) only.
 */
async function cleanupArchiveByAge(stats) {
  const cutoff = daysAgo(ARCHIVE_MAX_AGE_DAYS);
  let deletedThisPhase = 0;

  // eslint-disable-next-line no-constant-condition
  while (true) {
    if (MAX_DELETES_PER_RUN > 0 && stats.totalDeleted >= MAX_DELETES_PER_RUN) {
      stats.capped = true;
      break;
    }

    const remainingBudget =
      MAX_DELETES_PER_RUN > 0
        ? Math.min(DELETE_BATCH_SIZE, MAX_DELETES_PER_RUN - stats.totalDeleted)
        : DELETE_BATCH_SIZE;

    const batch = await prisma.custom_sms_archives.findMany({
      where: { created_at: { lt: cutoff } },
      orderBy: { id: 'asc' },
      take: remainingBudget,
      select: { id: true },
    });

    if (!batch.length) break;

    const ids = batch.map((r) => r.id);
    const result = await prisma.custom_sms_archives.deleteMany({
      where: { id: { in: ids } },
    });
    const n = result.count || 0;
    deletedThisPhase += n;
    stats.archiveAgeDeleted += n;
    stats.totalDeleted += n;

    await sleep(COOLDOWN_MS);
    if (n < remainingBudget) break;
  }

  return deletedThisPhase;
}

/**
 * Full nightly retention run.
 */
async function runSmsRetentionCleanup() {
  const startedAt = Date.now();
  const stats = {
    historyDeleted: 0,
    archiveAgeDeleted: 0,
    archiveCapDeleted: 0,
    totalDeleted: 0,
    capped: false,
  };

  console.log(
    `[SMS Retention] Start | history sold-out ≥${HISTORY_SOLD_OUT_DAYS}d | ` +
      `archive age ≥${ARCHIVE_MAX_AGE_DAYS}d (rolling window, no row cap) | ` +
      `batch=${DELETE_BATCH_SIZE} cooldown=${COOLDOWN_MS}ms`
  );

  await cleanupSoldOutSmsHistory(stats);
  if (!stats.capped) {
    await cleanupArchiveByAge(stats);
  }

  const ms = Date.now() - startedAt;
  console.log(
    `[SMS Retention] Done in ${ms}ms | history=${stats.historyDeleted} ` +
      `archiveAge=${stats.archiveAgeDeleted} archiveCap=${stats.archiveCapDeleted} ` +
      `total=${stats.totalDeleted}` +
      (stats.capped ? ' | CAPPED by SMS_RETENTION_MAX_DELETES' : '')
  );

  return stats;
}

module.exports = {
  runSmsRetentionCleanup,
  HISTORY_SOLD_OUT_DAYS,
  ARCHIVE_MAX_AGE_DAYS,
};
