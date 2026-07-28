/** Subscription System v3 — canonical categories & durations */

const CATEGORIES = Object.freeze({
  GATEWAY: 'gateway',
  PERSONAL_BUSINESS: 'personal_business',
  PERSONAL: 'personal',
});

const CATEGORY_TAB_ORDER = [
  CATEGORIES.GATEWAY,
  CATEGORIES.PERSONAL_BUSINESS,
  CATEGORIES.PERSONAL,
];

/** UI segment → duration key */
const DURATION_KEYS = Object.freeze({
  monthly: '1m',
  annually: '6m',
  yearly: '12m',
});

const DURATION_DAYS = Object.freeze({
  '1m': 30,
  '6m': 180,
  '12m': 365,
});

const DURATION_LABELS = Object.freeze({
  '1m': 'Monthly',
  '6m': 'Annually',
  '12m': 'Yearly',
});

const ADDON_KEYS = Object.freeze({
  SMART_POPUP: 'smart_popup',
  CUSTOM_SENDER: 'custom_sender',
  GATEWAY_PERMISSION: 'gateway_permission',
});

/** Category → allowed optional add-on keys */
const CATEGORY_ADDONS = Object.freeze({
  [CATEGORIES.GATEWAY]: [ADDON_KEYS.SMART_POPUP, ADDON_KEYS.CUSTOM_SENDER],
  [CATEGORIES.PERSONAL_BUSINESS]: [ADDON_KEYS.GATEWAY_PERMISSION, ADDON_KEYS.CUSTOM_SENDER],
  [CATEGORIES.PERSONAL]: [ADDON_KEYS.GATEWAY_PERMISSION, ADDON_KEYS.SMART_POPUP],
});

const UNLIMITED_WEBSITE_SENTINEL = 9999;

const SUBSCRIPTION_VERSION = 'v3';

module.exports = {
  CATEGORIES,
  CATEGORY_TAB_ORDER,
  DURATION_KEYS,
  DURATION_DAYS,
  DURATION_LABELS,
  ADDON_KEYS,
  CATEGORY_ADDONS,
  UNLIMITED_WEBSITE_SENTINEL,
  SUBSCRIPTION_VERSION,
};
