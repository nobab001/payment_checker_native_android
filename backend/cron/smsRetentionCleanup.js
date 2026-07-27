'use strict';

/**
 * SMS Retention Cleanup — low-load night job helpers.
 *
 * sms_history:
 *   Delete sold-out rows (is_used = 1) whose age is ≥ HISTORY_SOLD_OUT_DAYS
 *   (prefer used_at, else sms_timestamp, else created_at).
 *
 * custom_sms_archives:
 *   1) Delete rows older than ARCHIVE_MAX_AGE_DAYS
 *   2) Keep at most ARCHIVE_MAX_PER_USER newest rows per user; delete older surplus
 *
 * Load control: small delete batches + short sleep between users / batches.
 */

const prisma = require('../db/prisma');

const HISTORY_SOLD_OUT_DAYS = 30;
const ARCHIVE_MAX_AGE_DAYS = 45;
const ARCHIVE_MAX_PER_USER = 1000;

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
 * Per-user: keep newest ARCHIVE_MAX_PER_USER archives; delete older surplus.
 */
async function cleanupArchiveOverCap(stats) {
  const userGroups = await prisma.custom_sms_archives.groupBy({
    by: ['user_id'],
    _count: { _all: true },
  });

  const oversized = userGroups.filter(
    (g) => (g._count?._all || 0) > ARCHIVE_MAX_PER_USER
  );

  for (const group of oversized) {
    if (MAX_DELETES_PER_RUN > 0 && stats.totalDeleted >= MAX_DELETES_PER_RUN) {
      stats.capped = true;
      break;
    }

    const userId = group.user_id;

    // Newest N kept → first row after skip is the oldest among those to delete boundary
    const cutoff = await prisma.custom_sms_archives.findMany({
      where: { user_id: userId },
      orderBy: [{ created_at: 'desc' }, { id: 'desc' }],
      skip: ARCHIVE_MAX_PER_USER,
      take: 1,
      select: { id: true, created_at: true },
    });

    if (!cutoff.length) {
      await sleep(COOLDOWN_MS);
      continue;
    }

    // Delete older than the Nth newest: created_at < cutoff OR same time with smaller id
    // Simpler & safe: delete by id list in batches using id ASC among surplus
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

      const surplus = await prisma.custom_sms_archives.findMany({
        where: { user_id: userId },
        orderBy: [{ created_at: 'desc' }, { id: 'desc' }],
        skip: ARCHIVE_MAX_PER_USER,
        take: remainingBudget,
        select: { id: true },
      });

      if (!surplus.length) break;

      const ids = surplus.map((r) => r.id);
      const result = await prisma.custom_sms_archives.deleteMany({
        where: { user_id: userId, id: { in: ids } },
      });
      const n = result.count || 0;
      stats.archiveCapDeleted += n;
      stats.totalDeleted += n;

      await sleep(COOLDOWN_MS);
      if (n < remainingBudget) break;
    }

    await sleep(COOLDOWN_MS);
  }
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
      `archive age ≥${ARCHIVE_MAX_AGE_DAYS}d | archive max ${ARCHIVE_MAX_PER_USER}/user | ` +
      `batch=${DELETE_BATCH_SIZE} cooldown=${COOLDOWN_MS}ms`
  );

  await cleanupSoldOutSmsHistory(stats);
  if (!stats.capped) {
    await cleanupArchiveByAge(stats);
  }
  if (!stats.capped) {
    await cleanupArchiveOverCap(stats);
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
  ARCHIVE_MAX_PER_USER,
};
