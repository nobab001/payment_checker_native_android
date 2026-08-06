#!/usr/bin/env node
const prisma = require('../db/prisma');
async function main() {
  const cols = await prisma.$queryRaw`SHOW COLUMNS FROM gateway_methods`;
  console.log(cols.map((c) => c.Field).join(','));
  const sample = await prisma.$queryRaw`
    SELECT gm.id, gm.user_id, gm.provider, gm.is_enabled, gm.template_id, t.sender_id, t.is_parseable, t.template_name
    FROM gateway_methods gm
    LEFT JOIN sms_templates t ON t.id = gm.template_id
    WHERE gm.user_id = '1'
    LIMIT 30
  `;
  console.log(JSON.stringify(sample, null, 2));
  process.exit(0);
}
main().catch((e) => { console.error(e); process.exit(1); });
