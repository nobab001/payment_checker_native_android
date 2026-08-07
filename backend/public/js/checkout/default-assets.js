/** Built-in checkout tab / provider images (always available; admin upload overrides). */

export const DEFAULT_TAB_ICONS = {
  send_money: '/assets/checkout/tabs/send_money.png',
  cash_out: '/assets/checkout/tabs/cash_out.png',
  payment: '/assets/checkout/tabs/payment.png',
  bank: '/assets/checkout/tabs/bank.png',
  card: '/assets/checkout/tabs/card.png',
};

export const DEFAULT_PROVIDER_LOGOS = {
  bkash: '/assets/checkout/providers/bkash.png?v=4',
  nagad: '/assets/checkout/providers/nagad.png?v=2',
  rocket: '/assets/checkout/providers/rocket.png',
  upay: '/assets/checkout/providers/upay.png',
};

/** Map sender / provider string → built-in logo path. */
export function defaultProviderLogo(providerOrSender) {
  const k = String(providerOrSender || '').toLowerCase().replace(/[^a-z0-9]/g, '');
  if (!k) return '';
  if (k.includes('bkash') || k === 'bka') return DEFAULT_PROVIDER_LOGOS.bkash;
  if (k.includes('nagad')) return DEFAULT_PROVIDER_LOGOS.nagad;
  if (k.includes('rocket') || k === '16216') return DEFAULT_PROVIDER_LOGOS.rocket;
  if (k.includes('upay')) return DEFAULT_PROVIDER_LOGOS.upay;
  return DEFAULT_PROVIDER_LOGOS[k] || '';
}

export function defaultTabIcon(tabId) {
  return DEFAULT_TAB_ICONS[tabId] || '';
}
