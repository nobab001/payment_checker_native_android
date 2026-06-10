const { query } = require('../db/connection');

/**
 * Middleware: checkBillingStatus
 * Enforces SaaS subscription level locks.
 * - If account_level is 'FREE_LEVEL', the account is LOCKED (HTTP 402).
 * - Admin role is exempted.
 */
async function checkBillingStatus(req, res, next) {
  try {
    const userId = req.user.userId;
    if (userId === 0) {
      // Global Admin Console has userId 0, bypass checks
      return next();
    }

    const users = await query('SELECT account_level, role FROM users WHERE id = ? LIMIT 1', [userId]);
    if (users.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }

    const user = users[0];
    if (user.role === 'admin') {
      return next();
    }

    if (user.account_level === 'FREE_LEVEL') {
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
