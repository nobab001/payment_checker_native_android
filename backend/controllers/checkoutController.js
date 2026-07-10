const prisma = require('../db/prisma');
const axios = require('axios');
const merchantCallback = require('../services/merchantCallback');
const layoutHelper = require('../services/checkoutLayoutHelper');
const checkoutData = require('../services/checkoutDataService');
const vibeMatchService = require('../services/vibeMatchService');
const checkoutPaymentBridge = require('../services/checkoutPaymentBridge');

// Full column set needed to build an enriched, signed merchant callback.
const MERCHANT_CALLBACK_SELECT = {
  id: true, user_id: true, merchant_id: true, redirect_url: true, success_url: true,
  callback_url: true, webhook_url: true, api_secret: true,
  allow_payment_type_callback: true, allow_commission_callback: true,
  receive_payment_type: true, receive_commission: true,
};

const HISTORY_CALLBACK_SELECT = {
  id: true, amount: true, trx_id: true, provider_tag: true, sender_number: true, sms_timestamp: true,
};

const normalizeBdNumber = (n) => (n || '').replace(/\D/g, '').replace(/^880/, '').slice(-11);

/**
 * GET /api/checkout/:apiKey
 * Retrieves active merchant gateway layout options.
 */
async function getCheckoutLayout(req, res) {
  try {
    const { apiKey } = req.params;
    if (!apiKey) {
      return res.status(400).json({ error: 'Merchant API Key is required' });
    }

    const layout = await prisma.gateway_layouts.findFirst({
      where: { api_key: apiKey, is_active: 1 },
      select: {
        id: true, user_id: true, site_name: true, site_url: true, layout_config: true,
        redirect_url: true, callback_url: true,
        // API Integration v2 additions
        company_name: true, logo_url: true, checkout_theme: true, checkout_mode: true,
        success_url: true, cancel_url: true, number_order_json: true, merchant_id: true
      }
    });

    if (!layout) {
      return res.status(404).json({ error: 'সক্রিয় মার্চেন্ট গেটওয়ে লেআউট পাওয়া যায়নি।' });
    }

    // Fallback default block structure if layout_config is empty
    let layoutConfig = layout.layout_config;
    if (!layoutConfig) {
      layoutConfig = [
        { id: 'bkash', name: 'bKash', providerTag: 'bKash', isEnabled: true },
        { id: 'nagad', name: 'Nagad', providerTag: 'Nagad', isEnabled: true },
        { id: 'rocket', name: 'Rocket', providerTag: 'Rocket', isEnabled: true }
      ];
    } else if (typeof layoutConfig === 'string') {
      layoutConfig = JSON.parse(layoutConfig);
    }

    // Secure checkout gateways — only active + parseable official templates
    const { gateways: secureGateways, gatewaysByCategory } = await checkoutData.buildSecureCheckoutData(
      layout.user_id,
      layout.number_order_json,
      { excludeDisabled: true }
    );

    let formattedGateways = secureGateways;

    // Parse tab customization + design from layout_config (merged with global admin defaults)
    const { tabs: globalTabs, providerBranding } = await layoutHelper.loadGlobalCheckoutDefaults();
    const checkoutTabs = layoutHelper.parseTabs(layout.layout_config, globalTabs);
    const checkoutDesign = layoutHelper.resolveDesign(layout.checkout_theme);
    const providers = await layoutHelper.resolveProviderBrandingFull(providerBranding);

    // Official (live) payment channels for bank/card/merchant tabs
    const officialRows = await prisma.website_official_gateways.findMany({
      where: { website_id: layout.id, is_active: 1 },
      select: { id: true, provider: true, display_name: true, redirect_url_template: true },
    });
    const officialGateways = officialRows.map((og) => ({
      id: og.id,
      provider: og.provider,
      displayName: og.display_name || og.provider,
      tab: layoutHelper.officialProviderTab(og.provider),
      livePayment: true,
    }));

    // Enrich synced SIM rows with tab + group metadata for the 3 checkout designs
    formattedGateways = formattedGateways.map((g) => layoutHelper.enrichGatewayRow(g));

    let payload = {
      siteName: layout.site_name,
      siteUrl: layout.site_url,
      companyName: layout.company_name,
      logoUrl: layout.logo_url,
      checkoutTheme: checkoutDesign,
      checkoutDesign,
      checkoutMode: layout.checkout_mode || 'transaction',
      merchantId: layout.merchant_id,
      checkoutTabs: Object.values(checkoutTabs).filter((t) => t.enabled),
      checkoutTabsAll: checkoutTabs,
      providerBranding: providers,
      layoutConfig,
      activeGateways: formattedGateways,
      gatewaysByCategory,
      officialGateways,
      redirectUrl: layout.redirect_url,
      successUrl: layout.success_url || layout.redirect_url,
      cancelUrl: layout.cancel_url
    };

    const sessionQ = req.query.session;
    if (sessionQ) {
      payload = await checkoutPaymentBridge.mergeSessionUrlsIntoLayout(payload, sessionQ);
    }

    return res.json(payload);

  } catch (error) {
    console.error('Error fetching checkout layout:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * POST /api/checkout/verify
 * Public customer verification endpoint for Transaction IDs.
 * Locks the transaction to prevent dual usage.
 */
async function verifyCheckoutPayment(req, res) {
  try {
    const { apiKey, trxId, amount, session } = req.body;

    if (!apiKey || !trxId || !amount) {
      return res.status(400).json({ error: 'Missing required parameters: apiKey, trxId, amount' });
    }

    const cleanTrx = trxId.trim().toUpperCase();
    const cleanAmount = parseFloat(amount);

    // 1. Fetch merchant details
    const merchant = await prisma.gateway_layouts.findFirst({
      where: { api_key: apiKey, is_active: 1 },
      select: MERCHANT_CALLBACK_SELECT
    });

    if (!merchant) {
      return res.status(404).json({ error: 'Invalid API Key or Merchant inactive' });
    }

    // 2. Query sms_history for this user/merchant matching trxId and amount
    const payment = await prisma.sms_history.findFirst({
      where: {
        user_id: merchant.user_id,
        trx_id: cleanTrx,
        amount: cleanAmount
      },
      select: { id: true, is_used: true, used_by_merchant_id: true }
    });

    if (!payment) {
      return res.json({
        success: false,
        message: 'পেমেন্ট এখনও আমাদের সিস্টেমে যুক্ত হয়নি। ১-২ মিনিট অপেক্ষা করে আবার ভেরিফাই করুন।'
      });
    }

    // 3. Prevent transaction hijack/double-spend
    if (payment.is_used && payment.used_by_merchant_id !== merchant.id) {
      return res.json({
        success: false,
        message: 'দুঃখিত, এই ট্রানজেকশন আইডিটি অন্য মার্চেন্ট অর্ডারে ইতিমধ্যে ব্যবহার করা হয়েছে।'
      });
    }

    const wasUnused = !payment.is_used;

    // 4. Mark transaction as used/sold out for this merchant
    if (wasUnused) {
      await prisma.sms_history.update({
        where: { id: payment.id },
        data: {
          is_used: 1,
          used_at: new Date(),
          used_by_merchant_id: merchant.id
        }
      });
      console.log(`[VERIFICATION] Trx ${cleanTrx} (৳${cleanAmount}) marked SOLDOUT for merchant ID ${merchant.id}`);
    } else {
      console.log(`[VERIFICATION] Trx ${cleanTrx} was already locked for this merchant. Skipping update.`);
    }

    const history = await prisma.sms_history.findUnique({
      where: { id: payment.id },
      select: HISTORY_CALLBACK_SELECT,
    });

    let redirectUrl = null;
    if (session && history) {
      if (wasUnused) {
        const bridge = await checkoutPaymentBridge.notifySessionPaid(session, {
          history,
          trxId: cleanTrx,
        });
        redirectUrl = bridge.redirectUrl;
      } else {
        const sessionRow = await checkoutPaymentBridge.loadSession(session);
        redirectUrl = checkoutPaymentBridge.buildSuccessRedirectUrl(sessionRow, {
          trxId: cleanTrx,
          amount: cleanAmount,
        });
      }
    } else if (wasUnused && history) {
      merchantCallback.sendMerchantCallback(merchant, history, 'verified')
        .catch((e) => console.error('[VERIFICATION] callback error:', e.message));
    }

    if (!redirectUrl) {
      const successBase = merchant.success_url || merchant.redirect_url;
      redirectUrl = successBase
        ? `${successBase}${successBase.includes('?') ? '&' : '?'}trxId=${cleanTrx}&amount=${cleanAmount}&status=success`
        : null;
    }

    return res.json({
      success: true,
      message: 'পেমেন্ট সফলভাবে যাচাই করা হয়েছে!',
      redirectUrl
    });

  } catch (error) {
    console.error('Error verifying payment:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * POST /api/transactions/claim-check
 * B2B claim check endpoint for merchant systems.
 * Verifies apiKey, searches for READY transaction matching trxId (and optional amount),
 * marks it as SOLD_OUT and posts the Webhook callback.
 */
async function claimCheckTransaction(req, res) {
  try {
    const { apiKey, trxId, amount } = req.body;

    if (!apiKey || !trxId) {
      return res.status(400).json({ success: false, error: 'Missing required parameters: apiKey, trxId' });
    }

    // 1. Fetch merchant details
    const merchant = await prisma.gateway_layouts.findFirst({
      where: { api_key: apiKey, is_active: 1 },
      select: MERCHANT_CALLBACK_SELECT
    });

    if (!merchant) {
      return res.status(403).json({ success: false, error: 'Invalid API Key or Merchant inactive' });
    }

    const cleanTrx = trxId.trim().toUpperCase();

    // 2. Query sms_history for this user/merchant matching trxId where is_used = 0 (READY)
    let payment;
    const whereClause = {
      user_id: merchant.user_id,
      trx_id: cleanTrx,
      is_used: 0
    };

    if (amount !== undefined && amount !== null && amount !== '') {
      whereClause.amount = parseFloat(amount);
    }

    payment = await prisma.sms_history.findFirst({
      where: whereClause,
      select: { id: true, amount: true, trx_id: true, provider_tag: true, sender_number: true, sms_timestamp: true }
    });

    if (!payment) {
      return res.status(404).json({
        success: false,
        error: 'TRANSACTION_NOT_FOUND',
        message: 'No matching READY transaction found for the provided TrxID.'
      });
    }

    // 3. Mark transaction as used/sold out for this merchant
    await prisma.sms_history.update({
      where: { id: payment.id },
      data: {
        is_used: 1,
        used_at: new Date(),
        used_by_merchant_id: merchant.id
      }
    });

    console.log(`[B2B CLAIM] Trx ${payment.trx_id} (৳${payment.amount}) marked SOLDOUT for merchant ID ${merchant.id}`);

    // 4. Enriched + signed webhook callback (payment_type / commission gated inside).
    merchantCallback.sendMerchantCallback(merchant, {
      id: payment.id,
      amount: payment.amount,
      trx_id: payment.trx_id,
      provider_tag: payment.provider_tag,
      sender_number: payment.sender_number,
      sms_timestamp: payment.sms_timestamp,
    }, 'verified').catch((e) => console.error('[B2B WEBHOOK] callback error:', e.message));

    // 5. Return success telemetry response
    return res.json({
      success: true,
      message: 'Transaction claimed and locked successfully.',
      data: {
        trxId: payment.trx_id,
        amount: Number(payment.amount),
        provider: payment.provider_tag,
        sender: payment.sender_number,
        smsTimestamp: payment.sms_timestamp,
        claimedAt: new Date()
      }
    });

  } catch (error) {
    console.error('Error claiming transaction:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * POST /api/checkout/:apiKey/vibe-init
 * Merchant Vibe Mode — customer enters the number they will pay FROM, before
 * checkout. Creates a waiting request bound to (number, amount, expiry).
 * Body: { customerNumber, amount, expiresInSec? }
 */
async function vibeInit(req, res) {
  try {
    const { apiKey } = req.params;
    const { customerNumber, amount, session } = req.body;
    const expiresInSec = Math.min(Math.max(parseInt(req.body.expiresInSec, 10) || 900, 120), 3600); // 2min–1h, default 15min

    if (!apiKey || !customerNumber || !amount) {
      return res.status(400).json({ success: false, error: 'apiKey, customerNumber এবং amount আবশ্যক।' });
    }

    const merchant = await prisma.gateway_layouts.findFirst({
      where: { api_key: apiKey, is_active: 1 },
      select: { id: true, checkout_mode: true },
    });
    if (!merchant) return res.status(404).json({ success: false, error: 'Invalid API Key or Merchant inactive' });

    const normNumber = normalizeBdNumber(customerNumber);
    if (normNumber.length !== 11) {
      return res.status(400).json({ success: false, error: 'সঠিক ১১-সংখ্যার নাম্বার দিন।' });
    }
    const cleanAmount = parseFloat(amount);
    if (!(cleanAmount > 0)) return res.status(400).json({ success: false, error: 'সঠিক amount দিন।' });

    const expiresAt = new Date(Date.now() + expiresInSec * 1000);
    const sessionToken = session ? String(session).trim() : null;
    const created = await prisma.checkout_vibe_requests.create({
      data: {
        website_id: merchant.id,
        payment_session_token: sessionToken || null,
        customer_number: normNumber,
        amount: cleanAmount,
        status: 'waiting',
        expires_at: expiresAt,
      },
    });

    return res.status(201).json({
      success: true,
      requestId: created.id,
      customerNumber: normNumber,
      amount: cleanAmount,
      expiresAt,
      expiresInSec,
    });
  } catch (error) {
    console.error('Error in vibeInit:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * GET /api/checkout/vibe-status/:id
 * Poll a Vibe request. Lazily expires stale waiting requests.
 */
async function vibeStatus(req, res) {
  try {
    const id = parseInt(req.params.id, 10);
    if (!Number.isFinite(id)) return res.status(400).json({ success: false, error: 'INVALID_ID' });

    let request = await prisma.checkout_vibe_requests.findUnique({ where: { id } });
    if (!request) return res.status(404).json({ success: false, error: 'REQUEST_NOT_FOUND' });

    let status = request.status;
    if (status === 'waiting' && request.expires_at < new Date()) {
      status = 'expired';
      await prisma.checkout_vibe_requests.update({ where: { id }, data: { status } });
      request = await prisma.checkout_vibe_requests.findUnique({ where: { id } });
    } else if (status === 'waiting') {
      const matched = await vibeMatchService.attemptRetroactiveVibeMatch(id);
      if (matched) {
        request = await prisma.checkout_vibe_requests.findUnique({ where: { id } });
        status = request?.status || status;
      }
    }

    let redirectUrl = null;
    if (status === 'matched' && request.payment_session_token) {
      const sessionRow = await checkoutPaymentBridge.loadSession(request.payment_session_token);
      redirectUrl = checkoutPaymentBridge.buildSuccessRedirectUrl(sessionRow, {
        trxId: request.matched_trx_id,
        amount: Number(request.amount),
      });
    }

    return res.json({
      success: true,
      status, // waiting | matched | expired | cancelled
      matched: status === 'matched',
      trxId: request.matched_trx_id || null,
      redirectUrl,
      amount: Number(request.amount),
      expiresAt: request.expires_at,
    });
  } catch (error) {
    console.error('Error in vibeStatus:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * matchVibeForHistory — delegated to vibeMatchService (SMS worker hook).
 */
async function matchVibeForHistory(userId, history) {
  return vibeMatchService.matchVibeForHistory(userId, history);
}

// Background utility to trigger webhooks using axios
function triggerWebhook(url, payload) {
  console.log(`[WEBHOOK] Posting verified payment telemetry to ${url}`);
  axios.post(url, payload)
    .then(response => {
      console.log(`[WEBHOOK] Callback success: status ${response.status}`);
    })
    .catch(err => {
      console.error(`[WEBHOOK] Callback failed: ${err.message}`);
    });
}

/**
 * POST /api/checkout/:apiKey/live-init
 * Thin wrapper — all payment logic lives in PaymentEngine.
 */
async function liveInit(req, res) {
  const PaymentEngine = require('../payment/engine/payment-engine');
  return PaymentEngine.initiateCheckoutLiveInit(req, res);
}

module.exports = {
  getCheckoutLayout,
  verifyCheckoutPayment,
  claimCheckTransaction,
  vibeInit,
  vibeStatus,
  matchVibeForHistory,
  liveInit,
};
