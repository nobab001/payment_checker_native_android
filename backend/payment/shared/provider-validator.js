/**
 * @file Registry entry validator (Phase-3A contract enforcement).
 */

const { PAYMENT_PROVIDER_TYPE } = require('../core/payment-types');

const REQUIRED_ENTRY_KEYS = [
  'id', 'displayName', 'type', 'company', 'version', 'adapter', 'aliases',
  'supports', 'callbackPath', 'sessionTimeout', 'priority', 'enabled', 'maintenance',
  'adapterVersion', 'contractVersion', 'merchantCallbackVersion',
];

function validateRegistryEntry(entry) {
  const errors = [];
  if (!entry || typeof entry !== 'object') return ['Entry must be an object'];

  for (const key of REQUIRED_ENTRY_KEYS) {
    if (entry[key] === undefined || entry[key] === null) errors.push(`Missing: ${key}`);
  }

  if (entry.type && !Object.values(PAYMENT_PROVIDER_TYPE).includes(entry.type)) {
    errors.push(`Invalid type: ${entry.type}`);
  }

  if (entry.version != null && (!Number.isInteger(entry.version) || entry.version < 1)) {
    errors.push('version must be integer >= 1');
  }

  if (!Array.isArray(entry.aliases) || !entry.aliases.includes(entry.id)) {
    errors.push('aliases must include canonical id');
  }

  if (entry.priority != null && (!Number.isInteger(entry.priority) || entry.priority < 0)) {
    errors.push('priority must be integer >= 0');
  }

  if (entry.enabled != null && typeof entry.enabled !== 'boolean') {
    errors.push('enabled must be boolean');
  }

  if (entry.maintenance != null && typeof entry.maintenance !== 'boolean') {
    errors.push('maintenance must be boolean');
  }

  return errors;
}

module.exports = {
  validateRegistryEntry,
};
