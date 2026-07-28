/**
 * Subscription v3 unit tests (pure helpers — no DB).
 * Run: node backend/services/subscriptionV3/__tests__/subscriptionV3.test.js
 */

const assert = require('assert');
const { discountPercent, priceForDuration } = require('../catalogService');
const { remainingDays, addDays, formatYmd, dateOnly } = require('../sharedExpiryService');
const { DURATION_DAYS } = require('../constants');

function run() {
  let passed = 0;

  const pkg = {
    price_1m: 100,
    price_6m: 250,
    price_12m: 495,
  };

  assert.strictEqual(priceForDuration(pkg, '1m'), 100);
  assert.strictEqual(priceForDuration(pkg, '12m'), 495);
  passed++;

  const disc6 = discountPercent(pkg, '6m');
  assert.ok(disc6 > 0 && disc6 < 60, `expected 6m discount, got ${disc6}`);
  passed++;

  const disc12 = discountPercent(pkg, '12m');
  assert.ok(disc12 > 0, `expected 12m discount, got ${disc12}`);
  passed++;

  const today = dateOnly(new Date('2026-07-28'));
  const future = addDays(today, 30);
  assert.strictEqual(formatYmd(future), '2026-08-27');
  passed++;

  assert.strictEqual(remainingDays(today, future), 30);
  assert.strictEqual(remainingDays(future, today), 0);
  passed++;

  assert.strictEqual(DURATION_DAYS['1m'], 30);
  assert.strictEqual(DURATION_DAYS['6m'], 180);
  assert.strictEqual(DURATION_DAYS['12m'], 365);
  passed++;

  console.log(`subscriptionV3 tests passed: ${passed}`);
}

run();
