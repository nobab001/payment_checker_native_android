import { esc, providerLogoHtml } from '../utils.js';

const COPY_ICON_SVG = `<svg class="btn-copy-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="9" y="9" width="11" height="11" rx="2" stroke="currentColor" stroke-width="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>`;

function numberRowAttrs(number) {
  const inactive = number.enabled === false;
  return {
    inactive,
    rowClass: inactive ? 'list-row number-card number-inactive' : 'list-row number-card',
    disabled: inactive ? ' disabled' : '',
    badge: inactive ? '<span class="num-badge inactive" aria-label="Inactive number">Inactive</span>' : '',
    ariaLabel: inactive ? `Inactive number ${number.number}` : `Copy number ${number.number}`,
  };
}

/** Compact provider logo beside each number. */
function numberLogoHtml(number, provider, branding) {
  const tid = number.templateId ?? provider.metadata?.templateId ?? null;
  const prov = number.provider || provider.provider;
  return `<span class="num-logo">${providerLogoHtml(branding, tid, prov, 20)}</span>`;
}

function copyButtonHtml(number, ariaLabel, disabled) {
  return `<button type="button" class="btn-copy" data-copy="${esc(number)}" aria-label="${esc(ariaLabel)}"${disabled}>${COPY_ICON_SVG}<span class="btn-copy-lbl">কপি করুন</span></button>`;
}

/** List design: logo + number + outline copy button. */
export function renderNumberRow(number, branding = {}, provider = {}) {
  const { inactive, rowClass, disabled, badge, ariaLabel } = numberRowAttrs(number);
  const logo = numberLogoHtml(number, provider, branding);
  return `<div class="${rowClass}" data-number-row="${esc(number.methodId)}" data-number-enabled="${inactive ? 'false' : 'true'}">
    <span class="num-wrap">
      ${logo}
      <span class="num">${esc(number.number)}</span>
      ${badge}
    </span>
    ${copyButtonHtml(number.number, ariaLabel, disabled)}
  </div>`;
}

export function renderGroupNumberItem(number, provider, branding = {}) {
  const { inactive, disabled, badge, ariaLabel } = numberRowAttrs(number);
  const rowClass = inactive ? 'acc-item number-card number-inactive' : 'acc-item number-card';
  const logo = numberLogoHtml(number, provider, branding);
  return `<div class="${rowClass}" data-number-row="${esc(number.methodId)}" data-number-enabled="${inactive ? 'false' : 'true'}">
    ${logo}
    <div class="acc-info">
      <div class="pname">${esc(number.displayName || provider.displayName)}</div>
      <div class="pnum">${esc(number.number)}</div>
      ${badge}
    </div>
    ${copyButtonHtml(number.number, ariaLabel, disabled)}
  </div>`;
}

export function renderNumberList(provider, { layout = 'list', branding = {} } = {}) {
  const renderItem = layout === 'group'
    ? (n) => renderGroupNumberItem(n, provider, branding)
    : (n) => renderNumberRow(n, branding, provider);
  return provider.numbers.map(renderItem).join('');
}

export function renderEmptyTab() {
  return `<div class="checkout-empty" data-empty-state>
    <div class="checkout-empty-illus" aria-hidden="true">💳</div>
    <p class="checkout-empty-msg">No payment method available.</p>
    <button type="button" class="btn-primary checkout-empty-retry" data-checkout-retry aria-label="Retry loading payment methods">Retry</button>
  </div>`;
}
