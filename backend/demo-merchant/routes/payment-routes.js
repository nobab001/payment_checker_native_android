const express = require('express');
const config = require('../config');
const productService = require('../services/product-service');
const orderService = require('../services/order-service');
const paychekClient = require('../services/paychek-client');
const { requireAuth, requireConfigured } = require('../middleware/auth');

const router = express.Router();

const PRESET_AMOUNTS = [100, 200, 500, 1000, 2000, 5000];

router.get('/presets', requireAuth, (_req, res) => {
  return res.json({ success: true, amounts: PRESET_AMOUNTS });
});

async function startCheckout(req, res, { orderType, amount, productId = null }) {
  const parsedAmount = parseFloat(amount);
  if (!(parsedAmount > 0)) {
    return res.status(400).json({ success: false, error: 'INVALID_AMOUNT' });
  }

  let product = null;
  if (orderType === orderService.ORDER_TYPES.PRODUCT_PURCHASE) {
    product = await productService.getProductById(productId);
    if (Math.abs(parsedAmount - product.price) > 0.009) {
      return res.status(400).json({ success: false, error: 'AMOUNT_PRODUCT_MISMATCH' });
    }
  }

  const order = await orderService.createOrder({
    userId: req.demoUser.id,
    orderType,
    amount: parsedAmount,
    productId: product?.id || null,
    meta: product ? { productSku: product.sku, productName: product.name } : { source: 'wallet_recharge' },
  });

  const paychek = await paychekClient.initPaychekCheckout(req, {
    amount: parsedAmount,
    orderId: order.order_number,
    successUrl: config.paymentSuccessUrl(req, order.order_number),
    cancelUrl: config.paymentCancelUrl(req, order.order_number),
    callbackUrl: config.webhookUrl(req),
  });

  await orderService.attachPaymentSession(order.id, {
    sessionToken: paychek.sessionToken,
    traceId: paychek.traceId,
  });

  return res.status(201).json({
    success: true,
    order: orderService.formatOrder(order),
    checkoutUrl: paychek.checkoutUrl,
    sessionToken: paychek.sessionToken,
    expiresAt: paychek.expiresAt,
  });
}

router.post('/wallet-recharge', requireAuth, requireConfigured, async (req, res) => {
  try {
    return await startCheckout(req, res, {
      orderType: orderService.ORDER_TYPES.WALLET_RECHARGE,
      amount: req.body?.amount,
    });
  } catch (err) {
    if (err.code === 'CONFIG_ERROR' || err.code === 'PAYCHEK_INIT_FAILED') {
      return res.status(502).json({ success: false, error: err.code, message: err.message, details: err.details });
    }
    if (err.code === 'ECONNABORTED') {
      return res.status(502).json({
        success: false,
        error: 'PAYCHEK_TIMEOUT',
        message: 'PayCheck checkout init timed out. Ensure the backend is running and DEMO_MERCHANT_PAYCHEK_API_URL points to http://127.0.0.1:3000 if needed.',
      });
    }
    console.error('[DemoMerchant] wallet recharge error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR', message: err.message });
  }
});

router.post('/product-checkout', requireAuth, requireConfigured, async (req, res) => {
  try {
    const productId = parseInt(req.body?.productId, 10);
    if (!productId) {
      return res.status(400).json({ success: false, error: 'INVALID_PRODUCT' });
    }
    const product = await productService.getProductById(productId);
    return await startCheckout(req, res, {
      orderType: orderService.ORDER_TYPES.PRODUCT_PURCHASE,
      amount: product.price,
      productId,
    });
  } catch (err) {
    if (err.code === 'NOT_FOUND') {
      return res.status(404).json({ success: false, error: err.code });
    }
    if (err.code === 'CONFIG_ERROR' || err.code === 'PAYCHEK_INIT_FAILED') {
      return res.status(502).json({ success: false, error: err.code, message: err.message, details: err.details });
    }
    if (err.code === 'ECONNABORTED') {
      return res.status(502).json({
        success: false,
        error: 'PAYCHEK_TIMEOUT',
        message: 'PayCheck checkout init timed out.',
      });
    }
    console.error('[DemoMerchant] product checkout error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR', message: err.message });
  }
});

/** Public return handler — no JWT (checkout redirect may land on a fresh tab). */
router.post('/return-confirm', requireConfigured, async (req, res) => {
  try {
    const orderNumber = req.body?.orderNumber;
    const sessionToken = req.body?.sessionToken;
    if (!orderNumber || !sessionToken) {
      return res.status(400).json({ success: false, error: 'MISSING_PARAMS' });
    }

    const order = await orderService.getOrderByNumber(orderNumber);
    if (!order) {
      return res.status(404).json({ success: false, error: 'NOT_FOUND' });
    }

    if (order.payment_session_token !== String(sessionToken).trim()) {
      return res.status(403).json({ success: false, error: 'SESSION_MISMATCH' });
    }

    if (order.status === orderService.ORDER_STATUS.PAID) {
      return res.json({
        success: true,
        order: orderService.formatOrder(order),
        source: 'already_paid',
      });
    }

    const status = await paychekClient.getPaymentStatus(sessionToken, req);
    const synced = await orderService.syncOrderFromPaymentStatus(order, status);
    const fresh = await orderService.getOrderByNumber(orderNumber) || synced;

    return res.json({
      success: true,
      order: orderService.formatOrder(fresh),
      paymentStatus: status.status,
      source: 'return_confirm',
    });
  } catch (err) {
    if (err.code === 'SESSION_NOT_FOUND') {
      return res.status(404).json({ success: false, error: err.code });
    }
    console.error('[DemoMerchant] return-confirm error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.post('/confirm', requireAuth, requireConfigured, async (req, res) => {
  try {
    const orderNumber = req.body?.orderNumber;
    if (!orderNumber) {
      return res.status(400).json({ success: false, error: 'MISSING_ORDER' });
    }

    const order = await orderService.getOrderByNumber(orderNumber, req.demoUser.id);
    if (!order) {
      return res.status(404).json({ success: false, error: 'NOT_FOUND' });
    }

    if (order.status === orderService.ORDER_STATUS.PAID) {
      return res.json({ success: true, order: orderService.formatOrder(order), source: 'already_paid' });
    }

    if (!order.payment_session_token) {
      return res.status(400).json({ success: false, error: 'NO_PAYMENT_SESSION' });
    }

    const status = await paychekClient.getPaymentStatus(order.payment_session_token, req);
    const synced = await orderService.syncOrderFromPaymentStatus(order, status);
    const fresh = await orderService.getOrderByNumber(orderNumber, req.demoUser.id);

    return res.json({
      success: true,
      order: orderService.formatOrder(fresh || synced),
      paymentStatus: status.status,
      source: 'status_poll',
    });
  } catch (err) {
    if (err.code === 'SESSION_NOT_FOUND') {
      return res.status(404).json({ success: false, error: err.code });
    }
    console.error('[DemoMerchant] confirm error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

module.exports = router;
