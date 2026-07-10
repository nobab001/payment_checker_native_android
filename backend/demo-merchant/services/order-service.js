const prisma = require('../../db/prisma');
const authService = require('./auth-service');
const walletService = require('./wallet-service');
const transactionService = require('./transaction-service');

const ORDER_TYPES = {
  WALLET_RECHARGE: 'wallet_recharge',
  PRODUCT_PURCHASE: 'product_purchase',
};

const ORDER_STATUS = {
  PENDING: 'pending',
  PAID: 'paid',
  FAILED: 'failed',
  CANCELLED: 'cancelled',
};

function mapPaymentStatusToOrder(paymentStatus) {
  const s = String(paymentStatus || '').toLowerCase();
  if (s === 'completed' || s === 'success') return ORDER_STATUS.PAID;
  if (s === 'failed') return ORDER_STATUS.FAILED;
  if (s === 'cancelled' || s === 'expired') return ORDER_STATUS.CANCELLED;
  return ORDER_STATUS.PENDING;
}

async function createOrder({
  userId,
  orderType,
  amount,
  productId = null,
  meta = null,
}) {
  const orderNumber = authService.generateOrderNumber(userId);
  const order = await prisma.demo_merchant_orders.create({
    data: {
      user_id: userId,
      order_number: orderNumber,
      order_type: orderType,
      status: ORDER_STATUS.PENDING,
      amount,
      product_id: productId,
      paychek_order_id: orderNumber,
      meta_json: meta ? JSON.stringify(meta) : null,
    },
    include: {
      product: { select: { id: true, name: true, sku: true, price: true } },
    },
  });

  await transactionService.createPendingTransaction(order);
  return order;
}

async function attachPaymentSession(orderId, { sessionToken, traceId }) {
  return prisma.demo_merchant_orders.update({
    where: { id: orderId },
    data: {
      payment_session_token: sessionToken,
      trace_id: traceId || null,
    },
  });
}

async function getOrderByNumber(orderNumber, userId = null) {
  const where = { order_number: orderNumber };
  if (userId) where.user_id = userId;
  return prisma.demo_merchant_orders.findFirst({
    where,
    include: { product: true },
  });
}

async function listOrders(userId, { status, limit = 50 } = {}) {
  const where = { user_id: userId };
  if (status) where.status = status;

  const rows = await prisma.demo_merchant_orders.findMany({
    where,
    orderBy: { created_at: 'desc' },
    take: Math.min(Math.max(limit, 1), 200),
    include: { product: { select: { id: true, name: true, sku: true } } },
  });

  return rows.map(formatOrder);
}

function formatOrder(order) {
  return {
    id: order.id,
    orderNumber: order.order_number,
    orderType: order.order_type,
    status: order.status,
    amount: walletService.toNumber(order.amount),
    product: order.product
      ? { id: order.product.id, name: order.product.name, sku: order.product.sku }
      : null,
    paymentSessionToken: order.payment_session_token,
    paychekPaymentId: order.paychek_payment_id,
    traceId: order.trace_id,
    createdAt: order.created_at,
    paidAt: order.paid_at,
  };
}

async function markOrderPaid(order, { paymentId, traceId, provider } = {}) {
  if (order.status === ORDER_STATUS.PAID) return order;

  const updated = await prisma.$transaction(async (tx) => {
    const current = await tx.demo_merchant_orders.findUnique({ where: { id: order.id } });
    if (!current || current.status === ORDER_STATUS.PAID) return current;

    const paid = await tx.demo_merchant_orders.update({
      where: { id: order.id },
      data: {
        status: ORDER_STATUS.PAID,
        paid_at: new Date(),
        paychek_payment_id: paymentId || current.paychek_payment_id,
        trace_id: traceId || current.trace_id,
      },
      include: { product: true },
    });

    if (paid.order_type === ORDER_TYPES.WALLET_RECHARGE) {
      await walletService.creditWallet({
        userId: paid.user_id,
        amount: paid.amount,
        referenceType: 'order',
        referenceId: paid.order_number,
        description: `Wallet recharge via PayCheck${provider ? ` (${provider})` : ''}`,
      });
    }

    await transactionService.markTransactionPaid(paid.id, paid.user_id);
    return paid;
  });

  return updated;
}

async function markOrderFailed(order, reason = 'Payment failed') {
  if (order.status === ORDER_STATUS.PAID) return order;

  const updated = await prisma.demo_merchant_orders.update({
    where: { id: order.id },
    data: { status: ORDER_STATUS.FAILED },
  });

  await transactionService.markTransactionStatus(order.id, order.user_id, ORDER_STATUS.FAILED, reason);
  return updated;
}

async function markOrderCancelled(order, reason = 'Payment cancelled') {
  if (order.status === ORDER_STATUS.PAID) return order;

  const updated = await prisma.demo_merchant_orders.update({
    where: { id: order.id },
    data: { status: ORDER_STATUS.CANCELLED },
  });

  await transactionService.markTransactionStatus(order.id, order.user_id, ORDER_STATUS.CANCELLED, reason);
  return updated;
}

async function syncOrderFromPaymentStatus(order, paymentStatus) {
  const mapped = mapPaymentStatusToOrder(paymentStatus?.status);
  if (mapped === ORDER_STATUS.PAID) {
    return markOrderPaid(order, {
      paymentId: paymentStatus.trxId,
      traceId: paymentStatus.traceId,
    });
  }
  if (mapped === ORDER_STATUS.FAILED) return markOrderFailed(order);
  if (mapped === ORDER_STATUS.CANCELLED) return markOrderCancelled(order);
  return order;
}

module.exports = {
  ORDER_TYPES,
  ORDER_STATUS,
  createOrder,
  attachPaymentSession,
  getOrderByNumber,
  listOrders,
  formatOrder,
  markOrderPaid,
  markOrderFailed,
  markOrderCancelled,
  syncOrderFromPaymentStatus,
  mapPaymentStatusToOrder,
};
