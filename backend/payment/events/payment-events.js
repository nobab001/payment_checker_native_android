/**
 * @file Frozen payment lifecycle event names (v1.0).
 * @module payment/events/payment-events
 *
 * DO NOT RENAME — see EVENTS_FROZEN.md
 */

/** @readonly */
const PAYMENT_EVENTS = Object.freeze({
  PAYMENT_CREATED: 'PaymentCreated',
  PAYMENT_REDIRECTED: 'PaymentRedirected',
  GATEWAY_CALLBACK_RECEIVED: 'GatewayCallbackReceived',
  PAYMENT_VERIFIED: 'PaymentVerified',
  MERCHANT_CALLBACK_SENT: 'MerchantCallbackSent',
  PAYMENT_COMPLETED: 'PaymentCompleted',
  PAYMENT_FAILED: 'PaymentFailed',
  PAYMENT_EXPIRED: 'PaymentExpired',
  PAYMENT_CANCELLED: 'PaymentCancelled',
});

module.exports = { PAYMENT_EVENTS };
