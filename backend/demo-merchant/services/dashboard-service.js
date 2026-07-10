const prisma = require('../../db/prisma');
const walletService = require('./wallet-service');
const orderService = require('./order-service');
const transactionService = require('./transaction-service');

async function getDashboard(userId) {
  const [wallet, recentOrders, recentTransactions, orderStats] = await Promise.all([
    prisma.demo_merchant_wallets.findUnique({ where: { user_id: userId } }),
    orderService.listOrders(userId, { limit: 8 }),
    transactionService.listTransactions(userId, { limit: 8 }),
    prisma.demo_merchant_orders.groupBy({
      by: ['status'],
      where: { user_id: userId },
      _count: { _all: true },
      _sum: { amount: true },
    }),
  ]);

  const stats = {
    pending: 0,
    paid: 0,
    failed: 0,
    cancelled: 0,
    totalPaidAmount: 0,
  };

  for (const row of orderStats) {
    stats[row.status] = row._count._all;
    if (row.status === 'paid') {
      stats.totalPaidAmount = walletService.toNumber(row._sum.amount);
    }
  }

  const paidTxns = await prisma.demo_merchant_transactions.count({
    where: { user_id: userId, status: 'paid' },
  });

  return {
    walletBalance: walletService.toNumber(wallet?.balance),
    recentOrders,
    recentTransactions,
    paymentStatistics: {
      ordersByStatus: stats,
      completedTransactions: paidTxns,
    },
  };
}

module.exports = { getDashboard };
