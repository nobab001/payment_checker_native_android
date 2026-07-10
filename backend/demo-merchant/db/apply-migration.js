#!/usr/bin/env node
/**
 * Apply demo-merchant SQL migration using Prisma + shared DB connection.
 * Usage: node demo-merchant/db/apply-migration.js
 */

require('dotenv').config();
const fs = require('fs');
const path = require('path');
const prisma = require('../../db/prisma');

async function main() {
  const sqlPath = path.join(__dirname, 'migrate.sql');
  const sql = fs.readFileSync(sqlPath, 'utf8');
  const statements = sql
    .replace(/\r\n/g, '\n')
    .split(/;\s*\n/)
    .map((s) => s.replace(/^--[^\n]*\n?/gm, '').trim())
    .filter(Boolean);

  for (let i = 0; i < statements.length; i += 1) {
    const stmt = statements[i];
    try {
      await prisma.$executeRawUnsafe(stmt);
      console.log(`[DemoMerchant] OK (${i + 1}/${statements.length})`);
    } catch (err) {
      console.error(`[DemoMerchant] Failed statement ${i + 1}:`, err.message);
      throw err;
    }
  }

  console.log('[DemoMerchant] Migration applied successfully.');
}

main()
  .catch((err) => {
    console.error('[DemoMerchant] Migration failed:', err.message);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
