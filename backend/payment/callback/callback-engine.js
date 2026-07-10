/**
 * @file Gateway Callback Engine — idempotency, verify, state, normalize, merchant callback.
 * @module payment/callback/callback-engine
 */

const { getProvider } = require('../registry/provider-factory');
const { resolveProviderId } = require('../registry/provider-alias');
const { loadOfficialGateway } = require('../shared/gateway-config-loader');
const { loadMerchantByWebsiteId } = require('../shared/merchant-loader');
const PaymentSessionEngine = require('../session/payment-session');
const { isSessionExpired } = require('../shared/session-utils');
const prisma = require('../../db/prisma');
const { check, lock, complete } = require('../idempotency/idempotency-manager');
const {
  buildMerchantCallbackPayload,
  resolveCallbackTargets,
} = require('./merchant-callback-engine');
const {
  enqueueMerchantCallbacks,
  processOutboxBatch,
} = require('../reliability/merchant-callback-outbox');
const { emitPaymentEvent, PAYMENT_EVENTS } = require('../events/event-bus');
const { logPayment } = require('../logging/trace-logger');
const { runWithTrace } = require('../logging/trace-context');
const { recordLatency } = require('../monitoring/payment-monitor');
const { PAYMENT_STATUS } = require('../core/payment-status');
const { ProviderError } = require('../shared/provider-errors');
const { assertUniqueProviderTransaction } = require('../session/duplicate-guard');
const { assertTransition, toLegacyStatus } = require('../state/payment-state-machine');
const { PaymentError, PAY_ERROR_CODES } = require('../errors/error-registry');
const audit = require('../../services/auditLog');

const BKASH_DB_KEY = 'bkash_merchant';

function isBkashSession(session) {
  const p = String(session?.officialProvider || '').toLowerCase();
  return p === BKASH_DB_KEY || resolveProviderId(p) === 'bkash_live';
}

function idempotencyKey(sessionToken, trxId, event = 'callback') {
  return `bkash:${event}:${sessionToken}:${trxId || 'unknown'}`;
}

/**
 * Core bkash callback processing (shared by dedicated route + legacy gateway-callback).
 * @param {import('express').Request} req
 * @param {{ sessionToken?: string, respond?: 'redirect'|'json' }} opts
 */
async function processBkashCallback(req, opts = {}) {
  const started = Date.now();
  const adapter = getProvider('bkash_live');
  const parsed = await adapter.callback(req);

  const sessionToken = opts.sessionToken || parsed.sessionToken;
  if (!sessionToken) {
    throw new PaymentError(PAY_ERROR_CODES.INVALID_SESSION);
  }

  const session = await PaymentSessionEngine.getSession(sessionToken);
  if (!session) {
    throw new PaymentError(PAY_ERROR_CODES.INVALID_SESSION);
  }

  const traceId = session.traceId || `ptr_${sessionToken.slice(-12)}`;

  return runWithTrace(traceId, async () => {
    logPayment(traceId, 'Callback', 'received', { sessionToken, provider: 'bkash_live' });
    emitPaymentEvent(PAYMENT_EVENTS.GATEWAY_CALLBACK_RECEIVED, {
      traceId, sessionToken, providerId: 'bkash_live',
    });

    if (!isBkashSession(session)) {
      throw new ProviderError('NOT_BKASH_SESSION', 'Session is not bkash_live');
    }

    if (session.status === PAYMENT_STATUS.SUCCESS) {
      logPayment(traceId, 'Idempotency', 'session.already.completed', { sessionToken });
      return {
        duplicate: true,
        session,
        traceId,
        redirectUrl: buildSuccessRedirect(session, session.trxId),
      };
    }

    if (isSessionExpired(session)) {
      await PaymentSessionEngine.expireSession(sessionToken).catch(() => {});
      throw new PaymentError(PAY_ERROR_CODES.PAYMENT_EXPIRED);
    }

    const idemKey = idempotencyKey(sessionToken, parsed.trxId);
    const existing = await check(idemKey);
    if (existing.exists && existing.status === 'completed') {
      logPayment(traceId, 'Idempotency', 'duplicate.skip', { idemKey });
      return {
        duplicate: true,
        session,
        traceId,
        redirectUrl: buildSuccessRedirect(session, parsed.trxId),
      };
    }

    const locked = await lock(idemKey);
    if (!locked.acquired) {
      const again = await check(idemKey);
      if (again.exists) {
        return {
          duplicate: true,
          session,
          traceId,
          redirectUrl: buildSuccessRedirect(session, parsed.trxId),
        };
      }
      throw new PaymentError(PAY_ERROR_CODES.DUPLICATE_CALLBACK);
    }

    const gateway = await loadOfficialGateway(session.websiteId, 'bkash_live');
    const website = await loadMerchantByWebsiteId(session.websiteId);
    let cfg = {};
    try {
      cfg = gateway?.config_json ? JSON.parse(gateway.config_json) : {};
    } catch (_) {
      cfg = {};
    }
    const sigOk = adapter.verifySignature(
      parsed.raw,
      parsed.signature,
      {
        callbackSecret: cfg.callbackSecret || cfg.callback_secret,
      },
      website?.api_secret,
    );

    if (!sigOk) {
      throw new PaymentError(PAY_ERROR_CODES.INVALID_SIGNATURE);
    }

    const paymentCtx = {
      traceId,
      sessionToken,
      websiteId: session.websiteId,
      userId: session.userId,
      amount: session.amount,
      currency: session.currency || 'BDT',
      orderId: session.orderId,
      merchantConfig: gateway,
      callbackUrl: parsed.raw?.callback_url,
      meta: {},
    };

    const paymentRef = { providerReference: session.meta?.providerReference };
    const verified = await adapter.verify(paymentCtx, paymentRef);
    logPayment(traceId, 'Callback', 'verified', { verified: verified.verified });
    emitPaymentEvent(PAYMENT_EVENTS.PAYMENT_VERIFIED, {
      traceId, sessionToken, status: verified.status,
    });

    const normalized = await adapter.normalize(
      { ...parsed, success: parsed.success && verified.verified },
      paymentCtx,
    );

    if (normalized.status === PAYMENT_STATUS.SUCCESS && normalized.providerTransactionId) {
      await assertUniqueProviderTransaction(
        sessionToken,
        normalized.providerTransactionId,
        session.websiteId,
      );
    }

    let updated = session;
    if (normalized.status === PAYMENT_STATUS.SUCCESS) {
      if (session.status === PAYMENT_STATUS.SUCCESS) {
        await complete(idemKey, { status: 'completed', duplicate: true });
        return { duplicate: true, session, traceId, redirectUrl: buildSuccessRedirect(session, parsed.trxId) };
      }

      const payload = buildMerchantCallbackPayload({ website, session, normalized, traceId });
      const targets = website ? resolveCallbackTargets(session, website) : [];

      try {
        assertTransition(session.status, PAYMENT_STATUS.SUCCESS);
        await prisma.$transaction(async (tx) => {
          await tx.payment_sessions.update({
            where: { session_token: sessionToken },
            data: {
              status: toLegacyStatus(PAYMENT_STATUS.SUCCESS),
              trx_id: normalized.providerTransactionId || parsed.trxId || null,
              completed_at: new Date(),
            },
          });

          if (website && targets.length) {
            await enqueueMerchantCallbacks(tx, {
              sessionToken,
              websiteId: session.websiteId,
              traceId,
              targets,
              payload,
            });
          }
        });
        updated = await PaymentSessionEngine.getSession(sessionToken);
        emitPaymentEvent(PAYMENT_EVENTS.PAYMENT_COMPLETED, {
          traceId, sessionToken, status: PAYMENT_STATUS.SUCCESS, websiteId: session.websiteId,
        });
      } catch (err) {
        if (err.code === 'P2002') {
          throw new PaymentError(PAY_ERROR_CODES.DUPLICATE_CALLBACK, {
            providerTransactionId: normalized.providerTransactionId,
          });
        }
        throw err;
      }

      if (website && targets.length) {
        await processOutboxBatch({ sessionToken, limit: 10, workerId: 'inline-callback' });
        audit.log({
          eventType: 'payment.merchant_callback.scheduled',
          entityType: 'payment_session',
          entityId: sessionToken,
          websiteId: session.websiteId,
          userId: session.userId,
          status: 'outbox',
          detail: { traceId, targets: targets.length, providerTransactionId: normalized.providerTransactionId },
        });
      }
    } else {
      updated = await PaymentSessionEngine.cancelSession(sessionToken);
      emitPaymentEvent(PAYMENT_EVENTS.PAYMENT_FAILED, { traceId, sessionToken });
    }

    await complete(idemKey, {
      status: 'completed',
      paymentStatus: normalized.status,
      trxId: normalized.providerTransactionId,
    });

    recordLatency('callback', Date.now() - started, { traceId });

    return {
      duplicate: false,
      session: updated,
      normalized,
      traceId,
      redirectUrl: normalized.status === PAYMENT_STATUS.SUCCESS
        ? buildSuccessRedirect(updated, normalized.providerTransactionId)
        : (updated.cancelUrl || '/'),
    };
  });
}

function buildSuccessRedirect(session, trxId) {
  if (!session.successUrl) return '/';
  const sep = session.successUrl.includes('?') ? '&' : '?';
  return `${session.successUrl}${sep}trxId=${encodeURIComponent(trxId || '')}&amount=${encodeURIComponent(session.amount)}&status=success`;
}

module.exports = {
  processBkashCallback,
  isBkashSession,
  idempotencyKey,
};
