/**
 * @file Merchant Callback Engine — builds MerchantCallbackV1 payloads.
 * @module payment/callback/merchant-callback-engine
 */

const { buildMerchantCallbackV1 } = require('../core/merchant-callback-v1');
const { logPayment } = require('../logging/trace-logger');
const { deliverHttpMerchantCallback } = require('./merchant-callback-http');

function buildMerchantCallbackPayload({ website, session, normalized, traceId }) {
  return buildMerchantCallbackV1({
    paymentId: session.sessionToken,
    merchantId: String(website.merchant_id || website.id),
    websiteId: String(website.id),
    provider: normalized.provider || 'bkash_live',
    providerTransactionId: normalized.providerTransactionId,
    merchantTransactionId: normalized.merchantTransactionId || session.orderId,
    amount: normalized.amount,
    status: normalized.status,
    type: null,
    commission: 0,
    traceId,
    timestamp: normalized.completedAt || new Date().toISOString(),
    orderId: session.orderId,
    sessionId: session.sessionToken,
    currency: normalized.currency || 'BDT',
  });
}

function resolveCallbackTargets(session, website) {
  const targets = [];
  const callbackUrl = session.callbackUrl || website.callback_url;
  const webhookUrl = session.webhookUrl || website.webhook_url;
  if (callbackUrl) targets.push(callbackUrl);
  if (webhookUrl && webhookUrl !== callbackUrl) targets.push(webhookUrl);
  return targets;
}

/** @deprecated Use outbox enqueue + worker. Kept for failure-injection gate test. */
async function deliverMerchantCallback({ website, session, normalized, traceId, retryPolicy }) {
  const payload = buildMerchantCallbackPayload({ website, session, normalized, traceId });
  const targets = resolveCallbackTargets(session, website);

  if (!targets.length) {
    logPayment(traceId, 'MerchantCallback', 'skip.no_url', { sessionToken: session.sessionToken });
    return { delivered: false, reason: 'NO_URL', payload };
  }

  const results = [];
  for (const url of targets) {
    const outcome = await deliverHttpMerchantCallback({
      url, payload, website, traceId, sessionToken: session.sessionToken, retryPolicy,
    });
    results.push({ url, ...outcome });
  }

  return { payload, results };
}

module.exports = {
  buildMerchantCallbackPayload,
  resolveCallbackTargets,
  deliverMerchantCallback,
};
