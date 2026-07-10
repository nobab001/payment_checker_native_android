/** Provider type / variant enums — single source for model + renderers. */

export const PROVIDER_TYPE = Object.freeze({
  SIM: 'SIM',
  LIVE: 'LIVE',
  BANK: 'BANK',
  CARD: 'CARD',
});

export const PROVIDER_VARIANT = Object.freeze({
  PERSONAL: 'PERSONAL',
  AGENT: 'AGENT',
  MERCHANT: 'MERCHANT',
  PAYMENT: 'PAYMENT',
});

export function defaultMetadata(overrides = {}) {
  return {
    callbackSupported: false,
    liveRedirect: false,
    requiresOtp: false,
    requiresPin: false,
    supportsCommission: false,
    supportsTypeCallback: false,
    templateId: null,
    liveProviderKey: null,
    ...overrides,
  };
}
