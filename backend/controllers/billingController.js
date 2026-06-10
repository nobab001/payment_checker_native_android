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

async function purchaseSubscription(req, res) {
  try {
    const userId = req.user.userId;
    const { planName } = req.body;

    if (!planName) {
      return res.status(400).json({ success: false, error: 'Missing planName field.' });
    }

    const plans = await query('SELECT * FROM subscription_plans WHERE plan_name = ? LIMIT 1', [planName]);
    if (plans.length === 0) {
      return res.status(404).json({ success: false, error: 'PLAN_NOT_FOUND', message: 'প্ল্যানটি খুঁজে পাওয়া যায়নি।' });
    }
    const plan = plans[0];

    const users = await query('SELECT wallet_credits FROM users WHERE id = ? LIMIT 1', [userId]);
    const currentCredits = users.length > 0 ? parseFloat(users[0].wallet_credits || '0') : 0;

    if (currentCredits < parseFloat(plan.price)) {
      return res.status(400).json({
        success: false,
        error: 'INSUFFICIENT_CREDITS',
        message: `দুঃখিত, আপনার পর্যাপ্ত ব্যালেন্স নেই। প্ল্যানটির মূল্য ৳${plan.price}। অনুগ্রহ করে প্রথমে রিচার্জ করুন।`
      });
    }

    // Deduct price and add credits_given, update account_level
    const finalCredits = Math.round(currentCredits - parseFloat(plan.price) + (plan.credits_given || 365));
    await query(
      "UPDATE users SET account_level = ?, wallet_credits = ? WHERE id = ?",
      [plan.plan_name, finalCredits, userId]
    );

    console.log(`[Subscription] User ${userId} purchased plan ${plan.plan_name}. New credits: ${finalCredits}`);

    return res.json({
      success: true,
      message: `${plan.plan_name} প্যাকেজটি সফলভাবে সক্রিয় করা হয়েছে।`,
      account_level: plan.plan_name,
      wallet_credits: finalCredits
    });
  } catch (error) {
    console.error('[Billing Controller] purchaseSubscription error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function listPlans(req, res) {
  try {
    const plans = await query('SELECT * FROM subscription_plans ORDER BY price ASC');
    return res.json({
      success: true,
      plans
    });
  } catch (error) {
    console.error('[Billing Controller] listPlans error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function createPlan(req, res) {
  try {
    const { plan_name, price, max_sites, max_devices, credits_given } = req.body;

    if (!plan_name || price === undefined || max_sites === undefined || max_devices === undefined) {
      return res.status(400).json({ success: false, error: 'Missing required plan fields.' });
    }

    const existing = await query('SELECT id FROM subscription_plans WHERE plan_name = ? LIMIT 1', [plan_name]);
    if (existing.length > 0) {
      await query(
        'UPDATE subscription_plans SET price = ?, max_sites = ?, max_devices = ?, credits_given = ? WHERE plan_name = ?',
        [price, max_sites, max_devices, credits_given || 365, plan_name]
      );
    } else {
      await query(
        'INSERT INTO subscription_plans (plan_name, price, max_sites, max_devices, credits_given) VALUES (?, ?, ?, ?, ?)',
        [plan_name, price, max_sites, max_devices, credits_given || 365]
      );
    }

    return res.json({ success: true, message: 'প্ল্যান সফলভাবে তৈরি/আপডেট করা হয়েছে।' });
  } catch (error) {
    console.error('[Billing Controller] createPlan error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

module.exports = {
  recharge,
  updateFcmToken,
  purchaseSubscription,
  listPlans,
  createPlan
};
