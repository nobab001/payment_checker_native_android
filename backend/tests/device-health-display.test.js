'use strict';

const assert = require('assert');
const { computeState } = require('../services/numberHealthService');
const commPolicy = require('../services/commPolicyService');

const profile = commPolicy.PROFILES.gateway;
const now = Date.now();

assert.strictEqual(computeState(now, false, now, profile), 'ONLINE');
assert.strictEqual(computeState(now - profile.onlineMs - 1000, false, now, profile), 'GRACE');
assert.strictEqual(
  computeState(now - profile.graceMs - 1000, false, now, profile),
  'OFFLINE'
);
assert.strictEqual(
  computeState(now - (7 * 24 * 60 * 60 * 1000), false, now, profile),
  'STALE'
);

console.log('device-health-display.test.js PASS');
