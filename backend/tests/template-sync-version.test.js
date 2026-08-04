'use strict';

/**
 * A/C: template version bump + forceSync instruction (no DB — pure helpers).
 */
const assert = require('assert');
const { shouldForceTemplateSync } = require('../services/commPolicyService');

function simulateBumpAndForceSync(localVersion, bumpedVersion) {
  const forceSync = shouldForceTemplateSync(localVersion, bumpedVersion);
  return { forceSync, templateVersion: bumpedVersion };
}

// A: admin bump → newer version
const afterBump = simulateBumpAndForceSync(1_700_000_000_000, 1_700_000_000_500);
assert.strictEqual(afterBump.forceSync, true);

// B: current device → no sync
const current = simulateBumpAndForceSync(1_700_000_000_500, 1_700_000_000_500);
assert.strictEqual(current.forceSync, false);

// C: stale → sync
const stale = simulateBumpAndForceSync(1_700_000_000_000, 1_700_000_000_999);
assert.strictEqual(stale.forceSync, true);

/**
 * E: failed sync must not advance local version (pure state machine).
 */
function applySyncResult(localVersion, { methodsOk, templatesOk, appliedVersion }) {
  if (methodsOk && templatesOk && appliedVersion > 0) {
    return appliedVersion;
  }
  return localVersion;
}

assert.strictEqual(
  applySyncResult(100, { methodsOk: true, templatesOk: true, appliedVersion: 200 }),
  200
);
assert.strictEqual(
  applySyncResult(100, { methodsOk: false, templatesOk: true, appliedVersion: 200 }),
  100,
  'E: failed methods write must not advance version'
);
assert.strictEqual(
  applySyncResult(100, { methodsOk: true, templatesOk: false, appliedVersion: 200 }),
  100,
  'E: failed templates write must not advance version'
);

console.log('template-sync-version.test.js PASS');
