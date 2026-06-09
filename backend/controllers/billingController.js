const { query } = require('../db/connection');

/**
 * POST /api/v1/subscription/recharge
 * Adds recharge amount directly to the user's negative/positive wallet credits.
 * Enforces a minimum recharge requirement of ৳50.
 */
async function recharge(req, res) {
  try {
    const userId = req.user.userId;
    const { amount } = req.body;

    const parsedAmount = parseFloat(amount);
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      return res.status(400).json({ success: false, error: 'Invalid recharge amount' });
    }

    if (parsedAmount < 50.00) {
      return res.status(400).json({
        success: false,
        error: 'MINIMUM_LIMIT_ENFORCED',
        message: 'সর্বনিম্ন রিচার্জের পরিমাণ ৫০ টাকা।'
      });
    }

    // Add recharge amount directly to wallet_credits
    await query(
      'UPDATE users SET wallet_credits = wallet_credits + ? WHERE id = ?',
      [parsedAmount, userId]
    );

    // Retrieve updated credits
    const users = await query('SELECT wallet_credits FROM users WHERE id = ? LIMIT 1', [userId]);
    const updatedCredits = parseFloat(users[0].wallet_credits || '0.00');

    console.log(`[Recharge] User ${userId} successfully recharged ৳${parsedAmount}. New balance: ৳${updatedCredits}`);

    return res.json({
      success: true,
      message: 'রিচার্জ সফলভাবে সম্পন্ন হয়েছে।',
      wallet_credits: updatedCredits
    });
  } catch (error) {
    console.error('[Billing Controller] Recharge error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * POST /api/v1/subscription/fcm-token
 * Updates the user's FCM push token in the database.
 */
async function updateFcmToken(req, res) {
  try {
    const userId = req.user.userId;
    const { token } = req.body;

    await query('UPDATE users SET fcm_token = ? WHERE id = ?', [token || null, userId]);

    console.log(`[FCM-Token] Registered token for user ${userId}: ${token ? token.substring(0, 15) + '...' : 'CLEARED'}`);

    return res.json({
      success: true,
      message: 'FCM token updated successfully'
    });
  } catch (error) {
    console.error('[Billing Controller] updateFcmToken error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

module.exports = {
  recharge,
  updateFcmToken
};
