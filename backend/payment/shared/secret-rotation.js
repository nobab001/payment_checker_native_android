/**
 * @file Callback secret rotation helpers.
 * @module payment/shared/secret-rotation
 */

/**
 * @param {Object} cfg — parsed config_json
 * @returns {string[]}
 */
function getCallbackSecrets(cfg = {}) {
  const secrets = [];
  const current = cfg.callbackSecret || cfg.currentCallbackSecret || cfg.current_secret;
  const previous = cfg.previousCallbackSecret || cfg.previous_callback_secret || cfg.previousSecret;
  if (current) secrets.push(String(current));
  if (previous && previous !== current) secrets.push(String(previous));
  return secrets;
}

module.exports = {
  getCallbackSecrets,
};
