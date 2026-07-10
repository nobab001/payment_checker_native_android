/**
 * @file Callback module exports (Phase-3B).
 */

module.exports = {
  PHASE: '3B',
  status: 'active',
  spec: 'payment/LIFECYCLE_SPEC.md',
  callbackEngine: require('./callback-engine'),
  merchantCallbackEngine: require('./merchant-callback-engine'),
};
