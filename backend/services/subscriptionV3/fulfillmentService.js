const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');
const { SUBSCRIPTION_VERSION } = require('./constants');
const { getPackageBySku, durationDays } = require('./catalogService');
const {
  formatYmd,
  addDays,
  dateOnly,
  syncAllCategoriesToExpiry,
  getSharedExpiry,
} = require('./sharedExpiryService');
const { logAudit } = require('./auditService');
const { nextInvoiceNo } = require('./invoiceService');
const { bustEntitlementCache } = require('../permissionEngineService');

async function upsertUserSubscription(userId, quote, pkg) {
  await ensureSubscriptionV3Schema();
  const starts = formatYmd(dateOnly());
  const expires = quote.final_expiry;
  await prisma.$executeRaw`
    INSERT INTO user_subscriptions (
      user_id, category, package_sku, package_full_name, website_limit_internal,
      device_limit_internal, duration_key, starts_at, expires_at, status, subscription_version
    ) VALUES (
      ${Number(userId)},
      ${quote.category},
      ${quote.package_sku},
      ${quote.package_full_name},
      ${pkg.website_limit_internal},
      ${pkg.device_limit_internal},
      ${quote.duration_key},
      ${starts},
      ${expires},
      'active',
      ${SUBSCRIPTION_VERSION}
    )
    ON DUPLICATE KEY UPDATE
      package_sku = VALUES(package_sku),
      package_full_name = VALUES(package_full_name),
      website_limit_internal = VALUES(website_limit_internal),
      device_limit_internal = VALUES(device_limit_internal),
      duration_key = VALUES(duration_key),
      expires_at = VALUES(expires_at),
      status = 'active',
      subscription_version = VALUES(subscription_version),
      updated_at = NOW()
  `;
}

async function syncAddons(userId, addonKeys, expiresYmd) {
  if (!addonKeys?.length) return;
  for (const key of addonKeys) {
    await prisma.$executeRaw`
      INSERT INTO user_subscription_addons (user_id, addon_key, expires_at, status)
      VALUES (${Number(userId)}, ${key}, ${expiresYmd}, 'active')
      ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at), status = 'active', updated_at = NOW()
    `;
  }
}

async function insertPurchaseHistory({
  userId,
  quote,
  payable,
  transactionId,
  quoteToken,
}) {
  await ensureSubscriptionV3Schema();
  const invoiceNo = await nextInvoiceNo();
  const today = formatYmd(dateOnly());
  await prisma.$executeRaw`
    INSERT INTO subscription_purchases (
      user_id, plan_name, purchase_type, list_price, credit_applied, amount_paid,
      duration_days, started_at, ends_at, is_closed,
      invoice_no, package_full_name, package_sku, category, duration_key,
      transaction_id, quote_token
    ) VALUES (
      ${Number(userId)},
      ${quote.package_full_name},
      ${quote.purchase_type},
      ${quote.list_price},
      0,
      ${payable},
      ${quote.duration_days},
      ${today},
      ${quote.final_expiry},
      0,
      ${invoiceNo},
      ${quote.package_full_name},
      ${quote.package_sku},
      ${quote.category},
      ${quote.duration_key},
      ${transactionId || null},
      ${quoteToken || null}
    )
  `;
  return invoiceNo;
}

async function mirrorUserBilling(userId) {
  const subs = await prisma.$queryRaw`
    SELECT category, package_full_name, expires_at FROM user_subscriptions
    WHERE user_id = ${Number(userId)} AND status = 'active'
    ORDER BY expires_at DESC LIMIT 1
  `;
  const shared = await getSharedExpiry(userId);
  const top = subs[0];
  await prisma.users.update({
    where: { id: Number(userId) },
    data: {
      is_paid: shared ? 1 : 0,
      active_plan_name: top?.package_full_name || 'FREE_LEVEL',
      expiry_date: shared ? new Date(shared) : null,
      subscription_version: SUBSCRIPTION_VERSION,
    },
  });
  await prisma.$executeRaw`
    UPDATE users SET is_trial = 0 WHERE id = ${Number(userId)}
  `;
}

async function fulfillSubscription({
  userId,
  quote,
  transactionId,
  quoteToken,
  ipAddress,
}) {
  await ensureSubscriptionV3Schema();

  if (transactionId) {
    const dup = await prisma.$queryRaw`
      SELECT id FROM subscription_purchases WHERE transaction_id = ${transactionId} LIMIT 1
    `;
    if (dup.length) {
      return { already: true, invoice_no: dup[0].invoice_no };
    }
  }

  const pkg = await getPackageBySku(quote.package_sku);
  if (!pkg) return { error: 'PLAN_NOT_FOUND' };

  await upsertUserSubscription(userId, quote, pkg);
  await syncAllCategoriesToExpiry(userId, quote.final_expiry);
  await syncAddons(userId, quote.addons || [], quote.final_expiry);

  const invoiceNo = await insertPurchaseHistory({
    userId,
    quote,
    payable: quote.payable_amount,
    transactionId,
    quoteToken,
  });

  await mirrorUserBilling(userId);
  const { reactivateUser } = require('../subscriptionStatusService');
  await reactivateUser(userId);
  await bustEntitlementCache(userId);

  await logAudit({
    userId,
    action: 'purchase_fulfill',
    newPackage: quote.package_full_name,
    reason: quote.purchase_type,
    ipAddress,
    meta: { invoice_no: invoiceNo, transaction_id: transactionId },
  });

  return { success: true, invoice_no: invoiceNo };
}

module.exports = { fulfillSubscription, mirrorUserBilling };
