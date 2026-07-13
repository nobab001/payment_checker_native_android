/**
 * @file bKash Live Adapter — production v1.0 (no Prisma / session / merchant callback).
 * @module payment/providers/bkash-live
 *
 * Modes (config_json.mode):
 *   - template (default): redirect_url_template placeholders
 *   - api: bKash Tokenized Checkout v2.1 (credentials in config_json)
 */

const crypto = require('crypto');
const axios = require('axios');
const BaseProvider = require('./base-provider');
const { PAYMENT_STATUS } = require('../core/payment-status');
const { ProviderError, PROVIDER_ERROR_CODES } = require('../shared/provider-errors');
const { applyUrlTemplate } = require('../shared/provider-utils');
const { getCallbackSecrets } = require('../shared/secret-rotation');
const {
  canExecute, recordSuccess, recordFailure,
} = require('../reliability/circuit-breaker');

const BKASH_API_BASE = process.env.BKASH_API_BASE || 'https://tokenized.pay.bka.sh/v1.2.0-beta';

function parseConfig(merchantConfig) {
  let cfg = {};
  try {
    cfg = merchantConfig?.config_json ? JSON.parse(merchantConfig.config_json) : {};
  } catch (_) {
    cfg = {};
  }
  // Prefer config_json; fall back to flat merchant_accounts fields from gateway-config-loader.
  const appKey = cfg.appKey || cfg.app_key || merchantConfig?.app_key || merchantConfig?.appKey || '';
  const appSecret = cfg.appSecret || cfg.app_secret || merchantConfig?.app_secret || merchantConfig?.appSecret || '';
  const username = cfg.username || merchantConfig?.username || '';
  const password = cfg.password || merchantConfig?.password || '';
  const hasApi = !!(appKey && appSecret && username && password);
  const modeRaw = (cfg.mode || (hasApi ? 'api' : 'template')).toLowerCase();
  return {
    mode: modeRaw,
    appKey,
    appSecret,
    username,
    password,
    callbackSecret: cfg.callbackSecret || cfg.callback_secret || '',
    signatureHeader: cfg.signatureHeader || 'x-signature',
  };
}

function sortedQueryString(params) {
  return Object.keys(params)
    .filter((k) => params[k] != null && params[k] !== '')
    .sort()
    .map((k) => `${k}=${params[k]}`)
    .join('&');
}

class BkashLiveProvider extends BaseProvider {
  _cfg(ctx) {
    return parseConfig(ctx.merchantConfig);
  }

  async initialize(ctx) {
    const cfg = this._cfg(ctx);
    if (cfg.mode === 'api') {
      if (!cfg.appKey || !cfg.appSecret || !cfg.username || !cfg.password) {
        throw new ProviderError(PROVIDER_ERROR_CODES.NOT_CONFIGURED, 'bkash api credentials missing');
      }
    } else if (!ctx.merchantConfig?.redirect_url_template) {
      throw new ProviderError(PROVIDER_ERROR_CODES.NOT_CONFIGURED, 'Missing redirect_url_template');
    }
    return { ok: true, providerId: this.id, versions: this.versionContract, mode: cfg.mode };
  }

  async createPayment(ctx) {
    const cfg = this._cfg(ctx);
    const reference = ctx.sessionToken || `ref_${Date.now()}`;

    if (cfg.mode !== 'api') {
      return { providerReference: reference, providerMeta: { mode: 'template' } };
    }

    const token = await this._grantToken(cfg);
    const paymentID = await this._createBkashPayment(cfg, token, ctx);
    return {
      providerReference: paymentID,
      providerMeta: { mode: 'api', paymentID },
    };
  }

  async getRedirectUrl(ctx, payment) {
    const cfg = this._cfg(ctx);

    if (cfg.mode === 'api' && payment.providerMeta?.paymentID) {
      const token = await this._grantToken(cfg);
      const res = await axios.post(
        `${BKASH_API_BASE}/tokenized/checkout/create`,
        { paymentID: payment.providerMeta.paymentID },
        { headers: this._apiHeaders(cfg, token), timeout: 15000 },
      );
      const url = res.data?.bkashURL || res.data?.redirectURL;
      if (!url) {
        throw new ProviderError(PROVIDER_ERROR_CODES.REDIRECT_FAILED, 'bkash redirect url missing');
      }
      return url;
    }

    const tpl = ctx.merchantConfig?.redirect_url_template;
    if (!tpl) {
      throw new ProviderError(PROVIDER_ERROR_CODES.NOT_CONFIGURED, 'Missing redirect_url_template');
    }
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

  /**
   * Verify inbound callback signature (template + api modes).
   *
   * SECURITY: fail-closed. Previously, when no callback secret was configured
   * this returned `true` for an unsigned callback — letting anyone mark a
   * session paid by hitting the callback URL. Now, if no secret material
   * (config callbackSecret rotation set OR merchant api_secret fallback) is
   * available, OR the signature is missing, we REJECT.
   *
   * @param {Object} payload — flat key/values from query/body
   * @param {string} signature
   * @param {Object} cfg
   * @param {string} [fallbackSecret]
   */
  verifySignature(payload, signature, cfg, fallbackSecret) {
    const secrets = [...getCallbackSecrets(cfg)];
    if (fallbackSecret) secrets.push(String(fallbackSecret));

    // No secret material at all → cannot authenticate this callback → reject.
    if (!secrets.length) return false;
    if (!signature) return false;

    for (const secret of secrets) {
      if (this._matchSignature(payload, signature, secret)) return true;
    }
    return false;
  }

  _matchSignature(payload, signature, secret) {
    if (!secret) return false;
    const { signature: _s, sign: _sign, ...rest } = payload;
    const base = sortedQueryString(rest);
    const expected = crypto.createHmac('sha256', secret).update(base).digest('hex');
    const a = Buffer.from(String(signature));
    const b = Buffer.from(expected);
    if (a.length !== b.length) return false;
    return crypto.timingSafeEqual(a, b);
  }

  /** Parse gateway callback request into raw normalized input. */
  async callback(req) {
    const src = { ...req.query, ...req.body };
    // SECURITY: no default 'success'. An absent status is treated as non-success.
    const statusRaw = String(src.status || src.paymentStatus || '').toLowerCase();
    const success = ['success', 'completed', 'complete'].includes(statusRaw);
    const trxId = src.trxId || src.trx_id || src.transactionId || src.paymentID || null;
    const sessionToken = src.token || src.merchantInvoiceNumber || src.order_id || null;

    return {
      sessionToken,
      trxId,
      success,
      raw: src,
      signature: src.signature || src.sign || req.headers['x-signature'] || req.headers['x-bkash-signature'],
    };
  }

  async verify(ctx, payment) {
    const cfg = this._cfg(ctx);
    if (cfg.mode !== 'api' || !payment?.providerReference) {
      return { verified: true, status: PAYMENT_STATUS.SUCCESS, providerReference: payment?.providerReference };
    }

    const token = await this._grantToken(cfg);
    const res = await axios.post(
      `${BKASH_API_BASE}/tokenized/checkout/payment/status`,
      { paymentID: payment.providerReference },
      { headers: this._apiHeaders(cfg, token), timeout: 15000 },
    );
    const st = String(res.data?.transactionStatus || '').toLowerCase();
    const ok = st === 'completed' || st === 'success';
    return {
      verified: ok,
      status: ok ? PAYMENT_STATUS.SUCCESS : PAYMENT_STATUS.FAILED,
      providerTransactionId: res.data?.trxID || payment.providerReference,
      raw: res.data,
    };
  }

  async normalize(parsed, ctx) {
    const status = parsed.success ? PAYMENT_STATUS.SUCCESS : PAYMENT_STATUS.FAILED;
    return {
      provider: this.id,
      providerId: this.id,
      providerTransactionId: parsed.trxId || parsed.raw?.paymentID || null,
      merchantTransactionId: ctx?.orderId || parsed.sessionToken || null,
      amount: Number(ctx?.amount) || Number(parsed.raw?.amount) || 0,
      currency: ctx?.currency || 'BDT',
      status,
      completedAt: new Date().toISOString(),
      sessionId: parsed.sessionToken,
      orderId: ctx?.orderId || null,
      raw: parsed.raw,
    };
  }

  async health() {
    const base = await super.health();
    return { ...base, gateway: 'bkash_live', mode: 'ready' };
  }

  async ping() {
    const cfg = parseConfig({});
    if (process.env.BKASH_PING_URL) {
      try {
        const started = Date.now();
        await axios.get(process.env.BKASH_PING_URL, { timeout: 5000 });
        return { providerId: this.id, reachable: true, latencyMs: Date.now() - started, status: 'ok' };
      } catch (err) {
        return { providerId: this.id, reachable: false, status: 'error', error: err.message };
      }
    }
    return { providerId: this.id, reachable: null, status: 'configured', note: 'set BKASH_PING_URL for live probe' };
  }

  _apiHeaders(cfg, idToken) {
    return {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      username: cfg.username,
      password: cfg.password,
      'x-app-key': cfg.appKey,
      authorization: idToken,
    };
  }

  async _grantToken(cfg) {
    if (!canExecute(this.id)) {
      throw new ProviderError(PROVIDER_ERROR_CODES.NOT_CONFIGURED, 'bkash circuit open');
    }
    try {
      const res = await axios.post(
        `${BKASH_API_BASE}/tokenized/checkout/token/grant`,
        { app_key: cfg.appKey, app_secret: cfg.appSecret },
        { headers: { 'Content-Type': 'application/json', username: cfg.username, password: cfg.password }, timeout: 15000 },
      );
      const token = res.data?.id_token || res.data?.token;
      if (!token) throw new ProviderError(PROVIDER_ERROR_CODES.NOT_CONFIGURED, 'bkash token grant failed');
      recordSuccess(this.id);
      return token;
    } catch (err) {
      recordFailure(this.id);
      throw err;
    }
  }

  async _createBkashPayment(cfg, token, ctx) {
    const res = await axios.post(
      `${BKASH_API_BASE}/tokenized/checkout/create`,
      {
        mode: '0011',
        payerReference: ctx.meta?.customerNumber || 'paychek',
        callbackURL: ctx.callbackUrl,
        amount: String(ctx.amount),
        currency: ctx.currency || 'BDT',
        intent: 'sale',
        merchantInvoiceNumber: ctx.sessionToken,
      },
      { headers: this._apiHeaders(cfg, token), timeout: 15000 },
    );
    const paymentID = res.data?.paymentID;
    if (!paymentID) throw new ProviderError(PROVIDER_ERROR_CODES.REDIRECT_FAILED, 'bkash paymentID missing');
    return paymentID;
  }
}

module.exports = BkashLiveProvider;
