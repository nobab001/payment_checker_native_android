const crypto = require('crypto');
const { validateMerchantCallbackV1 } = require('../../payment/core/merchant-callback-v1');
const config = require('../config');
const orderService = require('./order-service');

function timingSafeEqual(a, b) {
  const ba = Buffer.from(String(a || ''));
  const bb = Buffer.from(String(b || ''));
  if (ba.length !== bb.length) return false;
  return crypto.timingSafeEqual(ba, bb);
}

function verifySignature(rawBody, signature) {
  if (!config.paychekApiSecret || !signature) return false;
  const expected = crypto
    .createHmac('sha256', config.paychekApiSecret)
    .update(rawBody)
    .digest('hex');
  return timingSafeEqual(signature, expected);
}

async function handlePaychekWebhook(rawBody, signature) {
  if (!verifySignature(rawBody, signature)) {
    const err = new Error('Invalid webhook signature');
    err.code = 'INVALID_SIGNATURE';
    throw err;
  }

  let payload;
  try {
    payload = JSON.parse(rawBody.toString('utf8'));
  } catch {
    const err = new Error('Invalid JSON payload');
    err.code = 'INVALID_PAYLOAD';
    throw err;
  }

  const validationErrors = validateMerchantCallbackV1(payload);
  if (validationErrors.length) {
    const err = new Error(`Invalid callback payload: ${validationErrors.join(', ')}`);
    err.code = 'INVALID_PAYLOAD';
    throw err;
  }

  const orderRef = payload.orderId || payload.merchantTransactionId;
  if (!orderRef) {
    return { handled: false, reason: 'NO_ORDER_ID' };
  }

  const order = await orderService.getOrderByNumber(orderRef);
  if (!order) {
    return { handled: false, reason: 'ORDER_NOT_FOUND', orderRef };
  }

  const status = String(payload.status || '').toUpperCase();

  if (status === 'SUCCESS') {
    await orderService.markOrderPaid(order, {
      paymentId: payload.paymentId || payload.providerTransactionId,
      traceId: payload.traceId,
      provider: payload.provider,
    });
    return { handled: true, orderNumber: order.order_number, status: 'paid' };
  }

  if (status === 'FAILED') {
    await orderService.markOrderFailed(order, 'PayCheck payment failed');
    return { handled: true, orderNumber: order.order_number, status: 'failed' };
  }

  if (status === 'CANCELLED' || status === 'EXPIRED' || status === 'TIMEOUT') {
    await orderService.markOrderCancelled(order, `PayCheck status: ${status}`);
    return { handled: true, orderNumber: order.order_number, status: 'cancelled' };
  }

  return { handled: true, orderNumber: order.order_number, status: 'ignored', paychekStatus: status };
}

module.exports = {
  verifySignature,
  handlePaychekWebhook,
};
