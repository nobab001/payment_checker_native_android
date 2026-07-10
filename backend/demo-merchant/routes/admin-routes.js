const express = require('express');
const adminService = require('../services/admin-service');
const { saveProductImage, productImageMiddleware } = require('../middleware/upload');
const { requireAuth, requireAdmin } = require('../middleware/auth');

const router = express.Router();

router.use(requireAuth, requireAdmin);

router.get('/stats', async (_req, res) => {
  try {
    const stats = await adminService.getAdminStats();
    return res.json({ success: true, stats });
  } catch (err) {
    console.error('[DemoMerchant] admin stats error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.get('/users', async (_req, res) => {
  try {
    const users = await adminService.listUsers();
    return res.json({ success: true, users });
  } catch (err) {
    console.error('[DemoMerchant] admin users error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.patch('/users/:userId/role', async (req, res) => {
  try {
    const role = req.body?.role;
    const user = await adminService.setUserRole(req.demoUser.id, parseInt(req.params.userId, 10), role);
    return res.json({ success: true, user });
  } catch (err) {
    if (err.code === 'NOT_FOUND') return res.status(404).json({ success: false, error: err.code });
    if (err.code === 'FORBIDDEN' || err.code === 'LAST_ADMIN') {
      return res.status(403).json({ success: false, error: err.code, message: err.message });
    }
    if (err.code === 'VALIDATION_ERROR') {
      return res.status(400).json({ success: false, error: err.code, message: err.message });
    }
    console.error('[DemoMerchant] admin role error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.get('/products', async (_req, res) => {
  try {
    const products = await adminService.listAllProducts();
    return res.json({ success: true, products });
  } catch (err) {
    console.error('[DemoMerchant] admin products list error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.post('/products', productImageMiddleware(), async (req, res) => {
  try {
    let imageUrl = null;
    if (req.file?.buffer) {
      imageUrl = await saveProductImage(req.file.buffer);
    }

    const product = await adminService.createProduct({
      sku: req.body.sku,
      name: req.body.name,
      description: req.body.description,
      price: req.body.price,
      imageUrl,
    });

    return res.status(201).json({ success: true, product });
  } catch (err) {
    if (err.message === 'INVALID_IMAGE_TYPE') {
      return res.status(400).json({ success: false, error: 'INVALID_IMAGE_TYPE' });
    }
    if (err.code === 'VALIDATION_ERROR') {
      return res.status(400).json({ success: false, error: err.code, message: err.message });
    }
    if (err.code === 'P2002') {
      return res.status(409).json({ success: false, error: 'SKU_EXISTS' });
    }
    console.error('[DemoMerchant] admin create product error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.put('/products/:productId', productImageMiddleware(), async (req, res) => {
  try {
    const productId = parseInt(req.params.productId, 10);
    let imageUrl;
    if (req.file?.buffer) {
      imageUrl = await saveProductImage(req.file.buffer);
    }

    const product = await adminService.updateProduct(productId, {
      sku: req.body.sku,
      name: req.body.name,
      description: req.body.description,
      price: req.body.price,
      imageUrl: imageUrl !== undefined ? imageUrl : undefined,
      isActive: req.body.isActive === 'true' || req.body.isActive === true
        ? true
        : (req.body.isActive === 'false' || req.body.isActive === false ? false : undefined),
    });

    return res.json({ success: true, product });
  } catch (err) {
    if (err.code === 'NOT_FOUND') return res.status(404).json({ success: false, error: err.code });
    if (err.code === 'VALIDATION_ERROR') {
      return res.status(400).json({ success: false, error: err.code, message: err.message });
    }
    console.error('[DemoMerchant] admin update product error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

router.delete('/products/:productId', async (req, res) => {
  try {
    const result = await adminService.deleteProduct(parseInt(req.params.productId, 10));
    return res.json({ success: true, ...result });
  } catch (err) {
    if (err.code === 'NOT_FOUND') return res.status(404).json({ success: false, error: err.code });
    console.error('[DemoMerchant] admin delete product error:', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
});

module.exports = router;
