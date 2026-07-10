/**
 * Payment module public API.
 * Checkout UI is FROZEN — only backend controllers should import from here.
 */
const { registerDefaultListeners } = require('./events/listeners');
const { registerAuditListeners } = require('./events/listeners/audit-listener');

registerDefaultListeners();
registerAuditListeners();

module.exports = {
  core: {
    types: require('./core/payment-types'),
    status: require('./core/payment-status'),
    flags: require('./core/provider-flags'),
    callbackSchema: require('./core/callback-schema'),
    merchantCallbackV1: require('./core/merchant-callback-v1'),
    paymentContextV1: require('./core/payment-context-v1'),
    providerVersionContract: require('./core/provider-version-contract'),
  },
  errors: {
    codes: require('./errors/error-codes'),
    registry: require('./errors/error-registry'),
  },
  registry: {
    ...require('./registry/provider-registry'),
    alias: require('./registry/provider-alias'),
    factory: require('./registry/provider-factory'),
    cache: require('./registry/provider-cache'),
  },
  engine: require('./engine/payment-engine'),
  flow: require('./engine/payment-flow-engine'),
  context: require('./engine/payment-context'),
  logging: {
    ...require('./logging/trace-logger'),
    trace: require('./logging/trace-context'),
  },
  logger: require('./logging/trace-logger'),
  session: require('./session/payment-session'),
  redirect: require('./redirect/redirect-service'),
  events: require('./events/event-bus'),
  state: require('./state/payment-state-machine'),
  idempotency: require('./idempotency/idempotency-manager'),
  retry: {
    policy: require('./retry/retry-policy'),
    engine: require('./retry/retry-engine'),
  },
  monitoring: require('./monitoring/payment-monitor'),
  callback: require('./callback/index'),
  commission: require('./commission/index'),
};
