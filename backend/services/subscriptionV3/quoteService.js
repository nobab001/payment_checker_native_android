const crypto = require('crypto');
const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');
const { getCheckoutSessionMinutes } = require('./configService');
const CHECKOUT_SESSION_EXPIRED_MSG = 'Checkout session expired. Please reopen checkout and try again.';
const {
  getPackageBySku,
  listAddonCatalog,
  packageFullName,
  priceForDuration,
  durationDays,
} = require('./catalogService');
const {
  getUserSubscriptions,
  getSharedExpiry,
  getSharedRemainingDays,
  formatYmd,
  addDays,
  dateOnly,
  parseYmd,
  remainingDays,
} = require('./sharedExpiryService');
const { CATEGORY_ADDONS, DURATION_DAYS } = require('./constants');

function roundMoney(n) {
  return Math.round(Number(n) * 100) / 100;
}

function unusedCredit(paidAmount, totalDays, remaining) {
  const paid = Number(paidAmount) || 0;
  const days = Number(totalDays) || 0;
  const rem = Math.max(0, Number(remaining) || 0);
  if (paid <= 0 || days <= 0 || rem <= 0) return 0;
  return roundMoney((paid / days) * rem);
}

/**
 * Tier compare within category by yearly (fallback monthly) list price.
 * >0 = new is higher (upgrade), <0 = new is lower (downgrade), 0 = sidegrade.
 */
function compareTier(oldPkg, newPkg) {
  const oldP = Number(oldPkg.price_12m || oldPkg.price_1m || 0);
  const newP = Number(newPkg.price_12m || newPkg.price_1m || 0);
  if (newP > oldP) return 1;
  if (newP < oldP) return -1;
  const oldM = Number(oldPkg.price_1m || 0);
  const newM = Number(newPkg.price_1m || 0);
  if (newM > oldM) return 1;
  if (newM < oldM) return -1;
  return 0;
}

async function getLastPurchaseBasis(userId, category, sameCat) {
  const rows = await prisma.$queryRaw`
    SELECT amount_paid, list_price, duration_days, duration_key, package_sku, credit_applied
    FROM subscription_purchases
    WHERE user_id = ${Number(userId)}
      AND category = ${category}
      AND purchase_type IN ('new', 'renew', 'upgrade')
    ORDER BY id DESC
    LIMIT 1
  `.catch(() => []);

  if (rows[0]) {
    const r = rows[0];
    const paid = Number(r.amount_paid) || 0;
    const list = Number(r.list_price) || 0;
    // Credit basis = what they effectively paid for the package (after prior credits).
    return {
      paid_amount: paid > 0 ? paid : list,
      duration_days: Number(r.duration_days) || DURATION_DAYS[r.duration_key] || 30,
      duration_key: r.duration_key,
      package_sku: r.package_sku,
    };
  }

  if (sameCat) {
    const snapPaid = Number(sameCat.amount_paid);
    const snapDays = Number(sameCat.paid_duration_days);
    if (snapPaid > 0 && snapDays > 0) {
      return {
        paid_amount: snapPaid,
        duration_days: snapDays,
        duration_key: sameCat.duration_key,
        package_sku: sameCat.package_sku,
      };
    }
    const oldPkg = await getPackageBySku(sameCat.package_sku);
    if (oldPkg) {
      const dur = sameCat.duration_key || '1m';
      return {
        paid_amount: priceForDuration(oldPkg, dur),
        duration_days: durationDays(dur),
        duration_key: dur,
        package_sku: sameCat.package_sku,
      };
    }
  }
  return null;
}

async function computeQuote(userId, { category, skuKey, durationKey, addons = [] }) {
  await ensureSubscriptionV3Schema();
  const pkg = await getPackageBySku(skuKey);
  if (!pkg || pkg.category !== category) {
    return { error: 'PLAN_NOT_FOUND', message: 'প্যাকেজটি খুঁজে পাওয়া যায়নি।' };
  }
  if (!pkg.is_visible || pkg.catalog_status === 'archived') {
    return { error: 'PLAN_UNAVAILABLE', message: 'এই প্যাকেজটি বর্তমানে বিক্রয়ের জন্য উপলব্ধ নয়।' };
  }

  const fullPrice = priceForDuration(pkg, durationKey);
  const durationDaysVal = durationDays(durationKey);
  const subs = await getUserSubscriptions(userId);
  const sameCat = subs.find((s) => s.category === category);
  const sharedExpiry = await getSharedExpiry(userId);
  const today = dateOnly();

  let catRemaining = 0;
  if (sameCat) {
    const exp = parseYmd(sameCat.expires_at);
    if (exp && exp >= today) catRemaining = remainingDays(today, exp);
  }

  const lineItems = [];
  const notes = [];
  const addonCatalog = await listAddonCatalog();
  const allowedAddons = CATEGORY_ADDONS[category] || [];
  let addonTotal = 0;
  for (const key of addons) {
    if (!allowedAddons.includes(key)) continue;
    const ac = addonCatalog.find((a) => a.addon_key === key);
    if (!ac) continue;
    const ap = Number(ac[`price_${durationKey}`] ?? ac.price_12m ?? 0);
    addonTotal += ap;
    lineItems.push({ type: 'addon', key, amount: ap });
  }
  addonTotal = roundMoney(addonTotal);

  let purchaseType = 'new';
  let payable = roundMoney(fullPrice + addonTotal);
  let finalExpiry = addDays(today, durationDaysVal);
  let creditApplied = 0;
  let deferred = false;
  let deferredStartsAt = null;
  let oldPackageSku = sameCat?.package_sku || null;
  let oldPackageName = sameCat?.package_full_name || null;

  // ── Same package code (sku) → RENEW / extend after current expiry ─────────
  if (sameCat && String(sameCat.package_sku) === String(pkg.sku_key) && catRemaining > 0) {
    purchaseType = 'renew';
    payable = roundMoney(fullPrice + addonTotal);
    const baseExp = parseYmd(sameCat.expires_at) || today;
    const stackFrom = baseExp > today ? baseExp : today;
    finalExpiry = addDays(stackFrom, durationDaysVal);
    notes.push({
      message: `রিনিউ — কোড ${pkg.sku_key}; মেয়াদ বর্তমান শেষ তারিখের পর যোগ হবে।`,
    });
  } else if (sameCat && catRemaining > 0 && String(sameCat.package_sku) !== String(pkg.sku_key)) {
    // ── Different package code in same category ─────────────────────────────
    const oldPkg = await getPackageBySku(sameCat.package_sku);
    const tierCmp = oldPkg ? compareTier(oldPkg, pkg) : 1;
    const basis = await getLastPurchaseBasis(userId, category, sameCat);

    if (tierCmp < 0) {
      // DOWNGRADE — current plan runs out, then new plan starts
      purchaseType = 'downgrade';
      deferred = true;
      const currentExp = parseYmd(sameCat.expires_at) || today;
      deferredStartsAt = addDays(currentExp, 1);
      finalExpiry = addDays(deferredStartsAt, durationDaysVal);
      payable = roundMoney(fullPrice + addonTotal);
      creditApplied = 0;
      notes.push({
        message: `ডাউনগ্রেড — বর্তমান প্যাকেজ (${oldPackageName || oldPackageSku}) শেষ হওয়ার পর নতুন কোড ${pkg.sku_key} চালু হবে (${formatYmd(deferredStartsAt)} থেকে)।`,
      });
    } else {
      // UPGRADE or sidegrade — unused credit + cycle reset from today
      purchaseType = tierCmp > 0 ? 'upgrade' : 'upgrade';
      const paidBasis = basis?.paid_amount ?? 0;
      const paidDays = basis?.duration_days ?? durationDays(sameCat.duration_key || '1m');
      creditApplied = unusedCredit(paidBasis, paidDays, catRemaining);
      payable = roundMoney(Math.max(0, fullPrice + addonTotal - creditApplied));
      finalExpiry = addDays(today, durationDaysVal);
      notes.push({
        message: `আপগ্রেড — কোড ${oldPackageSku} → ${pkg.sku_key}; অবশিষ্ট ${catRemaining} দিনের ক্রেডিট ৳${creditApplied}; আজ থেকে নতুন মেয়াদ।`,
      });
    }
  } else if (!sameCat && subs.length > 0) {
    // Cross-category first purchase in this category
    purchaseType = 'cross_category';
    payable = roundMoney(fullPrice + addonTotal);
    finalExpiry = addDays(today, durationDaysVal);
    notes.push({
      message: `ক্রস-ক্যাটাগরি — নতুন কোড ${pkg.sku_key} আজ থেকে সক্রিয়।`,
    });
  } else {
    purchaseType = 'new';
    payable = roundMoney(fullPrice + addonTotal);
    finalExpiry = addDays(today, durationDaysVal);
  }

  // Mid-cycle add-on co-term note when renewing/upgrading with addons
  if (addons.length && purchaseType === 'upgrade') {
    notes.push({
      message: 'নির্বাচিত অ্যাড-অন নতুন প্যাকেজ মেয়াদের সাথে একই দিনে শেষ হবে (co-term)।',
    });
  }

  return {
    purchase_type: purchaseType,
    category,
    package_sku: pkg.sku_key,
    package_code: pkg.sku_key,
    package_full_name: packageFullName(pkg, durationKey),
    duration_key: durationKey,
    duration_days: durationDaysVal,
    list_price: fullPrice,
    addon_total: addonTotal,
    credit_applied: creditApplied,
    payable_amount: payable,
    remaining_days: catRemaining,
    shared_expiry: sharedExpiry ? formatYmd(sharedExpiry) : null,
    final_expiry: formatYmd(finalExpiry),
    deferred,
    deferred_starts_at: deferredStartsAt ? formatYmd(deferredStartsAt) : null,
    old_package_sku: oldPackageSku,
    old_package_name: oldPackageName,
    current_packages: subs.map((s) => ({
      category: s.category,
      package_sku: s.package_sku,
      package_full_name: s.package_full_name,
      expires_at: formatYmd(s.expires_at),
    })),
    addons,
    line_items: lineItems,
    peer_upgrade_lines: notes,
  };
}

async function freezeQuote(userId, quote) {
  await ensureSubscriptionV3Schema();
  const token = `qt_${Date.now().toString(36)}_${crypto.randomBytes(8).toString('hex')}`;
  const mins = getCheckoutSessionMinutes();
  const expiresAt = new Date(Date.now() + mins * 60 * 1000);
  await prisma.$executeRaw`
    INSERT INTO subscription_quote_freeze (quote_token, user_id, quote_json, payable_amount, expires_at)
    VALUES (${token}, ${Number(userId)}, ${JSON.stringify(quote)}, ${quote.payable_amount}, ${expiresAt})
  `;
  return { quote_token: token, expires_at: expiresAt.toISOString(), quote };
}

async function loadFrozenQuote(quoteToken, userId) {
  await ensureSubscriptionV3Schema();
  const rows = await prisma.$queryRaw`
    SELECT quote_token, user_id, quote_json, payable_amount, expires_at
    FROM subscription_quote_freeze
    WHERE quote_token = ${quoteToken} AND user_id = ${Number(userId)}
    LIMIT 1
  `;
  const row = rows[0];
  if (!row) return { error: 'QUOTE_NOT_FOUND', message: 'কোটো পাওয়া যায়নি।' };
  if (new Date(row.expires_at) < new Date()) {
    return { error: 'QUOTE_EXPIRED', message: CHECKOUT_SESSION_EXPIRED_MSG };
  }
  let quote;
  try {
    quote = typeof row.quote_json === 'string' ? JSON.parse(row.quote_json) : row.quote_json;
  } catch {
    return { error: 'QUOTE_INVALID', message: 'কোটো ডেটা ত্রুটিপূর্ণ।' };
  }
  return { quote, payable_amount: Number(row.payable_amount) };
}

module.exports = {
  computeQuote,
  freezeQuote,
  loadFrozenQuote,
  unusedCredit,
  compareTier,
};
