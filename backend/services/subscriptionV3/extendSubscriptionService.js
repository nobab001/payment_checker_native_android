/**
 * Admin: unified subscription extension (v3.1).
 * Extends trial OR all active v3 categories via shared expiry — never users-only patch.
 */
const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');
const {
  getUserSubscriptions,
  getSharedExpiry,
  formatYmd,
  addDays,
  dateOnly,
  parseYmd,
  syncAllCategoriesToExpiry,
} = require('./sharedExpiryService');
const { mirrorUserBilling } = require('./fulfillmentService');
const { logAudit } = require('./auditService');
const { logExtension, normalizeReason } = require('./extensionHistoryService');
const { isUserOnTrial } = require('./trialFlagService');
const { ensureSubscriptionFresh, bustEntitlementCache } = require('../permissionEngineService');

const ALLOWED_DAYS = [1, 3, 7, 15, 30];

async function extendSubscription(userId, days, { adminId, ipAddress, reason } = {}) {
  if (!ALLOWED_DAYS.includes(Number(days))) {
    return {
      error: 'INVALID_DAYS',
      message: 'শুধু 1, 3, 7, 15 বা 30 দিন নির্বাচন করা যাবে।',
    };
  }

  const nDays = Number(days);
  const safeReason = normalizeReason(reason);
  await ensureSubscriptionV3Schema();

  const user = await prisma.users.findUnique({
    where: { id: Number(userId) },
    select: {
      id: true,
      active_plan_name: true,
      expiry_date: true,
      has_custom_sender_addon: true,
      custom_sender_ends_at: true,
    },
  });
  if (!user) {
    return { error: 'USER_NOT_FOUND', message: 'ইউজার পাওয়া যায়নি।' };
  }

  const subs = await getUserSubscriptions(userId);
  const isTrial = subs.length === 0 && (await isUserOnTrial(userId));
  const today = dateOnly();
  let newExpiry;
  let oldExpiryYmd = null;
  let mode;

  if (subs.length > 0) {
    mode = 'v3_shared';
    const shared = await getSharedExpiry(userId);
    oldExpiryYmd = shared ? formatYmd(shared) : null;
    const stackFrom = shared && shared > today ? shared : today;
    newExpiry = addDays(stackFrom, nDays);
    const newExpiryYmd = formatYmd(newExpiry);
    await syncAllCategoriesToExpiry(userId, newExpiryYmd);
    await mirrorUserBilling(userId);
  } else if (isTrial) {
    mode = 'trial';
    const trialExp = parseYmd(user.expiry_date) || today;
    oldExpiryYmd = formatYmd(trialExp);
    const stackFrom = trialExp > today ? trialExp : today;
    newExpiry = addDays(stackFrom, nDays);
    const updateData = {
      expiry_date: newExpiry,
      is_paid: 1,
    };
    if (Number(user.has_custom_sender_addon) === 1) {
      const csExp = parseYmd(user.custom_sender_ends_at) || trialExp;
      const csStack = csExp > today ? csExp : today;
      updateData.custom_sender_ends_at = addDays(csStack, nDays);
    }
    await prisma.users.update({
      where: { id: Number(userId) },
      data: updateData,
    });
    await prisma.$executeRaw`
      UPDATE users SET is_trial = 1 WHERE id = ${Number(userId)}
    `;
  } else {
    return {
      error: 'NO_ACTIVE_SUBSCRIPTION',
      message: 'এক্সটেন্ড করার মতো সক্রিয় ট্রায়াল বা সাবস্ক্রিপশন নেই।',
    };
  }

  await bustEntitlementCache(userId);
  await ensureSubscriptionFresh(userId);

  const history = await logExtension({
    userId,
    daysAdded: nDays,
    reason: safeReason,
    adminId,
    oldExpiry: oldExpiryYmd,
    newExpiry: formatYmd(newExpiry),
    mode,
  });

  await logAudit({
    adminId,
    userId: Number(userId),
    action: 'admin_extend_subscription',
    reason: safeReason,
    ipAddress,
    meta: { days: nDays, mode, old_expiry: oldExpiryYmd, new_expiry: formatYmd(newExpiry) },
  });

  return {
    success: true,
    extended_days: nDays,
    new_expiry: formatYmd(newExpiry),
    old_expiry: oldExpiryYmd,
    reason: history.reason,
    admin_name: history.admin_name,
    mode,
    message: `সাবস্ক্রিপশন ${nDays} দিন বাড়ানো হয়েছে। নতুন মেয়াদ: ${formatYmd(newExpiry)}`,
  };
}

module.exports = { extendSubscription, ALLOWED_DAYS };
