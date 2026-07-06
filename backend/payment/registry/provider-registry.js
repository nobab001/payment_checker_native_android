/**
 * @file Provider Registry — single source of truth for all payment providers.
 * @module payment/registry/provider-registry
 *
 * UI, Admin, API, and Payment Engine MUST resolve providers through this registry.
 * Add a gateway = add registry entry + adapter file. No if-else elsewhere.
 */

const { PAYMENT_PROVIDER_TYPE } = require('../core/payment-types');
const { buildSupports } = require('../core/provider-flags');

/**
 * @typedef {Object} ProviderRegistryEntry
 * @property {string} id — canonical stable id (bkash_live)
 * @property {string} displayName
 * @property {'SIM'|'LIVE'|'BANK'|'CARD'} type
 * @property {string} company
 * @property {number} version — adapter/API capability version
 * @property {string} adapter — module name under providers/
 * @property {string[]} aliases — legacy + frontend ids
 * @property {import('../core/provider-flags').ProviderSupports} supports
 * @property {string} callbackPath — PayChek inbound callback route (Phase-3B)
 * @property {number} sessionTimeout — seconds
 * @property {string} [dbProviderKey] — legacy website_official_gateways.provider column
 * @property {number} priority — admin sort / failover order
 * @property {boolean} enabled — registry gate (DB is_active still required)
 * @property {boolean} maintenance — temporary disable without removing entry
 * @property {string} adapterVersion — PayChek adapter semver (FROZEN contract)
 * @property {string} contractVersion — adapter interface version
 * @property {string|null} providerApiVersion — upstream gateway API version
 * @property {string} merchantCallbackVersion — merchant webhook schema version
 */

const VERSION_DEFAULTS = Object.freeze({
  adapterVersion: '1.0',
  contractVersion: '1.0',
  merchantCallbackVersion: '1.0',
});

/** @type {Record<string, ProviderRegistryEntry>} */
const PROVIDER_REGISTRY = Object.freeze({
  bkash_live: {
    id: 'bkash_live',
    displayName: 'bKash Merchant',
    type: PAYMENT_PROVIDER_TYPE.LIVE,
    company: 'bKash',
    version: 1,
    adapter: 'bkash-live',
    aliases: ['bkash_live', 'bkash_merchant', 'bkash'],
    supports: buildSupports({ otp: true, pin: true, commission: true, typeCallback: true, webhook: true }),
    callbackPath: '/payment/callback/bkash',
    sessionTimeout: 1800,
    dbProviderKey: 'bkash_merchant',
    priority: 100,
    enabled: true,
    maintenance: false,
    ...VERSION_DEFAULTS,
    providerApiVersion: '2.1',
  },
  nagad_live: {
    id: 'nagad_live',
    displayName: 'Nagad Merchant',
    type: PAYMENT_PROVIDER_TYPE.LIVE,
    company: 'Nagad',
    version: 1,
    adapter: 'nagad-live',
    aliases: ['nagad_live', 'nagad_merchant', 'nagad'],
    supports: buildSupports({ otp: true, pin: true, commission: true, typeCallback: true, webhook: true }),
    callbackPath: '/payment/callback/nagad',
    sessionTimeout: 1800,
    dbProviderKey: 'nagad_merchant',
    priority: 100,
    enabled: true,
    maintenance: false,
    ...VERSION_DEFAULTS,
    providerApiVersion: null,
  },
  rocket_live: {
    id: 'rocket_live',
    displayName: 'Rocket Merchant',
    type: PAYMENT_PROVIDER_TYPE.LIVE,
    company: 'Rocket',
    version: 1,
    adapter: 'rocket-live',
    aliases: ['rocket_live', 'rocket_merchant', 'rocket'],
    supports: buildSupports({ otp: true, pin: true, commission: true, typeCallback: true }),
    callbackPath: '/payment/callback/rocket',
    sessionTimeout: 1800,
    dbProviderKey: 'rocket_merchant',
    priority: 100,
    enabled: true,
    maintenance: false,
    ...VERSION_DEFAULTS,
    providerApiVersion: null,
  },
  sslcommerz: {
    id: 'sslcommerz',
    displayName: 'SSLCommerz',
    type: PAYMENT_PROVIDER_TYPE.LIVE,
    company: 'SSLCommerz',
    version: 1,
    adapter: 'sslcommerz',
    aliases: ['sslcommerz', 'ssl', 'ssl_commerz'],
    supports: buildSupports({ webhook: true, refund: false, capture: true }),
    callbackPath: '/payment/callback/sslcommerz',
    sessionTimeout: 1800,
    dbProviderKey: 'sslcommerz',
    priority: 100,
    enabled: true,
    maintenance: false,
    ...VERSION_DEFAULTS,
    providerApiVersion: null,
  },
  surjopay: {
    id: 'surjopay',
    displayName: 'SurjoPay',
    type: PAYMENT_PROVIDER_TYPE.LIVE,
    company: 'SurjoPay',
    version: 1,
    adapter: 'surjopay',
    aliases: ['surjopay', 'surjo_pay'],
    supports: buildSupports({ webhook: true }),
    callbackPath: '/payment/callback/surjopay',
    sessionTimeout: 1800,
    dbProviderKey: 'surjopay',
    priority: 100,
    enabled: true,
    maintenance: false,
    ...VERSION_DEFAULTS,
    providerApiVersion: null,
  },
  portwallet: {
    id: 'portwallet',
    displayName: 'PortWallet',
    type: PAYMENT_PROVIDER_TYPE.LIVE,
    company: 'PortWallet',
    version: 1,
    adapter: 'portwallet',
    aliases: ['portwallet', 'port_wallet'],
    supports: buildSupports({ webhook: true }),
    callbackPath: '/payment/callback/portwallet',
    sessionTimeout: 1800,
    dbProviderKey: 'portwallet',
    priority: 100,
    enabled: true,
    maintenance: false,
    ...VERSION_DEFAULTS,
    providerApiVersion: null,
  },
  bank: {
    id: 'bank',
    displayName: 'Bank Transfer',
    type: PAYMENT_PROVIDER_TYPE.BANK,
    company: 'Bank',
    version: 1,
    adapter: 'bank',
    aliases: ['bank', 'bank_transfer'],
    supports: buildSupports({ redirect: true, callback: true }),
    callbackPath: '/payment/callback/bank',
    sessionTimeout: 3600,
    dbProviderKey: 'bank',
    priority: 100,
    enabled: true,
    maintenance: false,
    ...VERSION_DEFAULTS,
    providerApiVersion: null,
  },
  card: {
    id: 'card',
    displayName: 'Card Payment',
    type: PAYMENT_PROVIDER_TYPE.CARD,
    company: 'Card',
    version: 1,
    adapter: 'card',
    aliases: ['card', 'card_payment'],
    supports: buildSupports({ redirect: true, callback: true, capture: true }),
    callbackPath: '/payment/callback/card',
    sessionTimeout: 1800,
    dbProviderKey: 'card',
    priority: 100,
    enabled: true,
    maintenance: false,
    ...VERSION_DEFAULTS,
    providerApiVersion: null,
  },
});

function getRegistryEntry(canonicalId) {
  return PROVIDER_REGISTRY[canonicalId] || null;
}

function listRegistryEntries() {
  return Object.values(PROVIDER_REGISTRY);
}

function listRedirectableProviders() {
  return listRegistryEntries().filter((e) => e.supports.redirect);
}

module.exports = {
  PROVIDER_REGISTRY,
  getRegistryEntry,
  listRegistryEntries,
  listRedirectableProviders,
};
