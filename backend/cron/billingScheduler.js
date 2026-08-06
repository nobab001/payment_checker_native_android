const cron = require('node-cron');
const { Prisma } = require('@prisma/client');
const prisma = require('../db/prisma');
const { runSmsRetentionCleanup } = require('./smsRetentionCleanup');
const {
  STATUS_SUSPENDED,
  ensureSubscriptionStatusSchema,
} = require('../services/subscriptionStatusService');

/**
 * Suspend every account whose paid/trial term has ended.
 *
 * expiry_date is deliberately preserved (previously nulled): the permission engine
 * treats a NULL expiry as "no expiry set" and would hand back full trial
 * entitlements forever, so nulling it turned expiry into an unlimited upgrade.
 *
 * is_trial is cleared here too — nothing else ever reset it, so expired trial
 * users kept matching the trial branch of the permission engine.
 */
async function runSubscriptionExpiryGuard() {
  await ensureSubscriptionStatusSchema();

  // Date-only comparison in SQL so DATE columns and server TZ cannot drift a day.
  // `< CURDATE()` keeps the final day of the term usable, matching checkBillingStatus.
  const expiredRows = await prisma.$queryRaw`
    SELECT id FROM users
    WHERE expiry_date IS NOT NULL
      AND DATE(expiry_date) < CURDATE()
      AND (
        is_paid = 1
        OR COALESCE(is_trial, 0) = 1
        OR COALESCE(subscription_status, 'active') <> ${STATUS_SUSPENDED}
      )
  `;
  const userIds = expiredRows.map((r) => Number(r.id));
  if (!userIds.length) {
    console.log('[Subscription Guard] ✅ No expired subscriptions found. All clear.');
    return 0;
  }

  await prisma.$executeRaw`
    UPDATE users
    SET is_paid = 0,
        is_trial = 0,
        active_plan_name = 'FREE_LEVEL',
        subscription_status = ${STATUS_SUSPENDED},
        perm_custom_sender = 0,
        perm_template = 0,
        perm_website = 0,
        perm_device = 0,
        perm_smart_popup = 0,
        perm_manual_transaction = 0,
        eff_max_devices = 0,
        eff_max_sites = 0
    WHERE id IN (${Prisma.join(userIds)})
  `;

  // Retire V3 subscription/addon rows so getUserSubscriptions() stops returning them.
  await prisma.$executeRaw`
    UPDATE user_subscriptions SET status = 'expired', updated_at = NOW()
    WHERE user_id IN (${Prisma.join(userIds)})
      AND status = 'active' AND DATE(expires_at) < CURDATE()
  `.catch(() => {});
  await prisma.$executeRaw`
    UPDATE user_subscription_addons SET status = 'expired', updated_at = NOW()
    WHERE user_id IN (${Prisma.join(userIds)})
      AND status = 'active' AND DATE(expires_at) < CURDATE()
  `.catch(() => {});

  // Drop cached entitlements so the suspension takes effect before the 5-min TTL.
  const { bustEntitlementCache } = require('../services/permissionEngineService');
  for (const id of userIds) {
    await bustEntitlementCache(id).catch(() => {});
    await prisma.$executeRaw`
      INSERT INTO subscription_audit_log (user_id, action, reason, meta_json)
      VALUES (${id}, 'auto_suspend_expiry', 'Subscription term ended', ${JSON.stringify({ by: 'billingScheduler' })})
    `.catch(() => {});
  }

  console.log(`[Subscription Guard] ✅ ${userIds.length} expired subscription(s) suspended.`);
  return userIds.length;
}

// =============================================================================
// Cron 1: Subscription Expiry Guard — প্রতিদিন রাত ১২:০১ মিনিটে রান হবে
// মেয়াদ শেষ → subscription_status='suspended', is_paid=0, is_trial=0,
// সব perm_* = 0। expiry_date অডিটের জন্য রেখে দেওয়া হয়।
// =============================================================================
cron.schedule('1 0 * * *', async () => {
  console.log('[Subscription Guard] Running midnight expiry check...');
  try {
    await runSubscriptionExpiryGuard();
    const { applyDueDeferredSubscriptions } = require('../services/subscriptionV3/fulfillmentService');
    const n = await applyDueDeferredSubscriptions();
    if (n > 0) console.log(`[Subscription Guard] ✅ Applied ${n} deferred downgrade(s).`);
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
// Cron 3: SMS Retention Cleanup — প্রতিদিন রাত ২:০০ (Asia/Dhaka via process TZ)
//  - sms_history: sold-out (is_used=1) এবং বয়স ≥ ৩০ দিন → ডিলিট
//  - custom_sms_archives: বয়স ≥ ৪৫ দিন → ডিলিট; ইউজারপ্রতি সর্বোচ্চ ১০০০ (পুরনো কেটে)
//  - ব্যাচ + কুলডাউন যাতে সার্ভারে চাপ না পড়ে
// =============================================================================
cron.schedule('0 2 * * *', async () => {
  console.log('[SMS Retention] Running daily cleanup at 2:00 AM...');
  try {
    await runSmsRetentionCleanup();
  } catch (err) {
    console.error('[SMS Retention] ❌ Cleanup job error:', err);
  }
});

console.log(
  '[Cron] ✅ Subscription Expiry Guard (12:01 AM), FCM Reminder (10:00 AM) & SMS Retention (2:00 AM) scheduled.'
);

module.exports = { runSubscriptionExpiryGuard };
