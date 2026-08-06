const prisma = require('../db/prisma');
const { getUserEntitlements } = require('../services/accountEntitlementsService');
const {
  STATUS_SUSPENDED,
  getSubscriptionStatus,
} = require('../services/subscriptionStatusService');

const SUSPENDED_MESSAGE =
  'আপনার প্যাকেজের মেয়াদ শেষ হয়ে গেছে। সেবা সচল করতে অনুগ্রহ করে একটি সাবস্ক্রিপশন প্যাকেজ কিনুন।';

function isActiveSubscription(user) {
  if (!user.is_paid || user.active_plan_name === 'FREE_LEVEL') return false;
  // Trial and paid both use expiry_date — no Prisma is_trial select (client may lag).
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
 * Allows access when user has an active subscription OR an active entitlement.
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

    // Suspended accounts are cut off regardless of any stale perm_* columns.
    if ((await getSubscriptionStatus(userId)) === STATUS_SUSPENDED) {
      return res.status(402).json({
        success: false,
        error: 'ACCOUNT_SUSPENDED',
        subscription_status: STATUS_SUSPENDED,
        message: SUSPENDED_MESSAGE,
      });
    }

    const ent = await getUserEntitlements(userId);
    const hasSub = isActiveSubscription(user);
    const hasAddon = isActiveCustomSenderAddon(user);

    if (hasSub || hasAddon) {
      req.accountEntitlements = ent;
      return next();
    }

    return res.status(402).json({
      success: false,
      error: 'ACCOUNT_SUSPENDED',
      subscription_status: STATUS_SUSPENDED,
      message: SUSPENDED_MESSAGE,
    });
  } catch (err) {
    console.error('[Billing Middleware] Error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * Middleware: attachBillingStatus (non-blocking)
 *
 * For read-only endpoints that must stay reachable so the app can render its
 * "buy a package" state — blocking these would leave the client with a generic
 * network error instead of the lock screen. Sets req.subscriptionSuspended and
 * req.subscriptionStatus for controllers to surface in their payload.
 */
async function attachBillingStatus(req, res, next) {
  try {
    const userId = req.user?.userId;
    if (!userId) return next();
    const status = await getSubscriptionStatus(userId);
    req.subscriptionStatus = status;
    req.subscriptionSuspended = status === STATUS_SUSPENDED;
  } catch (err) {
    console.warn('[Billing Middleware] status attach failed:', err.message);
  }
  return next();
}

module.exports = checkBillingStatus;
module.exports.checkBillingStatus = checkBillingStatus;
module.exports.attachBillingStatus = attachBillingStatus;
module.exports.SUSPENDED_MESSAGE = SUSPENDED_MESSAGE;
