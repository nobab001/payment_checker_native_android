/**
 * @file Merchant / website loader for payment flow (non-session Prisma reads).
 */

const prisma = require('../../db/prisma');
const merchantCache = require('../../services/merchantCache');

const MERCHANT_FLAGS_SELECT = {
  id: true,
  user_id: true,
  merchant_id: true,
  api_key: true,
  api_secret: true,
  redirect_url: true,
  success_url: true,
  cancel_url: true,
  callback_url: true,
  webhook_url: true,
  checkout_mode: true,
  website_purpose: true,
  allow_payment_type_callback: true,
  allow_commission_callback: true,
  receive_payment_type: true,
  receive_commission: true,
};

async function loadMerchantByWebsiteId(websiteId) {
  if (!websiteId) return null;
  return prisma.gateway_layouts.findUnique({
    where: { id: websiteId },
    select: MERCHANT_FLAGS_SELECT,
  });
}

async function loadMerchantByApiKey(apiKey) {
  if (!apiKey) return null;
  return merchantCache.getByApiKey(apiKey, 'payment-flags', () =>
    prisma.gateway_layouts.findFirst({
      where: { api_key: apiKey, is_active: 1 },
      select: MERCHANT_FLAGS_SELECT,
    }),
  );
}

module.exports = {
  MERCHANT_FLAGS_SELECT,
  loadMerchantByWebsiteId,
  loadMerchantByApiKey,
};
