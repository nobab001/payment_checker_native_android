const crypto = require('crypto');
const config = require('../config');
const sessionStore = require('../services/session-store');

function verifySignature(rawBody, signature) {
  if (!config.paychekApiSecret || !signature) return false;
  const expected = crypto
    .createHmac('sha256', config.paychekApiSecret)
    .update(rawBody)
    .digest('hex');
  try {
    const a = Buffer.from(String(signature));
    const b = Buffer.from(expected);
    if (a.length !== b.length) return false;
    return crypto.timingSafeEqual(a, b);
  } catch {
    return false;
  }
}

/**
 * Webhook only records status on the temporary demo session.
 * Does not expose merchant history/wallet to visitors.
 */
async function handlePaychekWebhook(rawBody, signature) {
  if (!verifySignature(rawBody, signature)) {
    const err = new Error('Invalid webhook signature');
    err.code = 'INVALID_SIGNATURE';
    throw err;
  }

  let payload = {};
  try {
    payload = typeof rawBody === 'string' ? JSON.parse(rawBody) : rawBody;
  } catch {
    payload = {};
  }

  const demoSessionId =
    payload?.meta?.demoSessionId ||
    payload?.metadata?.demoSessionId ||
    null;

  if (demoSessionId) {
    sessionStore.recordPayment(demoSessionId, {
      status: payload.status || payload.paymentStatus || 'success',
      amount: payload.amount,
      trxId: payload.trxId || payload.trx_id,
      orderId: payload.orderId,
      at: Date.now(),
    });
  }

  return { success: true, recorded: Boolean(demoSessionId) };
}

module.exports = { handlePaychekWebhook, verifySignature };
