/**
 * @file Retry Engine — outbound task retries with dead-letter queue.
 * @module payment/retry/retry-engine
 *
 * Phase-3B: merchant callback delivery uses this. In-memory DLQ for now;
 * swap to BullMQ worker when wiring production webhooks.
 */

const { delayForAttempt, DEFAULT_MERCHANT_CALLBACK_POLICY } = require('./retry-policy');
const { logPayment } = require('../logging/trace-logger');

/** @type {Array<Object>} */
const deadQueue = [];

/**
 * @typedef {Object} RetryTask
 * @property {string} id
 * @property {string} [traceId]
 * @property {string} type — merchant_callback | gateway_verify | …
 * @property {() => Promise<{ ok: boolean, retryable?: boolean }>} execute
 * @property {import('./retry-policy').RetryPolicy} [policy]
 * @property {Object} [meta]
 */

/**
 * Schedule a retryable task. Resolves when succeeded or moved to dead queue.
 * @param {RetryTask} task
 */
async function runWithRetry(task) {
  const policy = task.policy || DEFAULT_MERCHANT_CALLBACK_POLICY;
  const { traceId, id, type } = task;

  for (let attempt = 0; attempt < policy.maxAttempts; attempt++) {
    try {
      logPayment(traceId, 'Retry', 'attempt', { taskId: id, type, attempt: attempt + 1 });
      const outcome = await task.execute();
      if (outcome?.ok) {
        logPayment(traceId, 'Retry', 'success', { taskId: id, type, attempt: attempt + 1 });
        return { ok: true, attempts: attempt + 1 };
      }
      if (outcome?.retryable === false) {
        deadQueue.push({ ...task, reason: 'non_retryable', at: new Date().toISOString() });
        return { ok: false, dead: true, reason: 'non_retryable' };
      }
    } catch (err) {
      logPayment(traceId, 'Retry', 'error', { taskId: id, type, attempt: attempt + 1, error: err.message });
    }

    if (attempt < policy.maxAttempts - 1) {
      const wait = delayForAttempt(attempt, policy);
      await new Promise((r) => setTimeout(r, wait));
    }
  }

  deadQueue.push({ ...task, reason: 'max_attempts', at: new Date().toISOString() });
  logPayment(traceId, 'Retry', 'dead_queue', { taskId: id, type });
  return { ok: false, dead: true, reason: 'max_attempts' };
}

function getDeadQueue() {
  return [...deadQueue];
}

function clearDeadQueue() {
  deadQueue.length = 0;
}

module.exports = {
  runWithRetry,
  getDeadQueue,
  clearDeadQueue,
};
