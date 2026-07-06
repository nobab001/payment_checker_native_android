/**
 * @file Retry policy definitions for merchant callback / outbound HTTP.
 * @module payment/retry/retry-policy
 */

/** @typedef {Object} RetryPolicy
 * @property {number} maxAttempts
 * @property {number[]} delaysMs — delay before attempt 2, 3, …
 * @property {boolean} jitter
 */

/** Default: 3 attempts, exponential-ish backoff, then dead queue. */
const DEFAULT_MERCHANT_CALLBACK_POLICY = Object.freeze({
  maxAttempts: 3,
  delaysMs: [1_000, 5_000, 30_000],
  jitter: true,
});

const DEFAULT_GATEWAY_VERIFY_POLICY = Object.freeze({
  maxAttempts: 2,
  delaysMs: [2_000, 10_000],
  jitter: false,
});

/**
 * @param {number} attemptIndex — 0-based (0 = first retry delay)
 * @param {RetryPolicy} policy
 */
function delayForAttempt(attemptIndex, policy = DEFAULT_MERCHANT_CALLBACK_POLICY) {
  const base = policy.delaysMs[attemptIndex] ?? policy.delaysMs[policy.delaysMs.length - 1] ?? 30_000;
  if (!policy.jitter) return base;
  const jitter = Math.floor(Math.random() * Math.min(500, base * 0.1));
  return base + jitter;
}

module.exports = {
  DEFAULT_MERCHANT_CALLBACK_POLICY,
  DEFAULT_GATEWAY_VERIFY_POLICY,
  delayForAttempt,
};
