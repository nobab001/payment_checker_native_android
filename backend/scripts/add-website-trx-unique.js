require('dotenv').config();
const prisma = require('../db/prisma');

async function main() {
  try {
    await prisma.$executeRawUnsafe(
      'ALTER TABLE `payment_sessions` ADD UNIQUE KEY `uniq_website_trx` (`website_id`, `trx_id`)',
    );
    console.log('uniq_website_trx added');
  } catch (e) {
    console.log('uniq_website_trx:', e.message);
  }
  await prisma.$disconnect();
}

main();
