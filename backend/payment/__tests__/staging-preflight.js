#!/usr/bin/env node
/**
 * Phase-3B.6 Staging Preflight — run before sandbox QA.
 * Usage: npm run staging:preflight
 */
require('dotenv').config({ path: require('path').join(__dirname, '../../.env') });

const prisma = require('../../db/prisma');
const { getRedisClient } = require('../../services/redisClient');

const REQUIRED_ENV = [
  'DATABASE_URL',
  'PAYMENT_METRICS_API_KEY',
];

const STAGING_RECOMMENDED = [
  'APP_ENV',
  'REDIS_HOST',
  'BKASH_API_BASE',
];

let failed = 0;
let warned = 0;

function pass(msg) { console.log(`✅ ${msg}`); }
function fail(msg) { console.error(`❌ ${msg}`); failed += 1; }
function warn(msg) { console.warn(`⚠️  ${msg}`); warned += 1; }

async function checkEnv() {
  console.log('\n── Stage-2: Environment Variables ──');
  if (process.env.APP_ENV !== 'staging') {
    warn(`APP_ENV=${process.env.APP_ENV || '(unset)'} — set APP_ENV=staging on staging server`);
  } else {
    pass('APP_ENV=staging');
  }

  for (const key of REQUIRED_ENV) {
    if (!process.env[key] && !(key === 'DATABASE_URL' && process.env.DB_HOST)) {
      fail(`Missing ${key}`);
    } else {
      pass(`${key} is set`);
    }
  }

  for (const key of STAGING_RECOMMENDED) {
    if (!process.env[key]) warn(`Optional/recommended: ${key}`);
    else pass(`${key} is set`);
  }

  if (process.env.NODE_ENV === 'production' && process.env.APP_ENV !== 'staging') {
    warn('NODE_ENV=production on non-staging APP_ENV — verify this is intentional');
  }
}

async function checkDatabase() {
  console.log('\n── Stage-1: Database & Indexes ──');
  try {
    await prisma.$queryRaw`SELECT 1`;
    pass('Database connected');
  } catch (e) {
    fail(`Database unreachable: ${e.message}`);
    return;
  }

  const tables = ['payment_sessions', 'merchant_callback_outbox', 'audit_logs'];
  for (const table of tables) {
    try {
      await prisma.$queryRawUnsafe(`SELECT 1 FROM \`${table}\` LIMIT 1`);
      pass(`Table exists: ${table}`);
    } catch (e) {
      fail(`Table missing: ${table} — run npm run db:hardening-3b5`);
    }
  }

  const indexes = await prisma.$queryRawUnsafe(
    "SHOW INDEX FROM payment_sessions WHERE Key_name = 'uniq_website_trx'",
  ).catch(() => []);
  if (indexes.length) pass('Index uniq_website_trx on payment_sessions');
  else fail('Missing index uniq_website_trx — run scripts/add-website-trx-unique.js');

  const outboxIdx = await prisma.$queryRawUnsafe(
    "SHOW INDEX FROM merchant_callback_outbox WHERE Key_name = 'uniq_mcb_delivery_key'",
  ).catch(() => []);
  if (outboxIdx.length) pass('Index uniq_mcb_delivery_key on merchant_callback_outbox');
  else fail('Missing index uniq_mcb_delivery_key');
}

async function checkRedis() {
  console.log('\n── Stage-3: Redis ──');
  try {
    const redis = getRedisClient();
    const pong = await Promise.race([
      redis.ping(),
      new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), 2000)),
    ]);
    if (pong === 'PONG') pass('Redis PING ok');
    else warn(`Redis unexpected response: ${pong}`);
  } catch (e) {
    warn(`Redis unavailable (${e.message}) — idempotency will use memory fallback`);
  }
}

async function checkAutomatedGate() {
  console.log('\n── Automated Gate (optional) ──');
  if (process.argv.includes('--skip-gate')) {
    warn('Skipped npm run test:payment-gate (--skip-gate)');
    return;
  }
  try {
    const { execSync } = require('child_process');
    execSync('npm run test:payment-gate', {
      cwd: require('path').join(__dirname, '../..'),
      stdio: 'pipe',
      encoding: 'utf8',
    });
    pass('test:payment-gate PASS');
  } catch (e) {
    fail('test:payment-gate FAIL — fix before staging QA');
    if (e.stdout) console.error(e.stdout.slice(-2000));
  }
}

async function main() {
  console.log('═══ Phase-3B.6 Staging Preflight ═══');
  await checkEnv();
  await checkDatabase();
  await checkRedis();
  await checkAutomatedGate();

  console.log('\n═══ Summary ═══');
  console.log(`FAIL: ${failed} | WARN: ${warned}`);
  if (failed > 0) {
    console.error('\n⛔ Preflight FAILED — fix before staging sandbox QA.');
    process.exit(1);
  }
  console.log('\n✅ Preflight passed. Proceed to STAGING_DEPLOY_GATE.md Stage-4 (Functional QA).');
  await prisma.$disconnect();
  process.exit(0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
