/**
 * Staff/owner post-login device-management parity — route wiring checks (no DB).
 * Run: node backend/tests/device-mgmt-caller-parity.test.js
 */
'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const routesPath = path.join(__dirname, '..', 'routes', 'authRoutes.js');
const src = fs.readFileSync(routesPath, 'utf8');

function routeLine(methodPath) {
  const re = new RegExp(
    String.raw`router\.post\(\s*'${methodPath.replace(/\//g, '\\/')}'\s*,([\s\S]*?)authController\.`
  );
  const m = src.match(re);
  assert.ok(m, `route not found: POST ${methodPath}`);
  return m[1];
}

let passed = 0;

for (const p of [
  '/v1/devices/remote-update',
  '/v1/devices/approve-by-pin',
  '/v1/devices/delete',
]) {
  const middlewareChunk = routeLine(p);
  assert.ok(
    middlewareChunk.includes('authenticateToken'),
    `${p} must still require JWT authenticateToken`
  );
  assert.ok(
    !middlewareChunk.includes('requireOwnerCaller'),
    `${p} must NOT gate on requireOwnerCaller (staff/owner parity)`
  );
  assert.ok(
    !middlewareChunk.includes('restrictDevice'),
    `${p} must NOT gate on restrictDevice`
  );
  passed++;
  console.log(`PASS ${p}: JWT only, no caller-role gate`);
}

// Controllers must still scope by account userId (spot-check source contracts).
const controllerPath = path.join(__dirname, '..', 'controllers', 'authController.js');
const controller = fs.readFileSync(controllerPath, 'utf8');
for (const fn of ['remoteUpdateDevice', 'approveByPin', 'deleteDevice']) {
  assert.ok(controller.includes(`async function ${fn}`), `missing ${fn}`);
}
assert.ok(
  /WHERE user_id = \? AND device_id = \?/.test(controller),
  'target device updates must remain scoped by user_id + device_id'
);
assert.ok(
  controller.includes('verifyAccountPin'),
  'approve/delete must keep account PIN authorization'
);
passed++;
console.log('PASS controller account/target authorization markers present');

console.log(`---SUMMARY--- pass=${passed}`);
