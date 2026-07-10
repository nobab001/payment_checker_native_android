require('dotenv').config();
const prisma = require('../db/prisma');

async function main() {
  console.log('outbox delegate:', typeof prisma.merchant_callback_outbox);
  const count = await prisma.merchant_callback_outbox.count().catch((e) => `err:${e.message}`);
  console.log('count:', count);
  await prisma.$disconnect();
}

main();
