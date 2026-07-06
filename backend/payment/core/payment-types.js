/**
 * @file Canonical payment provider types — shared across UI, API, Admin, Engine.
 * @module payment/core/payment-types
 */

/** @readonly */
const PAYMENT_PROVIDER_TYPE = Object.freeze({
  SIM: 'SIM',
  LIVE: 'LIVE',
  BANK: 'BANK',
  CARD: 'CARD',
});

/** @readonly */
const PAYMENT_CHANNEL = Object.freeze({
  PAYCHECK: 'paycheck',
  OFFICIAL: 'official',
});

/** @readonly */
const PAYMENT_CURRENCY = Object.freeze({
  BDT: 'BDT',
});

module.exports = {
  PAYMENT_PROVIDER_TYPE,
  PAYMENT_CHANNEL,
  PAYMENT_CURRENCY,
};
