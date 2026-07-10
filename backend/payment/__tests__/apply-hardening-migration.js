#!/usr/bin/env node
/**
 * Apply Phase-3B.5 DDL via raw SQL (when prisma db push fails).
 */
require('dotenv').config({ path: require('path').join(__dirname, '../.env') });
const fs = require('fs');
const path = require('path');
const prisma = require('../../db/prisma');

async function main() {
  const sqlPath = path.join(__dirname, '../../db/migrations/add_payment_hardening_3b5.sql');
  const sql = fs.readFileSync(sqlPath, 'utf8');
  const statements = sql
    .split(';')
    .map((s) => s.trim())
    .filter((s) => s && !s.startsWith('--'));

  for (const stmt of statements) {
    try {
      await prisma.$executeRawUnsafe(stmt);
      console.log('OK:', stmt.slice(0, 60).replace(/\s+/g, ' '), '...');
    } catch (err) {
      if (err.message?.includes('Duplicate key name') || err.message?.includes('already exists')) {
        console.log('SKIP (exists):', stmt.slice(0, 50));
      } else {
        console.error('FAIL:', err.message);
        console.error(stmt);
      }
    }
  }
  console.log('Migration script finished.');
  await prisma.$disconnect();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
