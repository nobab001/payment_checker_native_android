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

function buildProviderFields({ tabId, provider, variant, type, displayName, instruction, sortOrder, templateId, liveProviderKey }) {
  const id = buildStableProviderId({ provider, variant, type });
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
    metadata: defaultMetadata({
      templateId: templateId ?? null,
      liveProviderKey: liveProviderKey ?? null,
      liveRedirect: type !== PROVIDER_TYPE.SIM,
    }),
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
  });
}

function createOfficialProvider(og) {
  const tabId = og.tab || 'payment';
  const providerName = og.provider || '';
  const type = inferOfficialType(providerName);
  const variant = inferOfficialVariant(type, tabId);

  const defaultInstruction = type === PROVIDER_TYPE.BANK
    ? 'ব্যাংক ট্রান্সফার — অফিসিয়াল গেটওয়ে দিয়ে সরাসরি পরিশোধ করুন।'
    : type === PROVIDER_TYPE.CARD
      ? 'কার্ড পেমেন্ট — অফিসিয়াল গেটওয়ে দিয়ে সরাসরি পরিশোধ করুন।'
      : 'লাইভ পেমেন্ট — অফিসিয়াল গেটওয়ে দিয়ে সরাসরি পরিশোধ করুন।';

  return buildProviderFields({
    tabId,
    provider: providerName,
    variant,
    type,
    displayName: og.displayName || providerName || 'Live Payment',
    instruction: defaultInstruction,
    sortOrder: Number.MAX_SAFE_INTEGER - 1,
    liveProviderKey: providerName,
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

  for (const og of apiData.officialGateways || []) {
    const tabId = og.tab || 'payment';
    if (!tabsById[tabId]) continue;
    const liveProv = createOfficialProvider(og);
    const key = bucketKey(tabId, liveProv.id);
    if (providerIndex.has(key)) continue;
    providerIndex.set(key, liveProv);
    ensureTabBucket(tabTree, tabId, tabsById).providers.push(liveProv);
  }

  for (const bucket of Object.values(tabTree)) {
    bucket.providers = sortProviders(bucket.providers.map(finalizeProvider));
    bucket.providers = filterRenderableProviders(bucket.providers);
  }

  const amount = parseFloat(amountStr) || 0;

  // Raw list of active official (live/redirect) gateways, used by Live mode.
  const officialGateways = (apiData.officialGateways || [])
    .filter((og) => og && (og.isActive !== false) && (og.provider))
    .map((og) => ({
      provider: og.provider,
      displayName: og.displayName || og.provider,
      tab: og.tab || 'payment',
    }));

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
    officialGateways,
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
