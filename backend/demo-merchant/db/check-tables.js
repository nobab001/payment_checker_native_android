require('dotenv').config();
const prisma = require('../../db/prisma');
prisma.$queryRawUnsafe(`
  SELECT TABLE_NAME FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'demo_merchant%'
  ORDER BY TABLE_NAME
`)
  .then((r) => console.log(r))
  .catch((e) => console.error(e))
  .finally(() => prisma.$disconnect());
