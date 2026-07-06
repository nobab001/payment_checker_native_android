/**
 * @file Payment State Machine — guards invalid status transitions.
 * @module payment/state/payment-state-machine
 *
 * Prevents e.g. FAILED → SUCCESS. Terminal states accept no further transitions.
 */

const { PAYMENT_STATUS, toCanonicalStatus } = require('../core/payment-status');
const { ProviderError } = require('../shared/provider-errors');

/** @type {Record<string, string[]>} */
const ALLOWED_TRANSITIONS = Object.freeze({
  [PAYMENT_STATUS.CREATED]: [
    PAYMENT_STATUS.REDIRECTED,
    PAYMENT_STATUS.PENDING,
    PAYMENT_STATUS.SUCCESS,
    PAYMENT_STATUS.FAILED,
    PAYMENT_STATUS.EXPIRED,
    PAYMENT_STATUS.CANCELLED,
  ],
  [PAYMENT_STATUS.REDIRECTED]: [
    PAYMENT_STATUS.PENDING,
    PAYMENT_STATUS.PROCESSING,
    PAYMENT_STATUS.SUCCESS,
    PAYMENT_STATUS.FAILED,
    PAYMENT_STATUS.EXPIRED,
    PAYMENT_STATUS.CANCELLED,
  ],
  [PAYMENT_STATUS.PENDING]: [
    PAYMENT_STATUS.PROCESSING,
    PAYMENT_STATUS.SUCCESS,
    PAYMENT_STATUS.FAILED,
    PAYMENT_STATUS.EXPIRED,
    PAYMENT_STATUS.CANCELLED,
  ],
  [PAYMENT_STATUS.PROCESSING]: [
    PAYMENT_STATUS.SUCCESS,
    PAYMENT_STATUS.FAILED,
    PAYMENT_STATUS.EXPIRED,
  ],
  [PAYMENT_STATUS.SUCCESS]: [],
  [PAYMENT_STATUS.FAILED]: [],
  [PAYMENT_STATUS.EXPIRED]: [],
  [PAYMENT_STATUS.CANCELLED]: [],
  [PAYMENT_STATUS.TIMEOUT]: [],
});

const TERMINAL_STATES = new Set([
  PAYMENT_STATUS.SUCCESS,
  PAYMENT_STATUS.FAILED,
  PAYMENT_STATUS.EXPIRED,
  PAYMENT_STATUS.CANCELLED,
  PAYMENT_STATUS.TIMEOUT,
]);

/** Canonical → legacy DB column (payment_sessions.status) */
const CANONICAL_TO_LEGACY = Object.freeze({
  [PAYMENT_STATUS.CREATED]: 'created',
  [PAYMENT_STATUS.REDIRECTED]: 'redirected',
  [PAYMENT_STATUS.PENDING]: 'redirected',
  [PAYMENT_STATUS.PROCESSING]: 'redirected',
  [PAYMENT_STATUS.SUCCESS]: 'completed',
  [PAYMENT_STATUS.FAILED]: 'failed',
  [PAYMENT_STATUS.EXPIRED]: 'expired',
  [PAYMENT_STATUS.CANCELLED]: 'failed',
  [PAYMENT_STATUS.TIMEOUT]: 'expired',
});

/**
 * @param {string} fromStatus — canonical or legacy
 * @param {string} toStatus — canonical or legacy
 */
function canTransition(fromStatus, toStatus) {
  const from = toCanonicalStatus(fromStatus);
  const to = toCanonicalStatus(toStatus);
  if (from === to) return true;
  const allowed = ALLOWED_TRANSITIONS[from];
  return Array.isArray(allowed) && allowed.includes(to);
}

/**
 * @param {string} fromStatus
 * @param {string} toStatus
 */
function assertTransition(fromStatus, toStatus) {
  if (!canTransition(fromStatus, toStatus)) {
    const from = toCanonicalStatus(fromStatus);
    const to = toCanonicalStatus(toStatus);
    throw new ProviderError(
      'INVALID_STATE_TRANSITION',
      `Invalid payment state transition: ${from} → ${to}`,
      { from, to },
    );
  }
}

function toLegacyStatus(status) {
  const canon = toCanonicalStatus(status);
  return CANONICAL_TO_LEGACY[canon] || String(status).toLowerCase();
}

function isTerminal(status) {
  return TERMINAL_STATES.has(toCanonicalStatus(status));
}

module.exports = {
  ALLOWED_TRANSITIONS,
  CANONICAL_TO_LEGACY,
  canTransition,
  assertTransition,
  toLegacyStatus,
  isTerminal,
};
