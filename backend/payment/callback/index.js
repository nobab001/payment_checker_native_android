/**
 * @file Callback Engine — Phase-3B placeholder.
 * @module payment/callback/index
 *
 * Phase-3B wires:
 *   - idempotency/idempotency-manager (check/lock/complete)
 *   - retry/retry-engine (merchant callback delivery)
 *   - core/merchant-callback-v1 (frozen payload)
 *   - events/event-bus (GatewayCallbackReceived, PaymentVerified)
 *
 * Phase-3A: NOT implemented. Adapters expose callback()/normalize() stubs only.
 */

module.exports = {
  PHASE: '3B',
  status: 'stub',
  spec: 'payment/LIFECYCLE_SPEC.md',
};
