/**
 * Phase-3B bkash_live unit tests (no DB — adapter, state machine, idempotency, normalize).
 * Run: node backend/payment/__tests__/phase-3b-bkash.test.js
 */

const assert = require('assert');
const crypto = require('crypto');
const { getProvider } = require('../registry/provider-factory');
const { assertTransition, canTransition } = require('../state/payment-state-machine');
const { check, lock, complete } = require('../idempotency/idempotency-manager');
const { buildMerchantCallbackV1, validateMerchantCallbackV1 } = require('../core/merchant-callback-v1');
const { PAYMENT_STATUS } = require('../core/payment-status');

async function run() {
  let passed = 0;

  // Invalid transition FAILED → SUCCESS blocked
  assert.strictEqual(canTransition('failed', 'success'), false);
  try {
    assertTransition('failed', 'success');
    throw new Error('should throw');
  } catch (e) {
    assert.strictEqual(e.code, 'INVALID_STATE_TRANSITION');
  }
  passed++;

  // Idempotency duplicate
  const key = `test:${Date.now()}`;
  const l1 = await lock(key);
  assert.strictEqual(l1.acquired, true);
  const l2 = await lock(key);
  assert.strictEqual(l2.acquired, false);
  await complete(key, { ok: true });
  const c = await check(key);
  assert.strictEqual(c.status, 'completed');
  passed++;

  const adapter = getProvider('bkash_live');

  // normalize shape
  const normalized = await adapter.normalize(
    { success: true, trxId: 'TXN1', sessionToken: 'ps_abc', raw: { status: 'success' } },
    { amount: 500, currency: 'BDT', orderId: 'ORD1', sessionToken: 'ps_abc' },
  );
  assert.strictEqual(normalized.provider, 'bkash_live');
  assert.strictEqual(normalized.status, PAYMENT_STATUS.SUCCESS);
  assert.strictEqual(normalized.amount, 500);
  passed++;

  // MerchantCallbackV1 from normalized
  const mc = buildMerchantCallbackV1({
    paymentId: 'ps_abc',
    merchantId: '1',
    websiteId: '2',
    provider: normalized.provider,
    providerTransactionId: normalized.providerTransactionId,
    merchantTransactionId: normalized.merchantTransactionId,
    amount: normalized.amount,
    status: normalized.status,
    traceId: 'ptr_test',
    currency: 'BDT',
  });
  assert.strictEqual(validateMerchantCallbackV1(mc).length, 0);
  passed++;

  // Signature verify
  const secret = 'test-secret';
  const payload = { token: 'ps_x', status: 'success', trxId: 'T1', amount: '500' };
  const sig = crypto.createHmac('sha256', secret).update('amount=500&status=success&token=ps_x&trxId=T1').digest('hex');
  assert.strictEqual(adapter.verifySignature(payload, sig, { callbackSecret: secret }, null), true);
  assert.strictEqual(adapter.verifySignature(payload, 'bad', { callbackSecret: secret }, null), false);
  passed++;

  // Invalid signature → PAY_1005 mapping
  const { fromProviderError, PAY_ERROR_CODES } = require('../errors/error-registry');
  const { body } = fromProviderError(PAY_ERROR_CODES.INVALID_SIGNATURE);
  assert.strictEqual(body.errorCode, 'PAY_1005');
  passed++;

  console.log(`phase-3b-bkash tests: ${passed} passed`);
  process.exit(0);
}

run().catch((e) => {
  console.error(e);
  process.exit(1);
});
