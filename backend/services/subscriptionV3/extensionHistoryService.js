const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');
const { formatYmd } = require('./sharedExpiryService');

const EXTEND_REASONS = Object.freeze([
  'Customer Support',
  'Bug Compensation',
  'Promotion',
  'Manual Adjustment',
  'Other',
]);

const DEFAULT_REASON = 'Manual Adjustment';

function normalizeReason(reason) {
  const r = String(reason || '').trim();
  if (!r) return DEFAULT_REASON;
  if (EXTEND_REASONS.includes(r)) return r;
  return r.slice(0, 120);
}

async function logExtension({
  userId,
  daysAdded,
  reason,
  adminId,
  oldExpiry,
  newExpiry,
  mode,
}) {
  await ensureSubscriptionV3Schema();
  let adminName = null;
  if (adminId) {
    const admin = await prisma.users.findUnique({
      where: { id: Number(adminId) },
      select: { name: true },
    });
    adminName = admin?.name || `Admin #${adminId}`;
  }
  const safeReason = normalizeReason(reason);
  await prisma.$executeRaw`
    INSERT INTO subscription_extension_history (
      user_id, extension_type, days_added, reason, admin_id, admin_name,
      old_expiry, new_expiry, mode
    ) VALUES (
      ${Number(userId)},
      'admin_extension',
      ${Number(daysAdded)},
      ${safeReason},
      ${adminId ? Number(adminId) : null},
      ${adminName},
      ${oldExpiry || null},
      ${newExpiry},
      ${mode || null}
    )
  `;
  return { reason: safeReason, admin_name: adminName };
}

async function listForUser(userId, limit = 20) {
  await ensureSubscriptionV3Schema();
  const rows = await prisma.$queryRaw`
    SELECT id, extension_type, days_added, reason, admin_id, admin_name,
           old_expiry, new_expiry, mode, created_at
    FROM subscription_extension_history
    WHERE user_id = ${Number(userId)}
    ORDER BY created_at DESC
    LIMIT ${Math.min(Number(limit) || 20, 50)}
  `;
  return rows.map((r) => ({
    id: Number(r.id),
    extension_type: r.extension_type,
    days_added: Number(r.days_added),
    reason: r.reason,
    admin_id: r.admin_id ? Number(r.admin_id) : null,
    admin_name: r.admin_name,
    old_expiry: r.old_expiry ? formatYmd(r.old_expiry) : null,
    new_expiry: r.new_expiry ? formatYmd(r.new_expiry) : null,
    mode: r.mode,
    created_at: r.created_at,
  }));
}

module.exports = {
  EXTEND_REASONS,
  DEFAULT_REASON,
  normalizeReason,
  logExtension,
  listForUser,
};
