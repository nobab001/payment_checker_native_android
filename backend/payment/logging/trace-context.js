/**
 * @file Async trace context — propagate traceId across the payment flow.
 * @module payment/logging/trace-context
 */

const { AsyncLocalStorage } = require('async_hooks');

const traceStore = new AsyncLocalStorage();

/**
 * @param {string} traceId
 * @param {() => Promise<*>|*} fn
 */
function runWithTrace(traceId, fn) {
  return traceStore.run({ traceId }, fn);
}

function getTraceId() {
  return traceStore.getStore()?.traceId || null;
}

module.exports = {
  runWithTrace,
  getTraceId,
};
