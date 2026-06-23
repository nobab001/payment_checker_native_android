const prisma = require('../db/prisma');

/**
 * Middleware: checkBillingStatus
 * Enforces SaaS subscription level locks.
 * - If is_paid is 0 or active_plan_name is 'FREE_LEVEL', the account is LOCKED (HTTP 402).
 * - Admin role is exempted.
 */
async function checkBillingStatus(req, res, next) {
  try {
    const userId = req.user.userId;
    if (userId === 0) {
      // Global Admin Console has userId 0, bypass checks
      return next();
    }

    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { is_paid: true, active_plan_name: true, role: true }
    });

    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    if (user.role === 'admin') {
      return next();
    }

    if (!user.is_paid || user.active_plan_name === 'FREE_LEVEL') {
      return res.status(402).json({
        success: false,
        error: 'ACCOUNT_SUSPENDED',
        message: 'অনুগ্রহ করে যেকোনো একটি সাবস্ক্রিপশন প্ল্যান কিনে আপনার সেবা সচল করুন।'
      });
    }

    next();
  } catch (err) {
    console.error('[Billing Middleware] Error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

module.exports = checkBillingStatus;
