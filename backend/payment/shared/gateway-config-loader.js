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
  if (merchantAccountId != null) {
    const acctId = typeof merchantAccountId === 'string' ? parseInt(merchantAccountId, 10) : merchantAccountId;
    if (!Number.isFinite(acctId)) return null;

    const acct = await prisma.merchant_accounts.findFirst({
      where: { id: acctId, website_id: websiteId, is_active: 1 },
    });
    if (!acct) return null;

    // Decrypt the credentials
    const config = {
      id: acct.id,
      provider: acct.provider,
      merchantName: acct.merchant_name,
      display_name: acct.merchant_name,
      redirect_url_template: acct.base_url,
      config_json: null,
      website_id: acct.website_id,
      api_key: acct.api_key,
      api_secret: decrypt(acct.api_secret_enc),
      username: acct.username,
      password: decrypt(acct.password_enc),
      app_key: acct.app_key,
      app_secret: decrypt(acct.app_secret_enc),
      base_url: acct.base_url,
      callback_url: acct.callback_url,
      notes: acct.notes,
    };
    return config;
  }

  // Fallback: load legacy official gateway
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

module.exports = {
  loadMerchantByApiKey,
  loadOfficialGateway,
};
