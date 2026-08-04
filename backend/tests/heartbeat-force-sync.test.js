'use strict';

/**
 * Heartbeat forceSync gate: client lastSync behind global template version.
 */
function shouldForceSync(clientLastSync, templateVersion) {
  const client = parseInt(clientLastSync || '0', 10) || 0;
  const tpl = Number(templateVersion) || 0;
  return tpl > 0 && client < tpl;
}

const assert = require('assert');
assert.strictEqual(shouldForceSync(100, 200), true);
assert.strictEqual(shouldForceSync(200, 200), false);
assert.strictEqual(shouldForceSync(300, 200), false);
assert.strictEqual(shouldForceSync(0, 200), true);
assert.strictEqual(shouldForceSync(100, 0), false);
assert.strictEqual(shouldForceSync('150', '151'), true);

console.log('heartbeat-force-sync.test.js PASS');
