const prisma = require('../../db/prisma');

const DEFAULTS = Object.freeze({
  subscription_version: 'v3',
  subscription_v3_enabled: 'true',
  trial_days: '7',
  quote_validity_min: '15',
  grace_period_min: '5',
  subscription_maintenance: '0',
  billing_tab_order_v3: JSON.stringify(['gateway', 'personal_business', 'personal']),
});

async function getConfig(key, fallback = null) {
  const row = await prisma.global_config.findUnique({ where: { config_key: key } });
  if (!row || row.config_value == null || String(row.config_value).trim() === '') {
    return fallback !== null ? fallback : DEFAULTS[key];
  }
  return String(row.config_value).trim();
}

async function setConfig(key, value) {
  const v = String(value);
  await prisma.global_config.upsert({
    where: { config_key: key },
    update: { config_value: v, updated_at: new Date() },
    create: { config_key: key, config_value: v },
  });
  return v;
}

async function isV3Enabled() {
  const v = await getConfig('subscription_v3_enabled', 'true');
  return v === 'true' || v === '1';
}

async function isMaintenanceOn() {
  const v = await getConfig('subscription_maintenance', '0');
  return v === '1' || v === 'true';
}

async function getGracePeriodMinutes() {
  const allowed = [0, 5, 10, 30];
  const n = parseInt(await getConfig('grace_period_min', '5'), 10);
  return allowed.includes(n) ? n : 5;
}

async function getQuoteValidityMinutes() {
  const n = parseInt(await getConfig('quote_validity_min', '15'), 10);
  return Number.isFinite(n) && n > 0 ? n : 15;
}

async function getTrialDays() {
  const n = parseInt(await getConfig('trial_days', '7'), 10);
  return Number.isFinite(n) && n > 0 ? n : 7;
}

async function getBillingSettings() {
  return {
    subscription_version: await getConfig('subscription_version', 'v3'),
    subscription_v3_enabled: await isV3Enabled(),
    trial_days: await getTrialDays(),
    quote_validity_min: await getQuoteValidityMinutes(),
    grace_period_min: await getGracePeriodMinutes(),
    subscription_maintenance: await isMaintenanceOn(),
    billing_tab_order: JSON.parse(await getConfig('billing_tab_order_v3', DEFAULTS.billing_tab_order_v3)),
  };
}

module.exports = {
  DEFAULTS,
  getConfig,
  setConfig,
  isV3Enabled,
  isMaintenanceOn,
  getGracePeriodMinutes,
  getQuoteValidityMinutes,
  getTrialDays,
  getBillingSettings,
};
