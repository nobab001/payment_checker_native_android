/**
 * Website purpose architecture:
 *   website: add_balance | payment | both  (lock-once after confirm)
 *   session: add_balance | payment
 *
 * Business rules (v2):
 *   Add Balance — customer always sends checkoutAmount; walletCredit is info-only.
 *   Payment     — customer must cover expectedPayable (charge/commission adjusted);
 *                 shortfall → multi-txn settlement; overpay → SUCCESS + overPaid.
 */

const WEBSITE_PURPOSES = new Set(['add_balance', 'payment', 'both']);
const SESSION_PURPOSES = new Set(['add_balance', 'payment']);

/** Max SMS/Trx parts that may contribute to one Payment settlement. */
const MAX_SETTLEMENT_TXNS = 5;

function normalizeWebsitePurpose(value) {
  const s = String(value || '').toLowerCase().trim();
  if (s === 'payment' || s === 'pay') return 'payment';
  if (s === 'both') return 'both';
  if (s === 'add_balance' || s === 'balance') return 'add_balance';
  return 'add_balance';
}

function normalizeSessionPurpose(value) {
  const s = String(value || '').toLowerCase().trim();
  if (s === 'payment' || s === 'pay') return 'payment';
  if (s === 'add_balance' || s === 'balance') return 'add_balance';
  return null;
}

/**
 * Round money for customer-facing payable (Payment mode).
 * Rule: fractional paisa < 0.50 → floor Taka; >= 0.50 → ceil Taka.
 * Same rule for every provider (global policy).
 */
function roundPayableTaka(amount) {
  const n = Number(amount);
  if (!Number.isFinite(n) || n <= 0) return 0;
  const floor = Math.floor(n);
  const frac = n - floor;
  if (frac < 0.5) return floor;
  return Math.ceil(n);
}

/** Standard 2-decimal money (wallet credit / accounting). */
function roundMoney2(amount) {
  return Math.round(Number(amount) * 100) / 100;
}

/**
 * Resolve session purpose from website setting + optional request purpose.
 *
 * Decisions:
 *   - both + missing purpose → PURPOSE_REQUIRED (hard error)
 *   - both + invalid purpose → PURPOSE_INVALID (hard error)
 *   - fixed mode → website setting wins (ignore client mismatch)
 */
function resolveInitPurpose(websitePurpose, requestedPurpose) {
  const wp = normalizeWebsitePurpose(websitePurpose);
  const raw = requestedPurpose == null ? '' : String(requestedPurpose).trim();
  const req = normalizeSessionPurpose(requestedPurpose);

  if (wp === 'both') {
    if (!raw) {
      return { ok: false, error: 'PURPOSE_REQUIRED' };
    }
    if (!req) {
      return { ok: false, error: 'PURPOSE_INVALID' };
    }
    return { ok: true, purpose: req };
  }

  return { ok: true, purpose: wp };
}

function isHeaderBaseAmountOnly(sessionPurpose) {
  return normalizeSessionPurpose(sessionPurpose) === 'payment';
}

/**
 * Compute incentive outcome for a provider at a base amount.
 * @returns {{ commission, charge, net, walletCredit, expectedPayable, kind }}
 */
function computePurposeAmounts(baseAmount, commission, charge, sessionPurpose) {
  const base = roundMoney2(baseAmount);
  const comm = roundMoney2(commission || 0);
  const chg = roundMoney2(charge || 0);
  const net = roundMoney2(comm - chg);
  const purpose = normalizeSessionPurpose(sessionPurpose) || 'add_balance';

  // Wallet credit = what merchant should add to customer wallet (info only).
  const walletCredit = roundMoney2(Math.max(0, base + net));

  // Payment mode: customer must send adjusted amount (rounded to whole Taka).
  let expectedPayable = base;
  if (purpose === 'payment') {
    if (net > 0) {
      // Commission: send less
      expectedPayable = roundPayableTaka(Math.max(0, base - net));
    } else if (net < 0) {
      // Charge: send more
      expectedPayable = roundPayableTaka(base + Math.abs(net));
    } else {
      expectedPayable = roundPayableTaka(base);
    }
  }

  let kind = null;
  if (net > 0) kind = 'commission';
  else if (net < 0) kind = 'charge';

  return {
    purpose,
    checkoutAmount: base,
    orderAmount: base,
    commission: comm,
    charge: chg,
    net,
    walletCredit,
    expectedPayable: purpose === 'payment' ? expectedPayable : base,
    customerSendAmount: purpose === 'payment' ? expectedPayable : base,
    kind,
  };
}

function purposeLabelBn(purpose) {
  const p = normalizeWebsitePurpose(purpose);
  if (p === 'payment') return 'Pay';
  if (p === 'both') return 'Both';
  return 'Add Balance';
}

module.exports = {
  WEBSITE_PURPOSES,
  SESSION_PURPOSES,
  MAX_SETTLEMENT_TXNS,
  normalizeWebsitePurpose,
  normalizeSessionPurpose,
  resolveInitPurpose,
  isHeaderBaseAmountOnly,
  roundPayableTaka,
  roundMoney2,
  computePurposeAmounts,
  purposeLabelBn,
};
