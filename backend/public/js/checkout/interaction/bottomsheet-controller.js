import { renderProviderHeader } from '../components/provider-shell.js';
import { renderInstruction } from '../components/instruction.js';
import { renderNumberList } from '../renderers/shared.js';
import { fadeIn, fadeOut } from './animation-controller.js';
import { trapFocus } from './focus-trap.js';
import { PROVIDER_TYPE } from '../provider-constants.js';
import { esc } from '../utils.js';

let overlay = null;
let sheet = null;
let previousFocus = null;

function ensureDom() {
  if (overlay) return;
  overlay = document.createElement('div');
  overlay.id = 'checkout-sheet-overlay';
  overlay.className = 'checkout-sheet-overlay';
  overlay.setAttribute('role', 'presentation');
  overlay.innerHTML = `
    <div class="checkout-sheet-backdrop" data-sheet-close tabindex="-1" aria-hidden="true"></div>
    <div class="checkout-sheet" role="dialog" aria-modal="true" aria-labelledby="checkout-sheet-title" tabindex="-1">
      <div class="checkout-sheet-handle" aria-hidden="true"></div>
      <button type="button" class="checkout-sheet-back" data-sheet-close aria-label="Back to payment methods">← ফিরে যান</button>
      <div class="checkout-sheet-tabs hidden" id="sheet-mode-tabs" role="tablist">
        <button type="button" class="checkout-sheet-tab active" data-sheet-mode="auto" role="tab">Automatic</button>
        <button type="button" class="checkout-sheet-tab" data-sheet-mode="manual" role="tab">Manual</button>
      </div>
      <div class="checkout-sheet-body"></div>
      <div class="checkout-sheet-wait hidden" id="sheet-wait-footer">
        <div class="sheet-wait-label">Waiting for payment</div>
        <div class="sheet-wait-timer" id="sheet-wait-timer">00:45</div>
        <button type="button" class="sheet-manual-link" id="sheet-manual-link">Didn't receive? Verify Manually</button>
      </div>
      <div class="checkout-sheet-manual hidden" id="sheet-manual-panel">
        <label for="trx-input-sheet">Transaction ID</label>
        <input type="text" id="trx-input-sheet" placeholder="যেমন: 8A47K89J2B" autocomplete="off">
        <button type="button" class="btn-primary" id="verify-btn-sheet">Verify</button>
        <div id="status-panel-sheet" class="status"></div>
      </div>
    </div>`;
  document.body.appendChild(overlay);
  sheet = overlay.querySelector('.checkout-sheet');
  overlay.addEventListener('click', (e) => {
    if (e.target.matches('[data-sheet-close], .checkout-sheet-backdrop')) close();
  });
  document.addEventListener('keydown', onDocumentKeydown);
}

function onDocumentKeydown(e) {
  if (!overlay?.classList.contains('open')) return;
  if (e.key === 'Escape') {
    e.preventDefault();
    close();
    return;
  }
  trapFocus(sheet, e);
}

function buildSheetHtml(provider, branding) {
  const header = renderProviderHeader(provider, branding, { logoPx: 48, className: 'checkout-sheet-header' });
  const instruction = renderInstruction(provider.instruction);
  const numbers = provider.type === PROVIDER_TYPE.SIM
    ? `<div class="checkout-sheet-numbers">${renderNumberList(provider, { branding })}</div>`
    : '';
  const hint = (provider.incentive?.payHint || '').trim();
  const payBanner = hint
    ? `<div class="checkout-pay-hint" role="status" style="margin:10px 0 4px;padding:10px 12px;border-radius:10px;background:#f1f5f9;color:var(--muted,#64748b);font-size:13px;font-weight:700;line-height:1.35;">${esc(hint)}</div>`
    : '';
  return `${header}${payBanner}${instruction}${numbers}`;
}

function close() {
  if (!overlay?.classList.contains('open')) return;
  fadeOut(sheet).then(() => {
    overlay.classList.remove('open');
    document.body.classList.remove('sheet-open');
    overlay.querySelector('.checkout-sheet-body').innerHTML = '';
    $('sheet-wait-footer')?.classList.add('hidden');
    $('sheet-mode-tabs')?.classList.add('hidden');
    $('sheet-manual-panel')?.classList.add('hidden');
    if (previousFocus && typeof previousFocus.focus === 'function') {
      previousFocus.focus();
    }
    previousFocus = null;
  });
}

function $(id) {
  return document.getElementById(id);
}

export const BottomSheetController = {
  init() {
    ensureDom();
  },

  open(provider, branding) {
    ensureDom();
    previousFocus = document.activeElement;
    const body = overlay.querySelector('.checkout-sheet-body');
    body.innerHTML = buildSheetHtml(provider, branding);
    if (provider?.id != null) {
      sheet.setAttribute('data-provider-id', String(provider.id));
      const hint = (provider.incentive?.payHint || '').trim();
      if (hint) sheet.setAttribute('data-pay-hint', hint);
      else sheet.removeAttribute('data-pay-hint');
      document.dispatchEvent(new CustomEvent('checkout:provider-focus', {
        detail: { id: String(provider.id) },
      }));
    }
    $('sheet-mode-tabs')?.classList.add('hidden');
    $('sheet-manual-panel')?.classList.add('hidden');
    $('sheet-wait-footer')?.classList.add('hidden');
    const title = body.querySelector('.checkout-sheet-header span, .provider-header span');
    if (title) title.id = 'checkout-sheet-title';
    overlay.classList.add('open');
    document.body.classList.add('sheet-open');
    const backBtn = sheet.querySelector('.checkout-sheet-back');
    (backBtn || sheet).focus();
    fadeIn(sheet);
  },

  close,

  bindCardGrid(root, getProviderById, branding) {
    if (!root || root.dataset.sheetGridBound) return;
    root.dataset.sheetGridBound = '1';

    const activate = (card) => {
      if (card.hasAttribute('data-live')) return;
      const id = card.getAttribute('data-provider-id');
      const provider = getProviderById(id);
      if (provider) this.open(provider, branding());
    };

    root.addEventListener('click', (e) => {
      const card = e.target.closest('.prov-card.provider-card');
      if (!card) return;
      activate(card);
    });
    root.addEventListener('keydown', (e) => {
      const card = e.target.closest('.prov-card.provider-card');
      if (!card || (e.key !== 'Enter' && e.key !== ' ')) return;
      e.preventDefault();
      activate(card);
    });
  },
};
