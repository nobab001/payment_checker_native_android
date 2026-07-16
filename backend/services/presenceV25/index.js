/**
 * Device Communication v2.5 — Phase 1–4 exports.
 */
const policyLoader = require('./policyLoader');
const presenceStore = require('./presenceStore');
const presenceWorker = require('./presenceWorker');
const engine = require('./engine');
const shadowCompare = require('./shadowCompare');
const KEYS = require('./keys');

module.exports = {
  KEYS,
  policyLoader,
  presenceStore,
  presenceWorker,
  markDeviceAlive: engine.markDeviceAlive,
  markDeviceOffline: engine.markDeviceOffline,
  getPolicy: policyLoader.getPolicy,
  isPresenceV2Enabled: policyLoader.isPresenceV2Enabled,
  loadGlobalV2Enabled: policyLoader.loadGlobalV2Enabled,
  refreshAllPolicies: policyLoader.refreshAllPolicies,
  runSweepTick: presenceWorker.runSweepTick,
  getPresenceMetrics: engine.getMetrics,
  runShadowCompare: shadowCompare.runShadowCompare,
  isShadowModeEnabled: shadowCompare.isShadowModeEnabled,
  DRY_RUN: engine.DRY_RUN,
};
