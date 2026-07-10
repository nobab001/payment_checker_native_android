const prisma = require('../../db/prisma');
const walletService = require('./wallet-service');

async function createPendingTransaction(order) {
  const txnType = order.order_type;
  const description = txnType === 'wallet_recharge'
    ? `Wallet recharge — ${order.order_number}`
    : `Product purchase — ${order.order_number}`;

  return prisma.demo_merchant_transactions.create({
    data: {
      user_id: order.user_id,
      order_id: order.id,
      txn_type: txnType,
      amount: order.amount,
      status: 'pending',
      description,
    },
  });
}

async function markTransactionPaid(orderId, userId) {
  const txn = await prisma.demo_merchant_transactions.findFirst({
    where: { order_id: orderId, user_id: userId },
    orderBy: { id: 'desc' },
  });
  if (!txn) return null;
  if (txn.status === 'paid') return txn;

  return prisma.demo_merchant_transactions.update({
    where: { id: txn.id },
    data: { status: 'paid' },
  });
}

async function markTransactionStatus(orderId, userId, status, description) {
  const txn = await prisma.demo_merchant_transactions.findFirst({
    where: { order_id: orderId, user_id: userId },
    orderBy: { id: 'desc' },
  });
  if (!txn || txn.status === 'paid') return txn;

  return prisma.demo_merchant_transactions.update({
    where: { id: txn.id },
    data: { status, description: description || txn.description },
  });
}

async function listTransactions(userId, { txnType, limit = 50 } = {}) {
  const where = { user_id: userId };
  if (txnType) where.txn_type = txnType;

  const rows = await prisma.demo_merchant_transactions.findMany({
    where,
    orderBy: { created_at: 'desc' },
    take: Math.min(Math.max(limit, 1), 200),
    include: {
      order: { select: { order_number: true, order_type: true, status: true } },
    },
  });

  return rows.map((row) => ({
    id: row.id,
    txnType: row.txn_type,
    amount: walletService.toNumber(row.amount),
    status: row.status,
    description: row.description,
    orderNumber: row.order?.order_number || null,
    orderStatus: row.order?.status || null,
    createdAt: row.created_at,
  }));
}

module.exports = {
  createPendingTransaction,
  markTransactionPaid,
  markTransactionStatus,
  listTransactions,
};
