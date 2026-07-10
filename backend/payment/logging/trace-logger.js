/**
 * @file Structured payment logging with traceId propagation.
 * @module payment/logging/trace-logger
 */

const crypto = require('crypto');
const { getTraceId } = require('./trace-context');

const REDACT_KEY_PATTERNS = [
  /password/i, /secret/i, /^authorization$/i, /^raw$/i, /appsecret/i, /app_secret/i,
];

function sanitizeMeta(meta) {
  if (!meta || typeof meta !== 'object') return meta;
  const out = {};
  for (const [key, val] of Object.entries(meta)) {
    if (REDACT_KEY_PATTERNS.some((re) => re.test(key))) {
      out[key] = '[REDACTED]';
    } else if (val && typeof val === 'object' && !Array.isArray(val)) {
      out[key] = sanitizeMeta(val);
    } else {
      out[key] = val;
    }
  }
  return out;
}

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
    ...sanitizeMeta(meta),
  };
  console.log(JSON.stringify(payload));
}

module.exports = {
  createTraceId,
  logPayment,
};
