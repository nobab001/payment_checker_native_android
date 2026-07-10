const prisma = require('../db/prisma');
const { getUserEntitlements } = require('../services/accountEntitlementsService');

function isActiveSubscription(user) {
  if (!user.is_paid || user.active_plan_name === 'FREE_LEVEL') return false;
  if (user.active_plan_name === 'Trial Package') return true;
  if (!user.expiry_date) return false;
  const expiry = new Date(user.expiry_date);
  expiry.setHours(0, 0, 0, 0);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return expiry >= today;
}

function isActiveCustomSenderAddon(user) {
  if (user.has_custom_sender_addon !== 1) return false;
  if (!user.custom_sender_ends_at) return true;
  const ends = new Date(user.custom_sender_ends_at);
  ends.setHours(0, 0, 0, 0);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return ends >= today;
}

/**
 * Middleware: checkBillingStatus
 * Allows access when user has an active subscription OR an active entitlement (e.g. payment-checker addon).
 */
async function checkBillingStatus(req, res, next) {
  try {
    const userId = req.user.userId;
    if (userId === 0) {
      return next();
    }

    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: {
        is_paid: true,
        active_plan_name: true,
        role: true,
        expiry_date: true,
        has_custom_sender_addon: true,
        custom_sender_ends_at: true,
      },
    });

    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    if (user.role === 'admin') {
      return next();
    }

    const ent = await getUserEntitlements(userId);
    const hasSub = isActiveSubscription(user);
    const hasAddon = isActiveCustomSenderAddon(user);
    const hasAnyPermission = ent && (
      ent.perm_custom_sender === 1 ||
      ent.perm_template === 1 ||
      ent.perm_website === 1 ||
      ent.perm_device === 1
    );

    if (hasSub || hasAddon || hasAnyPermission) {
      req.accountEntitlements = ent;
      return next();
    }

    return res.status(402).json({
      success: false,
      error: 'ACCOUNT_SUSPENDED',
      message: 'অনুগ্রহ করে যেকোনো একটি সাবস্ক্রিপশন প্যাকেজ কিনে আপনার সেবা সচল করুন।',
    });
  } catch (err) {
    console.error('[Billing Middleware] Error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

module.exports = checkBillingStatus;
