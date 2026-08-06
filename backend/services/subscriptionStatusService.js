/**
 * Subscription lifecycle status — single source of truth for access gating.
 *
 * users.subscription_status: 'active' | 'grace' | 'suspended'
 *  - active    → full entitlements per plan/trial
 *  - grace     → expired but still allowed (revenue-save window; permissions intact)
 *  - suspended → hard cut: every perm_* is 0, SMS ingest dropped, monitoring stopped
 *
 * expiry_date is NEVER nulled on expiry — it stays for audit trail and for the
 * permission engine's date comparison (a NULL date used to read as "no expiry"
 * and silently granted unlimited trial entitlements).
 */

const prisma = require('../db/prisma');
const { ensureColumn } = require('./subscriptionV3/schema');

const STATUS_ACTIVE = 'active';
const STATUS_GRACE = 'grace';
const STATUS_SUSPENDED = 'suspended';

const VALID_STATUSES = new Set([STATUS_ACTIVE, STATUS_GRACE, STATUS_SUSPENDED]);

let statusSchemaReady = false;

/** Additive, production-safe: add the column if the deploy predates it. */
async function ensureSubscriptionStatusSchema() {
  if (statusSchemaReady) return;
  try {
    await ensureColumn(
      'users',
      'subscription_status',
      "`subscription_status` ENUM('active','grace','suspended') NOT NULL DEFAULT 'active'"
    );
    statusSchemaReady = true;
  } catch (e) {
    console.warn('[SubStatus] schema ensure skipped:', e.message);
  }
}

/**
 * Read raw status for a user. Returns 'active' when the column is missing or the
 * row is gone — fail-open here is deliberate: gating decisions layer their own
 * checks on top, and a schema lag must not lock out paying users.
 */
async function getSubscriptionStatus(userId) {
  try {
    const rows = await prisma.$queryRaw`
      SELECT COALESCE(subscription_status, 'active') AS subscription_status
      FROM users WHERE id = ${Number(userId)} LIMIT 1
    `;
    const val = String(rows[0]?.subscription_status || STATUS_ACTIVE).toLowerCase();
    return VALID_STATUSES.has(val) ? val : STATUS_ACTIVE;
  } catch (_) {
    return STATUS_ACTIVE;
  }
}

async function isSuspended(userId) {
  return (await getSubscriptionStatus(userId)) === STATUS_SUSPENDED;
}

async function setSubscriptionStatus(userId, status) {
  if (!VALID_STATUSES.has(status)) {
    throw new Error(`Invalid subscription_status: ${status}`);
  }
  await ensureSubscriptionStatusSchema();
  await prisma.$executeRaw`
    UPDATE users SET subscription_status = ${status} WHERE id = ${Number(userId)}
  `;
  return status;
}

/**
 * Restore a user to 'active' after a successful purchase/renewal.
 * Callers must already have written the new expiry_date / is_paid.
 */
async function reactivateUser(userId) {
  await setSubscriptionStatus(userId, STATUS_ACTIVE);
  try {
    await require('./permissionEngineService').bustEntitlementCache(userId);
  } catch (_) { /* cache bust is best-effort */ }
}

module.exports = {
  STATUS_ACTIVE,
  STATUS_GRACE,
  STATUS_SUSPENDED,
  VALID_STATUSES,
  ensureSubscriptionStatusSchema,
  getSubscriptionStatus,
  isSuspended,
  setSubscriptionStatus,
  reactivateUser,
};
