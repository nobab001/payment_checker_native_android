/**
 * @file Payment Flow Engine — redirect, callback, init, status (orchestration).
 * @module payment/engine/payment-flow-engine
 */

const crypto = require('crypto');
const prisma = require('../../db/prisma');
const audit = require('../../services/auditLog');
const { createTraceId, logPayment } = require('../logging/trace-logger');
const { runWithTrace } = require('../logging/trace-context');
const { isSessionExpired, baseUrlFromRequest } = require('../shared/session-utils');
const { loadMerchantByApiKey } = require('../shared/merchant-loader');
const { loadOfficialGateway } = require('../shared/gateway-config-loader');
const PaymentSessionEngine = require('../session/payment-session');
const RedirectService = require('../redirect/redirect-service');
const { getProvider } = require('../registry/provider-factory');
const { processBkashCallback, isBkashSession } = require('../callback/callback-engine');
const { emitPaymentEvent, PAYMENT_EVENTS } = require('../events/event-bus');
const { recordLatency } = require('../monitoring/payment-monitor');
const { PAYMENT_STATUS } = require('../core/payment-status');
const { fromProviderError, PaymentError, PAY_ERROR_CODES } = require('../errors/error-registry');
const { ProviderError } = require('../shared/provider-errors');

const OFFICIAL_PROVIDERS = new Set([
  'bkash_merchant', 'nagad_merchant', 'rocket_merchant', 'sslcommerz', 'card', 'bank',
]);

function timingSafeEqual(a, b) {
  const ba = Buffer.from(String(a || ''));
  const bb = Buffer.from(String(b || ''));
  if (ba.length !== bb.length) return false;
  return crypto.timingSafeEqual(ba, bb);
}

function handleHttpError(res, err) {
  if (err instanceof PaymentError) {
    const { body, httpStatus } = fromProviderError(err.payCode);
    return res.status(httpStatus).json(body);
  }
  if (err instanceof ProviderError) {
    const { body, httpStatus } = fromProviderError(err);
    return res.status(httpStatus).json(body);
  }
  console.error('[PaymentFlowEngine]', err);
  const { body, httpStatus } = fromProviderError({ code: 'INTERNAL_ERROR' });
  return res.status(httpStatus).json(body);
}

const PaymentFlowEngine = {
  OFFICIAL_PROVIDERS,

  async initMerchantPayment(req, res) {
    try {
      const apiKey = req.headers['x-api-key'] || req.body.apiKey;
      if (!apiKey) {
        return res.status(401).json({ success: false, error: 'MISSING_API_KEY' });
      }

      const website = await loadMerchantByApiKey(apiKey);
      if (!website) {
        return res.status(403).json({ success: false, error: 'INVALID_API_KEY' });
      }

      const signature = req.headers['x-signature'];
      if (signature) {
        const raw = JSON.stringify(req.body || {});
        const expected = crypto.createHmac('sha256', website.api_secret || '').update(raw).digest('hex');
        if (!timingSafeEqual(signature, expected)) {
          return res.status(401).json({ success: false, error: 'INVALID_SIGNATURE' });
        }
      }

      const amount = parseFloat(req.body.amount);
      if (!(amount > 0)) {
        return res.status(400).json({ success: false, error: 'INVALID_AMOUNT' });
      }

      let channel = (req.body.channel || 'paycheck').toLowerCase();
      let officialProvider = null;

      if (channel === 'official') {
        officialProvider = (req.body.provider || '').toLowerCase();
        if (!OFFICIAL_PROVIDERS.has(officialProvider)) {
          return res.status(400).json({ success: false, error: 'INVALID_OFFICIAL_PROVIDER' });
        }
        const gw = await loadOfficialGateway(website.id, officialProvider);
        if (!gw) {
          return res.status(400).json({ success: false, error: 'OFFICIAL_PROVIDER_NOT_CONFIGURED' });
        }
      } else {
        channel = 'paycheck';
      }

      const traceId = createTraceId();
      const expiresInSec = Math.min(Math.max(parseInt(req.body.expiresInSec, 10) || 1800, 120), 86400);

      const session = await PaymentSessionEngine.createSession({
        websiteId: website.id,
        userId: website.user_id,
        officialProvider,
        amount,
        currency: (req.body.currency || 'BDT').toUpperCase().slice(0, 8),
        channel,
        orderId: req.body.orderId ? String(req.body.orderId).slice(0, 191) : null,
        customerNumber: req.body.customerNumber ? String(req.body.customerNumber).slice(0, 20) : null,
        successUrl: req.body.successUrl || website.success_url || website.redirect_url || null,
        cancelUrl: req.body.cancelUrl || website.cancel_url || null,
        callbackUrl: req.body.callbackUrl || website.callback_url || null,
        webhookUrl: req.body.webhookUrl || website.webhook_url || null,
        timeoutSec: expiresInSec,
        traceId,
        meta: req.body.meta || null,
      });

      audit.log({
        eventType: 'payment.init', entityType: 'payment_session', entityId: session.sessionToken,
        websiteId: website.id, userId: website.user_id, ip: req.ip, status: 'created',
        detail: { amount, channel, officialProvider, orderId: session.orderId },
      });

      emitPaymentEvent(PAYMENT_EVENTS.PAYMENT_CREATED, {
        traceId, sessionToken: session.sessionToken, websiteId: website.id, channel,
      });

      const checkoutUrl = RedirectService.buildPayTokenUrl(baseUrlFromRequest(req), session.sessionToken);
      return res.status(201).json({
        success: true,
        sessionToken: session.sessionToken,
        checkoutUrl,
        channel,
        amount,
        expiresAt: session.expiresAt,
      });
    } catch (error) {
      console.error('[PAY INIT] error:', error);
      return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
    }
  },

  async redirectPayment(req, res) {
    const { token } = req.params;
    try {
      const session = await PaymentSessionEngine.getSession(token);
      if (!session) return res.status(404).send('Payment session not found.');

      const traceId = session.traceId || createTraceId();

      return runWithTrace(traceId, async () => {
        if (isSessionExpired(session)) {
          await PaymentSessionEngine.expireSession(token).catch(() => {});
          return RedirectService.redirect(res, session.cancelUrl || '/');
        }

        if (session.status === PAYMENT_STATUS.SUCCESS && session.successUrl) {
          return RedirectService.redirect(res, session.successUrl);
        }

        if (session.channel === 'paycheck') {
          const website = await prisma.gateway_layouts.findUnique({
            where: { id: session.websiteId }, select: { api_key: true },
          });
          if (!website) return res.status(404).send('Merchant not found.');
          const q = new URLSearchParams({
            apiKey: website.api_key,
            amount: String(session.amount),
            session: token,
          });
          return RedirectService.redirect(res, `/checkout.html?${q.toString()}`);
        }

        if (isBkashSession(session)) {
          return this._redirectBkash(req, res, session, traceId);
        }

        return this._redirectLegacyOfficial(req, res, session, traceId);
      });
    } catch (error) {
      console.error('[PAY REDIRECT] error:', error);
      return res.status(500).send('Internal Server Error');
    }
  },

  async _redirectBkash(req, res, session, traceId) {
    const gateway = await loadOfficialGateway(session.websiteId, 'bkash_live');
    if (!gateway) {
      return RedirectService.redirect(res, session.cancelUrl || '/');
    }

    const adapter = getProvider('bkash_live');
    const base = baseUrlFromRequest(req);
    const callbackUrl = `${base}/api/payment/bkash/callback?token=${encodeURIComponent(session.sessionToken)}`;

    const paymentCtx = {
      traceId,
      sessionToken: session.sessionToken,
      websiteId: session.websiteId,
      userId: session.userId,
      amount: session.amount,
      currency: session.currency || 'BDT',
      orderId: session.orderId,
      successUrl: session.successUrl,
      cancelUrl: session.cancelUrl,
      merchantConfig: gateway,
      callbackUrl,
      meta: { customerNumber: session.customerNumber },
    };

    logPayment(traceId, 'Provider', 'redirect.start', { providerId: 'bkash_live' });
    await adapter.initialize(paymentCtx);
    const payment = await adapter.createPayment(paymentCtx);
    await PaymentSessionEngine.updateMeta(session.sessionToken, {
      providerReference: payment.providerReference,
      providerId: 'bkash_live',
    });

    const redirectUrl = await adapter.getRedirectUrl(paymentCtx, payment);
    await PaymentSessionEngine.markRedirected(session.sessionToken);

    emitPaymentEvent(PAYMENT_EVENTS.PAYMENT_REDIRECTED, {
      traceId, sessionToken: session.sessionToken, providerId: 'bkash_live',
    });
    recordLatency('redirect', 0, { traceId, providerId: 'bkash_live' });

    return RedirectService.redirect(res, redirectUrl);
  },

  async _redirectLegacyOfficial(req, res, session, traceId) {
    const gw = await loadOfficialGateway(session.websiteId, session.officialProvider);
    if (!gw) {
      return RedirectService.redirect(res, session.cancelUrl || '/');
    }

    const callbackUrl = `${baseUrlFromRequest(req)}/api/pay/${session.sessionToken}/gateway-callback`;
    const { applyUrlTemplate } = require('../shared/provider-utils');
    const redirectUrl = applyUrlTemplate(gw.redirect_url_template, {
      amount: session.amount,
      order_id: session.orderId || '',
      token: session.sessionToken,
      callback_url: callbackUrl,
      currency: session.currency,
      customer_number: session.customerNumber || '',
    });

    await PaymentSessionEngine.markRedirected(session.sessionToken);
    emitPaymentEvent(PAYMENT_EVENTS.PAYMENT_REDIRECTED, { traceId, sessionToken: session.sessionToken });
    return RedirectService.redirect(res, redirectUrl);
  },

  async bkashCallback(req, res) {
    try {
      const result = await processBkashCallback(req, {
        sessionToken: req.query.token || req.body?.token,
      });
      return RedirectService.redirect(res, result.redirectUrl || '/');
    } catch (err) {
      if (err instanceof PaymentError && err.payCode === PAY_ERROR_CODES.INVALID_SIGNATURE) {
        return handleHttpError(res, err);
      }
      if (err instanceof PaymentError && err.payCode === PAY_ERROR_CODES.PAYMENT_EXPIRED) {
        return handleHttpError(res, err);
      }
      console.error('[BKASH CALLBACK]', err);
      return res.status(500).json({ success: false, error: 'INTERNAL_ERROR', errorCode: PAY_ERROR_CODES.INTERNAL_ERROR });
    }
  },

  async legacyGatewayCallback(req, res) {
    const { token } = req.params;
    try {
      const session = await PaymentSessionEngine.getSession(token);
      if (!session) {
        return res.status(404).json({ success: false, error: 'SESSION_NOT_FOUND' });
      }

      if (isBkashSession(session)) {
        const result = await processBkashCallback(req, { sessionToken: token });
        return RedirectService.redirect(res, result.redirectUrl || '/');
      }

      return this._legacyNonBkashCallback(req, res, session);
    } catch (err) {
      console.error('[PAY CALLBACK] error:', err);
      return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
    }
  },

  async _legacyNonBkashCallback(req, res, session) {
    const src = { ...req.query, ...req.body };
    const outcome = (src.status || 'success').toLowerCase();
    const trxId = src.trxId || src.trx_id || src.transactionId || null;
    const token = session.sessionToken;

    if (session.status === PAYMENT_STATUS.SUCCESS) {
      return RedirectService.redirect(res, session.successUrl || '/');
    }

    if (outcome !== 'success') {
      await PaymentSessionEngine.cancelSession(token);
      audit.log({
        eventType: 'payment.failed', entityType: 'payment_session', entityId: token,
        websiteId: session.websiteId, userId: session.userId, ip: req.ip, status: 'failed',
        detail: { provider: session.officialProvider, trxId },
      });
      return RedirectService.redirect(res, session.cancelUrl || '/');
    }

    await PaymentSessionEngine.completeSession(token, { trx_id: trxId });
    audit.log({
      eventType: 'payment.completed', entityType: 'payment_session', entityId: token,
      websiteId: session.websiteId, userId: session.userId, ip: req.ip, status: 'success',
      detail: { provider: session.officialProvider, trxId, amount: session.amount },
    });

    const website = await loadMerchantByWebsiteId(session.websiteId);
    if (website) {
      const merchantCallback = require('../../services/merchantCallback');
      const synthetic = {
        amount: session.amount,
        trx_id: trxId,
        provider_tag: session.officialProvider,
        sender_number: session.customerNumber || null,
        sms_timestamp: new Date(),
        template_name: session.officialProvider,
      };
      const target = {
        ...website,
        callback_url: session.callbackUrl || website.callback_url,
        webhook_url: session.webhookUrl || website.webhook_url,
      };
      merchantCallback.sendMerchantCallback(target, synthetic, 'completed')
        .catch((e) => console.error('[PAY CALLBACK] merchant callback error:', e.message));
    }

    const success = session.successUrl
      ? `${session.successUrl}${session.successUrl.includes('?') ? '&' : '?'}trxId=${encodeURIComponent(trxId || '')}&amount=${encodeURIComponent(session.amount)}&status=success`
      : '/';
    return RedirectService.redirect(res, success);
  },

  async paymentStatus(req, res) {
    try {
      const { token } = req.params;
      const apiKey = req.headers['x-api-key'] || req.query.apiKey;
      const session = await PaymentSessionEngine.getSession(token);
      if (!session) return res.status(404).json({ success: false, error: 'SESSION_NOT_FOUND' });

      const website = await prisma.gateway_layouts.findUnique({
        where: { id: session.websiteId }, select: { api_key: true },
      });
      if (!website || !timingSafeEqual(apiKey, website.api_key)) {
        return res.status(403).json({ success: false, error: 'FORBIDDEN' });
      }

      let status = session.legacyStatus;
      if (isSessionExpired(session)) {
        await PaymentSessionEngine.expireSession(token).catch(() => {});
        status = 'expired';
      }

      return res.json({
        success: true,
        status,
        channel: session.channel,
        amount: session.amount,
        orderId: session.orderId,
        trxId: session.trxId,
        completedAt: session.completedAt,
        expiresAt: session.expiresAt,
      });
    } catch (error) {
      console.error('[PAY STATUS] error:', error);
      return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
    }
  },

  async completeSessionByToken(token, { trxId, historyId } = {}) {
    if (!token) return;
    try {
      const session = await PaymentSessionEngine.getSession(token);
      if (!session) return;
      if (session.status === PAYMENT_STATUS.SUCCESS) return;
      await PaymentSessionEngine.completeSession(token, {
        trx_id: trxId || null,
        matched_history_id: historyId || null,
      });
    } catch (e) {
      console.error('[PAY COMPLETE] error:', e.message);
    }
  },
};

module.exports = PaymentFlowEngine;
