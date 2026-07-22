const prisma = require('../db/prisma');
const axios = require('axios');
const merchantCallback = require('../services/merchantCallback');
const layoutHelper = require('../services/checkoutLayoutHelper');
const checkoutData = require('../services/checkoutDataService');
const vibeMatchService = require('../services/vibeMatchService');
const checkoutPaymentBridge = require('../services/checkoutPaymentBridge');
const { normalizeWebsitePurpose, normalizeSessionPurpose, computePurposeAmounts, roundMoney2 } = require('../services/websitePurpose');
const settlementService = require('../services/checkoutSettlementService');

// Full column set needed to build an enriched, signed merchant callback.
const MERCHANT_CALLBACK_SELECT = {
  id: true, user_id: true, merchant_id: true, redirect_url: true, success_url: true,
  callback_url: true, webhook_url: true, api_secret: true, api_key: true,
  website_purpose: true,
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
        success_url: true, cancel_url: true, number_order_json: true, merchant_id: true,
        commission_enabled: true, website_purpose: true
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

    // Merchant accounts (multi-account support for live payments)
    const merchantAccountRows = await prisma.merchant_accounts.findMany({
      where: { website_id: layout.id, is_active: 1 },
      orderBy: [{ priority: 'asc' }, { id: 'desc' }],
    }).catch(() => []);
    // Group by provider
    const merchantAccountsByProvider = {};
    for (const acct of merchantAccountRows) {
      const p = acct.provider;
      if (!merchantAccountsByProvider[p]) merchantAccountsByProvider[p] = [];
      merchantAccountsByProvider[p].push({
        id: acct.id,
        provider: acct.provider,
        merchantName: acct.merchant_name,
        merchantRef: acct.merchant_ref,
        logoUrl: acct.logo_url,
        isDefault: !!acct.is_default,
        priority: acct.priority,
      });
    }
    // Convert to array of { provider, accounts } objects
    const merchantAccountsGroups = Object.entries(merchantAccountsByProvider).map(([provider, accounts]) => ({
      provider,
      accounts,
    }));

    // Enrich synced SIM rows with tab + group metadata for the 3 checkout designs
    formattedGateways = formattedGateways.map((g) => layoutHelper.enrichGatewayRow(g));

    // ── Incentive rules (commission + campaign) exposed to the checkout client ──
    // Only when admin unlocked commission for this website. The client applies
    // these per provider using the amount the customer enters; server-side
    // computeIncentives remains the source of truth for the webhook/merchant payout.
    const incNP = merchantCallback.normalizePaymentType;
    const tokenForGateway = (g) => incNP(g.provider || '', `${g.display_name || ''} ${g.category || ''}`);
    let incentives = { enabled: false, commissions: [], campaigns: [] };
    if (layout.commission_enabled) {
      const commRows = await prisma.merchant_commissions.findMany({
        where: { website_id: layout.id, is_active: 1 },
      }).catch(() => []);
      let campRows = [];
      try {
        campRows = await prisma.$queryRawUnsafe(
          `SELECT * FROM merchant_campaigns WHERE website_id = ? AND is_active = 1`, layout.id,
        );
      } catch (_) { campRows = []; }
      incentives = {
        enabled: true,
        commissions: commRows.map((c) => ({
          token: c.payment_type,
          commissionType: c.commission_type,
          commissionValue: Number(c.commission_value),
          chargeType: c.charge_type,
          chargeValue: Number(c.charge_value),
        })),
        campaigns: campRows.map((c) => ({
          token: c.payment_type || '',
          label: c.label || '',
          minAmount: Number(c.min_amount),
          maxAmount: Number(c.max_amount),
          mode: c.mode,
          valueType: c.value_type,
          value: Number(c.value),
        })),
      };
      // Attach matching keys: coarse token + per-template tpl_<id> when available.
      formattedGateways = formattedGateways.map((g) => ({
        ...g,
        incentiveToken: tokenForGateway(g),
        incentiveTplKey: g.templateId != null ? `tpl_${g.templateId}` : null,
      }));
      for (const grp of merchantAccountsGroups) {
        grp.incentiveToken = incNP(grp.provider || '', 'merchant');
        grp.accounts = grp.accounts.map((a) => ({
          ...a, incentiveToken: incNP(a.provider || '', 'merchant'),
        }));
      }
    }

    let payload = {
      siteName: layout.site_name,
      siteUrl: layout.site_url,
      companyName: layout.company_name,
      logoUrl: layout.logo_url,
      checkoutTheme: checkoutDesign,
      checkoutDesign,
      checkoutMode: layout.checkout_mode || 'transaction',
      websitePurpose: normalizeWebsitePurpose(layout.website_purpose),
      // Effective purpose for this checkout view (overridden by session when present)
      purpose: normalizeWebsitePurpose(layout.website_purpose) === 'both'
        ? 'add_balance'
        : normalizeWebsitePurpose(layout.website_purpose),
      merchantId: layout.merchant_id,
      checkoutTabs: Object.values(checkoutTabs).filter((t) => t.enabled),
      checkoutTabsAll: checkoutTabs,
      providerBranding: providers,
      layoutConfig,
      activeGateways: formattedGateways,
      gatewaysByCategory,
      merchantAccountsGroups,
      incentives,
      redirectUrl: layout.redirect_url,
      successUrl: layout.success_url || layout.redirect_url,
      cancelUrl: layout.cancel_url
    };

    const sessionQ = req.query.session;
    if (sessionQ) {
      payload = await checkoutPaymentBridge.mergeSessionUrlsIntoLayout(payload, sessionQ);
    }

    // Preview before pay/init — temporary demo session overlays (Official Test Experience)
    const demoSessionId = req.query.demoSession;
    if (demoSessionId && !sessionQ) {
      try {
        const testCtrl = require('../official-website/controllers/test-controller');
        payload = await testCtrl.applyDemoSessionToLayoutAsync(payload, demoSessionId, req);
      } catch (e) {
        console.warn('[CHECKOUT] demoSession overlay failed:', e.message);
      }
    }

    return res.json(payload);

  } catch (error) {
    console.error('Error fetching checkout layout:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * POST /api/checkout/verify
 * Purpose-aware verification:
 *   add_balance — SMS amount must equal checkout amount; walletCredit in callback.
 *   payment     — accumulate Trx parts until expectedPayable met (max 5); overpay OK.
 */
async function verifyCheckoutPayment(req, res) {
  try {
    const { apiKey, trxId, amount, session, purpose: bodyPurpose, expectedPayable, settlementAttemptId } = req.body;

    if (!apiKey || !trxId) {
      return res.status(400).json({ error: 'Missing required parameters: apiKey, trxId' });
    }

    const cleanTrx = String(trxId).trim().toUpperCase();
    const clientAmount = amount != null && amount !== '' ? parseFloat(amount) : null;

    const merchant = await prisma.gateway_layouts.findFirst({
      where: { api_key: apiKey, is_active: 1 },
      select: MERCHANT_CALLBACK_SELECT,
    });
    if (!merchant) {
      return res.status(404).json({ error: 'Invalid API Key or Merchant inactive' });
    }

    // Resolve session purpose (session meta wins over body / website).
    let sessionPurpose = null;
    let orderAmount = clientAmount;
    let sessionRow = null;
    if (session) {
      sessionRow = await checkoutPaymentBridge.loadSession(session);
      if (sessionRow) {
        sessionPurpose = normalizeSessionPurpose(sessionRow.meta?.purpose);
        orderAmount = Number(sessionRow.amount);
      }
    }
    if (!sessionPurpose) {
      const wp = normalizeWebsitePurpose(merchant.website_purpose);
      if (wp === 'both') {
        sessionPurpose = normalizeSessionPurpose(bodyPurpose) || 'add_balance';
      } else {
        sessionPurpose = wp;
      }
    }
    if (!(orderAmount > 0) && clientAmount > 0) orderAmount = clientAmount;

    // Find SMS by TrxID (amount taken from SMS — source of truth).
    const payment = await prisma.sms_history.findFirst({
      where: {
        user_id: merchant.user_id,
        trx_id: cleanTrx,
      },
      select: { id: true, is_used: true, used_by_merchant_id: true, amount: true, provider_tag: true },
    });

    if (!payment) {
      return res.json({
        success: false,
        message: 'পেমেন্ট এখনও আমাদের সিস্টেমে যুক্ত হয়নি। ১-২ মিনিট অপেক্ষা করে আবার ভেরিফাই করুন।',
      });
    }

    if (payment.is_used && payment.used_by_merchant_id !== merchant.id) {
      return res.json({
        success: false,
        message: 'দুঃখিত, এই ট্রানজেকশন আইডিটি অন্য মার্চেন্ট অর্ডারে ইতিমধ্যে ব্যবহার করা হয়েছে।',
      });
    }

    const smsAmount = Number(payment.amount);
    const wasUnused = !payment.is_used;

    // ── Add Balance: exact checkout amount ────────────────────────────────
    if (sessionPurpose === 'add_balance') {
      if (!(orderAmount > 0)) {
        return res.status(400).json({ success: false, error: 'AMOUNT_REQUIRED' });
      }
      if (Math.abs(smsAmount - Number(orderAmount)) >= 0.01) {
        return res.json({
          success: false,
          purpose: 'add_balance',
          message: `এড ব্যালেন্সে ঠিক ৳${orderAmount} পাঠাতে হবে। SMS-এ এসেছে ৳${smsAmount}।`,
        });
      }

      if (wasUnused) {
        await prisma.sms_history.update({
          where: { id: payment.id },
          data: { is_used: 1, used_at: new Date(), used_by_merchant_id: merchant.id },
        });
      }

      const history = await prisma.sms_history.findUnique({
        where: { id: payment.id },
        select: HISTORY_CALLBACK_SELECT,
      });

      const { commission, charge } = await merchantCallback.computeIncentives(
        merchant.id,
        merchantCallback.normalizePaymentType(history.provider_tag || '', ''),
        orderAmount,
        history.provider_tag || '',
      );
      const amounts = computePurposeAmounts(orderAmount, commission, charge, 'add_balance');
      const extras = {
        purpose: 'add_balance',
        checkoutAmount: amounts.checkoutAmount,
        receivedAmount: smsAmount,
        walletCredit: amounts.walletCredit,
      };

      let redirectUrl = null;
      if (session && history && wasUnused) {
        const bridge = await checkoutPaymentBridge.notifySessionPaid(session, { history, trxId: cleanTrx });
        redirectUrl = bridge.redirectUrl;
      } else if (wasUnused && history) {
        merchantCallback.sendMerchantCallback(merchant, history, 'SUCCESS', extras)
          .catch((e) => console.error('[VERIFICATION] callback error:', e.message));
      }

      if (!redirectUrl) {
        const successBase = merchant.success_url || merchant.redirect_url;
        redirectUrl = successBase
          ? `${successBase}${successBase.includes('?') ? '&' : '?'}trxId=${cleanTrx}&amount=${smsAmount}&status=success`
          : null;
      }

      return res.json({
        success: true,
        purpose: 'add_balance',
        message: 'পেমেন্ট সফলভাবে যাচাই করা হয়েছে!',
        checkoutAmount: amounts.checkoutAmount,
        receivedAmount: smsAmount,
        walletCredit: amounts.walletCredit,
        redirectUrl,
      });
    }

    // ── Payment: multi-txn settlement ─────────────────────────────────────
    const payableHint = expectedPayable != null && expectedPayable !== ''
      ? Number(expectedPayable)
      : Number(orderAmount);
    const expectedPay = roundMoney2(payableHint > 0 ? payableHint : orderAmount);

    const settlementKey = settlementService.buildSettlementKey({
      sessionToken: session || null,
      apiKey,
      orderAmount,
      attemptId: settlementAttemptId,
    });

    await settlementService.openOrGetSettlement({
      settlementKey,
      websiteId: merchant.id,
      sessionToken: session || null,
      orderAmount,
      expectedPayable: expectedPay,
      purpose: 'payment',
    });

    if (wasUnused) {
      await prisma.sms_history.update({
        where: { id: payment.id },
        data: { is_used: 1, used_at: new Date(), used_by_merchant_id: merchant.id },
      });
    }

    const applied = await settlementService.applyPart(settlementKey, {
      trxId: cleanTrx,
      amount: smsAmount,
      historyId: payment.id,
      providerTag: payment.provider_tag,
    });

    if (!applied.ok) {
      return res.json({
        success: false,
        purpose: 'payment',
        error: applied.error,
        message: applied.message || 'Settlement failed',
      });
    }

    const history = await prisma.sms_history.findUnique({
      where: { id: payment.id },
      select: HISTORY_CALLBACK_SELECT,
    });

    if (applied.status === 'PARTIAL') {
      return res.json({
        success: false,
        partial: true,
        purpose: 'payment',
        status: 'PARTIAL',
        orderAmount: applied.orderAmount,
        expectedPayable: applied.expectedPayable,
        receivedAmount: applied.receivedAmount,
        remaining: applied.remaining,
        transactions: applied.transactions,
        message: `আপনি ৳${applied.remaining} কম পাঠিয়েছেন। আরও ৳${applied.remaining} পাঠিয়ে Transaction ID দিন।`,
      });
    }

    // SUCCESS (exact or overpay)
    const extras = {
      purpose: 'payment',
      orderAmount: applied.orderAmount,
      expectedPayable: applied.expectedPayable,
      receivedAmount: applied.receivedAmount,
      overPaid: applied.overPaid,
      transactions: applied.transactions,
    };

    let redirectUrl = null;
    if (session && history) {
      const bridge = await checkoutPaymentBridge.notifySessionPaid(session, { history, trxId: cleanTrx });
      redirectUrl = bridge.redirectUrl;
    } else if (history) {
      merchantCallback.sendMerchantCallback(merchant, history, 'SUCCESS', extras)
        .catch((e) => console.error('[VERIFICATION] callback error:', e.message));
    }

    if (!redirectUrl) {
      const successBase = merchant.success_url || merchant.redirect_url;
      redirectUrl = successBase
        ? `${successBase}${successBase.includes('?') ? '&' : '?'}trxId=${cleanTrx}&amount=${applied.receivedAmount}&status=success`
        : null;
    }

    const overMsg = applied.overPaid > 0
      ? ` আপনি ৳${applied.overPaid} বেশি পাঠিয়েছেন। অতিরিক্ত টাকা ফেরত/সমন্বয়ের জন্য মার্চেন্টের সাথে যোগাযোগ করুন।`
      : '';

    return res.json({
      success: true,
      purpose: 'payment',
      status: 'SUCCESS',
      message: `পেমেন্ট সফলভাবে যাচাই করা হয়েছে!${overMsg}`,
      orderAmount: applied.orderAmount,
      expectedPayable: applied.expectedPayable,
      receivedAmount: applied.receivedAmount,
      overPaid: applied.overPaid,
      transactions: applied.transactions,
      redirectUrl,
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
