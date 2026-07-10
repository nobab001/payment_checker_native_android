/** Shared DOM / branding helpers for checkout UI (no API calls). */

import { esc, safeImgSrc } from './sanitize.js';

export { esc, escAttr, safeImgSrc, safeText } from './sanitize.js';

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

export function providerLogoHtml(branding, tid, provider, px = 28) {
  px = px || 28;
  const logoUrl = templateLogoUrl(branding, tid);
  const color = provColor(provider);
  const initial = provInitial(provider);
  const radius = Math.round(px * 0.28);
  if (hasImg(logoUrl)) {
    return `<img src="${esc(safeImgSrc(logoUrl))}" alt="" class="prov-logo-img" style="width:${px}px;height:${px}px;border-radius:${radius}px;object-fit:contain;background:#fff;flex:0 0 auto" decoding="async" loading="lazy" data-initial="${esc(initial)}" data-color="${esc(color)}" data-px="${px}" onerror="window.__checkoutLogoFail&&window.__checkoutLogoFail(this)">`;
  }
  return `<span class="prov-avatar" style="width:${px}px;height:${px}px;border-radius:${radius}px;background:${esc(color)};font-size:${Math.round(px * 0.42)}px;flex:0 0 auto">${esc(initial)}</span>`;
}

export function tabIconHtml(tab) {
  const fb = TAB_FALLBACK[tab.id] || { icon: '💳', label: tab.label };
  const emoji = tab.icon || fb.icon;
  if (hasImg(tab.iconUrl)) {
    return `<img src="${esc(safeImgSrc(tab.iconUrl))}" alt="" width="22" height="22" decoding="async" loading="eager" data-emoji="${esc(emoji)}" onerror="window.__checkoutIconFail&&window.__checkoutIconFail(this)">`;
  }
  return esc(emoji);
}
