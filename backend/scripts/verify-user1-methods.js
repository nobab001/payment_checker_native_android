#!/usr/bin/env node
const prisma = require('../db/prisma');
async function main() {
  const allRows = await prisma.$queryRaw`
    SELECT gm.id, gm.provider, gm.is_enabled, t.sender_id, t.is_parseable
    FROM gateway_methods gm
    LEFT JOIN sms_templates t ON t.id = gm.template_id
    WHERE gm.user_id = '1'
      AND (LOWER(gm.provider) = 'all' OR COALESCE(t.is_parseable, 1) = 0)
  `;
  console.log('archive/all methods', JSON.stringify(allRows));
  const enabledOfficial = await prisma.$queryRaw`
    SELECT COUNT(*) AS c FROM gateway_methods gm
    LEFT JOIN sms_templates t ON t.id = gm.template_id
    WHERE gm.user_id = '1' AND gm.is_enabled = 1 AND COALESCE(t.is_parseable, 1) = 1
  `;
  console.log('enabled official', enabledOfficial);
  process.exit(0);
}
main().catch((e) => { console.error(e); process.exit(1); });
