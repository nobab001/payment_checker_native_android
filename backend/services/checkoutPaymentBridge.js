/**
 * Bridges checkout verify / vibe match → payment_sessions + per-init callback URLs.
 * Official Test / merchants pass successUrl + callbackUrl on pay/init (session), not gateway_layouts row.
 */

const prisma = require('../db/prisma');
const PaymentSessionEngine = require('../payment/session/payment-session');
const { buildMerchantCallbackV1 } = require('../payment/core/merchant-callback-v1');
const { PAYMENT_STATUS } = require('../payment/core/payment-status');
const { deliverHttpMerchantCallback } = require('../payment/callback/merchant-callback-http');
const { resolveInternalServerUrl, rewriteUrlForOrigin } = require('../utils/localServerUrl');

const WEBSITE_SELECT = {
  id: true,
  user_id: true,
  merchant_id: true,
  api_secret: true,
  callback_url: true,
  webhook_url: true,
  success_url: true,
  redirect_url: true,
};

async function loadSession(token) {
  if (!token) return null;
  return PaymentSessionEngine.getSession(String(token).trim());
}

function alignUrlToSessionOrigin(url, session) {
  const origin = session?.meta?.clientOrigin;
  if (!origin || !url) return url;
  return rewriteUrlForOrigin(url, origin);
}

function buildSuccessRedirectUrl(session, { trxId, amount } = {}) {
  if (!session?.successUrl) return null;
  try {
    const base = alignUrlToSessionOrigin(session.successUrl, session);
    const url = new URL(base);
    const trx = trxId || session.trxId || '';
    const amt = amount != null ? amount : session.amount;
    url.searchParams.set('trxId', trx);
    url.searchParams.set('amount', String(amt));
    url.searchParams.set('status', 'success');
    if (session.sessionToken) url.searchParams.set('session', session.sessionToken);
    return url.href;
  } catch {
    const sep = session.successUrl.includes('?') ? '&' : '?';
    const trx = trxId || session.trxId || '';
    const amt = amount != null ? amount : session.amount;
    let raw = `${alignUrlToSessionOrigin(session.successUrl, session)}${sep}trxId=${encodeURIComponent(trx)}&amount=${encodeURIComponent(amt)}&status=success`;
    if (session.sessionToken) raw += `&session=${encodeURIComponent(session.sessionToken)}`;
    return raw;
  }
}

async function notifySessionPaid(sessionToken, { history, trxId } = {}) {
  const session = await loadSession(sessionToken);
  if (!session) return { ok: false, reason: 'NO_SESSION' };

  const website = await prisma.gateway_layouts.findUnique({
    where: { id: session.websiteId },
    select: WEBSITE_SELECT,
  });
  if (!website) return { ok: false, reason: 'NO_WEBSITE' };

  const finalTrxId = trxId || history?.trx_id || session.trxId || null;

  if (session.status !== PAYMENT_STATUS.SUCCESS) {
    await PaymentSessionEngine.completeSession(session.sessionToken, {
      trx_id: finalTrxId,
      matched_history_id: history?.id || null,
    });
  }

  const callbackUrl = resolveInternalServerUrl(session.callbackUrl || website.callback_url);
  if (!callbackUrl) {
    return {
      ok: true,
      callback: false,
      reason: 'NO_CALLBACK_URL',
      redirectUrl: buildSuccessRedirectUrl(session, { trxId: finalTrxId }),
    };
  }

  const payload = buildMerchantCallbackV1({
    paymentId: session.sessionToken,
    merchantId: String(website.merchant_id || website.id),
    websiteId: String(website.id),
    provider: (history?.provider_tag || 'paycheck').toLowerCase(),
    providerTransactionId: finalTrxId,
    merchantTransactionId: session.orderId,
    amount: Number(session.amount),
    status: PAYMENT_STATUS.SUCCESS,
    traceId: session.traceId || session.sessionToken,
    orderId: session.orderId,
    sessionId: session.sessionToken,
    currency: session.currency || 'BDT',
  });

  deliverHttpMerchantCallback({
    url: callbackUrl,
    payload,
    website,
    traceId: session.traceId || session.sessionToken,
    sessionToken: session.sessionToken,
  }).catch((e) => console.error('[CHECKOUT BRIDGE] webhook error:', e.message));

  return {
    ok: true,
    callback: true,
    redirectUrl: buildSuccessRedirectUrl(session, { trxId: finalTrxId }),
  };
}

async function mergeSessionUrlsIntoLayout(apiData, sessionToken) {
  const session = await loadSession(sessionToken);
  if (!session) return apiData;
  const successUrl = alignUrlToSessionOrigin(session.successUrl || apiData.successUrl, session);
  const cancelUrl = alignUrlToSessionOrigin(session.cancelUrl || apiData.cancelUrl, session);
  let payload = {
    ...apiData,
    successUrl,
    cancelUrl,
    clientOrigin: session.meta?.clientOrigin || null,
    paymentSessionToken: session.sessionToken,
    orderId: session.orderId,
  };

  // Official Test Experience: visitor edits live in session.meta.demoOverrides
  // (never written to gateway_layouts). Apply on checkout load.
  const demoOverrides = session.meta?.demoOverrides;
  if (demoOverrides && typeof demoOverrides === 'object') {
    try {
      const { applyOverrides } = require('../official-website/services/session-store');
      payload = applyOverrides(payload, demoOverrides);
    } catch (e) {
      console.warn('[CHECKOUT BRIDGE] demoOverrides apply failed:', e.message);
    }
  }

  return payload;
}

module.exports = {
  loadSession,
  buildSuccessRedirectUrl,
  notifySessionPaid,
  mergeSessionUrlsIntoLayout,
  alignUrlToSessionOrigin,
};
