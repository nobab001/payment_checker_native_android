const prisma = require('../../db/prisma');
const walletService = require('./wallet-service');

async function listActiveProducts() {
  const rows = await prisma.demo_merchant_products.findMany({
    where: { is_active: 1 },
    orderBy: { id: 'asc' },
  });

  return rows.map((p) => ({
    id: p.id,
    sku: p.sku,
    name: p.name,
    description: p.description,
    price: walletService.toNumber(p.price),
    imageUrl: p.image_url,
  }));
}

async function getProductById(productId) {
  const product = await prisma.demo_merchant_products.findFirst({
    where: { id: productId, is_active: 1 },
  });
  if (!product) {
    const err = new Error('Product not found');
    err.code = 'NOT_FOUND';
    throw err;
  }
  return {
    id: product.id,
    sku: product.sku,
    name: product.name,
    description: product.description,
    price: walletService.toNumber(product.price),
    imageUrl: product.image_url,
  };
}

module.exports = {
  listActiveProducts,
  getProductById,
};
