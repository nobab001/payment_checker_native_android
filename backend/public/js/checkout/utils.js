/** Shared DOM / branding helpers for checkout UI (no API calls). */

import { esc, safeImgSrc } from './sanitize.js';
import {
  DEFAULT_TAB_ICONS,
  defaultProviderLogo,
  defaultTabIcon,
} from './default-assets.js';

export { esc, escAttr, safeImgSrc, safeText } from './sanitize.js';
export {
  DEFAULT_TAB_ICONS,
  DEFAULT_PROVIDER_LOGOS,
  defaultProviderLogo,
  defaultTabIcon,
} from './default-assets.js';

/** @deprecated emoji kept only as last-resort label metadata — UI uses images */
export const TAB_FALLBACK = {
  send_money: { icon: '💸', label: 'Send Money' },
  cash_out: { icon: '💵', label: 'Cash Out' },
  payment: { icon: '📱', label: 'Payment' },
  bank: { icon: '🏦', label: 'Bank' },
  card: { icon: '💳', label: 'Card' },
};

export const COLORS = {
  bkash: '#E2136E',
  nagad: '#EF4123',
  rocket: '#8C3494',
  upay: '#00B99B',
};

export function provKey(p) {
  return (p || '').toLowerCase().replace(/[^a-z0-9]/g, '');
}

export function provColor(p) {
  return COLORS[provKey(p)] || COLORS[(p || '').toLowerCase()] || '#94a3b8';
}

export function provInitial(p) {
  return (p || '?').charAt(0).toUpperCase();
}

export function hasImg(u) {
  return typeof u === 'string' && safeImgSrc(u) !== '';
}

/** @deprecated use safeImgSrc */
export function imgSrc(u) {
  return safeImgSrc(u);
}

export function resolveDesign(theme) {
  const t = (theme || '').toLowerCase();
  if (t === 'design-4' || t === 'design-5') return 'group';
  if (t === 'design-2') return 'card';
  if (t === 'design-3') return 'group';
  if (t === 'design-1') return 'list';
  return 'list';
}

export function designFromApi(apiDesign) {
  return resolveDesign(apiDesign || 'design-1');
}

export function provBrandByTemplate(branding, tid) {
  if (tid === undefined || tid === null || tid === '') return null;
  return branding['t' + tid] || null;
}

export function templateLogoUrl(branding, tid) {
  const b = provBrandByTemplate(branding, tid);
  return (b && b.logoUrl) ? b.logoUrl : '';
}

function imgWithFallback(src, fallbackSrc, attrs) {
  const primary = safeImgSrc(src) || safeImgSrc(fallbackSrc);
  const fb = safeImgSrc(fallbackSrc);
  if (!primary) return '';
  const fbAttr = fb && fb !== primary
    ? ` data-fallback-src="${esc(fb)}"`
    : (fb ? ` data-fallback-src="${esc(fb)}"` : '');
  return `<img src="${esc(primary)}"${fbAttr} ${attrs}>`;
}

export function providerLogoHtml(branding, tid, provider, px = 28) {
  px = px || 28;
  const logoUrl = templateLogoUrl(branding, tid);
  const fallback = defaultProviderLogo(provider);
  const radius = Math.round(px * 0.28);
  const attrs = `alt="" class="prov-logo-img" style="width:${px}px;height:${px}px;border-radius:${radius}px;object-fit:contain;background:#fff;flex:0 0 auto" decoding="async" loading="lazy" data-px="${px}" onerror="window.__checkoutLogoFail&&window.__checkoutLogoFail(this)"`;
  const html = imgWithFallback(logoUrl, fallback, attrs);
  if (html) return html;
  // Absolute last resort — still an image path if known, else empty box (no letter avatar)
  if (fallback) {
    return `<img src="${esc(safeImgSrc(fallback))}" alt="" class="prov-logo-img" style="width:${px}px;height:${px}px;border-radius:${radius}px;object-fit:contain;background:#fff;flex:0 0 auto" decoding="async" loading="lazy">`;
  }
  return `<span class="prov-avatar" style="width:${px}px;height:${px}px;border-radius:${radius}px;background:#e2e8f0;flex:0 0 auto"></span>`;
}

export function tabIconHtml(tab) {
  const tabId = tab.id || tab.key || '';
  const fallback = defaultTabIcon(tabId) || DEFAULT_TAB_ICONS.send_money;
  const attrs = `alt="" width="22" height="22" decoding="async" loading="eager" data-tab="${esc(tabId)}" onerror="window.__checkoutIconFail&&window.__checkoutIconFail(this)"`;
  const html = imgWithFallback(tab.iconUrl, fallback, attrs);
  if (html) return html;
  return `<img src="${esc(safeImgSrc(fallback))}" alt="" width="22" height="22" decoding="async" loading="eager">`;
}
