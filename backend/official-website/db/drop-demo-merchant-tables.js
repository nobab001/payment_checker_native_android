/**
 * Drop leftover demo_merchant_* tables after removing Demo Merchant.
 * Usage: node official-website/db/drop-demo-merchant-tables.js
 */
require('dotenv').config({ path: require('path').join(__dirname, '..', '..', '.env') });
const prisma = require('../../db/prisma');

const TABLES = [
  'demo_merchant_transactions',
  'demo_merchant_wallet_ledger',
  'demo_merchant_orders',
  'demo_merchant_products',
  'demo_merchant_wallets',
  'demo_merchant_users',
];

async function main() {
  for (const table of TABLES) {
    try {
      await prisma.$executeRawUnsafe(`DROP TABLE IF EXISTS \`${table}\``);
      console.log(`Dropped ${table}`);
    } catch (e) {
      console.warn(`Skip ${table}:`, e.message);
    }
  }
  await prisma.$disconnect();
}

main().catch(async (e) => {
  console.error(e);
  await prisma.$disconnect();
  process.exit(1);
});
