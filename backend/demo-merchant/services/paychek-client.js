const axios = require('axios');
const config = require('../config');

/**
 * Thin HTTP client for PayCheck payment APIs.
 * Does NOT implement payment logic — only calls existing platform endpoints.
 */
async function initPaychekCheckout(req, { amount, orderId, successUrl, cancelUrl, callbackUrl }) {
  if (!config.paychekApiKey) {
    const err = new Error('DEMO_MERCHANT_PAYCHEK_API_KEY is not configured');
    err.code = 'CONFIG_ERROR';
    throw err;
  }

  const baseUrl = config.resolvePaychekApiBaseUrl(req);
  const clientOrigin = config.resolveBrowserBaseUrl(req);
  const response = await axios.post(
    `${baseUrl}/api/v1/pay/init`,
    {
      amount,
      orderId,
      channel: 'paycheck',
      currency: 'BDT',
      successUrl,
      cancelUrl,
      callbackUrl,
      meta: { clientOrigin },
    },
    {
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': config.paychekApiKey,
      },
      timeout: 15000,
      validateStatus: () => true,
    },
  );

  if (response.status >= 400 || !response.data?.success) {
    const err = new Error(response.data?.error || `PayCheck init failed (${response.status})`);
    err.code = 'PAYCHEK_INIT_FAILED';
    err.details = response.data;
    throw err;
  }

  return response.data;
}

async function getPaymentStatus(sessionToken, req = null) {
  if (!config.paychekApiKey) {
    const err = new Error('DEMO_MERCHANT_PAYCHEK_API_KEY is not configured');
    err.code = 'CONFIG_ERROR';
    throw err;
  }

  const baseUrl = config.resolvePaychekApiBaseUrl(req);
  const response = await axios.get(
    `${baseUrl.replace(/\/$/, '')}/api/v1/pay/${encodeURIComponent(sessionToken)}/status`,
    {
      headers: { 'X-API-Key': config.paychekApiKey },
      timeout: 10000,
      validateStatus: () => true,
    },
  );

  if (response.status === 404) {
    const err = new Error('Payment session not found');
    err.code = 'SESSION_NOT_FOUND';
    throw err;
  }

  if (response.status >= 400 || !response.data?.success) {
    const err = new Error(response.data?.error || `PayCheck status failed (${response.status})`);
    err.code = 'PAYCHEK_STATUS_FAILED';
    throw err;
  }

  return response.data;
}

module.exports = {
  initPaychekCheckout,
  getPaymentStatus,
};
