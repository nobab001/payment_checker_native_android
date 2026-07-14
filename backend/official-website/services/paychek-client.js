const axios = require('axios');
const crypto = require('crypto');
const config = require('../config');

function signRequestBody(rawBody, secret) {
  return crypto.createHmac('sha256', secret).update(rawBody).digest('hex');
}

async function initPaychekCheckout(req, { amount, orderId, successUrl, cancelUrl, callbackUrl, meta }) {
  if (!config.paychekApiKey || !config.paychekApiSecret) {
    const err = new Error('Official Test merchant API key/secret not configured');
    err.code = 'CONFIG_ERROR';
    throw err;
  }

  const baseUrl = config.resolvePaychekApiBaseUrl();
  const payload = {
    amount,
    orderId,
    channel: 'paycheck',
    currency: 'BDT',
    successUrl,
    cancelUrl,
    callbackUrl,
    meta: meta || {},
  };
  const rawBody = JSON.stringify(payload);
  const signature = signRequestBody(rawBody, config.paychekApiSecret);

  const response = await axios.post(`${baseUrl}/api/v1/pay/init`, rawBody, {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': config.paychekApiKey,
      'X-Signature': signature,
    },
    timeout: 15000,
    validateStatus: () => true,
  });

  if (response.status >= 400 || !response.data?.success) {
    const err = new Error(response.data?.error || `PayCheck init failed (${response.status})`);
    err.code = 'PAYCHEK_INIT_FAILED';
    err.details = response.data;
    throw err;
  }

  return response.data;
}

module.exports = { initPaychekCheckout, signRequestBody };
