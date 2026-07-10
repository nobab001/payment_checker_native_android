const prisma = require('../../db/prisma');

function toNumber(value) {
  return Number(value || 0);
}

async function getBalance(userId) {
  const wallet = await prisma.demo_merchant_wallets.findUnique({ where: { user_id: userId } });
  return toNumber(wallet?.balance);
}

async function creditWallet({ userId, amount, referenceType, referenceId, description }) {
  const credit = toNumber(amount);
  if (!(credit > 0)) {
    const err = new Error('Invalid credit amount');
    err.code = 'VALIDATION_ERROR';
    throw err;
  }

  return prisma.$transaction(async (tx) => {
    const wallet = await tx.demo_merchant_wallets.findUnique({ where: { user_id: userId } });
    if (!wallet) {
      const err = new Error('Wallet not found');
      err.code = 'NOT_FOUND';
      throw err;
    }

    const newBalance = toNumber(wallet.balance) + credit;
    await tx.demo_merchant_wallets.update({
      where: { user_id: userId },
      data: { balance: newBalance },
    });

    await tx.demo_merchant_wallet_ledger.create({
      data: {
        user_id: userId,
        amount: credit,
        balance_after: newBalance,
        entry_type: 'credit',
        reference_type: referenceType,
        reference_id: referenceId,
        description,
      },
    });

    return newBalance;
  });
}

async function getLedger(userId, { limit = 50 } = {}) {
  const rows = await prisma.demo_merchant_wallet_ledger.findMany({
    where: { user_id: userId },
    orderBy: { created_at: 'desc' },
    take: Math.min(Math.max(limit, 1), 200),
  });

  return rows.map((row) => ({
    id: row.id,
    amount: toNumber(row.amount),
    balanceAfter: toNumber(row.balance_after),
    entryType: row.entry_type,
    referenceType: row.reference_type,
    referenceId: row.reference_id,
    description: row.description,
    createdAt: row.created_at,
  }));
}

module.exports = {
  getBalance,
  creditWallet,
  getLedger,
  toNumber,
};
