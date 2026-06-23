const { PrismaClient } = require('@prisma/client');
const prisma = new PrismaClient();

async function run() {
  const result = await prisma.$executeRawUnsafe(`DELETE FROM gateway_methods WHERE provider IN ('bKash', 'Nagad', 'Rocket', 'Upay') AND (number = '' OR number IS NULL)`);
  console.log('Deleted:', result);
}

run().catch(console.error).finally(() => prisma.$disconnect());
