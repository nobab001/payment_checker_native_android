const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');
const { CATEGORIES, DURATION_LABELS } = require('./constants');

function shortDisplayName(fullName, durationKey) {
  const raw = String(fullName || '').trim();
  if (!raw) return '';
  const label = DURATION_LABELS[durationKey];
  if (label && raw.toLowerCase().startsWith(`${String(label).toLowerCase()} `)) {
    return raw.slice(String(label).length + 1).trim();
  }
  return raw.replace(/^(Monthly|Yearly|Annually|Half Yearly|Half-Yearly)\s+/i, '').trim() || raw;
}

function mapActiveSubscriptionsForClient(rows) {
  return (rows || []).map((r) => {
    const full = String(r.package_full_name || '').trim();
    const durationKey = r.duration_key ? String(r.duration_key) : null;
    return {
      ...r,
      category: String(r.category || ''),
      package_sku: r.package_sku ? String(r.package_sku) : null,
      package_full_name: full,
      duration_key: durationKey,
      display_name: shortDisplayName(full, durationKey),
      expires_at: r.expires_at,
      starts_at: r.starts_at,
    };
  });
}

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
           device_limit_internal, duration_key, starts_at, expires_at, status,
           amount_paid, paid_duration_days, list_price_paid
    FROM user_subscriptions
    WHERE user_id = ${Number(userId)} AND status = 'active'
  `;
  return mapActiveSubscriptionsForClient(rows);
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
  mapActiveSubscriptionsForClient,
  getSharedExpiry,
  getEffectiveExpiry,
  remainingDays,
  getSharedRemainingDays,
  syncAllCategoriesToExpiry,
  CATEGORIES,
};
