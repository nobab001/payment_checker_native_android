/**
 * @file Base Provider Adapter — mandatory interface for all payment gateways.
 * @module payment/providers/base-provider
 *
 * CONTRACT (Phase-3A):
 *   initialize()
 *   createPayment()
 *   getRedirectUrl()
 *   verify()
 *   normalize()   ← stub in 3A
 *   callback()    ← stub in 3A
 *   health()
 *   ping()
 *
 * Providers MUST NOT persist sessions — SessionEngine owns persistence.
 */

const { buildNormalizedCallback } = require('../core/callback-schema');
const { buildVersionContract } = require('../core/provider-version-contract');
const { PROVIDER_ERROR_CODES, ProviderError } = require('../shared/provider-errors');

/**
 * @typedef {Object} PaymentContext
 * @property {string} sessionId
 * @property {string} sessionToken
 * @property {number} websiteId
 * @property {number} userId
 * @property {number} amount
 * @property {string} currency
 * @property {string} [orderId]
 * @property {string} [successUrl]
 * @property {string} [cancelUrl]
 * @property {string} [callbackUrl]
 * @property {Object} [merchantConfig] — from website_official_gateways
 * @property {Object} [meta]
 */

/**
 * @typedef {Object} CreatePaymentResult
 * @property {string} providerReference
 * @property {Object} [providerMeta]
 */

class BaseProvider {
  /**
   * @param {import('../registry/provider-registry').ProviderRegistryEntry} registryEntry
   */
  constructor(registryEntry) {
    if (new.target === BaseProvider) {
      throw new Error('BaseProvider is abstract — extend with a concrete adapter');
    }
    this.registry = registryEntry;
    this.id = registryEntry.id;
    this.version = registryEntry.version;
    this.versionContract = buildVersionContract(registryEntry);
  }

  /** Frozen version chain for this adapter. */
  getVersions() {
    return this.versionContract;
  }

  /** Load credentials / validate merchant gateway config. Phase-3A: no-op or basic check. */
  async initialize(_ctx) {
    return { ok: true, providerId: this.id, versions: this.versionContract };
  }

  /**
   * Create provider-side payment intent (no session DB writes here).
   * @param {PaymentContext} ctx
   * @returns {Promise<CreatePaymentResult>}
   */
  async createPayment(ctx) {
    return {
      providerReference: ctx.sessionToken,
      providerMeta: {},
    };
  }

  /**
   * Build redirect URL for customer browser.
   * @param {PaymentContext} ctx
   * @param {CreatePaymentResult} payment
   * @returns {Promise<string>}
   */
  async getRedirectUrl(ctx, payment) {
    const tpl = ctx.merchantConfig?.redirect_url_template;
    if (!tpl) {
      throw new ProviderError(PROVIDER_ERROR_CODES.NOT_CONFIGURED, 'Missing redirect_url_template');
    }
    const { applyUrlTemplate } = require('../shared/provider-utils');
    return applyUrlTemplate(tpl, {
      amount: ctx.amount,
      order_id: ctx.orderId || '',
      token: ctx.sessionToken,
      callback_url: ctx.callbackUrl || '',
      currency: ctx.currency || 'BDT',
      customer_number: ctx.meta?.customerNumber || '',
      provider_reference: payment.providerReference,
    });
  }

  /** Verify payment status with provider API. Phase-3A: stub. */
  async verify(_ctx) {
    throw new ProviderError(PROVIDER_ERROR_CODES.CALLBACK_NOT_IMPLEMENTED, 'verify() — Phase-3B');
  }

  /**
   * Normalize provider raw payload → merchant callback shape. Phase-3A: stub.
   * @param {Object} _raw
   * @returns {import('../core/callback-schema').NormalizedCallbackPayload}
   */
  async normalize(raw = {}) {
    return buildNormalizedCallback({
      providerId: this.id,
      provider: this.registry.company,
      providerType: this.registry.type,
      ...raw,
    });
  }

  /** Handle inbound provider callback. Phase-3A: stub. */
  async callback(_req, _ctx) {
    throw new ProviderError(PROVIDER_ERROR_CODES.CALLBACK_NOT_IMPLEMENTED, 'callback() — Phase-3B');
  }

  /** Health probe — adapter internal state (no network). */
  async health() {
    return {
      providerId: this.id,
      adapter: this.registry.adapter,
      version: this.version,
      versions: this.versionContract,
      enabled: this.registry.enabled !== false,
      maintenance: this.registry.maintenance === true,
      status: 'ok',
    };
  }

  /** Reachability probe — gateway/API ping. Phase-3A: stub until 3B wiring. */
  async ping() {
    return {
      providerId: this.id,
      reachable: null,
      status: 'not_implemented',
    };
  }
}

module.exports = BaseProvider;
