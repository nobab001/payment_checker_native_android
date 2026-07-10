const express = require('express');
const productService = require('../services/product-service');

const router = express.Router();

router.get('/', async (_req, res) => {
  try {
    const products = await productService.listActiveProducts();
    return res.json({ success: true, products });
  } catch (err) {
    console.error('[DemoMerchant] products error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

module.exports = router;
