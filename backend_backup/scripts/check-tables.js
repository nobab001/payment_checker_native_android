/**
 * scripts/check-tables.js
 *
 * Lists every table in the configured database with its row count.
 * Pure read-only — safe to run anytime.
 *
 * Usage:
 *   node scripts/check-tables.js
 */

const mysql = require('mysql2/promise');
require('dotenv').config();

const DB_HOST = process.env.DB_HOST || '127.0.0.1';
const DB_PORT = parseInt(process.env.DB_PORT || '3306', 10);
const DB_USER = process.env.DB_USER || 'root';
const DB_PASS = process.env.DB_PASS || '';
const DB_NAME = process.env.DB_NAME || 'payment_checker_db';

const EXPECTED_TABLES = [
  'users',
  'otps',
  'user_credentials',
  'registered_devices',
  'sms_history',
  'sms_templates',
  'checkout_view_templates',
  'gateway_layouts',
  'gateway_methods',
  'global_config',
  'sms_gateways',
  'smtp_gateways',
  'sms_parse_failures',
  'device_trial_logs',
];

async function main() {
  const conn = await mysql.createConnection({
    host: DB_HOST,
    port: DB_PORT,
    user: DB_USER,
    password: DB_PASS,
    database: DB_NAME,
  });

  const [rows] = await conn.query(
    `SELECT TABLE_NAME, TABLE_ROWS
       FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = ?
   ORDER BY TABLE_NAME`,
    [DB_NAME]
  );

  const present = new Set(rows.map(r => r.TABLE_NAME));
  const missing = EXPECTED_TABLES.filter(t => !present.has(t));
  const extra   = rows.map(r => r.TABLE_NAME).filter(t => !EXPECTED_TABLES.includes(t));

  console.log('=================================================');
  console.log(`[CHECK] Database: ${DB_NAME}`);
  console.log(`[CHECK] Tables present: ${rows.length} / expected ${EXPECTED_TABLES.length}`);
  console.log('=================================================');
  for (const r of rows) {
    const marker = EXPECTED_TABLES.includes(r.TABLE_NAME) ? '✓' : '?';
    console.log(`  ${marker} ${r.TABLE_NAME.padEnd(30)} rows=${r.TABLE_ROWS}`);
  }
  if (missing.length > 0) {
    console.log('\n[CHECK] ❌ MISSING TABLES:');
    for (const t of missing) console.log(`     - ${t}`);
  }
  if (extra.length > 0) {
    console.log('\n[CHECK] ⚠ EXTRA TABLES (not in expected list):');
    for (const t of extra) console.log(`     - ${t}`);
  }
  if (missing.length === 0 && extra.length === 0) {
    console.log('\n[CHECK] ✅ All 14 expected tables present.');
  }

  await conn.end();
}

main().catch(err => {
  console.error('[CHECK] Fatal:', err.message);
  process.exit(1);
});
