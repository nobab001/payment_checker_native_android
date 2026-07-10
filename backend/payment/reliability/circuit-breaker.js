/**
 * @file Circuit Breaker — per-provider API resilience.
 * @module payment/reliability/circuit-breaker
 */

const STATES = Object.freeze({
  CLOSED: 'CLOSED',
  OPEN: 'OPEN',
  HALF_OPEN: 'HALF_OPEN',
});

/** @type {Map<string, { state: string, failures: number, openedAt: number|null }>} */
const breakers = new Map();

const DEFAULT_OPTS = {
  failureThreshold: 5,
  openMs: 10 * 60 * 1000,
  halfOpenMaxAttempts: 1,
};

function getState(providerId) {
  if (!breakers.has(providerId)) {
    breakers.set(providerId, { state: STATES.CLOSED, failures: 0, openedAt: null });
  }
  return breakers.get(providerId);
}

function canExecute(providerId, opts = DEFAULT_OPTS) {
  const b = getState(providerId);
  if (b.state === STATES.CLOSED) return true;
  if (b.state === STATES.OPEN) {
    if (Date.now() - b.openedAt >= opts.openMs) {
      b.state = STATES.HALF_OPEN;
      return true;
    }
    return false;
  }
  return true;
}

function recordSuccess(providerId) {
  const b = getState(providerId);
  b.state = STATES.CLOSED;
  b.failures = 0;
  b.openedAt = null;
}

function recordFailure(providerId, opts = DEFAULT_OPTS) {
  const b = getState(providerId);
  b.failures += 1;
  if (b.failures >= opts.failureThreshold) {
    b.state = STATES.OPEN;
    b.openedAt = Date.now();
  }
}

function getSnapshot() {
  const out = {};
  for (const [id, b] of breakers.entries()) {
    out[id] = { ...b };
  }
  return out;
}

module.exports = {
  STATES,
  canExecute,
  recordSuccess,
  recordFailure,
  getSnapshot,
};
