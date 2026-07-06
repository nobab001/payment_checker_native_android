/**
 * @file Load merchant + official gateway config (non-session Prisma reads).
 * Only used by PaymentEngine — not controllers or providers.
 */

const prisma = require('../../db/prisma');
const { resolveProviderId, toDbProviderKey } = require('../registry/provider-alias');

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
 */
async function loadOfficialGateway(websiteId, providerIdOrAlias) {
  if (!websiteId || providerIdOrAlias == null || providerIdOrAlias === '') return null;

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
