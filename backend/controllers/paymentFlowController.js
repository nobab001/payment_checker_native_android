/**
 * paymentFlowController.js — thin HTTP wrappers for PaymentEngine (Phase-3B).
 */

const PaymentEngine = require('../payment/engine/payment-engine');

async function initPayment(req, res) {
  return PaymentEngine.initMerchantPayment(req, res);
}

async function redirectPayment(req, res) {
  return PaymentEngine.redirectPayment(req, res);
}

async function officialGatewayCallback(req, res) {
  return PaymentEngine.legacyGatewayCallback(req, res);
}

async function bkashCallback(req, res) {
  return PaymentEngine.bkashCallback(req, res);
}

async function paymentStatus(req, res) {
  return PaymentEngine.paymentStatus(req, res);
}

async function completeSessionByToken(token, opts) {
  return PaymentEngine.completeSessionByToken(token, opts);
}

module.exports = {
  initPayment,
  redirectPayment,
  officialGatewayCallback,
  bkashCallback,
  paymentStatus,
  completeSessionByToken,
  OFFICIAL_PROVIDERS: require('../payment/engine/payment-flow-engine').OFFICIAL_PROVIDERS,
};
