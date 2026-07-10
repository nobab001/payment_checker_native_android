/**
 * Seed demo products for the Demo Merchant storefront.
 * Usage: node demo-merchant/db/seed.js
 */

require('dotenv').config();
const prisma = require('../../db/prisma');

const DEMO_PRODUCTS = [
  { sku: 'DEMO-TSHIRT', name: 'Demo T-Shirt', description: 'Cotton demo tee for PayCheck testing.', price: 450, image_url: null },
  { sku: 'DEMO-MUG', name: 'Demo Coffee Mug', description: 'Ceramic mug — sandbox purchase only.', price: 250, image_url: null },
  { sku: 'DEMO-NOTEBOOK', name: 'Demo Notebook', description: 'A5 ruled notebook for QA flows.', price: 180, image_url: null },
  { sku: 'DEMO-HEADPHONES', name: 'Demo Headphones', description: 'Wired headphones (not real inventory).', price: 1200, image_url: null },
  { sku: 'DEMO-SUB-1M', name: 'Demo Subscription (1 mo)', description: 'Monthly demo subscription plan.', price: 299, image_url: null },
  { sku: 'DEMO-GIFT-CARD', name: 'Demo Gift Card', description: 'Virtual gift card for checkout tests.', price: 500, image_url: null },
];

async function main() {
  for (const item of DEMO_PRODUCTS) {
    await prisma.demo_merchant_products.upsert({
      where: { sku: item.sku },
      update: {
        name: item.name,
        description: item.description,
        price: item.price,
        image_url: item.image_url,
        is_active: 1,
      },
      create: { ...item, is_active: 1 },
    });
  }
  console.log(`[DemoMerchant] Seeded ${DEMO_PRODUCTS.length} demo products.`);
}

main()
  .catch((err) => {
    console.error('[DemoMerchant] seed failed:', err);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
