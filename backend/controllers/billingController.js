const { query } = require('../db/connection');

/**
 * POST /api/v1/subscription/purchase
 * 
 * Boolean Paid-Gate & Dynamic Plan Name Architecture:
 * ─────────────────────────────────────────────────────
 * ১. subscription_plans টেবিল থেকে plan_name অনুযায়ী প্ল্যান ডেটা ফেচ করে
 * ২. Date Stacking Logic:
 *    - ইউজার যদি ফ্রি (is_paid=0) বা মেয়াদ উত্তীর্ণ → আজ থেকে duration_days যোগ
 *    - ইউজার যদি পেইড (is_paid=1) এবং মেয়াদ বাকি → আগের expiry_date থেকে duration_days যোগ
 * ৩. users টেবিলে is_paid=1, active_plan_name=plan.plan_name, expiry_date=নতুন তারিখ সেট করে
 */
async function purchaseSubscription(req, res) {
  try {
    const userId = req.user.userId;
    const planName = req.body.planName || req.body.plan_name;

    if (!planName) {
      return res.status(400).json({ success: false, error: 'Missing planName field.' });
    }

    // ১. প্ল্যান ফেচ
    const plans = await query('SELECT * FROM subscription_plans WHERE plan_name = ? LIMIT 1', [planName]);
    if (plans.length === 0) {
      return res.status(404).json({ success: false, error: 'PLAN_NOT_FOUND', message: 'প্ল্যানটি খুঁজে পাওয়া যায়নি।' });
    }
    const plan = plans[0];
    const durationDays = plan.duration_days || 365;

    // ২. ইউজারের বর্তমান সাবস্ক্রিপশন স্ট্যাটাস পড়া
    const users = await query('SELECT is_paid, active_plan_name, expiry_date FROM users WHERE id = ? LIMIT 1', [userId]);
    if (users.length === 0) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }
    const user = users[0];

    // ৩. Date Stacking Logic
    let baseDate = new Date(); // ডিফল্ট: আজ থেকে হিসাব শুরু

    // কাস্টমার যদি অলরেডি পেইড এবং মেয়াদ বাকি থাকে → আগের expiry_date থেকে স্ট্যাক
    if (user.is_paid === 1 && user.expiry_date) {
      const existingExpiry = new Date(user.expiry_date);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      if (existingExpiry > today) {
        baseDate = existingExpiry;
      }
    }

    baseDate.setDate(baseDate.getDate() + durationDays);
    const year = baseDate.getFullYear();
    const month = String(baseDate.getMonth() + 1).padStart(2, '0');
    const day = String(baseDate.getDate()).padStart(2, '0');
    const newExpiryDate = `${year}-${month}-${day}`; // YYYY-MM-DD in local time

    // ৪. ডাটাবেজ আপডেট — is_paid=1, active_plan_name=purchased plan, expiry_date=stacked date
    await query(
      "UPDATE users SET is_paid = 1, active_plan_name = ?, expiry_date = ? WHERE id = ?",
      [plan.plan_name, newExpiryDate, userId]
    );

    console.log(`[Subscription] ✅ User ${userId} purchased "${plan.plan_name}". Expiry stacked to: ${newExpiryDate} (+${durationDays} days)`);

    return res.json({
      success: true,
      message: `${plan.plan_name} প্যাকেজটি সফলভাবে সক্রিয় করা হয়েছে। মেয়াদ: ${newExpiryDate}`,
      is_paid: true,
      active_plan_name: plan.plan_name,
      expiry_date: newExpiryDate
    });
  } catch (error) {
    console.error('[Billing Controller] purchaseSubscription error:', error);
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

/**
 * GET /api/v1/plans
 * Lists all available subscription plans.
 */
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

/**
 * POST /api/admin/plans/create
 * Creates or updates a subscription plan (Admin Only).
 */
async function createPlan(req, res) {
  try {
    const { plan_name, price, max_sites, max_devices, duration_days } = req.body;

    if (!plan_name || price === undefined || max_sites === undefined || max_devices === undefined) {
      return res.status(400).json({ success: false, error: 'Missing required plan fields.' });
    }

    const existing = await query('SELECT id FROM subscription_plans WHERE plan_name = ? LIMIT 1', [plan_name]);
    if (existing.length > 0) {
      await query(
        'UPDATE subscription_plans SET price = ?, max_sites = ?, max_devices = ?, duration_days = ? WHERE plan_name = ?',
        [price, max_sites, max_devices, duration_days || 365, plan_name]
      );
    } else {
      await query(
        'INSERT INTO subscription_plans (plan_name, price, max_sites, max_devices, duration_days) VALUES (?, ?, ?, ?, ?)',
        [plan_name, price, max_sites, max_devices, duration_days || 365]
      );
    }

    return res.json({ success: true, message: 'প্ল্যান সফলভাবে তৈরি/আপডেট করা হয়েছে।' });
  } catch (error) {
    console.error('[Billing Controller] createPlan error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

module.exports = {
  updateFcmToken,
  purchaseSubscription,
  listPlans,
  createPlan
};
