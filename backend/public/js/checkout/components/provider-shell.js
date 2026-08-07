import { esc, providerLogoHtml, provColor, provInitial, hasImg, safeImgSrc, defaultProviderLogo } from '../utils.js';
import { renderInstruction } from './instruction.js';
import { PROVIDER_TYPE } from '../provider-constants.js';

function templateId(provider) {
  return provider.metadata?.templateId ?? null;
}

function liveKey(provider) {
  return provider.metadata?.liveProviderKey || provider.provider || '';
}

function merchantIdAttr(provider) {
  const mid = provider.metadata?.merchantAccountId;
  return mid != null && mid !== '' ? ` data-merchant-id="${esc(String(mid))}"` : '';
}

/**
 * One-line muted incentive badge (follows --muted / theme).
 */
export function incentiveBadgeHtml(provider, { compact = false } = {}) {
  const inc = provider.incentive;
  if (!inc || !inc.kind) return '';
  const base = Number(inc.amount) || 0;
  const delta = Number(inc.delta) || 0;
  const pay = Number(inc.payAmount) || 0;
  if (!base || !delta) return '';

  const fmt = (n) => {
    const x = Math.round(Number(n) * 100) / 100;
    return Number.isInteger(x) ? String(x) : String(x);
  };

  const label = inc.kind === 'commission' ? 'কমিশন' : 'চার্জ';
  const op = inc.kind === 'commission' ? '-' : '+';
  let text;
  if (inc.purpose === 'add_balance') {
    const credit = Number(inc.walletCredit) || 0;
    if (!credit) return '';
    text = `পাঠান ৳${fmt(pay)} › ওয়ালেট ৳${fmt(credit)}`;
  } else {
    if (!pay) return '';
    text = `${label} ৳${fmt(delta)} › ${fmt(base)}${op}${fmt(delta)}=${fmt(pay)}`;
  }

  const fs = compact ? '9px' : '10px';
  const style = [
    'display:inline-block',
    `font-size:${fs}`,
    'font-weight:600',
    'line-height:1.2',
    'margin-left:6px',
    'white-space:nowrap',
    'color:var(--muted)',
    'opacity:0.92',
  ].join(';');

  return `<span class="incentive-badge" style="${style}">${esc(text)}</span>`;
}

function payHintAttr(provider) {
  const hint = provider.incentive?.payHint || '';
  return hint ? ` data-pay-hint="${esc(hint)}"` : '';
}

/** Provider header: compact logo + display name. */
export function renderProviderHeader(provider, branding, { logoPx = 28, className = 'provider-card-head' } = {}) {
  return `<div class="${className}">
    ${providerLogoHtml(branding, templateId(provider), provider.provider, logoPx)}
    <div class="provider-name">${esc(provider.displayName)}${incentiveBadgeHtml(provider)}</div>
  </div>`;
}

/** Soft brand wash (~7%) — solid fallback + light diagonal gradient. */
function tintFromProvider(provider, alpha = 0.07) {
  const hex = String(provColor(provider.provider) || '#94a3b8').replace('#', '');
  if (hex.length !== 6) {
    return `linear-gradient(135deg, rgba(148,163,184,${alpha}) 0%, rgba(148,163,184,${alpha * 0.4}) 100%)`;
  }
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  return `linear-gradient(135deg, rgba(${r},${g},${b},${alpha}) 0%, rgba(${r},${g},${b},${alpha * 0.4}) 100%)`;
}

/** Standard provider block: logo | name+hint column, then compact numbers. */
export function renderProviderShell(provider, branding, bodyHtml, { headerLogoPx = 30 } = {}) {
  const instruction = renderInstruction(provider.instruction);
  const tint = tintFromProvider(provider, 0.07);
  return `<section class="provider-block" style="background:${esc(tint)}" data-provider-id="${esc(provider.id)}" data-provider-type="${esc(provider.type)}" data-tab="${esc(provider.tabId)}" data-pay-amount="${esc(String(provider.incentive?.payAmount ?? ''))}"${payHintAttr(provider)}>
    <div class="provider-card-top">
      ${providerLogoHtml(branding, templateId(provider), provider.provider, headerLogoPx)}
      <div class="provider-card-meta">
        <div class="provider-name">${esc(provider.displayName)}${incentiveBadgeHtml(provider)}</div>
        ${instruction}
      </div>
    </div>
    <div class="provider-card-numbers">${bodyHtml || ''}</div>
  </section>`;
}

/** Card grid tile — logo + name only. */
export function renderProviderCardPreview(provider, branding) {
  const tid = templateId(provider);
  const logoUrl = (provider.metadata?.logoUrl)
    || (tid != null ? (branding['t' + tid]?.logoUrl || '') : '');
  const fallback = defaultProviderLogo(provider.provider);
  const c = provColor(provider.provider);
  const isRedirect = provider.type !== PROVIDER_TYPE.SIM;
  const primary = safeImgSrc(logoUrl) || safeImgSrc(fallback);
  const fbAttr = fallback && safeImgSrc(fallback) !== primary
    ? ` data-fallback-src="${esc(safeImgSrc(fallback))}"`
    : (fallback ? ` data-fallback-src="${esc(safeImgSrc(fallback))}"` : '');
  const logoInner = primary
    ? `<img src="${esc(primary)}" class="prov-card-logo" decoding="async" loading="lazy"${fbAttr} onerror="window.__checkoutCardLogoFail&&window.__checkoutCardLogoFail(this)">`
    : isRedirect
      ? `<div class="logo" style="background:var(--purple)">⚡</div>`
      : `<div class="logo" style="background:${esc(c)}"></div>`;

  const liveAttr = isRedirect ? ` data-live="${esc(liveKey(provider))}"${merchantIdAttr(provider)}` : '';
  const extraClass = isRedirect ? ' live-prov-card' : ' provider-card';
  const idAttr = ` data-provider-id="${esc(provider.id)}"`;

  return `<article class="prov-card${extraClass}"${idAttr}${liveAttr} data-pay-amount="${esc(String(provider.incentive?.payAmount ?? ''))}"${payHintAttr(provider)} tabindex="0" role="button" aria-label="${esc(provider.displayName)}">
    ${logoInner}
    <div class="name">${esc(provider.displayName)}</div>
    ${incentiveBadgeHtml(provider, { compact: true })}
  </article>`;
}

/** Redirect / live payment action row (LIVE, BANK, CARD). */
export function renderProviderLiveBody(provider) {
  const badge = provider.type === PROVIDER_TYPE.LIVE
    ? 'লাইভ পেমেন্ট — অফিসিয়াল গেটওয়ে'
    : provider.type === PROVIDER_TYPE.BANK
      ? 'ব্যাংক ট্রান্সফার — অফিসিয়াল গেটওয়ে'
      : 'কার্ড পেমেন্ট — অফিসিয়াল গেটওয়ে';

  return `<div class="live-card" data-live="${esc(liveKey(provider))}"${merchantIdAttr(provider)} data-provider-type="${esc(provider.type)}">
    <div style="flex:1;display:flex;align-items:center;gap:8px">
      ${provider.metadata?.logoUrl ? `<img src="${esc(safeImgSrc(provider.metadata.logoUrl))}" alt="" style="width:24px;height:24px;border-radius:6px;object-fit:cover" onerror="this.remove()">` : ''}
      <div style="flex:1">
        <div style="font-weight:700;font-size:13px">${esc(provider.displayName)} ${incentiveBadgeHtml(provider)}</div>
        <span class="live-badge">${esc(badge)}</span>
      </div>
    </div><span aria-hidden="true">→</span>
  </div>`;
}

/** Group accordion shell. */
export function renderProviderAccordion(provider, branding, bodyHtml) {
  const header = renderProviderHeader(provider, branding, {
    logoPx: 24,
    className: 'acc-head-title',
  });
  const instruction = renderInstruction(provider.instruction);

  return `<div class="accordion provider-block" data-provider-id="${esc(provider.id)}" data-provider-type="${esc(provider.type)}" data-tab="${esc(provider.tabId)}" data-collapsible="true" data-pay-amount="${esc(String(provider.incentive?.payAmount ?? ''))}"${payHintAttr(provider)}>
    <div class="acc-head" role="button" tabindex="0" data-acc-toggle aria-expanded="false">
      <span class="acc-head-inner">${header}</span>
      <span class="chev" aria-hidden="true">▼</span>
    </div>
    <div class="acc-panel-outer" data-acc-panel aria-hidden="true">
      <div class="acc-panel-inner">${instruction}${bodyHtml || ''}</div>
    </div>
  </div>`;
}
