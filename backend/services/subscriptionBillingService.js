const prisma = require('../db/prisma');

let purchasesTableReady = false;

function todayDateOnly() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

function dateOnly(val) {
  const d = new Date(val);
  d.setHours(0, 0, 0, 0);
  return d;
}

function daysBetween(start, end) {
  const ms = dateOnly(end).getTime() - dateOnly(start).getTime();
  return Math.max(0, Math.round(ms / (1000 * 60 * 60 * 24)));
}

function addDays(base, days) {
  const d = new Date(base);
  d.setDate(d.getDate() + days);
  return d;
}

function roundMoney(n) {
  return Math.round(Number(n) * 100) / 100;
}

async function ensureSubscriptionPurchasesTable() {
  if (purchasesTableReady) return;
  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS subscription_purchases (
      id INT NOT NULL AUTO_INCREMENT,
      user_id INT NOT NULL,
      plan_name VARCHAR(100) NOT NULL,
      plan_id INT NULL,
      purchase_type VARCHAR(16) NOT NULL DEFAULT 'new',
      list_price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
      credit_applied DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
      amount_paid DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
      duration_days INT NOT NULL DEFAULT 365,
      started_at DATE NOT NULL,
      ends_at DATE NOT NULL,
      replaced_purchase_id INT NULL,
      is_closed TINYINT NOT NULL DEFAULT 0,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      KEY idx_sub_purch_user (user_id),
      KEY idx_sub_purch_ends (user_id, ends_at, is_closed)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);
  purchasesTableReady = true;
}

function isRenewablePlanName(name) {
  if (!name) return false;
  const n = String(name).trim();
  return n !== 'FREE_LEVEL' && n !== 'Trial Package';
}

async function getUserBillingRow(userId) {
  return prisma.users.findUnique({
    where: { id: Number(userId) },
    select: {
      is_paid: true,
      active_plan_name: true,
      expiry_date: true,
    },
  });
}

async function getCatalogPlan(planName) {
  return prisma.subscription_plans.findFirst({
    where: { plan_name: planName },
  });
}

async function findOpenPurchase(userId) {
  await ensureSubscriptionPurchasesTable();
  const today = formatDateYmd(todayDateOnly());
  const rows = await prisma.$queryRaw`
    SELECT id, plan_name, plan_id, list_price, amount_paid, duration_days, started_at, ends_at
    FROM subscription_purchases
    WHERE user_id = ${Number(userId)}
      AND is_closed = 0
      AND ends_at >= ${today}
    ORDER BY ends_at DESC, id DESC
    LIMIT 1
  `;
  return rows[0] || null;
}

async function getActiveSubscriptionContext(userId) {
  const user = await getUserBillingRow(userId);
  if (!user || !user.is_paid || !user.expiry_date || !isRenewablePlanName(user.active_plan_name)) {
    return null;
  }

  const expiry = dateOnly(user.expiry_date);
  const today = todayDateOnly();
  if (expiry < today) return null;

  const remainingDays = daysBetween(today, expiry);
  if (remainingDays <= 0) return null;

  const openPurchase = await findOpenPurchase(userId);
  if (openPurchase) {
    const startedAt = openPurchase.started_at;
    const durationDays = Number(openPurchase.duration_days) || 1;
    const paidBasis = Number(openPurchase.amount_paid || openPurchase.list_price || 0);
    return {
      planName: openPurchase.plan_name,
      planId: openPurchase.plan_id ? Number(openPurchase.plan_id) : null,
      purchaseId: Number(openPurchase.id),
      listPrice: Number(openPurchase.list_price || paidBasis),
      paidBasis,
      durationDays,
      startedAt,
      expiry,
      remainingDays,
      fromPurchaseRecord: true,
    };
  }

  const catalog = await getCatalogPlan(user.active_plan_name);
  if (!catalog) {
    return {
      planName: user.active_plan_name,
      planId: null,
      purchaseId: null,
      listPrice: 0,
      paidBasis: 0,
      durationDays: remainingDays,
      startedAt: formatDateYmd(addDays(expiry, -remainingDays)),
      expiry,
      remainingDays,
      fromPurchaseRecord: false,
    };
  }

  const durationDays = Number(catalog.duration_days) || 365;
  const paidBasis = Number(catalog.price) || 0;
  const startedAt = addDays(expiry, -durationDays);

  return {
    planName: user.active_plan_name,
    planId: catalog.id ? Number(catalog.id) : null,
    purchaseId: null,
    listPrice: paidBasis,
    paidBasis,
    durationDays,
    startedAt: formatDateYmd(startedAt),
    expiry,
    remainingDays,
    fromPurchaseRecord: false,
  };
}

function formatDateYmd(date) {
  const d = dateOnly(date);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function computeUnusedCredit(ctx) {
  if (!ctx || ctx.remainingDays <= 0 || ctx.durationDays <= 0) return 0;
  const ratio = ctx.remainingDays / ctx.durationDays;
  return roundMoney(Math.min(ctx.paidBasis, ctx.paidBasis * ratio));
}

/**
 * @returns {'new'|'renew'|'upgrade'}
 */
function resolvePurchaseType(activeCtx, targetPlanName) {
  if (!activeCtx) return 'new';
  if (activeCtx.planName === targetPlanName) return 'renew';
  return 'upgrade';
}

async function computeSubscriptionQuote(userId, planName) {
  await ensureSubscriptionPurchasesTable();

  const targetPlan = await getCatalogPlan(planName);
  if (!targetPlan) {
    return { error: 'PLAN_NOT_FOUND', message: 'প্ল্যানটি খুঁজে পাওয়া যায়নি।' };
  }

  const listPrice = Number(targetPlan.price) || 0;
  const durationDays = Number(targetPlan.duration_days) || 365;
  const activeCtx = await getActiveSubscriptionContext(userId);
  const purchaseType = resolvePurchaseType(activeCtx, targetPlan.plan_name);

  let creditApplied = 0;
  let payableAmount = listPrice;
  let newExpiryDate;
  const today = todayDateOnly();

  if (purchaseType === 'renew') {
    creditApplied = 0;
    payableAmount = listPrice;
    newExpiryDate = addDays(activeCtx.expiry, durationDays);
  } else if (purchaseType === 'upgrade') {
    creditApplied = computeUnusedCredit(activeCtx);
    payableAmount = roundMoney(Math.max(0, listPrice - creditApplied));
    newExpiryDate = addDays(today, durationDays);
  } else {
    creditApplied = 0;
    payableAmount = listPrice;
    newExpiryDate = addDays(today, durationDays);
  }

  return {
    plan_name: targetPlan.plan_name,
    plan_id: targetPlan.id ? Number(targetPlan.id) : null,
    purchase_type: purchaseType,
    list_price: listPrice,
    credit_applied: creditApplied,
    payable_amount: payableAmount,
    duration_days: durationDays,
    new_expiry_date: formatDateYmd(newExpiryDate),
    current_plan_name: activeCtx?.planName || null,
    current_expiry_date: activeCtx ? formatDateYmd(activeCtx.expiry) : null,
    remaining_days: activeCtx?.remainingDays || 0,
    credit_source_plan: purchaseType === 'upgrade' ? activeCtx?.planName || null : null,
  };
}

async function closeOpenPurchases(userId, replacedPurchaseId = null) {
  await ensureSubscriptionPurchasesTable();
  const today = formatDateYmd(todayDateOnly());
  await prisma.$executeRaw`
    UPDATE subscription_purchases
    SET is_closed = 1, ends_at = ${today}
    WHERE user_id = ${Number(userId)} AND is_closed = 0
  `;
  return replacedPurchaseId;
}

async function insertPurchaseRecord({
  userId,
  plan,
  quote,
  newExpiryDate,
  replacedPurchaseId = null,
}) {
  await ensureSubscriptionPurchasesTable();
  const today = formatDateYmd(todayDateOnly());
  const startedAt = quote.purchase_type === 'renew' && quote.current_expiry_date
    ? quote.current_expiry_date
    : today;

  await prisma.$executeRaw`
    INSERT INTO subscription_purchases (
      user_id, plan_name, plan_id, purchase_type,
      list_price, credit_applied, amount_paid, duration_days,
      started_at, ends_at, replaced_purchase_id, is_closed
    ) VALUES (
      ${Number(userId)},
      ${plan.plan_name},
      ${plan.id ? Number(plan.id) : null},
      ${quote.purchase_type},
      ${quote.list_price},
      ${quote.credit_applied},
      ${quote.payable_amount},
      ${quote.duration_days},
      ${startedAt},
      ${formatDateYmd(newExpiryDate)},
      ${replacedPurchaseId},
      0
    )
  `;
}

async function applySubscriptionPurchase(userId, planName) {
  const quoteResult = await computeSubscriptionQuote(userId, planName);
  if (quoteResult.error) return quoteResult;

  const plan = await getCatalogPlan(planName);
  const activeCtx = await getActiveSubscriptionContext(userId);
  const newExpiryDate = dateOnly(quoteResult.new_expiry_date);
  let replacedPurchaseId = null;

  if (quoteResult.purchase_type === 'upgrade') {
    replacedPurchaseId = activeCtx?.purchaseId || null;
    await closeOpenPurchases(userId);
  } else if (quoteResult.purchase_type === 'renew') {
    replacedPurchaseId = activeCtx?.purchaseId || null;
    await closeOpenPurchases(userId);
  }

  await prisma.users.update({
    where: { id: Number(userId) },
    data: {
      is_paid: 1,
      active_plan_name: plan.plan_name,
      expiry_date: newExpiryDate,
      ...(plan.is_custom_sender_allowed === 1 ? { has_custom_sender_addon: 1 } : {}),
    },
  });

  await insertPurchaseRecord({
    userId,
    plan,
    quote: quoteResult,
    newExpiryDate,
    replacedPurchaseId,
  });

  return quoteResult;
}

module.exports = {
  ensureSubscriptionPurchasesTable,
  computeSubscriptionQuote,
  applySubscriptionPurchase,
  formatDateYmd,
  getActiveSubscriptionContext,
};
