const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');
const { CATEGORIES } = require('./constants');

function dateOnly(d = new Date()) {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
}

function formatYmd(d) {
  const x = dateOnly(d);
  const y = x.getFullYear();
  const m = String(x.getMonth() + 1).padStart(2, '0');
  const day = String(x.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function parseYmd(s) {
  if (!s) return null;
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? null : dateOnly(d);
}

function addDays(base, days) {
  const d = new Date(base);
  d.setDate(d.getDate() + days);
  return dateOnly(d);
}

async function getUserSubscriptions(userId) {
  await ensureSubscriptionV3Schema();
  const rows = await prisma.$queryRaw`
    SELECT id, user_id, category, package_sku, package_full_name, website_limit_internal,
           device_limit_internal, duration_key, starts_at, expires_at, status
    FROM user_subscriptions
    WHERE user_id = ${Number(userId)} AND status = 'active'
  `;
  return rows.map((r) => ({
    ...r,
    category: String(r.category),
    expires_at: r.expires_at,
    starts_at: r.starts_at,
  }));
}

async function getSharedExpiry(userId) {
  const subs = await getUserSubscriptions(userId);
  let max = null;
  for (const s of subs) {
    const exp = parseYmd(s.expires_at);
    if (!exp) continue;
    if (!max || exp > max) max = exp;
  }
  return max;
}

async function getEffectiveExpiry(userId) {
  return getSharedExpiry(userId);
}

function remainingDays(fromDate, toDate) {
  const from = dateOnly(fromDate);
  const to = dateOnly(toDate);
  const ms = to.getTime() - from.getTime();
  return Math.max(0, Math.round(ms / (86400000)));
}

async function getSharedRemainingDays(userId) {
  const shared = await getSharedExpiry(userId);
  if (!shared) return 0;
  const today = dateOnly();
  if (shared < today) return 0;
  return remainingDays(today, shared);
}

async function syncAllCategoriesToExpiry(userId, targetYmd) {
  await ensureSubscriptionV3Schema();
  const subs = await getUserSubscriptions(userId);
  for (const s of subs) {
    await prisma.$executeRaw`
      UPDATE user_subscriptions SET expires_at = ${targetYmd}, updated_at = NOW()
      WHERE id = ${Number(s.id)}
    `;
  }
  // Addon expiry = shared
  await prisma.$executeRaw`
    UPDATE user_subscription_addons SET expires_at = ${targetYmd}, updated_at = NOW()
    WHERE user_id = ${Number(userId)} AND status = 'active'
  `;
}

module.exports = {
  dateOnly,
  formatYmd,
  parseYmd,
  addDays,
  getUserSubscriptions,
  getSharedExpiry,
  getEffectiveExpiry,
  remainingDays,
  getSharedRemainingDays,
  syncAllCategoriesToExpiry,
  CATEGORIES,
};
