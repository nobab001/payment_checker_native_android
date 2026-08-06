/**
 * App notification service — admin announcements delivered over HTTP heartbeat.
 *
 * Targeting uses the three subscriptionV3 categories (gateway /
 * personal_business / personal). A notice targeting all three is a broadcast:
 * it also reaches trial and expired accounts that hold no active package, so
 * the admin never needs a fourth "everyone" toggle.
 *
 * Delivery is pull-based (Comm Policy v1.2): the heartbeat response carries the
 * oldest unread notice; the app shows it and POSTs a read receipt.
 */
const prisma = require('../db/prisma');
const { CATEGORIES } = require('./subscriptionV3/constants');
const { ensureAppNotifications } = require('../db/ensure-app-notifications');

const ALL_CATEGORIES = [
  CATEGORIES.GATEWAY,
  CATEGORIES.PERSONAL_BUSINESS,
  CATEGORIES.PERSONAL,
];

const MAX_TITLE = 180;
const MAX_BODY = 4000;

/**
 * Unread window. A device installed today should not replay months of old
 * announcements; anything older is history the admin panel still shows.
 * Interpolated into SQL as a literal — module constant, never user input.
 */
const UNREAD_LOOKBACK_DAYS = 30;

let schemaReady = false;
async function ensureSchema() {
  if (schemaReady) return;
  await ensureAppNotifications();
  schemaReady = true;
}

/** Admin stores `payment_gateway`; the v3 UI key is `gateway`. */
function normalizeCategory(raw) {
  const c = String(raw || '').trim().toLowerCase();
  if (c === 'personal_business') return CATEGORIES.PERSONAL_BUSINESS;
  if (c === 'personal' || c === 'personal_custom_center') return CATEGORIES.PERSONAL;
  if (c === 'gateway' || c === 'payment_gateway') return CATEGORIES.GATEWAY;
  return null;
}

/** Validated, de-duplicated, canonically ordered category list. */
function sanitizeCategories(raw) {
  const list = Array.isArray(raw)
    ? raw
    : String(raw || '').split(',');
  const picked = new Set();
  for (const item of list) {
    const cat = normalizeCategory(item);
    if (cat) picked.add(cat);
  }
  return ALL_CATEGORIES.filter((c) => picked.has(c));
}

/**
 * Categories an account currently holds, derived from the Comm Policy profile
 * (which already resolves v3 subscriptions + legacy mirror + trial).
 * Trial-only accounts resolve to `welcome`, which maps to no package category —
 * they receive broadcasts only.
 */
function categoriesFromProfile(profile) {
  const active = Array.isArray(profile?.activeCategories) ? profile.activeCategories : [];
  const picked = new Set();
  for (const key of active) {
    const cat = normalizeCategory(key);
    if (cat) picked.add(cat);
  }
  return ALL_CATEGORIES.filter((c) => picked.has(c));
}

/** Same, but resolves the profile itself when the caller does not have one. */
async function resolveUserCategories(userId) {
  const commPolicy = require('./commPolicyService');
  const profile = await commPolicy.resolveCommProfile(userId);
  return categoriesFromProfile(profile);
}

/**
 * Create a notice. Returns the stored row.
 * @throws {Error} with `.statusCode = 400` on invalid input.
 */
async function createNotification({ title, body, categories, createdBy }) {
  await ensureSchema();

  const cleanTitle = String(title || '').trim();
  const cleanBody = String(body || '').trim();
  const cats = sanitizeCategories(categories);

  if (!cleanTitle) throw badRequest('নোটিফিকেশনের শিরোনাম আবশ্যক');
  if (cleanTitle.length > MAX_TITLE) throw badRequest(`শিরোনাম সর্বোচ্চ ${MAX_TITLE} অক্ষর`);
  if (!cleanBody) throw badRequest('নোটিফিকেশনের বিস্তারিত আবশ্যক');
  if (cleanBody.length > MAX_BODY) throw badRequest(`বিস্তারিত সর্বোচ্চ ${MAX_BODY} অক্ষর`);
  if (!cats.length) throw badRequest('অন্তত একটি ক্যাটাগরি নির্বাচন করুন');

  // LAST_INSERT_ID() is per-connection — the transaction pins both statements to
  // the same one, so concurrent admin sends cannot read each other's row.
  const rows = await prisma.$transaction(async (tx) => {
    await tx.$executeRaw`
      INSERT INTO app_notifications (title, body, categories, created_by, is_active)
      VALUES (${cleanTitle}, ${cleanBody}, ${cats.join(',')}, ${createdBy ? Number(createdBy) : null}, 1)
    `;
    return tx.$queryRaw`
      SELECT id, title, body, categories, created_by, is_active, created_at
      FROM app_notifications
      WHERE id = LAST_INSERT_ID()
    `;
  });
  return rows[0] ? mapNotificationRow(rows[0]) : null;
}

function badRequest(message) {
  const err = new Error(message);
  err.statusCode = 400;
  return err;
}

function mapNotificationRow(row) {
  return {
    id: Number(row.id),
    title: row.title,
    body: row.body,
    categories: sanitizeCategories(row.categories),
    created_by: row.created_by == null ? null : Number(row.created_by),
    is_active: Number(row.is_active) === 1,
    created_at: row.created_at,
  };
}

/** Admin history — newest first, with per-notice read counts. */
async function listNotifications({ limit = 50 } = {}) {
  await ensureSchema();
  const take = Math.min(Math.max(Number(limit) || 50, 1), 200);
  const rows = await prisma.$queryRawUnsafe(
    `SELECT n.id, n.title, n.body, n.categories, n.created_by, n.is_active, n.created_at,
            (SELECT COUNT(*) FROM app_notification_reads r WHERE r.notification_id = n.id) AS read_count
     FROM app_notifications n
     ORDER BY n.id DESC
     LIMIT ?`,
    take,
  );
  return rows.map((row) => ({
    ...mapNotificationRow(row),
    read_count: Number(row.read_count || 0),
  }));
}

/** Hard delete — cascades read receipts. Returns true when a row was removed. */
async function deleteNotification(id) {
  await ensureSchema();
  const nid = Number(id);
  if (!nid) throw badRequest('অবৈধ নোটিফিকেশন আইডি');
  const affected = await prisma.$executeRaw`
    DELETE FROM app_notifications WHERE id = ${nid}
  `;
  return Number(affected) > 0;
}

/**
 * Unread notices for an account, oldest first.
 *
 * Only the last [UNREAD_LOOKBACK_DAYS] days are considered so a freshly
 * installed device is not flooded with a backlog of stale announcements.
 *
 * @param {number} userId
 * @param {string[]} userCategories from [categoriesFromProfile]
 * @param {{limit?: number}} [opts]
 */
async function listUnreadForUser(userId, userCategories, { limit = 5 } = {}) {
  await ensureSchema();
  const uid = Number(userId);
  if (!uid) return [];
  const take = Math.min(Math.max(Number(limit) || 5, 1), 20);

  // Broadcast = notice covers all three categories (also reaches trial/expired).
  const broadcast = ALL_CATEGORIES
    .map(() => 'FIND_IN_SET(?, n.categories) > 0')
    .join(' AND ');
  const params = [...ALL_CATEGORIES];

  const cats = Array.isArray(userCategories) ? sanitizeCategories(userCategories) : [];
  let ownMatch = '';
  if (cats.length) {
    ownMatch = ` OR (${cats.map(() => 'FIND_IN_SET(?, n.categories) > 0').join(' OR ')})`;
    params.push(...cats);
  }

  const rows = await prisma.$queryRawUnsafe(
    `SELECT n.id, n.title, n.body, n.categories, n.created_at
     FROM app_notifications n
     LEFT JOIN app_notification_reads r
       ON r.notification_id = n.id AND r.user_id = ?
     WHERE n.is_active = 1
       AND r.id IS NULL
       AND n.created_at >= (NOW() - INTERVAL ${UNREAD_LOOKBACK_DAYS} DAY)
       AND ((${broadcast})${ownMatch})
     ORDER BY n.id ASC
     LIMIT ?`,
    uid,
    ...params,
    take,
  );

  return rows.map((row) => ({
    id: Number(row.id),
    title: row.title,
    body: row.body,
    created_at: row.created_at,
  }));
}

/**
 * Record a read receipt. Idempotent — a device retrying the POST (or a second
 * device on the same account) must not fail or duplicate.
 */
async function markRead(userId, notificationId) {
  await ensureSchema();
  const uid = Number(userId);
  const nid = Number(notificationId);
  if (!uid || !nid) throw badRequest('অবৈধ নোটিফিকেশন আইডি');
  await prisma.$executeRaw`
    INSERT IGNORE INTO app_notification_reads (notification_id, user_id)
    VALUES (${nid}, ${uid})
  `;
  return true;
}

module.exports = {
  ALL_CATEGORIES,
  MAX_TITLE,
  MAX_BODY,
  UNREAD_LOOKBACK_DAYS,
  ensureSchema,
  normalizeCategory,
  sanitizeCategories,
  categoriesFromProfile,
  resolveUserCategories,
  createNotification,
  listNotifications,
  deleteNotification,
  listUnreadForUser,
  markRead,
};
