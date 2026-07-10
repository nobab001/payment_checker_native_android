require('dotenv').config({ path: require('path').join(__dirname, '../../.env') });
const prisma = require('../../db/prisma');
prisma.website_official_gateways.findMany({
  where: { website_id: 7 },
}).then((r) => { console.log(JSON.stringify(r, null, 2)); process.exit(0); });
