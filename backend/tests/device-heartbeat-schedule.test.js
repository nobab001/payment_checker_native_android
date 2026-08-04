'use strict';

/**
 * O (partial): device schedules from server-returned heartbeat seconds + jitter bounds.
 * Mirrors CommPolicyStore / AppConfig rules without Android runtime.
 */
const assert = require('assert');

const JITTER_MS = 30_000;
const MIN_MS = 30_000;

function scheduleNextMs(heartbeatSec) {
  const baseMs = heartbeatSec * 1000;
  // worst-case bounds of ±jitter (actual uses random)
  return {
    min: Math.max(MIN_MS, baseMs - JITTER_MS),
    max: baseMs + JITTER_MS,
    base: baseMs,
  };
}

const gateway = scheduleNextMs(900);
assert.strictEqual(gateway.base, 900_000);
assert.ok(gateway.min >= 870_000);
assert.ok(gateway.max <= 930_000);

const personal = scheduleNextMs(3600);
assert.strictEqual(personal.base, 3_600_000);

const pb = scheduleNextMs(1800);
assert.strictEqual(pb.base, 1_800_000);

console.log('device-heartbeat-schedule.test.js PASS');
