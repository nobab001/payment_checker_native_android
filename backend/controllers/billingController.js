const prisma = require('../db/prisma');

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
    const plan = await prisma.subscription_plans.findFirst({
      where: { plan_name: planName }
    });
    
    if (!plan) {
      return res.status(404).json({ success: false, error: 'PLAN_NOT_FOUND', message: 'প্ল্যানটি খুঁজে পাওয়া যায়নি।' });
    }
    const durationDays = plan.duration_days || 365;

    // ২. ইউজারের বর্তমান সাবস্ক্রিপশন স্ট্যাটাস পড়া
    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { is_paid: true, active_plan_name: true, expiry_date: true, custom_sender_ends_at: true }
    });
    
    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    const isCustomSenderPlan = plan.plan_name.toLowerCase().includes('custom sender');

    if (isCustomSenderPlan) {
      let baseDate = new Date();
      if (user.custom_sender_ends_at) {
        const existingExpiry = new Date(user.custom_sender_ends_at);
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        if (existingExpiry > today) {
          baseDate = existingExpiry;
        }
      }

      baseDate.setDate(baseDate.getDate() + durationDays);
      const newCustomSenderExpiry = new Date(baseDate);

      await prisma.users.update({
        where: { id: userId },
        data: {
          has_custom_sender_addon: 1,
          custom_sender_ends_at: newCustomSenderExpiry
        }
      });

      const year = newCustomSenderExpiry.getFullYear();
      const month = String(newCustomSenderExpiry.getMonth() + 1).padStart(2, '0');
      const day = String(newCustomSenderExpiry.getDate()).padStart(2, '0');
      const formattedExpiry = `${year}-${month}-${day}`;

      console.log(`[Subscription] ✅ User ${userId} purchased Custom Sender Add-on "${plan.plan_name}". Expiry stacked to: ${formattedExpiry} (+${durationDays} days)`);

      return res.json({
        success: true,
        message: `${plan.plan_name} সফলভাবে সক্রিয় করা হয়েছে। মেয়াদ: ${formattedExpiry}`,
        has_custom_sender_addon: 1,
        custom_sender_ends_at: formattedExpiry
      });
    }

    // ৩. Date Stacking Logic
    let baseDate = new Date(); // ডিফল্ট: আজ থেকে হিসাব শুরু

    // কাস্টমার যদি অলরেডি পেইড এবং মেয়াদ বাকি থাকে → আগের expiry_date থেকে স্ট্যাক
    if (user.is_paid && user.expiry_date) {
      const existingExpiry = new Date(user.expiry_date);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      if (existingExpiry > today) {
        baseDate = existingExpiry;
      }
    }

    baseDate.setDate(baseDate.getDate() + durationDays);
    const newExpiryDate = new Date(baseDate);

    // ৪. ডাটাবেজ আপডেট — is_paid=1, active_plan_name=purchased plan, expiry_date=stacked date
    await prisma.users.update({
      where: { id: userId },
      data: {
        is_paid: 1,
        active_plan_name: plan.plan_name,
        expiry_date: newExpiryDate
      }
    });

    const year = newExpiryDate.getFullYear();
    const month = String(newExpiryDate.getMonth() + 1).padStart(2, '0');
    const day = String(newExpiryDate.getDate()).padStart(2, '0');
    const formattedExpiry = `${year}-${month}-${day}`;

    console.log(`[Subscription] ✅ User ${userId} purchased "${plan.plan_name}". Expiry stacked to: ${formattedExpiry} (+${durationDays} days)`);

    return res.json({
      success: true,
      message: `${plan.plan_name} প্যাকেজটি সফলভাবে সক্রিয় করা হয়েছে। মেয়াদ: ${formattedExpiry}`,
      is_paid: 1,
      active_plan_name: plan.plan_name,
      expiry_date: formattedExpiry
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

    await prisma.users.update({
      where: { id: userId },
      data: { fcm_token: token || null }
    });

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
    const plans = await prisma.subscription_plans.findMany({
      orderBy: { price: 'asc' }
    });
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
    const { id, plan_name, price, max_sites, max_devices, duration_days, is_custom_sender_allowed } = req.body;

    if (!plan_name || price === undefined || max_sites === undefined || max_devices === undefined) {
      return res.status(400).json({ success: false, error: 'Missing required plan fields.' });
    }

    const data = {
      plan_name,
      price,
      max_sites,
      max_devices,
      is_custom_sender_allowed: is_custom_sender_allowed ? 1 : 0,
      duration_days: duration_days || 365
    };

    if (id) {
      await prisma.subscription_plans.update({
        where: { id: parseInt(id, 10) },
        data
      });
    } else {
      const existingName = await prisma.subscription_plans.findUnique({
        where: { plan_name }
      });
      if (existingName) {
        return res.status(400).json({ success: false, error: 'PLAN_NAME_EXISTS', message: 'এই নামের একটি প্যাকেজ ইতিমধ্যেই রয়েছে।' });
      }
      await prisma.subscription_plans.create({
        data
      });
    }

    return res.json({ success: true, message: 'প্ল্যান সফলভাবে তৈরি/আপডেট করা হয়েছে।' });
  } catch (error) {
    console.error('[Billing Controller] createPlan error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * DELETE /api/admin/plans/:id
 * Deletes a subscription plan (Admin Only).
 */
async function deletePlan(req, res) {
  try {
    const { id } = req.params;
    if (!id) {
      return res.status(400).json({ success: false, error: 'Missing plan ID.' });
    }
    await prisma.subscription_plans.delete({
      where: { id: parseInt(id) }
    });
    return res.json({ success: true, message: 'প্ল্যান সফলভাবে ডিলিট করা হয়েছে।' });
  } catch (error) {
    console.error('[Billing Controller] deletePlan error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function purchaseCustomSenderAddon(req, res) {
  try {
    const userId = req.user.userId;

    const plan = await prisma.subscription_plans.findFirst({
      where: {
        plan_name: {
          contains: 'custom sender'
        }
      }
    });

    const durationDays = plan ? (plan.duration_days || 365) : 365;

    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { custom_sender_ends_at: true }
    });

    let baseDate = new Date();
    if (user && user.custom_sender_ends_at) {
      const existingExpiry = new Date(user.custom_sender_ends_at);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      if (existingExpiry > today) {
        baseDate = existingExpiry;
      }
    }

    baseDate.setDate(baseDate.getDate() + durationDays);
    const newCustomSenderExpiry = new Date(baseDate);

    await prisma.users.update({
      where: { id: userId },
      data: {
        has_custom_sender_addon: 1,
        custom_sender_ends_at: newCustomSenderExpiry
      }
    });

    console.log(`[Subscription] ✅ User ${userId} purchased Custom Sender Add-on (+${durationDays} days).`);

    const year = newCustomSenderExpiry.getFullYear();
    const month = String(newCustomSenderExpiry.getMonth() + 1).padStart(2, '0');
    const day = String(newCustomSenderExpiry.getDate()).padStart(2, '0');
    const formattedExpiry = `${year}-${month}-${day}`;

    return res.json({
      success: true,
      message: `কাস্টম সেন্ডার অ্যাড-অন সফলভাবে সক্রিয় করা হয়েছে। মেয়াদ: ${formattedExpiry}`,
      has_custom_sender_addon: 1,
      custom_sender_ends_at: formattedExpiry
    });
  } catch (error) {
    console.error('[Billing Controller] purchaseCustomSenderAddon error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

module.exports = {
  updateFcmToken,
  purchaseSubscription,
  listPlans,
  createPlan,
  deletePlan,
  purchaseCustomSenderAddon
};
