/**
 * @file Shared provider utilities.
 * @module payment/shared/provider-utils
 */

const crypto = require('crypto');

function genPaymentToken() {
  return `ps_${crypto.randomBytes(24).toString('hex')}`;
}

function applyUrlTemplate(template, vars) {
  if (!template) return '';
  return template.replace(/\{(\w+)\}/g, (_, key) => {
    const val = vars[key];
    return val != null ? encodeURIComponent(String(val)) : '';
  });
}

module.exports = {
  genPaymentToken,
  applyUrlTemplate,
};
