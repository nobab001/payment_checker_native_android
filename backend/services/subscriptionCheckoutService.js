/**
 * App subscription/addon Buy → PayChek customer checkout (purpose=payment).
 * Uses a dedicated billing merchant (BILLING_PAYCHEK_* or OFFICIAL_TEST_* credentials).
 */

const crypto = require('crypto');
const axios = require('axios');
const prisma = require('../db/prisma');
const {
  computeSubscriptionQuote,
  applySubscriptionPurchase,
  formatDateYmd,
} = require('./subscriptionBillingService');
const { syncUserEntitlements } = require('./accountEntitlementsService');

let ordersTableReady = false;

function env(name, fallback = null) {
  const v = process.env[name];
  return v != null && String(v).trim() !== '' ? String(v).trim() : fallback;
}

function billingCredentials() {
  return {
    apiKey:
      env('BILLING_PAYCHEK_API_KEY') ||
      env('OFFICIAL_TEST_PAYCHEK_API_KEY') ||
      env('DEMO_MERCHANT_PAYCHEK_API_KEY'),
    apiSecret:
      env('BILLING_PAYCHEK_API_SECRET') ||
      env('OFFICIAL_TEST_PAYCHEK_API_SECRET') ||
      env('DEMO_MERCHANT_PAYCHEK_API_SECRET'),
    apiBase:
      env('BILLING_PAYCHEK_API_URL') ||
      env('OFFICIAL_TEST_PAYCHEK_API_URL') ||
      env('DEMO_MERCHANT_PAYCHEK_API_URL'),
  };
}

function isLoopbackHost(host) {
  const h = String(host || '').toLowerCase();
  return /^127\.0\.0\.1(?::|$)/.test(h) || /^localhost(?::|$)/.test(h) || h === '::1';
}

function serverPort() {
  return Number(process.env.PORT) || 3000;
}

function resolveBrowserBaseUrl(req) {
  const proto = req?.headers?.['x-forwarded-proto'] || req?.protocol || 'http';
  const host = req?.headers?.['x-forwarded-host'] || req?.get?.('host');
  if (host && !isLoopbackHost(host)) {
    return `${proto}://${host}`.replace(/\/$/, '');
  }
  const publicBase =
    env('BILLING_PUBLIC_URL') ||
    env('PUBLIC_BASE_URL') ||
    env('OFFICIAL_TEST_PUBLIC_URL');
  if (publicBase) return publicBase.replace(/\/$/, '');
  if (host) return `${proto}://${host}`.replace(/\/$/, '');
  return `http://127.0.0.1:${serverPort()}`;
}

/** App deep link — after paid checkout, open Home. */
function billingSuccessDeepLink(orderId) {
  const q = new URLSearchParams({ status: 'success' });
  if (orderId) q.set('orderId', String(orderId));
  return `paychek://billing/success?${q.toString()}`;
}

/** Cancel / failure stays on website so user can read the reason. */
function billingCancelWebUrl(browserOrigin, orderId) {
  const q = new URLSearchParams({ status: 'cancel', orderId: String(orderId || '') });
  return `${browserOrigin}/subscription-paid.html?${q.toString()}`;
}

/** S2S webhook — prefer loopback so same Node process never hairpins LAN IP. */
function resolveWebhookUrl() {
  return `http://127.0.0.1:${serverPort()}/api/v1/subscription/payment-webhook`;
}

function signRequestBody(rawBody, secret) {
  return crypto.createHmac('sha256', secret).update(rawBody).digest('hex');
}

function verifyWebhookSignature(rawBody, signature, secret) {
  if (!secret || !signature) return false;
  const expected = signRequestBody(
    typeof rawBody === 'string' ? rawBody : Buffer.isBuffer(rawBody) ? rawBody.toString('utf8') : JSON.stringify(rawBody),
    secret,
  );
  try {
    const a = Buffer.from(String(signature));
    const b = Buffer.from(expected);
    if (a.length !== b.length) return false;
    return crypto.timingSafeEqual(a, b);
  } catch {
    return false;
  }
}

function makeOrderId(kind) {
  const prefix = kind === 'addon' ? 'add' : 'sub';
  return `${prefix}_${Date.now().toString(36)}_${crypto.randomBytes(4).toString('hex')}`;
}

async function ensureCheckoutOrdersTable() {
  if (ordersTableReady) return;
  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS subscription_checkout_orders (
      id INT NOT NULL AUTO_INCREMENT,
      order_id VARCHAR(64) NOT NULL,
      user_id INT NOT NULL,
      kind VARCHAR(16) NOT NULL DEFAULT 'subscription',
      plan_name VARCHAR(100) NULL,
      plan_id INT NULL,
      amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
      status VARCHAR(16) NOT NULL DEFAULT 'pending',
      session_token VARCHAR(128) NULL,
      trx_id VARCHAR(64) NULL,
      quote_json TEXT NULL,
      activated_at DATETIME NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_sub_checkout_order (order_id),
      KEY idx_sub_checkout_user (user_id, status),
      KEY idx_sub_checkout_session (session_token)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);
  ordersTableReady = true;
}

async function initPaychekCheckout(req, { amount, orderId, successUrl, cancelUrl, callbackUrl, purpose, meta }) {
  const creds = billingCredentials();
  if (!creds.apiKey || !creds.apiSecret) {
    const err = new Error(
      'Billing merchant API key/secret not configured (BILLING_PAYCHEK_* or OFFICIAL_TEST_PAYCHEK_*)',
    );
    err.code = 'CONFIG_ERROR';
    throw err;
  }

  const baseUrl = (creds.apiBase || `http://127.0.0.1:${serverPort()}`).replace(/\/$/, '');
  const payload = {
    amount,
    orderId,
    channel: 'paycheck',
    currency: 'BDT',
    successUrl,
    cancelUrl,
    callbackUrl,
    purpose: purpose || 'payment',
    meta: meta || {},
  };
  const rawBody = JSON.stringify(payload);
  const signature = signRequestBody(rawBody, creds.apiSecret);

  const response = await axios.post(`${baseUrl}/api/v1/pay/init`, rawBody, {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': creds.apiKey,
      'X-Signature': signature,
      'X-Forwarded-Host': req?.headers?.['x-forwarded-host'] || req?.get?.('host') || '',
      'X-Forwarded-Proto': req?.headers?.['x-forwarded-proto'] || req?.protocol || 'http',
    },
    timeout: 15000,
    validateStatus: () => true,
  });

  if (response.status >= 400 || !response.data?.success) {
    const err = new Error(response.data?.error || `PayChek init failed (${response.status})`);
    err.code = 'PAYCHEK_INIT_FAILED';
    err.details = response.data;
    throw err;
  }

  return response.data;
}

function rewriteCheckoutUrl(checkoutUrl, browserOrigin) {
  if (!checkoutUrl) return checkoutUrl;
  try {
    const u = new URL(checkoutUrl, browserOrigin);
    if (u.hostname === '127.0.0.1' || u.hostname === 'localhost') {
      const origin = new URL(browserOrigin);
      u.protocol = origin.protocol;
      u.host = origin.host;
    }
    return u.toString();
  } catch {
    return checkoutUrl;
  }
}

async function stackCustomSenderExpiry(userId, durationDays) {
  const user = await prisma.users.findUnique({
    where: { id: userId },
    select: { custom_sender_ends_at: true },
  });
  let baseDate = new Date();
  if (user?.custom_sender_ends_at) {
    const existingExpiry = new Date(user.custom_sender_ends_at);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (existingExpiry > today) {
      baseDate = existingExpiry;
    }
  }
  baseDate.setDate(baseDate.getDate() + durationDays);
  return baseDate;
}

async function applyAddonPurchase(userId, planId) {
  const rows = await prisma.$queryRaw`
    SELECT id, plan_name, price, duration_days, is_active
    FROM addon_plans
    WHERE id = ${Number(planId)}
    LIMIT 1
  `;
  const plan = rows[0];
  if (!plan || Number(plan.is_active) !== 1) {
    return { error: 'PLAN_NOT_FOUND', message: 'অ্যাড-অন প্যাকেজটি খুঁজে পাওয়া যায়নি।' };
  }
  const durationDays = Number(plan.duration_days) || 30;
  const newExpiry = await stackCustomSenderExpiry(userId, durationDays);
  const formattedExpiry = formatDateYmd(newExpiry);
  await prisma.users.update({
    where: { id: userId },
    data: {
      has_custom_sender_addon: 1,
      custom_sender_ends_at: newExpiry,
      active_addon_plan_id: Number(planId),
    },
  });
  await syncUserEntitlements(userId);
  return {
    plan_name: plan.plan_name,
    plan_id: Number(plan.id),
    payable_amount: Number(plan.price) || 0,
    new_expiry_date: formattedExpiry,
    message: `${plan.plan_name} সফলভাবে সক্রিয় করা হয়েছে। মেয়াদ: ${formattedExpiry}`,
  };
}

async function fulfillPaidOrder(order, { trxId } = {}) {
  if (order.status === 'paid' || order.status === 'activated') {
    return { already: true, order };
  }

  let quoteParsed = null;
  try {
    if (order.quote_json) {
      quoteParsed = typeof order.quote_json === 'string' ? JSON.parse(order.quote_json) : order.quote_json;
    }
  } catch (_) {}

  let result;
  if (order.kind === 'addon') {
    result = await applyAddonPurchase(order.user_id, order.plan_id);
  } else if (quoteParsed?.v3 || quoteParsed?.package_sku) {
    const { fulfillSubscription } = require('./subscriptionV3/fulfillmentService');
    result = await fulfillSubscription({
      userId: order.user_id,
      quote: quoteParsed,
      transactionId: trxId || order.trx_id || order.order_id,
      quoteToken: quoteParsed.quote_token || null,
    });
    if (!result.error) {
      await syncUserEntitlements(order.user_id);
    }
  } else {
    result = await applySubscriptionPurchase(order.user_id, order.plan_name);
    if (!result.error) {
      await syncUserEntitlements(order.user_id);
    }
  }

  if (result.error) {
    await prisma.$executeRaw`
      UPDATE subscription_checkout_orders
      SET status = 'failed', trx_id = ${trxId || order.trx_id || null}
      WHERE order_id = ${order.order_id} AND status = 'pending'
    `;
    return { error: result.error, message: result.message, order };
  }

  await prisma.$executeRaw`
    UPDATE subscription_checkout_orders
    SET status = 'activated',
        trx_id = ${trxId || order.trx_id || null},
        activated_at = NOW()
    WHERE order_id = ${order.order_id} AND status IN ('pending', 'paid')
  `;

  return { success: true, result, order };
}

/**
 * Create pending order + PayChek pay/init (purpose=payment).
 */
async function createSubscriptionCheckout(req, {
  userId,
  planName,
  quoteToken = null,
  v3Quote = null,
  payableOverride = null,
}) {
  await ensureCheckoutOrdersTable();

  let quote;
  let payable;

  if (v3Quote) {
    quote = { ...v3Quote, plan_name: v3Quote.package_full_name, v3: true };
    payable = Number(payableOverride ?? v3Quote.payable_amount) || 0;
  } else {
    quote = await computeSubscriptionQuote(userId, planName);
    if (quote.error) {
      return { error: quote.error, message: quote.message, status: 404 };
    }
    payable = Number(quote.payable_amount) || 0;
  }

  // Full credit / free upgrade — activate without gateway.
  if (payable <= 0 && !v3Quote) {
    const applied = await applySubscriptionPurchase(userId, planName);
    if (applied.error) {
      return { error: applied.error, message: applied.message, status: 404 };
    }
    await syncUserEntitlements(userId);
    return {
      activated: true,
      orderId: null,
      checkoutUrl: null,
      amount: 0,
      quote,
      message: `${planName} সক্রিয় হয়েছে (ক্রেডিট দিয়ে পরিশোধ)। মেয়াদ: ${applied.new_expiry_date}`,
    };
  }

  const orderId = makeOrderId('subscription');
  const browserOrigin = resolveBrowserBaseUrl(req);
  const successUrl = billingSuccessDeepLink(orderId);
  const cancelUrl = billingCancelWebUrl(browserOrigin, orderId);

  await prisma.$executeRaw`
    INSERT INTO subscription_checkout_orders
      (order_id, user_id, kind, plan_name, plan_id, amount, status, quote_json)
    VALUES (
      ${orderId},
      ${Number(userId)},
      'subscription',
      ${quote.plan_name || quote.package_full_name},
      ${quote.plan_id || null},
      ${payable},
      'pending',
      ${JSON.stringify({ ...quote, quote_token: quoteToken })}
    )
  `;

  try {
    const data = await initPaychekCheckout(req, {
      amount: payable,
      orderId,
      purpose: 'payment',
      successUrl,
      cancelUrl,
      callbackUrl: resolveWebhookUrl(),
      meta: {
        clientOrigin: browserOrigin,
        billing: true,
        kind: 'subscription',
        userId: Number(userId),
        planName: quote.plan_name,
        purpose: 'payment',
      },
    });

    const sessionToken = data.sessionToken || data.data?.sessionToken || null;
    let checkoutUrl = rewriteCheckoutUrl(
      data.checkoutUrl || data.data?.checkoutUrl,
      browserOrigin,
    );

    if (sessionToken) {
      await prisma.$executeRaw`
        UPDATE subscription_checkout_orders
        SET session_token = ${sessionToken}
        WHERE order_id = ${orderId}
      `;
    }

    return {
      activated: false,
      orderId,
      checkoutUrl,
      sessionToken,
      amount: payable,
      quote,
      message: 'চেকআউট খুলুন এবং পেমেন্ট সম্পন্ন করুন।',
    };
  } catch (err) {
    await prisma.$executeRaw`
      UPDATE subscription_checkout_orders SET status = 'failed' WHERE order_id = ${orderId}
    `;
    throw err;
  }
}

async function createAddonCheckout(req, { userId, planId }) {
  await ensureCheckoutOrdersTable();

  const rows = await prisma.$queryRaw`
    SELECT id, plan_name, price, duration_days, is_active
    FROM addon_plans
    WHERE id = ${Number(planId)}
    LIMIT 1
  `;
  const plan = rows[0];
  if (!plan || Number(plan.is_active) !== 1) {
    return {
      error: 'PLAN_NOT_FOUND',
      message: 'অ্যাড-অন প্যাকেজটি খুঁজে পাওয়া যায়নি।',
      status: 404,
    };
  }

  const payable = Number(plan.price) || 0;
  if (payable <= 0) {
    const applied = await applyAddonPurchase(userId, planId);
    if (applied.error) {
      return { error: applied.error, message: applied.message, status: 404 };
    }
    return {
      activated: true,
      orderId: null,
      checkoutUrl: null,
      amount: 0,
      message: applied.message,
    };
  }

  const orderId = makeOrderId('addon');
  const browserOrigin = resolveBrowserBaseUrl(req);
  const successUrl = billingSuccessDeepLink(orderId);
  const cancelUrl = billingCancelWebUrl(browserOrigin, orderId);

  await prisma.$executeRaw`
    INSERT INTO subscription_checkout_orders
      (order_id, user_id, kind, plan_name, plan_id, amount, status, quote_json)
    VALUES (
      ${orderId},
      ${Number(userId)},
      'addon',
      ${plan.plan_name},
      ${Number(plan.id)},
      ${payable},
      'pending',
      ${JSON.stringify({ plan_name: plan.plan_name, payable_amount: payable, duration_days: plan.duration_days })}
    )
  `;

  try {
    const data = await initPaychekCheckout(req, {
      amount: payable,
      orderId,
      purpose: 'payment',
      successUrl,
      cancelUrl,
      callbackUrl: resolveWebhookUrl(),
      meta: {
        clientOrigin: browserOrigin,
        billing: true,
        kind: 'addon',
        userId: Number(userId),
        planId: Number(plan.id),
        purpose: 'payment',
      },
    });

    const sessionToken = data.sessionToken || data.data?.sessionToken || null;
    const checkoutUrl = rewriteCheckoutUrl(
      data.checkoutUrl || data.data?.checkoutUrl,
      browserOrigin,
    );

    if (sessionToken) {
      await prisma.$executeRaw`
        UPDATE subscription_checkout_orders
        SET session_token = ${sessionToken}
        WHERE order_id = ${orderId}
      `;
    }

    return {
      activated: false,
      orderId,
      checkoutUrl,
      sessionToken,
      amount: payable,
      message: 'চেকআউট খুলুন এবং পেমেন্ট সম্পন্ন করুন।',
    };
  } catch (err) {
    await prisma.$executeRaw`
      UPDATE subscription_checkout_orders SET status = 'failed' WHERE order_id = ${orderId}
    `;
    throw err;
  }
}

async function handleBillingWebhook(rawBody, signature) {
  const { apiSecret } = billingCredentials();
  if (!verifyWebhookSignature(rawBody, signature, apiSecret)) {
    const err = new Error('Invalid webhook signature');
    err.code = 'INVALID_SIGNATURE';
    throw err;
  }

  let payload = {};
  try {
    payload = typeof rawBody === 'string'
      ? JSON.parse(rawBody)
      : Buffer.isBuffer(rawBody)
        ? JSON.parse(rawBody.toString('utf8'))
        : rawBody;
  } catch {
    payload = {};
  }

  const status = String(payload.status || payload.paymentStatus || '').toUpperCase();
  const isSuccess = status === 'SUCCESS' || status === 'VERIFIED' || status === 'PAID';
  if (!isSuccess) {
    return { success: true, ignored: true, reason: 'NOT_SUCCESS', status };
  }

  await ensureCheckoutOrdersTable();

  const orderId =
    payload.orderId ||
    payload.merchantTransactionId ||
    null;
  const sessionToken =
    payload.sessionToken ||
    payload.sessionId ||
    payload.paymentId ||
    null;
  const trxId =
    payload.trxId ||
    payload.providerTransactionId ||
    null;

  let rows = [];
  if (orderId) {
    rows = await prisma.$queryRaw`
      SELECT * FROM subscription_checkout_orders WHERE order_id = ${String(orderId)} LIMIT 1
    `;
  }
  if (!rows[0] && sessionToken) {
    rows = await prisma.$queryRaw`
      SELECT * FROM subscription_checkout_orders WHERE session_token = ${String(sessionToken)} LIMIT 1
    `;
  }

  const order = rows[0];
  if (!order) {
    return { success: true, ignored: true, reason: 'ORDER_NOT_FOUND' };
  }

  const fulfilled = await fulfillPaidOrder(order, { trxId });
  return {
    success: !fulfilled.error,
    orderId: order.order_id,
    activated: Boolean(fulfilled.success || fulfilled.already),
    already: Boolean(fulfilled.already),
    error: fulfilled.error || null,
    message: fulfilled.message || fulfilled.result?.message || null,
  };
}

async function getCheckoutOrderStatus(userId, orderId) {
  await ensureCheckoutOrdersTable();
  const rows = await prisma.$queryRaw`
    SELECT order_id, user_id, kind, plan_name, plan_id, amount, status, trx_id, activated_at, created_at
    FROM subscription_checkout_orders
    WHERE order_id = ${String(orderId)} AND user_id = ${Number(userId)}
    LIMIT 1
  `;
  const order = rows[0];
  if (!order) {
    return { error: 'ORDER_NOT_FOUND', message: 'অর্ডার পাওয়া যায়নি।' };
  }
  return {
    orderId: order.order_id,
    kind: order.kind,
    planName: order.plan_name,
    planId: order.plan_id != null ? Number(order.plan_id) : null,
    amount: Number(order.amount),
    status: order.status,
    trxId: order.trx_id,
    activated: order.status === 'activated',
    activatedAt: order.activated_at,
  };
}

module.exports = {
  createSubscriptionCheckout,
  createAddonCheckout,
  handleBillingWebhook,
  getCheckoutOrderStatus,
  billingCredentials,
  ensureCheckoutOrdersTable,
};
