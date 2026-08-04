'use strict';

const assert = require('assert');
const {
  PROFILES,
  resolveProfileFromActiveCategories,
  collectActiveCategoryKeys,
  shouldForceTemplateSync,
  isActiveDate,
} = require('../services/commPolicyService');

// F–I: single-package intervals
assert.strictEqual(PROFILES.welcome.heartbeatSec, 900);
assert.strictEqual(PROFILES.gateway.heartbeatSec, 900);
assert.strictEqual(PROFILES.personal_business.heartbeatSec, 1800);
assert.strictEqual(PROFILES.personal.heartbeatSec, 3600);

// J: Gateway + Personal = 15 min
assert.strictEqual(
  resolveProfileFromActiveCategories(['payment_gateway', 'personal']).heartbeatSec,
  900
);

// K: Personal + Personal Business = 30 min
assert.strictEqual(
  resolveProfileFromActiveCategories(['personal', 'personal_business']).heartbeatSec,
  1800
);

// L: Gateway + Personal + Personal Business = 15 min
assert.strictEqual(
  resolveProfileFromActiveCategories(['payment_gateway', 'personal', 'personal_business']).heartbeatSec,
  900
);

// Trial alone / Trial + Personal = 15
assert.strictEqual(resolveProfileFromActiveCategories(['welcome']).heartbeatSec, 900);
assert.strictEqual(
  resolveProfileFromActiveCategories(['welcome', 'personal']).heartbeatSec,
  900
);

// Personal only = 60
assert.strictEqual(resolveProfileFromActiveCategories(['personal']).heartbeatSec, 3600);

// M: expired package no longer contributes
const tomorrow = new Date();
tomorrow.setDate(tomorrow.getDate() + 7);
const yesterday = new Date();
yesterday.setDate(yesterday.getDate() - 2);

const keysActive = collectActiveCategoryKeys({
  subscriptionRows: [
    { category: 'personal', status: 'active', expires_at: tomorrow },
    { category: 'payment_gateway', status: 'active', expires_at: yesterday },
  ],
});
assert.deepStrictEqual(
  resolveProfileFromActiveCategories(keysActive).heartbeatSec,
  3600,
  'expired gateway must not keep 15m interval'
);

// N: newly activated package changes interval
const keysWithGateway = collectActiveCategoryKeys({
  subscriptionRows: [
    { category: 'personal', status: 'active', expires_at: tomorrow },
    { category: 'payment_gateway', status: 'active', expires_at: tomorrow },
  ],
});
assert.strictEqual(resolveProfileFromActiveCategories(keysWithGateway).heartbeatSec, 900);

// Refunded/inactive status ignored
const keysInactive = collectActiveCategoryKeys({
  subscriptionRows: [
    { category: 'payment_gateway', status: 'refunded', expires_at: tomorrow },
    { category: 'personal_business', status: 'active', expires_at: tomorrow },
  ],
});
assert.strictEqual(resolveProfileFromActiveCategories(keysInactive).heartbeatSec, 1800);

// C / B: forceSync gate
assert.strictEqual(shouldForceTemplateSync(100, 200), true);
assert.strictEqual(shouldForceTemplateSync(200, 200), false);
assert.strictEqual(shouldForceTemplateSync(300, 200), false);
assert.strictEqual(shouldForceTemplateSync(0, 200), true);

assert.strictEqual(isActiveDate(yesterday), false);
assert.strictEqual(isActiveDate(tomorrow), true);

console.log('comm-policy-heartbeat.test.js PASS');
