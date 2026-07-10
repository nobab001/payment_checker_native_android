require('dotenv').config({ path: require('path').join(__dirname, '../../.env') });
const prisma = require('../../db/prisma');
prisma.$queryRaw`SELECT 1 AS ok`
  .then(() => { console.log('DB_OK'); process.exit(0); })
  .catch((e) => { console.log('DB_FAIL', e.message); process.exit(1); });
