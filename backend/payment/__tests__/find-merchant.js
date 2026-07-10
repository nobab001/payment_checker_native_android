require('dotenv').config({ path: require('path').join(__dirname, '../../.env') });
const prisma = require('../../db/prisma');
prisma.gateway_layouts.findFirst({
  where: { is_active: 1 },
  select: { id: true, api_key: true, user_id: true },
}).then((r) => { console.log(JSON.stringify(r)); process.exit(0); })
  .catch((e) => { console.error(e); process.exit(1); });
