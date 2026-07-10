/**
 * @file Payment monitoring — latency, health, ping aggregation.
 * @module payment/monitoring/payment-monitor
 */

const { listRegistryEntries } = require('../registry/provider-registry');
const { getProvider } = require('../registry/provider-factory');
const { getDeadQueue } = require('../retry/retry-engine');
const { getSnapshot: getCircuitSnapshot } = require('../reliability/circuit-breaker');
const { logPayment } = require('../logging/trace-logger');

const STAGES = Object.freeze([
  'gateway',
  'callback',
  'redirect',
  'merchant_callback',
  'engine',
]);

/** @type {Record<string, number[]>} */
const latencySamples = Object.fromEntries(STAGES.map((s) => [s, []]));

const MAX_SAMPLES = 200;

/**
 * @param {string} stage
 * @param {number} durationMs
 * @param {Object} [meta]
 */
function recordLatency(stage, durationMs, meta = {}) {
  if (!STAGES.includes(stage)) return;
  const bucket = latencySamples[stage];
  bucket.push(durationMs);
  if (bucket.length > MAX_SAMPLES) bucket.shift();

  if (meta.traceId) {
    logPayment(meta.traceId, 'Monitor', 'latency', { stage, durationMs, ...meta });
  }
}

function percentile(arr, p) {
  if (!arr.length) return null;
  const sorted = [...arr].sort((a, b) => a - b);
  const idx = Math.ceil((p / 100) * sorted.length) - 1;
  return sorted[Math.max(0, idx)];
}

function snapshotLatencies() {
  const out = {};
  for (const stage of STAGES) {
    const samples = latencySamples[stage];
    out[stage] = {
      count: samples.length,
      p50: percentile(samples, 50),
      p95: percentile(samples, 95),
      p99: percentile(samples, 99),
    };
  }
  return out;
}

/**
 * Run health() + ping() on all enabled registry providers.
 */
async function runProviderProbes() {
  const results = [];
  for (const entry of listRegistryEntries()) {
    if (entry.enabled === false) continue;
    const started = Date.now();
    try {
      const adapter = getProvider(entry.id);
      const health = await adapter.health();
      const ping = await adapter.ping();
      recordLatency('gateway', Date.now() - started, { providerId: entry.id });
      results.push({ providerId: entry.id, health, ping });
    } catch (err) {
      results.push({ providerId: entry.id, error: err.message });
    }
  }
  return results;
}

async function getMonitoringSnapshot() {
  return {
    latencies: snapshotLatencies(),
    providers: await runProviderProbes(),
    at: new Date().toISOString(),
  };
}

async function getDashboardMetrics() {
  const providers = await runProviderProbes();
  const { getPendingCount, getDeadCount } = require('../reliability/merchant-callback-outbox');
  const [outboxPending, outboxDead] = await Promise.all([
    getPendingCount(),
    getDeadCount(),
  ]);
  return {
    at: new Date().toISOString(),
    latencies: snapshotLatencies(),
    providers,
    circuitBreakers: getCircuitSnapshot(),
    retryDeadQueue: getDeadQueue().length,
    outbox: { pending: outboxPending, dead: outboxDead },
  };
}

module.exports = {
  STAGES,
  recordLatency,
  snapshotLatencies,
  runProviderProbes,
  getMonitoringSnapshot,
  getDashboardMetrics,
};
