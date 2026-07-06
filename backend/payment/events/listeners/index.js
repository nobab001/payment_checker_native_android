/**
 * @file Default event listeners (audit log). Extend for SMS / email / analytics.
 */

const { onPaymentEvent, PAYMENT_EVENTS } = require('../event-bus');

let registered = false;

function registerDefaultListeners() {
  if (registered) return;
  registered = true;

  const lifecycleEvents = Object.values(PAYMENT_EVENTS);
  for (const event of lifecycleEvents) {
    onPaymentEvent(event, (payload) => {
      // Structured audit trail — Phase-3B can persist to payment_audit table.
      if (!payload.traceId) return;
    });
  }
}

module.exports = { registerDefaultListeners };
