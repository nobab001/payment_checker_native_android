/**
 * @file Load merchant + official gateway config (non-session Prisma reads).
 * Only used by PaymentEngine — not controllers or providers.
 */

const prisma = require('../../db/prisma');
const { resolveProviderId, toDbProviderKey } = require('../registry/provider-alias');
const { decrypt } = require('../../utils/merchantCrypto');

async function loadMerchantByApiKey(apiKey) {
  if (!apiKey) return null;
  return prisma.gateway_layouts.findFirst({
    where: { api_key: apiKey, is_active: 1 },
    select: {
      id: true,
      user_id: true,
      api_key: true,
      success_url: true,
      cancel_url: true,
      redirect_url: true,
    },
  });
}

/**
 * @param {number} websiteId
 * @param {string} providerIdOrAlias — any alias or canonical id
 * @param {number} [merchantAccountId] — optional: load a specific merchant account
 */
async function loadOfficialGateway(websiteId, providerIdOrAlias, merchantAccountId = null) {
  if (!websiteId || providerIdOrAlias == null || providerIdOrAlias === '') return null;

  // If merchantAccountId provided, use that
  if (merchantAccountId != null && merchantAccountId !== '') {
    const acctId = typeof merchantAccountId === 'string' ? parseInt(merchantAccountId, 10) : merchantAccountId;
    if (Number.isFinite(acctId)) {
      const acct = await prisma.merchant_accounts.findFirst({
        where: { id: acctId, website_id: websiteId, is_active: 1 },
      });
      if (acct) return shapeMerchantAccount(acct);
    }
  }

  // Fallback: merchant_accounts by provider aliases (preferred over legacy official gateways)
  const fromAcct = await findMerchantAccountByProvider(websiteId, providerIdOrAlias);
  if (fromAcct) return shapeMerchantAccount(fromAcct);

  // Legacy official gateway
  const canonical = resolveProviderId(providerIdOrAlias);
  const dbKey = canonical
    ? toDbProviderKey(canonical).toLowerCase()
    : String(providerIdOrAlias).toLowerCase();

  return prisma.website_official_gateways.findFirst({
    where: {
      website_id: websiteId,
      provider: dbKey,
      is_active: 1,
    },
  });
}

function providerLookupKeys(providerIdOrAlias) {
  const raw = String(providerIdOrAlias || '').trim().toLowerCase();
  const keys = new Set([raw]);
  const canonical = resolveProviderId(raw);
  if (canonical) {
    keys.add(canonical.toLowerCase());
    const dbKey = toDbProviderKey(canonical);
    if (dbKey) keys.add(String(dbKey).toLowerCase());
    const entry = require('../registry/provider-registry').PROVIDER_REGISTRY[canonical];
    for (const a of entry?.aliases || []) keys.add(String(a).toLowerCase());
  }
  // Common short names stored on merchant_accounts.provider
  if (raw.includes('bkash')) keys.add('bkash');
  if (raw.includes('nagad')) keys.add('nagad');
  if (raw.includes('rocket')) keys.add('rocket');
  return [...keys].filter(Boolean);
}

async function findMerchantAccountByProvider(websiteId, providerIdOrAlias) {
  const keys = providerLookupKeys(providerIdOrAlias);
  if (!keys.length) return null;
  const rows = await prisma.merchant_accounts.findMany({
    where: {
      website_id: websiteId,
      is_active: 1,
      provider: { in: keys },
    },
    orderBy: [{ is_default: 'desc' }, { priority: 'asc' }, { id: 'desc' }],
    take: 1,
  });
  return rows[0] || null;
}

function shapeMerchantAccount(acct) {
  const appKey = acct.app_key || acct.api_key || '';
  const appSecret = decrypt(acct.app_secret_enc) || decrypt(acct.api_secret_enc) || '';
  const username = acct.username || '';
  const password = decrypt(acct.password_enc) || '';
  const hasApiCreds = !!(appKey && appSecret && username && password);

  return {
    id: acct.id,
    provider: acct.provider,
    merchantName: acct.merchant_name,
    display_name: acct.merchant_name,
    redirect_url_template: acct.base_url || '',
    website_id: acct.website_id,
    api_key: acct.api_key,
    api_secret: decrypt(acct.api_secret_enc),
    username,
    password,
    app_key: appKey,
    app_secret: appSecret,
    base_url: acct.base_url,
    callback_url: acct.callback_url,
    notes: acct.notes,
    config_json: JSON.stringify({
      mode: hasApiCreds ? 'api' : 'template',
      appKey,
      appSecret,
      username,
      password,
      callbackSecret: '',
    }),
  };
}

module.exports = {
  loadMerchantByApiKey,
  loadOfficialGateway,
};
