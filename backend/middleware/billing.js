const { query } = require('../db/connection');

/**
 * Middleware: checkBillingStatus
 * Enforces resource-consumption negative-billing account locks.
 * - Under-30 days: Free trial remains active, negative balances are permitted.
 * - Older than 30 days: If wallet_credits <= 0, service is LOCKED (HTTP 402).
 * Admin role (userId: 0 or role: admin) is exempted.
 */
async function checkBillingStatus(req, res, next) {
  try {
    const userId = req.user.userId;
    if (userId === 0) {
      // Global Admin Console has userId 0, bypass checks
      return next();
    }

    const users = await query('SELECT created_at, wallet_credits, role FROM users WHERE id = ? LIMIT 1', [userId]);
    if (users.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }

    const user = users[0];
    if (user.role === 'admin') {
      return next();
    }

    const createdTime = new Date(user.created_at);
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    const walletCredits = parseFloat(user.wallet_credits || '0.00');

    // Suspension check: user created more than 30 days ago AND has zero/negative balance
    if (createdTime < thirtyDaysAgo && walletCredits <= 0.00) {
      return res.status(402).json({
        success: false,
        error: 'ACCOUNT_SUSPENDED',
        message: 'আপনার অ্যাকাউন্টে বকেয়া বিল রয়েছে এবং ফ্রি ট্রায়াল পিরিয়ড শেষ হয়েছে। ওটিপি এবং ট্র্যাকিং সার্ভিস সচল রাখতে অনুগ্রহ করে সর্বনিম্ন ৳৫০ রিচার্জ সম্পন্ন করুন।'
      });
    }

    next();
  } catch (err) {
    console.error('[Billing Middleware] Error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

module.exports = checkBillingStatus;
