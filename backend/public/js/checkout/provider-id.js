import { PROVIDER_TYPE, PROVIDER_VARIANT } from './provider-constants.js';
import { provKey } from './utils.js';

/**
 * Stable provider IDs for UI, sort, callback mapping, analytics.
 * Examples: bkash_personal, bkash_live, sslcommerz, bank, card
 */
export function buildStableProviderId({ provider, variant, type }) {
  const base = provKey(provider) || 'provider';

  if (type === PROVIDER_TYPE.BANK) return 'bank';
  if (type === PROVIDER_TYPE.CARD) return 'card';

  if (type === PROVIDER_TYPE.LIVE) {
    if (base === 'sslcommerz' || base === 'ssl') return 'sslcommerz';
    return `${base}_live`;
  }

  const v = (variant || PROVIDER_VARIANT.PERSONAL).toUpperCase();
  if (v === PROVIDER_VARIANT.PAYMENT) return `${base}_payment`;
  if (v === PROVIDER_VARIANT.AGENT) return `${base}_agent`;
  if (v === PROVIDER_VARIANT.MERCHANT) return `${base}_merchant`;
  return `${base}_personal`;
}
