const crypto = require('crypto');
const config = require('../config');
const demoVisitor = require('./demo-visitor-service');

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
 * Webhook only records status on the sandbox demo visitor.
 * Does not touch real merchant history/wallet.
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
    await demoVisitor.recordPayment(demoSessionId, {
      status: payload.status || payload.paymentStatus || 'success',
      amount: payload.amount || 0,
      purpose: payload?.meta?.purpose || 'pay',
      orderId: payload.orderId,
      sessionToken: payload.sessionToken || null,
    }).catch(() => null);
  }

  return { success: true, recorded: Boolean(demoSessionId) };
}

module.exports = { handlePaychekWebhook, verifySignature };
