/**
 * @file Payment Event Bus — decoupled lifecycle notifications.
 * @module payment/events/event-bus
 *
 * Analytics, audit, SMS, email, retry — attach via onPaymentEvent().
 * No behavior change to API responses; events are side-channel only.
 */

const { EventEmitter } = require('events');
const { PAYMENT_EVENTS } = require('./payment-events');
const { logPayment } = require('../logging/trace-logger');

const bus = new EventEmitter();
bus.setMaxListeners(50);

/**
 * @param {string} event — PAYMENT_EVENTS value
 * @param {Object} payload — must include traceId when available
 */
function emitPaymentEvent(event, payload = {}) {
  const enriched = {
    ts: new Date().toISOString(),
    event,
    ...payload,
  };

  if (payload.traceId) {
    logPayment(payload.traceId, 'Event', event, {
      sessionToken: payload.sessionToken,
      providerId: payload.providerId,
      status: payload.status,
    });
  }

  bus.emit(event, enriched);
  bus.emit('*', enriched);
}

/**
 * @param {string} event
 * @param {(payload: Object) => void|Promise<void>} handler
 */
function onPaymentEvent(event, handler) {
  bus.on(event, handler);
}

function oncePaymentEvent(event, handler) {
  bus.once(event, handler);
}

function offPaymentEvent(event, handler) {
  bus.off(event, handler);
}

module.exports = {
  PAYMENT_EVENTS,
  emitPaymentEvent,
  onPaymentEvent,
  oncePaymentEvent,
  offPaymentEvent,
  _bus: bus,
};
