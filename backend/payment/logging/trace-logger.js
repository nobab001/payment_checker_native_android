/**
 * @file Structured payment logging with traceId propagation.
 * @module payment/logging/trace-logger
 */

const crypto = require('crypto');
const { getTraceId } = require('./trace-context');

function createTraceId() {
  return `ptr_${crypto.randomBytes(8).toString('hex')}`;
}

/**
 * @param {string} traceId
 * @param {string} stage — PaymentEngine | Provider | Session | Redirect | Event | Retry | Monitor
 * @param {string} message
 * @param {Object} [meta]
 */
function logPayment(traceId, stage, message, meta = {}) {
  const effectiveTrace = traceId || getTraceId();
  const payload = {
    ts: new Date().toISOString(),
    traceId: effectiveTrace,
    stage,
    message,
    ...meta,
  };
  console.log(JSON.stringify(payload));
}

module.exports = {
  createTraceId,
  logPayment,
};
