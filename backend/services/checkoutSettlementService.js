/**
 * Payment-mode multi-transaction settlement.
 *
 * Add Balance never uses this — verify is always exact checkoutAmount.
 * Payment accumulates Trx amounts until received_sum >= expected_payable
 * (overpay allowed → SUCCESS with overPaid). Max MAX_SETTLEMENT_TXNS parts.
 */

const prisma = require('../db/prisma');
const { MAX_SETTLEMENT_TXNS, roundMoney2 } = require('./websitePurpose');

function parseParts(raw) {
  if (!raw) return [];
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw;
    return Array.isArray(arr) ? arr : [];
  } catch (_) {
    return [];
  }
}

async function getByKey(settlementKey) {
  const rows = await prisma.$queryRawUnsafe(
    `SELECT * FROM checkout_settlements WHERE settlement_key = ? LIMIT 1`,
    settlementKey,
  );
  return rows[0] || null;
}

/**
 * Build a stable key for a checkout attempt.
 * Prefer session token; else apiKey+orderAmount+clientAttemptId.
 */
function buildSettlementKey({ sessionToken, apiKey, orderAmount, attemptId }) {
  if (sessionToken) return `sess:${String(sessionToken).slice(0, 80)}`;
  const amt = roundMoney2(orderAmount);
  const attempt = String(attemptId || 'default').slice(0, 40);
  return `pub:${String(apiKey).slice(0, 40)}:${amt}:${attempt}`;
}

async function openOrGetSettlement({
  settlementKey,
  websiteId,
  sessionToken,
  orderAmount,
  expectedPayable,
  purpose = 'payment',
}) {
  const existing = await getByKey(settlementKey);
  if (existing) return existing;

  await prisma.$executeRawUnsafe(
    `INSERT INTO checkout_settlements
      (settlement_key, website_id, session_token, purpose, order_amount, expected_payable, received_sum, status, parts_json)
     VALUES (?, ?, ?, ?, ?, ?, 0, 'open', '[]')`,
    settlementKey,
    websiteId,
    sessionToken || null,
    purpose,
    roundMoney2(orderAmount),
    roundMoney2(expectedPayable),
  );
  return getByKey(settlementKey);
}

/**
 * Apply one verified SMS amount to an open settlement.
 * @returns settlement result object for UI + callback
 */
async function applyPart(settlementKey, { trxId, amount, historyId, providerTag }) {
  const row = await getByKey(settlementKey);
  if (!row) return { ok: false, error: 'SETTLEMENT_NOT_FOUND' };
  if (row.status === 'completed') {
    return {
      ok: true,
      alreadyCompleted: true,
      status: 'SUCCESS',
      orderAmount: Number(row.order_amount),
      expectedPayable: Number(row.expected_payable),
      receivedAmount: Number(row.received_sum),
      remaining: 0,
      overPaid: Math.max(0, roundMoney2(Number(row.received_sum) - Number(row.expected_payable))),
      transactions: parseParts(row.parts_json),
    };
  }

  const parts = parseParts(row.parts_json);
  if (parts.some((p) => String(p.trxId).toUpperCase() === String(trxId).toUpperCase())) {
    return { ok: false, error: 'TRX_ALREADY_IN_SETTLEMENT', message: 'এই TrxID ইতিমধ্যে যোগ হয়েছে।' };
  }
  if (parts.length >= MAX_SETTLEMENT_TXNS) {
    return {
      ok: false,
      error: 'MAX_TXNS_REACHED',
      message: `সর্বোচ্চ ${MAX_SETTLEMENT_TXNS}টি ট্রানজেকশন যোগ করা যাবে।`,
    };
  }

  const amt = roundMoney2(amount);
  parts.push({
    trxId: String(trxId).toUpperCase(),
    amount: amt,
    historyId: historyId || null,
    at: new Date().toISOString(),
  });

  const receivedSum = roundMoney2(parts.reduce((s, p) => s + Number(p.amount), 0));
  const expected = roundMoney2(row.expected_payable);
  const remaining = roundMoney2(Math.max(0, expected - receivedSum));
  const overPaid = roundMoney2(Math.max(0, receivedSum - expected));
  const complete = receivedSum + 0.001 >= expected; // tiny epsilon

  await prisma.$executeRawUnsafe(
    `UPDATE checkout_settlements
        SET received_sum = ?, parts_json = ?, status = ?, provider_tag = COALESCE(?, provider_tag),
            completed_at = ?, updated_at = NOW()
      WHERE settlement_key = ?`,
    receivedSum,
    JSON.stringify(parts),
    complete ? 'completed' : 'open',
    providerTag || null,
    complete ? new Date() : null,
    settlementKey,
  );

  return {
    ok: true,
    status: complete ? 'SUCCESS' : 'PARTIAL',
    orderAmount: Number(row.order_amount),
    expectedPayable: expected,
    receivedAmount: receivedSum,
    remaining,
    overPaid,
    transactions: parts,
    partsUsed: parts.length,
    maxParts: MAX_SETTLEMENT_TXNS,
  };
}

module.exports = {
  buildSettlementKey,
  openOrGetSettlement,
  applyPart,
  getByKey,
  MAX_SETTLEMENT_TXNS,
};
