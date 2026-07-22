/**
 * Normalizes API payload into Tab → Provider → Numbers tree.
 * All business rules (type, variant, sort, filter, stable id) live here.
 */

import { sortGateways, sortNumbers, sortProviders } from './sorter.js';
import { designFromApi } from './utils.js';
import { buildStableProviderId } from './provider-id.js';
import { PROVIDER_TYPE, PROVIDER_VARIANT, defaultMetadata } from './provider-constants.js';

function normalizeTabs(apiData) {
  const raw = apiData.checkoutTabs;
  if (Array.isArray(raw) && raw.length) {
    return raw.filter((t) => t.enabled !== false);
  }
  const all = apiData.checkoutTabsAll || {};
  return Object.values(all).filter((t) => t.enabled);
}

function bucketKey(tabId, stableId, templateId) {
  if (templateId != null && templateId !== '') {
    return `${tabId}::tpl_${templateId}`;
  }
  return `${tabId}::${stableId}`;
}

function inferVariantFromGateway(g, tabId) {
  const kind = (g.kind || '').toLowerCase();
  if (kind.includes('agent')) return PROVIDER_VARIANT.AGENT;
  if (kind.includes('merchant')) return PROVIDER_VARIANT.MERCHANT;
  if (tabId === 'payment') return PROVIDER_VARIANT.PAYMENT;
  return PROVIDER_VARIANT.PERSONAL;
}

function inferOfficialType(providerName) {
  const p = (providerName || '').toLowerCase();
  if (p === 'bank') return PROVIDER_TYPE.BANK;
  if (p === 'card') return PROVIDER_TYPE.CARD;
  return PROVIDER_TYPE.LIVE;
}

function inferOfficialVariant(type, tabId) {
  if (type === PROVIDER_TYPE.BANK) return PROVIDER_VARIANT.PAYMENT;
  if (type === PROVIDER_TYPE.CARD) return PROVIDER_VARIANT.PAYMENT;
  if (tabId === 'payment') return PROVIDER_VARIANT.PAYMENT;
  return PROVIDER_VARIANT.MERCHANT;
}

function gatewayToNumber(g) {
  return {
    methodId: g.methodId ?? g.id,
    number: g.number,
    displayName: g.displayName || g.provider,
    provider: g.provider,
    templateId: g.templateId ?? null,
    simSlot: g.simSlot ?? 1,
    sortOrder: Number.isFinite(Number(g.position)) ? Number(g.position) : Number.MAX_SAFE_INTEGER,
    enabled: g.enabled !== false,
  };
}

function buildProviderFields({ tabId, provider, variant, type, displayName, instruction, sortOrder, templateId, liveProviderKey, merchantAccountId, logoUrl, stableId, incentiveToken, incentiveTplKey }) {
  const id = stableId || buildStableProviderId({ provider, variant, type });
  const metadata = defaultMetadata({
    templateId: templateId ?? null,
    liveProviderKey: liveProviderKey ?? null,
    liveRedirect: type !== PROVIDER_TYPE.SIM,
    merchantAccountId: merchantAccountId ?? null,
    logoUrl: logoUrl || null,
  });
  // Normalized token + optional tpl_<id> used to match commission/campaign rules.
  metadata.incentiveToken = incentiveToken || null;
  metadata.incentiveTplKey = incentiveTplKey || (templateId != null ? `tpl_${templateId}` : null);
  return {
    id,
    tabId,
    type,
    provider: (provider || '').trim(),
    variant,
    displayName: displayName || provider || 'Provider',
    instruction: instruction || '',
    sortOrder,
    enabled: true,
    numbers: [],
    metadata,
  };
}

function createSimProvider(g, tabId) {
  const variant = inferVariantFromGateway(g, tabId);
  const pos = Number.isFinite(Number(g.position)) ? Number(g.position) : Number.MAX_SAFE_INTEGER;

  return buildProviderFields({
    tabId,
    provider: g.provider,
    variant,
    type: PROVIDER_TYPE.SIM,
    displayName: g.displayName || g.groupLabel || g.provider,
    instruction: g.instruction || '',
    sortOrder: pos,
    templateId: g.templateId ?? null,
    incentiveToken: g.incentiveToken ?? null,
    incentiveTplKey: g.incentiveTplKey ?? (g.templateId != null ? `tpl_${g.templateId}` : null),
  });
}

/** One live card per active merchant account (multi-account / multi-provider). */
function createMerchantAccountProvider(acct, providerSlug) {
  const tabId = 'payment';
  const type = inferOfficialType(providerSlug);
  const variant = inferOfficialVariant(type, tabId);
  const baseId = buildStableProviderId({ provider: providerSlug, variant, type });
  return buildProviderFields({
    tabId,
    provider: providerSlug,
    variant,
    type,
    displayName: acct.merchantName || providerSlug || 'Live Payment',
    instruction: 'লাইভ পেমেন্ট — মার্চেন্ট গেটওয়ে দিয়ে সরাসরি পরিশোধ করুন।',
    sortOrder: Number.isFinite(Number(acct.priority)) ? Number(acct.priority) : Number.MAX_SAFE_INTEGER - 2,
    liveProviderKey: providerSlug,
    merchantAccountId: acct.id,
    logoUrl: acct.logoUrl || null,
    stableId: `${baseId}_acct_${acct.id}`,
    incentiveToken: acct.incentiveToken ?? null,
  });
}

function applyInstructionDefaults(prov) {
  if (prov.instruction) return;
  if (prov.type !== PROVIDER_TYPE.SIM) return;
  if (prov.numbers.length > 1) {
    prov.instruction = `${prov.displayName} নম্বারগুলোর যেকোনো একটিতে টাকা পাঠান`;
  } else if (prov.numbers.length === 1) {
    prov.instruction = `${prov.displayName} নম্বারে টাকা পাঠান`;
  }
}

function finalizeProvider(prov) {
  prov.numbers = sortNumbers(prov.numbers.filter((n) => n.enabled !== false));
  applyInstructionDefaults(prov);

  if (prov.type === PROVIDER_TYPE.SIM) {
    prov.enabled = prov.numbers.length > 0;
  } else {
    prov.enabled = true;
  }

  return prov;
}

function filterRenderableProviders(providers) {
  return providers.filter((p) => {
    if (!p.enabled) return false;
    if (p.type === PROVIDER_TYPE.SIM && p.numbers.length === 0) return false;
    return true;
  });
}

function ensureTabBucket(tabTree, tabId, tabsById) {
  if (!tabTree[tabId]) {
    tabTree[tabId] = {
      tab: tabsById[tabId] || { id: tabId, label: tabId, enabled: true },
      providers: [],
    };
  }
  return tabTree[tabId];
}

/**
 * @param {Object} apiData — GET /api/checkout/:apiKey JSON
 * @param {string} amountStr — from URL query
 */
export function buildCheckoutModel(apiData, amountStr) {
  const tabs = normalizeTabs(apiData);
  const tabsById = Object.fromEntries(tabs.map((t) => [t.id, t]));
  const tabTree = {};
  for (const t of tabs) {
    tabTree[t.id] = { tab: t, providers: [] };
  }

  const providerIndex = new Map();
  const sortedGateways = sortGateways(apiData.activeGateways || []);

  for (const g of sortedGateways) {
    if (g.enabled === false || !g.number) continue;
    const tabId = g.tab || 'send_money';
    if (!tabsById[tabId]) continue;

    const prov = createSimProvider(g, tabId);
    const key = bucketKey(tabId, prov.id, g.templateId);
    let existing = providerIndex.get(key);
    if (!existing) {
      existing = prov;
      providerIndex.set(key, existing);
      ensureTabBucket(tabTree, tabId, tabsById).providers.push(existing);
    }

    existing.numbers.push(gatewayToNumber(g));
    if (g.instruction) existing.instruction = g.instruction;
    const pos = Number.isFinite(Number(g.position)) ? Number(g.position) : Number.MAX_SAFE_INTEGER;
    existing.sortOrder = Math.min(existing.sortOrder, pos);
    if (g.templateId != null && existing.metadata.templateId == null) {
      existing.metadata.templateId = g.templateId;
    }
  }

  // Multi-account live merchants → one card each (hybrid + live tabs that include payment)
  const merchantAccountsGroups = apiData.merchantAccountsGroups || [];
  const hasMerchantAccounts = merchantAccountsGroups.some((g) => (g.accounts || []).length > 0);
  if (hasMerchantAccounts && !tabsById.payment) {
    const paymentTab = (apiData.checkoutTabsAll && apiData.checkoutTabsAll.payment)
      || { id: 'payment', label: 'Payment', enabled: true, icon: '📱' };
    tabs.push({ ...paymentTab, id: 'payment', enabled: true });
    tabsById.payment = tabs[tabs.length - 1];
    tabTree.payment = { tab: tabsById.payment, providers: [] };
  }
  for (const group of merchantAccountsGroups) {
    const providerSlug = group.provider || '';
    for (const acct of group.accounts || []) {
      if (!acct || !acct.id) continue;
      const liveProv = createMerchantAccountProvider(acct, providerSlug);
      const tabId = liveProv.tabId;
      if (!tabsById[tabId]) {
        // Ensure payment tab bucket exists even if disabled in hybrid — still show if payment enabled
        continue;
      }
      const key = bucketKey(tabId, liveProv.id);
      if (providerIndex.has(key)) continue;
      providerIndex.set(key, liveProv);
      ensureTabBucket(tabTree, tabId, tabsById).providers.push(liveProv);
    }
  }

  for (const bucket of Object.values(tabTree)) {
    bucket.providers = sortProviders(bucket.providers.map(finalizeProvider));
    bucket.providers = filterRenderableProviders(bucket.providers);
  }

  const amount = parseFloat(amountStr) || 0;
  const sessionPurpose = apiData.purpose || (
    apiData.websitePurpose === 'both' ? 'add_balance' : (apiData.websitePurpose || 'add_balance')
  );

  // Attach the customer-facing incentive (cashback/charge) per provider so the
  // shared provider header can render a badge without threading amount/rules.
  const incentiveRules = apiData.incentives || { enabled: false, commissions: [], campaigns: [] };
  for (const bucket of Object.values(tabTree)) {
    for (const p of bucket.providers) {
      const inc = computeProviderIncentive(
        incentiveRules,
        p.metadata?.incentiveToken,
        amount,
        p.metadata?.incentiveTplKey || p.metadata?.templateId,
        sessionPurpose,
      );
      if (inc) {
        inc.payHint = payHintForIncentive(inc);
      }
      p.incentive = inc;
    }
  }

  return {
    merchant: {
      companyName: apiData.companyName || apiData.siteName || 'Paychek',
      siteName: apiData.siteName || '',
      siteUrl: apiData.siteUrl || '',
      logoUrl: apiData.logoUrl || '',
    },
    amount,
    design: designFromApi(apiData.checkoutDesign || apiData.checkoutTheme),
    checkoutMode: apiData.checkoutMode || 'transaction',
    websitePurpose: apiData.websitePurpose || 'add_balance',
    purpose: sessionPurpose,
    incentives: apiData.incentives || { enabled: false, commissions: [], campaigns: [] },
    merchantAccountsGroups,
    providerBranding: apiData.providerBranding || {},
    tabs,
    tabTree,
    urls: {
      successUrl: apiData.successUrl || apiData.redirectUrl || null,
      cancelUrl: apiData.cancelUrl || null,
    },
  };
}

export function getActiveTabBucket(model, tabId) {
  return model.tabTree[tabId] || { tab: { id: tabId, label: tabId }, providers: [] };
}

/**
 * Round payable for Payment mode: <0.50 paisa → floor Taka; >=0.50 → ceil.
 * Global rule — same for every provider.
 */
export function roundPayableTaka(amount) {
  const n = Number(amount);
  if (!Number.isFinite(n) || n <= 0) return 0;
  const floor = Math.floor(n);
  const frac = n - floor;
  if (frac < 0.5) return floor;
  return Math.ceil(n);
}

/**
 * Compute customer-facing incentive for a provider at the given amount.
 *
 * Add Balance: customer always sends `amount`; UI shows walletCredit.
 * Payment: customer must send expectedPayable (charge/commission adjusted + rounded).
 */
export function computeProviderIncentive(incentives, token, amount, tplKeyOrId, purpose = 'add_balance') {
  if (!incentives || !incentives.enabled) return null;
  const amt = Number(amount) || 0;
  const tplKey = tplKeyOrId == null
    ? null
    : (String(tplKeyOrId).startsWith('tpl_') ? String(tplKeyOrId) : `tpl_${tplKeyOrId}`);

  const ruleMatches = (ruleToken) => {
    if (!ruleToken) return true; // campaign ALL
    if (tplKey && ruleToken === tplKey) return true;
    if (token && ruleToken === token) return true;
    return false;
  };

  // Prefer a tpl_ commission rule over a coarse token when both exist.
  const commRules = incentives.commissions || [];
  const preferredComm = commRules.find((c) => tplKey && c.token === tplKey)
    || commRules.find((c) => token && c.token === token);

  let commission = 0;
  let charge = 0;
  if (preferredComm) {
    commission += preferredComm.commissionType === 'percentage'
      ? amt * (Number(preferredComm.commissionValue) / 100)
      : Number(preferredComm.commissionValue);
    charge += preferredComm.chargeType === 'percentage'
      ? amt * (Number(preferredComm.chargeValue) / 100)
      : Number(preferredComm.chargeValue);
  }
  for (const cp of incentives.campaigns || []) {
    if (!ruleMatches(cp.token)) continue;
    if (amt < Number(cp.minAmount)) continue;
    if (Number(cp.maxAmount) > 0 && amt > Number(cp.maxAmount)) continue;
    const v = cp.valueType === 'percentage' ? amt * (Number(cp.value) / 100) : Number(cp.value);
    if (cp.mode === 'charge') charge += v; else commission += v;
  }

  commission = Math.round(commission * 100) / 100;
  charge = Math.round(charge * 100) / 100;
  if (commission <= 0 && charge <= 0) return null;
  const net = Math.round((commission - charge) * 100) / 100;
  if (net === 0) return null;

  const base = Math.round(amt * 100) / 100;
  const delta = Math.round(Math.abs(net) * 100) / 100;
  const sessionPurpose = purpose === 'payment' ? 'payment' : 'add_balance';

  if (sessionPurpose === 'add_balance') {
    // Customer always sends base; wallet credit informs merchant callback.
    const walletCredit = Math.round((base + net) * 100) / 100;
    return {
      kind: net > 0 ? 'commission' : 'charge',
      purpose: 'add_balance',
      commission,
      charge,
      net,
      delta,
      amount: base,
      payAmount: base,
      customerSendAmount: base,
      walletCredit,
      resultAmount: walletCredit,
    };
  }

  // Payment mode — customer pays adjusted amount (whole Taka rounding).
  if (net > 0) {
    const payAmount = roundPayableTaka(Math.max(0, base - delta));
    return {
      kind: 'commission',
      purpose: 'payment',
      commission,
      charge,
      net,
      delta,
      amount: base,
      payAmount,
      customerSendAmount: payAmount,
      expectedPayable: payAmount,
      resultAmount: base,
    };
  }

  const payAmount = roundPayableTaka(base + delta);
  return {
    kind: 'charge',
    purpose: 'payment',
    commission,
    charge,
    net,
    delta,
    amount: base,
    payAmount,
    customerSendAmount: payAmount,
    expectedPayable: payAmount,
    resultAmount: base,
  };
}

/** Short BN hint for copy/sheet. */
export function payHintForIncentive(inc) {
  if (!inc || !inc.kind) return null;
  const fmt = (n) => {
    const x = Math.round(Number(n) * 100) / 100;
    return Number.isInteger(x) ? String(x) : String(x);
  };
  if (inc.purpose === 'add_balance') {
    const credit = Number(inc.walletCredit);
    if (!Number.isFinite(credit) || credit <= 0) return null;
    if (inc.kind === 'commission') {
      return `আপনি ৳${fmt(inc.payAmount)} পাঠান — ওয়ালেটে ৳${fmt(credit)} যোগ হবে`;
    }
    return `আপনি ৳${fmt(inc.payAmount)} পাঠান — ওয়ালেটে ৳${fmt(credit)} যোগ হবে`;
  }
  const pay = Number(inc.payAmount);
  if (!Number.isFinite(pay) || pay <= 0) return null;
  if (inc.kind === 'charge') return `চার্জসহ ৳${fmt(pay)} পাঠান`;
  if (inc.kind === 'commission') return `কমিশন মাইনাস করে ৳${fmt(pay)} পাঠান`;
  return null;
}
