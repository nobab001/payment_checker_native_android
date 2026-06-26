const cron = require('node-cron');
const prisma = require('../db/prisma');

// =============================================================================
// Cron 1: Subscription Expiry Guard — প্রতিদিন রাত ১২:০১ মিনিটে রান হবে
// যাদের মেয়াদ শেষ → is_paid = 0, active_plan_name = 'FREE_LEVEL', expiry_date = NULL
// =============================================================================
cron.schedule('1 0 * * *', async () => {
  console.log('[Subscription Guard] Running midnight expiry check...');
  try {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const result = await prisma.users.updateMany({
      where: {
        is_paid: 1,
        expiry_date: {
          not: null,
          lte: today
        }
      },
      data: {
        is_paid: 0,
        active_plan_name: 'FREE_LEVEL',
        expiry_date: null
      }
    });

    const affected = result.count || 0;
    if (affected > 0) {
      console.log(`[Subscription Guard] ✅ ${affected} expired subscription(s) reset to FREE_LEVEL.`);
    } else {
      console.log('[Subscription Guard] ✅ No expired subscriptions found. All clear.');
    }
  } catch (err) {
    console.error('[Subscription Guard] ❌ Cron Expiry Error:', err);
  }
});

// =============================================================================
// Cron 2: FCM Subscription Reminder — প্রতিদিন সকাল ১০:০০ AM
// ≤ ৩০ দিন বাকি থাকা পেইড ইউজারদের FCM নোটিফিকেশন পাঠানো হবে
// =============================================================================
cron.schedule('0 10 * * *', async () => {
  console.log('[Subscription Reminder] Running 10 AM reminder check...');
  try {
    const expiringUsers = await prisma.$queryRaw`
      SELECT id, name, expiry_date, fcm_token,
             DATEDIFF(expiry_date, CURRENT_DATE()) AS days_left
      FROM users 
      WHERE is_paid = 1 
        AND expiry_date IS NOT NULL 
        AND DATEDIFF(expiry_date, CURRENT_DATE()) <= 30
        AND DATEDIFF(expiry_date, CURRENT_DATE()) > 0
        AND fcm_token IS NOT NULL AND fcm_token != ''
    `;

    if (expiringUsers.length === 0) {
      console.log('[Subscription Reminder] ✅ No users with ≤30 days remaining. All clear.');
      return;
    }

    for (const u of expiringUsers) {
      // TODO: Replace with real Firebase Admin SDK send call in production
      console.log(
        `[Mock FCM] → User ${u.id} (${u.name || 'Unknown'}) | Token: ${u.fcm_token.substring(0, 15)}... | ` +
        `Days Left: ${Number(u.days_left)} | Expiry: ${u.expiry_date} | ` +
        `Message: "আপনার সাবস্ক্রিপশনের মেয়াদ আগামী ${Number(u.days_left)} দিন পর শেষ হতে যাচ্ছে। সার্ভিস সচল রাখতে অনুগ্রহ করে রিনিউ করুন।"`
      );
    }

    console.log(`[Subscription Reminder] ✅ Dispatched reminder alerts to ${expiringUsers.length} user(s).`);
  } catch (err) {
    console.error('[Subscription Reminder] ❌ FCM Reminder Cron Error:', err);
  }
});

// =============================================================================
// Cron 3: Archive Retention Policy — প্রতিদিন রাত ২:০০ টায় রান হবে
// প্রতিটি ইউজারের শুধুমাত্র লেটেস্ট ২০০০টি কাস্টম আর্কাইভ রাখবে, পেছনেরগুলো ডিলিট করবে।
// =============================================================================
cron.schedule('0 2 * * *', async () => {
  console.log('[Archive Retention] Running daily cleanup at 2:00 AM...');
  try {
    const userGroups = await prisma.custom_sms_archives.groupBy({
      by: ['user_id']
    });

    let totalDeleted = 0;
    for (const group of userGroups) {
      const userId = group.user_id;
      // Get the 2000th record ID for this user (descending order)
      const cutoff = await prisma.custom_sms_archives.findMany({
        where: { user_id: userId },
        orderBy: { id: 'desc' },
        skip: 2000,
        take: 1,
        select: { id: true }
      });

      if (cutoff.length > 0) {
        const thresholdId = cutoff[0].id;
        // Delete all records older than or equal to the 2000th record ID
        const deleteResult = await prisma.custom_sms_archives.deleteMany({
          where: {
            user_id: userId,
            id: { lte: thresholdId }
          }
        });
        totalDeleted += deleteResult.count || 0;
      }
    }
    console.log(`[Archive Retention] ✅ Cleaned up old archives. Deleted ${totalDeleted} rows.`);
  } catch (err) {
    console.error('[Archive Retention] ❌ Cleanup job error:', err);
  }
});

console.log('[Cron] ✅ Subscription Expiry Guard (12:01 AM), FCM Reminder (10:00 AM) & Archive Retention (2:00 AM) scheduled.');

module.exports = {};
