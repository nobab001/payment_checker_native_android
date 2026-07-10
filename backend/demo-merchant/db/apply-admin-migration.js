#!/usr/bin/env node
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const prisma = require('../../db/prisma');

async function main() {
  const sqlPath = path.join(__dirname, 'migrate-admin.sql');
  const sql = fs.readFileSync(sqlPath, 'utf8').trim();

  try {
    await prisma.$executeRawUnsafe(sql);
    console.log('[DemoMerchant] Admin migration applied.');
  } catch (err) {
    if (String(err.message).includes('Duplicate column')) {
      console.log('[DemoMerchant] role column already exists — skipped.');
      return;
    }
    throw err;
  }
}

main()
  .catch((err) => {
    console.error('[DemoMerchant] Admin migration failed:', err.message);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
