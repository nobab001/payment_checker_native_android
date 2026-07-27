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
 * Webhook records/updates sandbox demo payment for refund history.
 * Does not touch real merchant wallet.
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

  const orderId =
    payload.orderId ||
    payload.merchantTransactionId ||
    null;
  const sessionToken =
    payload.sessionToken ||
    payload.sessionId ||
    payload.paymentId ||
    null;
  const trxId =
    payload.trxId ||
    payload.providerTransactionId ||
    null;
  const status = payload.status || payload.paymentStatus || 'success';
  const amount = payload.amount != null ? Number(payload.amount) : null;
  const provider = payload.provider || null;
  const senderNumber = payload.sender || payload.senderNumber || null;
  const receiverNumber = payload.receiver || payload.receiverNumber || null;
  const fullSms = payload.fullSms || payload.full_sms || payload.rawSms || null;
  const purpose = payload?.meta?.purpose || payload.purpose || 'pay';

  let recorded = false;

  if (demoSessionId) {
    const id = await demoVisitor.recordPayment(demoSessionId, {
      status,
      amount,
      purpose,
      orderId,
      sessionToken,
      trxId,
      provider,
      senderNumber,
      receiverNumber,
      fullSms,
    }).catch(() => null);
    recorded = Boolean(id);
  }

  if (!recorded) {
    const id = await demoVisitor.updatePaymentByRefs({
      orderId,
      sessionToken,
      status,
      amount,
      trxId,
      provider,
      senderNumber,
      receiverNumber,
      fullSms,
    }).catch(() => null);
    recorded = Boolean(id);
  }

  return { success: true, recorded };
}

module.exports = { handlePaychekWebhook, verifySignature };
