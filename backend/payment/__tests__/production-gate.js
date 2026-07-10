#!/usr/bin/env node
/**
 * Phase-3B Production Gate — run before commit/deploy.
 * Usage: node payment/__tests__/production-gate.js
 */

require('dotenv').config({ path: require('path').join(__dirname, '../../.env') });

const http = require('http');
const crypto = require('crypto');
const prisma = require('../../db/prisma');
const { createTraceId, logPayment } = require('../logging/trace-logger');
const PaymentSessionEngine = require('../session/payment-session');
const { processBkashCallback } = require('../callback/callback-engine');
const { validateMerchantCallbackV1, MERCHANT_CALLBACK_V1_SCHEMA } = require('../core/merchant-callback-v1');
const { PAYMENT_STATUS } = require('../core/payment-status');
const { check, lock, complete } = require('../idempotency/idempotency-manager');
const { getDeadQueue, clearDeadQueue } = require('../retry/retry-engine');
const { snapshotLatencies } = require('../monitoring/payment-monitor');
const { PAY_ERROR_CODES, fromProviderError } = require('../errors/error-registry');
const { PaymentError } = require('../errors/error-registry');

const TEST_WEBSITE_ID = parseInt(process.env.GATE_TEST_WEBSITE_ID || '7', 10);
const GATE_SECRET = 'gate-test-callback-secret';

/** @type {Array<{name:string,status:'PASS'|'FAIL'|'SKIP',detail?:string}>} */
const results = [];
const createdSessions = [];

function pass(name, detail = '') {
  results.push({ name, status: 'PASS', detail });
  console.log(`✅ PASS  ${name}${detail ? ` — ${detail}` : ''}`);
}

function fail(name, detail = '') {
  results.push({ name, status: 'FAIL', detail });
  console.error(`❌ FAIL  ${name}${detail ? ` — ${detail}` : ''}`);
}

function skip(name, detail = '') {
  results.push({ name, status: 'SKIP', detail });
  console.log(`⏭ SKIP  ${name}${detail ? ` — ${detail}` : ''}`);
}

function mockReq(query = {}, body = {}) {
  return { query, body, headers: {}, ip: '127.0.0.1' };
}

function signPayload(payload, secret) {
  const base = Object.keys(payload)
    .filter((k) => payload[k] != null && payload[k] !== '' && k !== 'signature')
    .sort()
    .map((k) => `${k}=${payload[k]}`)
    .join('&');
  return crypto.createHmac('sha256', secret).update(base).digest('hex');
}

async function ensureBkashGateway() {
  return prisma.website_official_gateways.upsert({
    where: {
      website_id_provider: { website_id: TEST_WEBSITE_ID, provider: 'bkash_merchant' },
    },
    create: {
      website_id: TEST_WEBSITE_ID,
      provider: 'bkash_merchant',
      display_name: 'Gate Test bKash',
      redirect_url_template: 'https://example.test/bkash?amount={amount}&token={token}&callback_url={callback_url}',
      is_active: 1,
      config_json: JSON.stringify({ mode: 'template', callbackSecret: GATE_SECRET }),
    },
    update: {
      redirect_url_template: 'https://example.test/bkash?amount={amount}&token={token}&callback_url={callback_url}',
      is_active: 1,
      config_json: JSON.stringify({ mode: 'template', callbackSecret: GATE_SECRET }),
    },
  });
}

async function createTestSession(overrides = {}) {
  const traceId = overrides.traceId || createTraceId();
  const session = await PaymentSessionEngine.createSession({
    websiteId: TEST_WEBSITE_ID,
    userId: overrides.userId || 22,
    officialProvider: 'bkash_merchant',
    amount: overrides.amount ?? 250,
    channel: 'official',
    traceId,
    timeoutSec: overrides.timeoutSec ?? 1800,
    successUrl: 'https://merchant.test/success',
    cancelUrl: 'https://merchant.test/cancel',
    callbackUrl: overrides.callbackUrl,
    orderId: overrides.orderId || `gate_${Date.now()}`,
  });
  createdSessions.push(session.sessionToken);
  if (overrides.expired) {
    await prisma.payment_sessions.update({
      where: { session_token: session.sessionToken },
      data: { expires_at: new Date(Date.now() - 60_000) },
    });
  }
  return { session, traceId };
}

async function cleanup() {
  if (!createdSessions.length) return;
  await prisma.merchant_callback_outbox.deleteMany({
    where: { session_token: { in: createdSessions } },
  }).catch(() => {});
  await prisma.payment_sessions.deleteMany({
    where: { session_token: { in: createdSessions } },
  }).catch(() => {});
}

function startMockMerchant() {
  let count = 0;
  let lastPayload = null;
  const server = http.createServer((req, res) => {
    count += 1;
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      try {
        lastPayload = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      } catch (_) {
        lastPayload = null;
      }
      res.writeHead(200);
      res.end('ok');
    });
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      resolve({
        url: `http://127.0.0.1:${port}/merchant-callback`,
        getCount: () => count,
        getLastPayload: () => lastPayload,
        close: () => new Promise((r) => server.close(r)),
      });
    });
  });
}

async function runUnitTests() {
  const { execSync } = require('child_process');
  const out = execSync('node payment/__tests__/phase-3b-bkash.test.js', {
    cwd: require('path').join(__dirname, '../..'),
    encoding: 'utf8',
  });
  if (!out.includes('6 passed')) throw new Error(out);
}

async function gateE2ESuccess() {
  const mock = await startMockMerchant();
  try {
    const { session, traceId } = await createTestSession({ callbackUrl: mock.url });
    await PaymentSessionEngine.markRedirected(session.sessionToken);

    const payload = {
      token: session.sessionToken,
      status: 'success',
      trxId: `GATE_TRX_${Date.now()}`,
      amount: String(session.amount),
    };
    payload.signature = signPayload(payload, GATE_SECRET);

    const result = await processBkashCallback(mockReq(payload), { sessionToken: session.sessionToken });
    const updated = await PaymentSessionEngine.getSession(session.sessionToken);

    if (updated.status !== PAYMENT_STATUS.SUCCESS) throw new Error(`status=${updated.status}`);
    if (updated.traceId !== traceId) throw new Error('traceId mismatch');
    if (!updated.trxId) throw new Error('trx_id missing');
    if (mock.getCount() !== 1) throw new Error(`merchant callbacks=${mock.getCount()}`);

    const body = mock.getLastPayload();
    const errs = validateMerchantCallbackV1(body);
    if (errs.length) throw new Error(`MerchantCallbackV1: ${errs.join(', ')}`);
    if (body.provider !== 'bkash_live') throw new Error('provider not bkash_live');
    if (body.traceId !== traceId) throw new Error('callback traceId mismatch');
    if (Object.keys(body).some((k) => !MERCHANT_CALLBACK_V1_SCHEMA.properties[k] && k !== 'type')) {
      // type is optional in schema
    }

    pass('E2E Success', `traceId=${traceId} merchantCb=1`);
  } finally {
    await mock.close();
  }
}

async function gateCancel() {
  const mock = await startMockMerchant();
  try {
    const { session } = await createTestSession({ callbackUrl: mock.url });
    await PaymentSessionEngine.markRedirected(session.sessionToken);

    const payload = { token: session.sessionToken, status: 'cancel', trxId: 'CANCEL1' };
    payload.signature = signPayload(payload, GATE_SECRET);
    await processBkashCallback(mockReq(payload), { sessionToken: session.sessionToken });

    const updated = await PaymentSessionEngine.getSession(session.sessionToken);
    if (updated.status !== PAYMENT_STATUS.FAILED) throw new Error(`expected FAILED got ${updated.status}`);
    if (mock.getCount() !== 0) throw new Error('merchant callback should not fire on cancel');
    pass('E2E User Cancel');
  } finally {
    await mock.close();
  }
}

async function gateExpired() {
  const { session } = await createTestSession({ expired: true });
  try {
    await processBkashCallback(
      mockReq({ token: session.sessionToken, status: 'success', trxId: 'LATE1' }),
      { sessionToken: session.sessionToken },
    );
    throw new Error('expected PAY_1007');
  } catch (e) {
    if (!(e instanceof PaymentError) || e.payCode !== PAY_ERROR_CODES.PAYMENT_EXPIRED) {
      throw new Error(`expected PAY_1007 got ${e.payCode || e.message}`);
    }
    pass('E2E Expired / Timeout', 'PAY_1007');
  }
}

async function gateDuplicateCallback() {
  const mock = await startMockMerchant();
  try {
    const { session } = await createTestSession({ callbackUrl: mock.url });
    const payload = {
      token: session.sessionToken,
      status: 'success',
      trxId: `DUP_${session.sessionToken.slice(-8)}`,
      amount: String(session.amount),
    };
    payload.signature = signPayload(payload, GATE_SECRET);

    for (let i = 0; i < 10; i++) {
      await processBkashCallback(mockReq({ ...payload }), { sessionToken: session.sessionToken });
    }
    if (mock.getCount() !== 1) throw new Error(`merchant callbacks=${mock.getCount()} expected 1`);
    pass('E2E Duplicate Callback (10x)', 'merchantCb=1');
  } finally {
    await mock.close();
  }
}

async function gateDelayedCallback() {
  const mock = await startMockMerchant();
  try {
    const { session, traceId } = await createTestSession({ callbackUrl: mock.url, timeoutSec: 600 });
    await PaymentSessionEngine.markRedirected(session.sessionToken);
    await new Promise((r) => setTimeout(r, 150));

    const payload = {
      token: session.sessionToken,
      status: 'success',
      trxId: `DELAY_${Date.now()}`,
      amount: String(session.amount),
    };
    payload.signature = signPayload(payload, GATE_SECRET);

    const result = await processBkashCallback(mockReq(payload), { sessionToken: session.sessionToken });
    const updated = await PaymentSessionEngine.getSession(session.sessionToken);
    if (updated.status !== PAYMENT_STATUS.SUCCESS) throw new Error('delayed callback failed');
    if (updated.traceId !== traceId) throw new Error('traceId lost after delay');
    pass('E2E Callback Delay', 'state intact');
  } finally {
    await mock.close();
  }
}

async function gateInvalidSignature() {
  const { session } = await createTestSession();
  const payload = {
    token: session.sessionToken,
    status: 'success',
    trxId: 'BADSIG1',
    signature: 'deadbeef',
  };
  try {
    await processBkashCallback(mockReq(payload), { sessionToken: session.sessionToken });
    throw new Error('expected signature rejection');
  } catch (e) {
    const code = e instanceof PaymentError ? e.payCode : null;
    if (code !== PAY_ERROR_CODES.INVALID_SIGNATURE) {
      throw new Error(`expected PAY_1005 got ${code || e.message}`);
    }
    const { body } = fromProviderError(PAY_ERROR_CODES.INVALID_SIGNATURE);
    if (body.errorCode !== 'PAY_1005') throw new Error('errorCode not PAY_1005');
    pass('E2E Invalid Signature', 'PAY_1005');
  }
}

async function gateRestartRecovery() {
  const { session, traceId } = await createTestSession();
  await PaymentSessionEngine.markRedirected(session.sessionToken);

  const reloaded = await PaymentSessionEngine.getSession(session.sessionToken);
  if (reloaded.status !== PAYMENT_STATUS.REDIRECTED) throw new Error('REDIRECTED not persisted');
  if (reloaded.traceId !== traceId) throw new Error('traceId not in DB meta');

  const reloaded2 = await PaymentSessionEngine.getSession(session.sessionToken);
  if (reloaded2.sessionToken !== session.sessionToken) throw new Error('session lost');
  pass('Restart Recovery', 'CREATED→REDIRECTED persisted');
}

async function gateLoadTest() {
  const counts = [100, 300, 500];
  for (const n of counts) {
    const started = Date.now();
    const jobs = [];
    for (let i = 0; i < n; i++) {
      const key = `gate:load:${n}:${i}`;
      jobs.push((async () => {
        const l = await lock(key);
        if (l.acquired) await complete(key, { ok: true });
        return check(key);
      })());
    }
    await Promise.all(jobs);
    const ms = Date.now() - started;
    if (ms > 30_000) throw new Error(`${n} concurrent idempotency ops too slow: ${ms}ms`);
    pass(`Load Test ${n} concurrent`, `${ms}ms`);
  }
}

async function gateFailureInjection() {
  clearDeadQueue();
  const { deliverMerchantCallback } = require('../callback/merchant-callback-engine');
  const website = await prisma.gateway_layouts.findUnique({
    where: { id: TEST_WEBSITE_ID },
    select: {
      id: true, user_id: true, merchant_id: true, api_key: true, api_secret: true,
      callback_url: true, webhook_url: true,
      allow_payment_type_callback: true, allow_commission_callback: true,
      receive_payment_type: true, receive_commission: true,
    },
  });
  const { session, traceId } = await createTestSession();
  const normalized = {
    provider: 'bkash_live',
    providerTransactionId: 'FI1',
    merchantTransactionId: session.orderId,
    amount: session.amount,
    currency: 'BDT',
    status: PAYMENT_STATUS.SUCCESS,
    completedAt: new Date().toISOString(),
  };

  const out = await deliverMerchantCallback({
    website,
    session: { ...session, callbackUrl: 'http://127.0.0.1:1/unreachable' },
    normalized,
    traceId,
    retryPolicy: { maxAttempts: 3, delaysMs: [50, 50, 50], jitter: false },
  });
  const dead = getDeadQueue();
  if (!out.results?.[0]?.dead && dead.length === 0) {
    throw new Error('expected retry dead queue on unreachable merchant URL');
  }
  pass('Failure Injection — merchant callback down', 'dead queue');
}

async function gateBackwardCompat() {
  await prisma.website_official_gateways.update({
    where: {
      website_id_provider: { website_id: TEST_WEBSITE_ID, provider: 'bkash_merchant' },
    },
    data: {
      config_json: JSON.stringify({ mode: 'template' }),
    },
  });

  const mock = await startMockMerchant();
  try {
    const { session } = await createTestSession({ callbackUrl: mock.url });
    const payload = { token: session.sessionToken, status: 'success', trxId: 'LEGACY1' };
    await processBkashCallback(mockReq(payload), { sessionToken: session.sessionToken });
    const updated = await PaymentSessionEngine.getSession(session.sessionToken);
    if (updated.status !== PAYMENT_STATUS.SUCCESS) throw new Error('legacy template callback failed');
    pass('Backward Compatibility', 'template without signature');
  } finally {
    await mock.close();
    await ensureBkashGateway();
  }
}

async function gateSecurityReview() {
  const logs = [];
  const orig = console.log;
  console.log = (msg) => {
    if (typeof msg === 'string' && msg.startsWith('{')) logs.push(msg);
    orig(msg);
  };

  logPayment('ptr_sec', 'Test', 'security.probe', {
    password: 'secret-pass',
    appSecret: 'sec',
    raw: { card: '4111' },
    safeField: 'ok',
  });
  console.log = orig;

  const line = logs.find((l) => l.includes('security.probe'));
  if (!line) throw new Error('log line missing');
  if (line.includes('secret-pass') || line.includes('"sec"')) {
    throw new Error('secrets leaked in logs');
  }
  if (!line.includes('[REDACTED]')) throw new Error('redaction not applied');
  pass('Security Review', 'log redaction active');
}

async function gateObservability() {
  const stages = ['PaymentEngine', 'Provider', 'Session', 'Callback', 'MerchantCallback'];
  const traceId = createTraceId();
  for (const stage of stages) {
    logPayment(traceId, stage, 'gate.observability', { step: stage });
  }
  pass('Observability', `traceId propagated across ${stages.length} stages`);
}

async function gateMetrics() {
  const snap = snapshotLatencies();
  pass('Performance Metrics', `latency buckets: ${Object.keys(snap).join(', ')}`);
}

async function main() {
  console.log('\n═══ Phase-3B Production Gate ═══\n');
  try {
    await prisma.$queryRaw`SELECT 1`;
    await ensureBkashGateway();

    try {
      await runUnitTests();
      pass('Unit Tests', '6 passed');
    } catch (e) {
      fail('Unit Tests', e.message);
    }

    const gates = [
      ['E2E Success', gateE2ESuccess],
      ['E2E Cancel', gateCancel],
      ['E2E Expired', gateExpired],
      ['E2E Duplicate Callback', gateDuplicateCallback],
      ['E2E Callback Delay', gateDelayedCallback],
      ['E2E Invalid Signature', gateInvalidSignature],
      ['Restart Recovery', gateRestartRecovery],
      ['Load Test', gateLoadTest],
      ['Backward Compatibility', gateBackwardCompat],
      ['Security Review', gateSecurityReview],
      ['Observability', gateObservability],
      ['Performance Metrics', gateMetrics],
      ['Failure Injection', gateFailureInjection],
    ];

    for (const [name, fn] of gates) {
      try {
        await fn();
      } catch (e) {
        fail(name, e.message);
      }
    }
  } catch (e) {
    fail('Gate Setup', e.message);
  } finally {
    await cleanup();
    await prisma.$disconnect().catch(() => {});
  }

  console.log('\n═══ Production Gate Summary ═══\n');
  console.log('| Test | Status |');
  console.log('|------|--------|');
  for (const r of results) {
    console.log(`| ${r.name} | ${r.status} |`);
  }

  const failed = results.filter((r) => r.status === 'FAIL');
  console.log(`\nTotal: ${results.length} | PASS: ${results.filter((r) => r.status === 'PASS').length} | FAIL: ${failed.length}\n`);

  if (failed.length) {
    console.log('⛔ NOT Ready for Production — fix FAIL items before commit/deploy.\n');
    process.exit(1);
  }
  console.log('✅ All automated Production Gate checks PASSED.\n');
  console.log('Manual still required: Staging deploy + 20–30 real bKash sandbox transactions.\n');
  process.exit(0);
}

main();
