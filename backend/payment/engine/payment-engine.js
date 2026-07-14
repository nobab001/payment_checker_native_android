/**
 * @file Payment Engine — sole entry point for payment initiation.
 * @module payment/engine/payment-engine
 */

const { resolveProviderEntry, toDbProviderKey } = require('../registry/provider-alias');
const { getCachedProviderEntry } = require('../registry/provider-cache');
const { getProvider } = require('../registry/provider-factory');
const { ProviderError, PROVIDER_ERROR_CODES } = require('../shared/provider-errors');
const { fromProviderError } = require('../errors/error-registry');
const { logPayment } = require('../logging/trace-logger');
const { runWithTrace } = require('../logging/trace-context');
const { loadMerchantByApiKey, loadOfficialGateway } = require('../shared/gateway-config-loader');
const PaymentSessionEngine = require('../session/payment-session');
const RedirectService = require('../redirect/redirect-service');
const { buildContextFromLiveInitRequest } = require('./payment-context');
const { emitPaymentEvent, PAYMENT_EVENTS } = require('../events/event-bus');
const { recordLatency } = require('../monitoring/payment-monitor');

const PaymentEngine = {
  resolveProvider(providerIdOrAlias) {
    return resolveProviderEntry(providerIdOrAlias);
  },

  getAdapter(providerIdOrAlias) {
    return getProvider(providerIdOrAlias);
  },

  /**
   * Checkout POST /api/checkout/:apiKey/live-init — thin controller delegates here.
   * @param {import('express').Request} req
   * @param {import('express').Response} res
   */
  async initiateCheckoutLiveInit(req, res) {
    const ctx = buildContextFromLiveInitRequest(req);
    return runWithTrace(ctx.traceId, async () => {
      try {
        const started = Date.now();
        const result = await this.initiate(ctx);
        recordLatency('engine', Date.now() - started, { traceId: ctx.traceId });
        return res.json(result);
      } catch (err) {
        console.error('Error in PaymentEngine.initiate:', err);
        if (err instanceof ProviderError) {
          const { body, httpStatus } = fromProviderError(err);
          return res.status(httpStatus).json(body);
        }
        const { body, httpStatus } = fromProviderError({ code: 'INTERNAL_ERROR' });
        return res.status(httpStatus).json(body);
      }
    });
  },

  /**
   * @param {import('./payment-context').PaymentContext} ctx
   * @returns {Promise<{ success: boolean, redirectUrl: string }>}
   */
  async initiate(ctx) {
    if (ctx.source === 'checkout-live-init') {
      return this._initiateCheckoutLive(ctx);
    }
    throw new ProviderError('UNSUPPORTED_SOURCE', `Unsupported initiate source: ${ctx.source}`);
  },

  async _initiateCheckoutLive(ctx) {
    const { traceId } = ctx;
    logPayment(traceId, 'PaymentEngine', 'initiate.start', { source: ctx.source });

    const apiKey = ctx.merchant?.apiKey;
    const rawProvider = ctx.provider?.raw;
    const amount = ctx.amount;

    if (!apiKey || !rawProvider || amount == null) {
      throw new ProviderError('MISSING_PARAMS', 'apiKey, provider, amount আবশ্যক।');
    }

    const merchant = await loadMerchantByApiKey(apiKey);
    if (!merchant) {
      throw new ProviderError('INVALID_API_KEY', 'Invalid API Key');
    }

    if (!(amount > 0)) {
      throw new ProviderError(PROVIDER_ERROR_CODES.INVALID_AMOUNT, 'INVALID_AMOUNT');
    }

    const registryEntry = resolveProviderEntry(rawProvider);

    if (registryEntry?.enabled === false) {
      throw new ProviderError(PROVIDER_ERROR_CODES.PROVIDER_DISABLED, 'PROVIDER_NOT_CONFIGURED');
    }
    if (registryEntry?.maintenance === true) {
      throw new ProviderError(PROVIDER_ERROR_CODES.PROVIDER_MAINTENANCE, 'PROVIDER_NOT_CONFIGURED');
    }

    const dbProviderKey = registryEntry
      ? toDbProviderKey(registryEntry.id).toLowerCase()
      : String(rawProvider).toLowerCase();

    const gateway = await loadOfficialGateway(merchant.id, registryEntry?.id || rawProvider, ctx.merchantAccountId);
    if (!gateway) {
      throw new ProviderError(PROVIDER_ERROR_CODES.NOT_CONFIGURED, 'PROVIDER_NOT_CONFIGURED');
    }

    const successUrl = ctx.successUrl || merchant.success_url || merchant.redirect_url || null;
    const cancelUrl = ctx.cancelUrl || merchant.cancel_url || null;
    const sessionMeta = {};
    if (ctx.merchantAccountId != null) {
      sessionMeta.merchantAccountId = Number(ctx.merchantAccountId) || ctx.merchantAccountId;
    }
    const demoSessionId = ctx.metadata?.extra?.demoSessionId;
    if (demoSessionId) sessionMeta.demoSessionId = String(demoSessionId);

    if (registryEntry) {
      await getCachedProviderEntry(registryEntry.id);
      const adapter = getProvider(registryEntry.id);
      const paymentCtx = {
        traceId,
        sessionToken: null,
        websiteId: merchant.id,
        userId: merchant.user_id,
        amount,
        currency: ctx.currency || 'BDT',
        successUrl,
        cancelUrl,
        merchantConfig: gateway,
        meta: ctx.metadata || {},
      };

      // Validate credentials only — actual create happens on /pay/:token with callbackURL + invoice.
      logPayment(traceId, 'Provider', 'initialize', { providerId: registryEntry.id });
      await adapter.initialize(paymentCtx);
    }

    logPayment(traceId, 'Session', 'createSession', { providerId: dbProviderKey });
    const session = await PaymentSessionEngine.createSession({
      websiteId: merchant.id,
      userId: merchant.user_id,
      officialProvider: dbProviderKey,
      amount,
      currency: ctx.currency || 'BDT',
      successUrl,
      cancelUrl,
      timeoutSec: registryEntry?.sessionTimeout ?? 1800,
      traceId,
      meta: sessionMeta,
    });

    const redirectUrl = RedirectService.buildPayTokenUrl(ctx.http?.baseUrl, session.sessionToken);
    const redirectStarted = Date.now();
    logPayment(traceId, 'Redirect', 'liveInit.response', { redirectUrl });
    recordLatency('redirect', Date.now() - redirectStarted, { traceId });

    emitPaymentEvent(PAYMENT_EVENTS.PAYMENT_CREATED, {
      traceId,
      sessionToken: session.sessionToken,
      websiteId: merchant.id,
      userId: merchant.user_id,
      providerId: registryEntry?.id || dbProviderKey,
      amount,
      status: 'CREATED',
    });

    return RedirectService.liveInitJson(redirectUrl);
  },

  // ── Phase-3B flow methods (delegate to PaymentFlowEngine) ──
  initMerchantPayment(req, res) {
    return require('./payment-flow-engine').initMerchantPayment(req, res);
  },
  redirectPayment(req, res) {
    return require('./payment-flow-engine').redirectPayment(req, res);
  },
  bkashCallback(req, res) {
    return require('./payment-flow-engine').bkashCallback(req, res);
  },
  legacyGatewayCallback(req, res) {
    return require('./payment-flow-engine').legacyGatewayCallback(req, res);
  },
  paymentStatus(req, res) {
    return require('./payment-flow-engine').paymentStatus(req, res);
  },
  completeSessionByToken(token, opts) {
    return require('./payment-flow-engine').completeSessionByToken(token, opts);
  },
};

module.exports = PaymentEngine;
