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

function dailyRate(totalPrice, durationKey) {
  const days = DURATION_DAYS[durationKey] || 365;
  if (days <= 0) return 0;
  return totalPrice / days;
}

async function computeQuote(userId, { category, skuKey, durationKey, addons = [] }) {
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
  const remainingDaysCount = await getSharedRemainingDays(userId);
  const today = dateOnly();

  let purchaseType = 'new';
  let payable = fullPrice;
  let finalExpiry = addDays(today, durationDaysVal);
  const lineItems = [];
  const peerUpgradeLines = [];

  const addonCatalog = await listAddonCatalog();
  const allowedAddons = CATEGORY_ADDONS[category] || [];
  let addonTotal = 0;
  for (const key of addons) {
    if (!allowedAddons.includes(key)) continue;
    const ac = addonCatalog.find((a) => a.addon_key === key);
    if (!ac) continue;
    const ap = ac[`price_${durationKey}`] ?? ac.price_12m ?? 0;
    addonTotal += Number(ap);
    lineItems.push({ type: 'addon', key, amount: Number(ap) });
  }

  if (sameCat && sameCat.package_sku === skuKey) {
    purchaseType = 'renew';
    payable = fullPrice + addonTotal;
    const baseExp = parseYmd(sameCat.expires_at) || today;
    const stackFrom = baseExp > today ? baseExp : today;
    finalExpiry = addDays(stackFrom, durationDaysVal);
    if (sharedExpiry && finalExpiry < sharedExpiry) {
      finalExpiry = sharedExpiry;
    }
  } else if (subs.length > 0 && sharedExpiry && remainingDaysCount > 0) {
    purchaseType = subs.some((s) => s.category === category) ? 'upgrade' : 'cross_category';
    const rate = dailyRate(fullPrice, durationKey);
    payable = roundMoney(rate * remainingDaysCount);
    for (const a of lineItems) {
      const ac = addonCatalog.find((x) => x.addon_key === a.key);
      if (ac) {
        const ap = ac[`price_${durationKey}`] ?? 0;
        payable += roundMoney(dailyRate(ap, durationKey) * remainingDaysCount);
      }
    }
    finalExpiry = sharedExpiry;
    for (const s of subs) {
      if (s.category !== category) {
        peerUpgradeLines.push({
          category: s.category,
          message: `${s.package_full_name} — shared expiry ${formatYmd(sharedExpiry)} পর্যন্ত সিঙ্ক`,
        });
      }
    }
  } else {
    payable = fullPrice + addonTotal;
    if (sharedExpiry && sharedExpiry > finalExpiry) {
      finalExpiry = sharedExpiry;
    }
  }

  // Buying longer than shared — extend all peers
  const newEnd = addDays(today, durationDaysVal);
  if (!sharedExpiry || newEnd > sharedExpiry) {
    if (subs.length > 0) {
      for (const s of subs) {
        const sExp = parseYmd(s.expires_at);
        if (!sExp || sExp < newEnd) {
          const pkgPeer = await getPackageBySku(s.package_sku);
          if (pkgPeer) {
            const peerDur = s.duration_key || '12m';
            const peerFull = priceForDuration(pkgPeer, peerDur);
            const extraDays = remainingDays(sExp || today, newEnd);
            const extra = roundMoney(dailyRate(peerFull, peerDur) * Math.max(0, extraDays));
            if (extra > 0) {
              peerUpgradeLines.push({
                category: s.category,
                sku: s.package_sku,
                extra_cost: extra,
                message: `${s.category} — ${extraDays} দিন সিঙ্ক: ৳${extra}`,
              });
              payable += extra;
            }
          }
        }
      }
    }
    finalExpiry = newEnd;
  }

  payable = roundMoney(Math.max(0, payable));

  return {
    purchase_type: purchaseType,
    category,
    package_sku: pkg.sku_key,
    package_full_name: packageFullName(pkg, durationKey),
    duration_key: durationKey,
    duration_days: durationDaysVal,
    list_price: fullPrice,
    addon_total: roundMoney(addonTotal),
    payable_amount: payable,
    remaining_days: remainingDaysCount,
    shared_expiry: sharedExpiry ? formatYmd(sharedExpiry) : null,
    final_expiry: formatYmd(finalExpiry),
    current_packages: subs.map((s) => ({
      category: s.category,
      package_full_name: s.package_full_name,
      expires_at: formatYmd(s.expires_at),
    })),
    addons,
    line_items: lineItems,
    peer_upgrade_lines: peerUpgradeLines,
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
};
