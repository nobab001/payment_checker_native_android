const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');
const { SUBSCRIPTION_VERSION } = require('./constants');
const { getPackageBySku } = require('./catalogService');
const {
  formatYmd,
  dateOnly,
  parseYmd,
  getSharedExpiry,
} = require('./sharedExpiryService');
const { logAudit } = require('./auditService');
const { nextInvoiceNo } = require('./invoiceService');
const { bustEntitlementCache } = require('../permissionEngineService');

async function cancelPendingDeferred(userId, category) {
  await prisma.$executeRaw`
    UPDATE subscription_deferred
    SET status = 'cancelled'
    WHERE user_id = ${Number(userId)}
      AND category = ${category}
      AND status = 'pending'
  `;
}

async function upsertUserSubscription(userId, quote, pkg, payable) {
  await ensureSubscriptionV3Schema();
  const starts = quote.deferred_starts_at
    ? quote.deferred_starts_at
    : formatYmd(dateOnly());
  const expires = quote.final_expiry;
  const amountPaid = Number(payable);
  const listPrice = Number(quote.list_price || 0);
  const paidDays = Number(quote.duration_days || 0);

  await prisma.$executeRaw`
    INSERT INTO user_subscriptions (
      user_id, category, package_sku, package_full_name, website_limit_internal,
      device_limit_internal, duration_key, starts_at, expires_at, status, subscription_version,
      amount_paid, paid_duration_days, list_price_paid
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
      ${SUBSCRIPTION_VERSION},
      ${amountPaid},
      ${paidDays},
      ${listPrice}
    )
    ON DUPLICATE KEY UPDATE
      package_sku = VALUES(package_sku),
      package_full_name = VALUES(package_full_name),
      website_limit_internal = VALUES(website_limit_internal),
      device_limit_internal = VALUES(device_limit_internal),
      duration_key = VALUES(duration_key),
      starts_at = VALUES(starts_at),
      expires_at = VALUES(expires_at),
      status = 'active',
      subscription_version = VALUES(subscription_version),
      amount_paid = VALUES(amount_paid),
      paid_duration_days = VALUES(paid_duration_days),
      list_price_paid = VALUES(list_price_paid),
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
  const credit = Number(quote.credit_applied || 0);
  const startsAt = quote.deferred_starts_at || today;
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
      ${credit},
      ${payable},
      ${quote.duration_days},
      ${startsAt},
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

async function insertDeferred(userId, quote, pkg, payable, invoiceNo) {
  await cancelPendingDeferred(userId, quote.category);
  await prisma.$executeRaw`
    INSERT INTO subscription_deferred (
      user_id, category, package_sku, package_full_name, duration_key, duration_days,
      website_limit_internal, device_limit_internal, starts_at, expires_at,
      amount_paid, list_price, addons_json, status, purchase_invoice
    ) VALUES (
      ${Number(userId)},
      ${quote.category},
      ${quote.package_sku},
      ${quote.package_full_name},
      ${quote.duration_key},
      ${quote.duration_days},
      ${pkg.website_limit_internal},
      ${pkg.device_limit_internal},
      ${quote.deferred_starts_at},
      ${quote.final_expiry},
      ${Number(payable)},
      ${Number(quote.list_price || 0)},
      ${JSON.stringify(quote.addons || [])},
      'pending',
      ${invoiceNo}
    )
  `;
}

async function mirrorUserBilling(userId) {
  const subs = await prisma.$queryRaw`
    SELECT category, package_full_name, expires_at FROM user_subscriptions
    WHERE user_id = ${Number(userId)} AND status = 'active'
    ORDER BY expires_at DESC LIMIT 1
  `;
  const shared = await getSharedExpiry(userId);
  const top = subs[0];
  const planName = top?.package_full_name || 'FREE_LEVEL';
  const isPaid = shared ? 1 : 0;
  // Raw SQL — Prisma client on VPS may lag behind ALTER-added columns (e.g. subscription_version).
  if (shared) {
    await prisma.$executeRaw`
      UPDATE users
      SET is_paid = ${isPaid},
          active_plan_name = ${planName},
          expiry_date = ${shared},
          subscription_version = ${SUBSCRIPTION_VERSION},
          is_trial = 0
      WHERE id = ${Number(userId)}
    `;
  } else {
    await prisma.$executeRaw`
      UPDATE users
      SET is_paid = 0,
          active_plan_name = ${planName},
          subscription_version = ${SUBSCRIPTION_VERSION},
          is_trial = 0
      WHERE id = ${Number(userId)}
    `;
  }
}

/**
 * Activate deferred downgrades whose start date has arrived.
 * Call after midnight expiry guard so old plan is expired first.
 */
async function applyDueDeferredSubscriptions() {
  await ensureSubscriptionV3Schema();
  const today = formatYmd(dateOnly());
  const rows = await prisma.$queryRaw`
    SELECT * FROM subscription_deferred
    WHERE status = 'pending' AND DATE(starts_at) <= ${today}
    ORDER BY id ASC
  `;
  let applied = 0;
  for (const row of rows) {
    try {
      const userId = Number(row.user_id);
      await prisma.$executeRaw`
        INSERT INTO user_subscriptions (
          user_id, category, package_sku, package_full_name, website_limit_internal,
          device_limit_internal, duration_key, starts_at, expires_at, status, subscription_version,
          amount_paid, paid_duration_days, list_price_paid
        ) VALUES (
          ${userId},
          ${row.category},
          ${row.package_sku},
          ${row.package_full_name},
          ${Number(row.website_limit_internal)},
          ${Number(row.device_limit_internal)},
          ${row.duration_key},
          ${formatYmd(row.starts_at)},
          ${formatYmd(row.expires_at)},
          'active',
          ${SUBSCRIPTION_VERSION},
          ${Number(row.amount_paid)},
          ${Number(row.duration_days)},
          ${Number(row.list_price)}
        )
        ON DUPLICATE KEY UPDATE
          package_sku = VALUES(package_sku),
          package_full_name = VALUES(package_full_name),
          website_limit_internal = VALUES(website_limit_internal),
          device_limit_internal = VALUES(device_limit_internal),
          duration_key = VALUES(duration_key),
          starts_at = VALUES(starts_at),
          expires_at = VALUES(expires_at),
          status = 'active',
          amount_paid = VALUES(amount_paid),
          paid_duration_days = VALUES(paid_duration_days),
          list_price_paid = VALUES(list_price_paid),
          updated_at = NOW()
      `;

      let addons = [];
      try {
        addons = typeof row.addons_json === 'string'
          ? JSON.parse(row.addons_json || '[]')
          : (row.addons_json || []);
      } catch (_) {
        addons = [];
      }
      await syncAddons(userId, addons, formatYmd(row.expires_at));

      await prisma.$executeRaw`
        UPDATE subscription_deferred
        SET status = 'applied', applied_at = NOW()
        WHERE id = ${Number(row.id)}
      `;

      const { reactivateUser } = require('../subscriptionStatusService');
      await reactivateUser(userId);
      await mirrorUserBilling(userId);
      await bustEntitlementCache(userId);
      try {
        const { ensureSubscriptionFresh } = require('../permissionEngineService');
        await ensureSubscriptionFresh(userId);
      } catch (_) { /* non-fatal */ }
      applied += 1;
    } catch (e) {
      console.error('[V3] applyDueDeferredSubscriptions failed for', row.id, e.message);
    }
  }
  return applied;
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
      SELECT id, invoice_no FROM subscription_purchases WHERE transaction_id = ${transactionId} LIMIT 1
    `;
    if (dup.length) {
      // Purchase row may exist from a partial prior fulfill — ensure live subscription is present.
      const pkg = await getPackageBySku(quote.package_sku);
      if (pkg && !(quote.purchase_type === 'downgrade' && quote.deferred)) {
        const live = await prisma.$queryRaw`
          SELECT id FROM user_subscriptions
          WHERE user_id = ${Number(userId)}
            AND category = ${quote.category}
            AND status = 'active'
            AND package_sku = ${quote.package_sku}
          LIMIT 1
        `;
        if (!live.length) {
          await upsertUserSubscription(userId, quote, pkg, Number(quote.payable_amount));
          await syncAddons(userId, quote.addons || [], quote.final_expiry);
          await mirrorUserBilling(userId);
          const { reactivateUser } = require('../subscriptionStatusService');
          await reactivateUser(userId);
          await bustEntitlementCache(userId);
          try {
            const { ensureSubscriptionFresh } = require('../permissionEngineService');
            await ensureSubscriptionFresh(userId);
          } catch (_) { /* non-fatal */ }
          return { success: true, invoice_no: dup[0].invoice_no, repaired: true };
        }
      }
      return { already: true, invoice_no: dup[0].invoice_no };
    }
  }

  const pkg = await getPackageBySku(quote.package_sku);
  if (!pkg) return { error: 'PLAN_NOT_FOUND' };

  const payable = Number(quote.payable_amount);

  // Any non-deferred purchase in this category cancels a pending downgrade.
  if (quote.purchase_type !== 'downgrade') {
    await cancelPendingDeferred(userId, quote.category);
  }

  const invoiceNo = await insertPurchaseHistory({
    userId,
    quote,
    payable,
    transactionId,
    quoteToken,
  });

  if (quote.purchase_type === 'downgrade' && quote.deferred && quote.deferred_starts_at) {
    await insertDeferred(userId, quote, pkg, payable, invoiceNo);
  } else {
    await upsertUserSubscription(userId, quote, pkg, payable);
    // Only sync addons that were purchased in this quote (co-term to final_expiry).
    // Do not free-extend other addons.
    await syncAddons(userId, quote.addons || [], quote.final_expiry);
  }

  await mirrorUserBilling(userId);
  const { reactivateUser } = require('../subscriptionStatusService');
  await reactivateUser(userId);
  await bustEntitlementCache(userId);
  // Refresh perm_* columns + revoke excess ALL/templates/websites for the new plan.
  try {
    const { ensureSubscriptionFresh } = require('../permissionEngineService');
    await ensureSubscriptionFresh(userId);
  } catch (e) {
    console.warn('[V3] ensureSubscriptionFresh after fulfill:', e.message);
  }

  await logAudit({
    userId,
    action: 'purchase_fulfill',
    oldPackage: quote.old_package_sku || null,
    newPackage: quote.package_full_name,
    reason: quote.purchase_type,
    ipAddress,
    meta: {
      invoice_no: invoiceNo,
      transaction_id: transactionId,
      package_sku: quote.package_sku,
      credit_applied: quote.credit_applied || 0,
      amount_paid: payable,
      deferred: !!quote.deferred,
    },
  });

  return { success: true, invoice_no: invoiceNo, deferred: !!quote.deferred };
}

module.exports = {
  fulfillSubscription,
  mirrorUserBilling,
  applyDueDeferredSubscriptions,
  cancelPendingDeferred,
};
