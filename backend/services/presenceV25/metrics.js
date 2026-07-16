/**
 * Presence v2.5 Phase 2 metrics (in-memory counters).
 * No Prometheus dependency here; counters are for debug/monitoring.
 */
const counters = {
  alive_calls: 0,
  offline_calls: 0,
  heartbeat_success: 0,
  boot_success: 0,
  network_restored_success: 0,
  sms_success: 0,
  login_success: 0,
  probe_started: 0,
  probe_cancelled: 0,
  probe_resumed: 0,
  lock_skipped: 0,
  state_flip_online: 0,
  state_flip_offline: 0,
  false_offline_count: 0,
};

let recoverySamples = 0;
let recoveryTotalMs = 0;

function inc(key, by = 1) {
  if (!Object.prototype.hasOwnProperty.call(counters, key)) counters[key] = 0;
  counters[key] += by;
}

function incAliveSource(source) {
  const map = {
    HB_SUCCESS: 'heartbeat_success',
    BOOT_SUCCESS: 'boot_success',
    NETWORK_RESTORED: 'network_restored_success',
    SMS_SUCCESS: 'sms_success',
    LOGIN_SUCCESS: 'login_success',
  };
  const key = map[source] || 'heartbeat_success';
  inc(key, 1);
}

function recordRecoveryMs(ms) {
  if (!Number.isFinite(ms) || ms < 0) return;
  recoverySamples += 1;
  recoveryTotalMs += ms;
}

function snapshot() {
  const avgRecoveryMs = recoverySamples
    ? Math.round(recoveryTotalMs / recoverySamples)
    : 0;
  return {
    ...counters,
    recovery_samples: recoverySamples,
    avg_recovery_ms: avgRecoveryMs,
  };
}

module.exports = {
  inc,
  incAliveSource,
  recordRecoveryMs,
  snapshot,
  counters,
};

