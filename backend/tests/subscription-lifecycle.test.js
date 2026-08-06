/**
 * Subscription lifecycle — expiry must suspend without nulling expiry_date.
 */
const assert = require('assert');
const {
  STATUS_ACTIVE,
  STATUS_SUSPENDED,
  VALID_STATUSES,
} = require('../services/subscriptionStatusService');

assert.ok(VALID_STATUSES.has(STATUS_ACTIVE));
assert.ok(VALID_STATUSES.has('grace'));
assert.ok(VALID_STATUSES.has(STATUS_SUSPENDED));

// billingScheduler must NOT null expiry_date (regression guard on source text).
const fs = require('fs');
const path = require('path');
const schedulerSrc = fs.readFileSync(
  path.join(__dirname, '../cron/billingScheduler.js'),
  'utf8'
);
assert.ok(
  !/expiry_date\s*=\s*NULL/i.test(schedulerSrc),
  'billingScheduler must not null expiry_date on suspend'
);
assert.ok(
  schedulerSrc.includes("subscription_status = ${STATUS_SUSPENDED}"),
  'billingScheduler must set subscription_status suspended'
);
assert.ok(
  schedulerSrc.includes('expiry_date is deliberately preserved'),
  'billingScheduler must document expiry_date preservation'
);

// Heartbeat must expose STOP_MONITORING for suspended accounts.
const hbSrc = fs.readFileSync(
  path.join(__dirname, '../controllers/heartbeatController.js'),
  'utf8'
);
assert.ok(hbSrc.includes("'STOP_MONITORING'"), 'heartbeat must return STOP_MONITORING action');

// Gateway heartbeat route must use attachBillingStatus (not blocking checkBillingStatus).
const gwRoutes = fs.readFileSync(
  path.join(__dirname, '../routes/gatewayRoutes.js'),
  'utf8'
);
assert.ok(
  gwRoutes.includes("attachBillingStatus, hb.postHeartbeat"),
  'gateway heartbeat must use attachBillingStatus'
);

console.log('subscription-lifecycle.test.js PASS');
