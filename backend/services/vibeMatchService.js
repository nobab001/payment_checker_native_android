/**
 * Vibe Mode matching — links waiting checkout_vibe_requests to sms_history rows.
 *
 * Phone match policy (security):
 *  1. Full 11-digit match → accept
 *  2. Otherwise masked payer must match customer first-4 AND last-3 digits
 *  3. Amount must match (±0.01)
 */

const prisma = require('../db/prisma');
const merchantCallback = require('./merchantCallback');
const checkoutPaymentBridge = require('./checkoutPaymentBridge');

const MERCHANT_CALLBACK_SELECT = {
  id: true,
  user_id: true,
  merchant_id: true,
  redirect_url: true,
  success_url: true,
  callback_url: true,
  webhook_url: true,
  api_secret: true,
  allow_payment_type_callback: true,
  allow_commission_callback: true,
  receive_payment_type: true,
  receive_commission: true,
};

const MASK_CHAR = /[*xX]/;

function digitsOnly(value) {
  return String(value || '').replace(/\D/g, '').replace(/^880/, '');
}

function normalizeBdNumber(value) {
  const d = digitsOnly(value);
  return d.length >= 11 ? d.slice(-11) : d;
}

function amountsMatch(a, b) {
  const left = Number(a);
  const right = Number(b);
  if (!Number.isFinite(left) || !Number.isFinite(right)) return false;
  return Math.abs(left - right) < 0.01;
}

/**
 * Extract visible prefix (4) and suffix (3) from a masked token like 0189XXXX541.
 */
function parseMaskedSegments(raw) {
  const token = String(raw || '').trim();
  if (!token) return null;

  // 0189XXXX541 / 0189****541 / 0189XXX541
  const withMaskChars = token.match(/^(0\d{3})[*xX]+(\d{3})$/i);
  if (withMaskChars) {
    return { prefix4: withMaskChars[1], suffix3: withMaskChars[2] };
  }

  const digits = digitsOnly(token);
  if (digits.length === 11 && !MASK_CHAR.test(token)) {
    return { full: digits };
  }
  if (digits.length >= 7 && digits.length < 11) {
    return { prefix4: digits.slice(0, 4), suffix3: digits.slice(-3) };
  }

  return null;
}

/**
 * Match customer checkout number to payer number from SMS (full or masked).
 * Masked: first 4 + last 3 digits must both match (7-digit fingerprint).
 */
function phoneNumbersMatchForVibe(customerNumber, payerNumber) {
  const customer = normalizeBdNumber(customerNumber);
  if (!customer || customer.length !== 11) return false;

  const payerRaw = String(payerNumber || '').trim();
  if (!payerRaw) return false;

  const customerPrefix4 = customer.slice(0, 4);
  const customerSuffix3 = customer.slice(-3);

  const payerDigits = digitsOnly(payerRaw);

  // 1) Full 11-digit match
  if (payerDigits.length === 11 && customer === payerDigits) return true;
  if (!MASK_CHAR.test(payerRaw) && payerDigits.length === 11 && customer === payerDigits) {
    return true;
  }

  // 2) Masked match — first 4 + last 3
  const segments = parseMaskedSegments(payerRaw);
  if (!segments) return false;
  if (segments.full) return segments.full === customer;

  return (
    segments.prefix4 === customerPrefix4 &&
    segments.suffix3 === customerSuffix3
  );
}

function extractPayerFromSmsBody(body) {
  if (!body || typeof body !== 'string') return '';
  const patterns = [
    // Cash Out / Cash In / Received — 0189XXXX541 style
    /from\s+(0\d{3}[*xX]{2,}\d{3})/i,
    /from\s+(0\d{3}\*+\d{3})/i,
    // Full 11-digit
    /from\s+(0\d{10})/i,
    /from\s+(?:A\/C[:\s]*)?([0-9]{3}[*xX]+[0-9]{2,})/i,
    /from\s+([\d*xX]{7,})/i,
  ];
  for (const regex of patterns) {
    const match = regex.exec(body);
    if (match?.[1]) return match[1];
  }
  return '';
}

function payerCandidatesFromHistory(history) {
  const out = new Set();
  if (history.sender_number) out.add(String(history.sender_number));
  if (history.full_sms) {
    const fromBody = extractPayerFromSmsBody(history.full_sms);
    if (fromBody) out.add(fromBody);
  }
  return [...out];
}

function payerMatchesCustomer(customerNumber, history) {
  const candidates = payerCandidatesFromHistory(history);
  return candidates.some((payer) => phoneNumbersMatchForVibe(customerNumber, payer));
}

async function claimHistoryForVibe(historyId, merchantId) {
  return prisma.sms_history.updateMany({
    where: { id: historyId, is_used: 0 },
    data: { is_used: 1, used_at: new Date(), used_by_merchant_id: merchantId },
  });
}

async function applyVibeMatch(vibeRequest, history, merchant) {
  const claimed = await claimHistoryForVibe(history.id, merchant.id);
  if (claimed.count === 0) return false;

  await prisma.checkout_vibe_requests.update({
    where: { id: vibeRequest.id },
    data: {
      status: 'matched',
      matched_trx_id: history.trx_id,
      matched_history_id: history.id,
      matched_at: new Date(),
    },
  });

  console.log(`[VIBE MATCH] Request ${vibeRequest.id} matched Trx ${history.trx_id} (৳${history.amount})`);

  if (vibeRequest.payment_session_token) {
    await checkoutPaymentBridge
      .notifySessionPaid(vibeRequest.payment_session_token, { history, trxId: history.trx_id })
      .catch((e) => console.error('[VIBE MATCH] session notify error:', e.message));
  } else {
    await merchantCallback
      .sendMerchantCallback(merchant, history, 'verified')
      .catch((e) => console.error('[VIBE MATCH] callback error:', e.message));
  }
  return true;
}

/**
 * Called by SMS worker right after a new history row is saved.
 */
async function matchVibeForHistory(userId, history) {
  try {
    if (!history || history.amount == null) return;
    if (!payerCandidatesFromHistory(history).length) return;

    const candidates = await prisma.checkout_vibe_requests.findMany({
      where: {
        status: 'waiting',
        expires_at: { gt: new Date() },
        gateway_layouts: { user_id: userId },
      },
      include: { gateway_layouts: { select: MERCHANT_CALLBACK_SELECT } },
      orderBy: { created_at: 'asc' },
    });

    for (const vibeRequest of candidates) {
      if (!amountsMatch(history.amount, vibeRequest.amount)) continue;
      if (!payerMatchesCustomer(vibeRequest.customer_number, history)) continue;
      const ok = await applyVibeMatch(vibeRequest, history, vibeRequest.gateway_layouts);
      if (ok) return;
    }
  } catch (e) {
    console.error('[VIBE MATCH] error:', e.message);
  }
}

/**
 * Poll-time retry — SMS may exist before match logic ran or sender was masked.
 */
async function attemptRetroactiveVibeMatch(requestId) {
  try {
    const request = await prisma.checkout_vibe_requests.findUnique({
      where: { id: requestId },
      include: { gateway_layouts: { select: MERCHANT_CALLBACK_SELECT } },
    });
    if (!request || request.status !== 'waiting') return false;
    if (request.expires_at < new Date()) return false;

    const userId = request.gateway_layouts.user_id;
    const targetAmount = Number(request.amount);

    const histories = await prisma.sms_history.findMany({
      where: {
        user_id: userId,
        is_used: 0,
        sms_timestamp: { gte: new Date(request.created_at.getTime() - 120_000) },
      },
      orderBy: { sms_timestamp: 'desc' },
      take: 50,
      select: {
        id: true,
        amount: true,
        trx_id: true,
        provider_tag: true,
        sender_number: true,
        sms_timestamp: true,
        full_sms: true,
      },
    });

    for (const history of histories) {
      if (!amountsMatch(history.amount, targetAmount)) continue;
      if (!payerMatchesCustomer(request.customer_number, history)) continue;
      const ok = await applyVibeMatch(request, history, request.gateway_layouts);
      if (ok) return true;
    }
    return false;
  } catch (e) {
    console.error('[VIBE MATCH] retroactive error:', e.message);
    return false;
  }
}

module.exports = {
  matchVibeForHistory,
  attemptRetroactiveVibeMatch,
  phoneNumbersMatchForVibe,
  amountsMatch,
  normalizeBdNumber,
  parseMaskedSegments,
};
