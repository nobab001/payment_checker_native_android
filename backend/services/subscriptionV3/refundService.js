const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');
const { getPackageBySku } = require('./catalogService');
const { logAudit } = require('./auditService');
const { bustEntitlementCache } = require('../permissionEngineService');
const { formatYmd, dateOnly } = require('./sharedExpiryService');

async function getPurchase(purchaseId, userId) {
  await ensureSubscriptionV3Schema();
  const rows = await prisma.$queryRaw`
    SELECT id, user_id, invoice_no, package_full_name, package_sku, amount_paid,
           started_at, ends_at, refund_status, created_at
    FROM subscription_purchases
    WHERE id = ${Number(purchaseId)} AND user_id = ${Number(userId)}
    LIMIT 1
  `;
  return rows[0] || null;
}

function daysSinceActivation(startedAt) {
  const start = dateOnly(startedAt);
  const today = dateOnly();
  return Math.floor((today - start) / 86400000);
}

async function canRequestRefund(userId, purchaseId) {
  const purchase = await getPurchase(purchaseId, userId);
  if (!purchase) return { ok: false, message: 'ক্রয় রেকর্ড পাওয়া যায়নি।' };
  if (purchase.refund_status === 'refunded' || purchase.refund_status === 'completed') {
    return { ok: false, message: 'ইতিমধ্যে রিফান্ড সম্পন্ন।' };
  }
  const pkg = await getPackageBySku(purchase.package_sku);
  const refundDays = pkg?.refund_days ?? 7;
  const elapsed = daysSinceActivation(purchase.started_at);
  if (elapsed > refundDays) {
    return { ok: false, message: `রিফান্ডের ${refundDays} দিনের মেয়াদ শেষ।` };
  }
  const pending = await prisma.$queryRaw`
    SELECT id FROM subscription_refund_requests
    WHERE purchase_id = ${Number(purchaseId)} AND status IN ('pending', 'approved')
    LIMIT 1
  `;
  if (pending.length) {
    return { ok: false, message: 'রিফান্ড রিকোয়েস্ট ইতিমধ্যে আছে।', status: 'pending' };
  }
  return { ok: true, purchase, refundDays, daysRemaining: refundDays - elapsed };
}

async function createRefundRequest(userId, purchaseId, reason, ipAddress) {
  const check = await canRequestRefund(userId, purchaseId);
  if (!check.ok) return check;
  await prisma.$executeRaw`
    INSERT INTO subscription_refund_requests (user_id, purchase_id, invoice_no, status, reason)
    VALUES (
      ${Number(userId)},
      ${Number(purchaseId)},
      ${check.purchase.invoice_no},
      'pending',
      ${reason || null}
    )
  `;
  await logAudit({
    userId,
    action: 'refund_request',
    oldPackage: check.purchase.package_full_name,
    reason,
    ipAddress,
    meta: { purchase_id: purchaseId, invoice_no: check.purchase.invoice_no },
  });
  return { ok: true, status: 'pending' };
}

async function listPendingRefunds() {
  await ensureSubscriptionV3Schema();
  return prisma.$queryRaw`
    SELECT r.id, r.user_id, r.purchase_id, r.invoice_no, r.status, r.reason, r.requested_at,
           p.package_full_name, p.amount_paid, u.name AS user_name, u.phone
    FROM subscription_refund_requests r
    JOIN subscription_purchases p ON p.id = r.purchase_id
    JOIN users u ON u.id = r.user_id
    WHERE r.status = 'pending'
    ORDER BY r.requested_at ASC
  `;
}

async function resolveRefund(requestId, adminId, approve, adminNote, ipAddress) {
  const rows = await prisma.$queryRaw`
    SELECT r.*, p.user_id, p.package_sku, p.package_full_name
    FROM subscription_refund_requests r
    JOIN subscription_purchases p ON p.id = r.purchase_id
    WHERE r.id = ${Number(requestId)} AND r.status = 'pending'
    LIMIT 1
  `;
  const req = rows[0];
  if (!req) return { error: 'NOT_FOUND' };

  const newStatus = approve ? 'approved' : 'rejected';
  await prisma.$executeRaw`
    UPDATE subscription_refund_requests
    SET status = ${newStatus}, admin_id = ${Number(adminId)}, admin_note = ${adminNote || null},
        resolved_at = NOW()
    WHERE id = ${Number(requestId)}
  `;

  if (approve) {
    await prisma.$executeRaw`
      UPDATE subscription_purchases SET refund_status = 'refunded', is_closed = 1
      WHERE id = ${Number(req.purchase_id)}
    `;
    await prisma.$executeRaw`
      UPDATE user_subscriptions SET status = 'refunded'
      WHERE user_id = ${Number(req.user_id)} AND package_sku = ${req.package_sku}
    `;
    await prisma.$executeRaw`
      UPDATE subscription_refund_requests SET status = 'completed' WHERE id = ${Number(requestId)}
    `;
    await bustEntitlementCache(req.user_id);
  }

  await logAudit({
    userId: req.user_id,
    adminId,
    action: approve ? 'refund_approved' : 'refund_rejected',
    oldPackage: req.package_full_name,
    reason: adminNote,
    ipAddress,
  });

  return { success: true, status: approve ? 'completed' : 'rejected' };
}

async function getUserRefundStatus(userId) {
  const rows = await prisma.$queryRaw`
    SELECT status, invoice_no, requested_at FROM subscription_refund_requests
    WHERE user_id = ${Number(userId)}
    ORDER BY requested_at DESC LIMIT 1
  `;
  return rows[0] || null;
}

module.exports = {
  canRequestRefund,
  createRefundRequest,
  listPendingRefunds,
  resolveRefund,
  getUserRefundStatus,
  getPurchaseHistory: async (userId) => {
    await ensureSubscriptionV3Schema();
    return prisma.$queryRaw`
      SELECT id, invoice_no, package_full_name, package_sku, category, duration_key,
             amount_paid AS paid_amount, duration_days, created_at AS purchased_at, refund_status
      FROM subscription_purchases
      WHERE user_id = ${Number(userId)}
      ORDER BY created_at DESC
      LIMIT 50
    `;
  },
};
