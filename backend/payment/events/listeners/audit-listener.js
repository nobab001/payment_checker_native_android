/**
 * @file Audit listener — business events → immutable audit_logs.
 */

const audit = require('../../../services/auditLog');
const { onPaymentEvent, PAYMENT_EVENTS } = require('../event-bus');

const AUDIT_MAP = {
  [PAYMENT_EVENTS.PAYMENT_CREATED]: 'payment.created',
  [PAYMENT_EVENTS.PAYMENT_COMPLETED]: 'payment.completed',
  [PAYMENT_EVENTS.PAYMENT_FAILED]: 'payment.failed',
  [PAYMENT_EVENTS.PAYMENT_EXPIRED]: 'payment.expired',
  [PAYMENT_EVENTS.MERCHANT_CALLBACK_SENT]: 'payment.merchant_callback.sent',
  [PAYMENT_EVENTS.GATEWAY_CALLBACK_RECEIVED]: 'payment.gateway_callback.received',
};

let registered = false;

function registerAuditListeners() {
  if (registered) return;
  registered = true;

  for (const [event, eventType] of Object.entries(AUDIT_MAP)) {
    onPaymentEvent(event, (payload) => {
      audit.log({
        eventType,
        entityType: 'payment_session',
        entityId: payload.sessionToken,
        websiteId: payload.websiteId,
        userId: payload.userId,
        status: payload.status ? String(payload.status).toLowerCase() : null,
        detail: { traceId: payload.traceId, event },
      });
    });
  }
}

module.exports = { registerAuditListeners };
