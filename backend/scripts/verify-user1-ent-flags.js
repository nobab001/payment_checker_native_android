#!/usr/bin/env node
const prisma = require('../db/prisma');
async function main() {
  const rows = await prisma.$queryRaw`
    SELECT has_custom_sender_addon, custom_sender_ends_at, perm_custom_sender, perm_template, eff_max_sites
    FROM users WHERE id = 1 LIMIT 1
  `;
  console.log(JSON.stringify(rows[0]));
  process.exit(0);
}
main().catch((e) => { console.error(e); process.exit(1); });
