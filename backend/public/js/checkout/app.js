/**
 * Checkout app bootstrap — fetch API, build model, mount layout + interaction layers.
 */

import { buildCheckoutModel } from './model.js';
import { CheckoutRenderer } from './checkout-renderer.js';
import { HeaderComponent } from './components/header.js';
import { InteractionController } from './interaction/interaction-controller.js';
import { SkeletonController } from './interaction/skeleton-controller.js';
import { ErrorController } from './interaction/error-controller.js';
import { snapshotTab, SortController } from './interaction/sort-controller.js';
import { VerifyUxController } from './interaction/verify-ux-controller.js';
import { applyI18n, t } from './i18n.js';

const urlParams = new URLSearchParams(window.location.search);
export const apiKey = urlParams.get('apiKey');
export const amount = urlParams.get('amount') || '0.00';
export const sessionToken = urlParams.get('session') || null;

let checkoutModel = null;
let activeTabId = 'send_money';
let vibeRequestId = null;
let vibePollTimer = null;
let successUrl = null;
let initialLoaded = false;

window.__checkoutIconFail = function (img) {
  const s = document.createElement('span');
  s.textContent = img.getAttribute('data-emoji') || '💳';
  img.replaceWith(s);
};

window.__checkoutLogoFail = function (img) {
  const px = parseInt(img.getAttribute('data-px') || '28', 10);
  const s = document.createElement('span');
  s.className = 'prov-avatar';
  s.style.width = px + 'px';
  s.style.height = px + 'px';
  s.style.borderRadius = Math.round(px * 0.28) + 'px';
  s.style.background = img.getAttribute('data-color') || '#94a3b8';
  s.style.fontSize = Math.round(px * 0.42) + 'px';
  s.style.flex = '0 0 auto';
  s.textContent = img.getAttribute('data-initial') || '?';
  img.replaceWith(s);
};

window.__checkoutCardLogoFail = function (img) {
  const d = document.createElement('div');
  d.className = 'logo';
  d.style.background = img.getAttribute('data-color') || '#94a3b8';
  d.textContent = img.getAttribute('data-initial') || '?';
  img.replaceWith(d);
};

function onTabChange(tabId) {
  if (tabId === activeTabId) return;
  activeTabId = tabId;
  InteractionController.switchTab(tabId, () => {
    CheckoutRenderer.render(buildRenderCtx(), { contentOnly: true });
    InteractionController.remountContent();
  });
}

function buildRenderCtx() {
  return {
    model: checkoutModel,
    activeTabId,
    onTabChange,
  };
}

function renderCheckoutFull() {
  if (!checkoutModel) return;
  CheckoutRenderer.render(buildRenderCtx(), { contentOnly: false });
  InteractionController.mount();
}

function renderCheckoutContent() {
  if (!checkoutModel) return;
  CheckoutRenderer.render(buildRenderCtx(), { contentOnly: true });
  InteractionController.remountContent();
}

function updateCheckoutPartial(prevSnap) {
  const content = document.getElementById('pay-content');
  const nextSnap = snapshotTab(checkoutModel, activeTabId);
  const patched = SortController.applyContentUpdate(
    content,
    prevSnap,
    nextSnap,
    (id) => CheckoutRenderer.renderProviderHtml(checkoutModel, activeTabId, id),
    checkoutModel.providerBranding,
  );
  if (!patched) renderCheckoutContent();
  else InteractionController.remountContent({ resetAccordion: false });
}

async function loadCheckoutData({ silent = false } = {}) {
  if (!apiKey) throw new Error('missing api key');
  const sessionQ = sessionToken ? `?session=${encodeURIComponent(sessionToken)}` : '';
  const r = await fetch(`/api/checkout/${apiKey}${sessionQ}`);
  if (!r.ok) throw new Error('load failed');
  const data = await r.json();
  const prevSnap = checkoutModel ? snapshotTab(checkoutModel, activeTabId) : null;
  checkoutModel = buildCheckoutModel(data, amount);
  successUrl = checkoutModel.urls.successUrl;

  if (!initialLoaded) {
    HeaderComponent.mount(checkoutModel.merchant, checkoutModel.amount);
    activeTabId = checkoutModel.tabs[0]?.id || 'send_money';
    if (!checkoutModel.tabs.find((t) => t.id === activeTabId)) {
      activeTabId = checkoutModel.tabs[0]?.id || 'send_money';
    }
    initialLoaded = true;
    VerifyUxController.init(checkoutModel);
    return { first: true };
  }

  if (prevSnap) updateCheckoutPartial(prevSnap);
  return { first: false };
}

async function fetchLayout() {
  if (!apiKey) {
    showStatus('API Key অনুপস্থিত!', 'err');
    return;
  }

  SkeletonController.show();
  ErrorController.init(() => refreshCheckout({ silent: false }));

  try {
    const { first } = await loadCheckoutData();
    SkeletonController.hide();
    ErrorController.hide();
    applyI18n(document);

    if (checkoutModel.checkoutMode === 'merchant_vibe') {
      document.getElementById('vibe-step')?.classList.remove('hidden');
    } else {
      document.getElementById('checkout-main')?.classList.remove('hidden');
      if (first) renderCheckoutFull();
    }
  } catch (e) {
    console.error(e);
    SkeletonController.hide();
    document.getElementById('checkout-main')?.classList.remove('hidden');
    ErrorController.show('Unable to refresh payment methods.');
    const el = document.getElementById('pay-content');
    if (el && !el.querySelector('[data-empty-state]')) {
      el.innerHTML = `<div class="checkout-empty" data-empty-state>
        <div class="checkout-empty-illus" aria-hidden="true">💳</div>
        <p class="checkout-empty-msg">No payment method available.</p>
        <button type="button" class="btn-primary checkout-empty-retry" data-checkout-retry>Retry</button>
      </div>`;
      InteractionController.mount();
    }
  }
}

async function refreshCheckout(opts = {}) {
  try {
    await loadCheckoutData(opts);
    ErrorController.hide();
    if (checkoutModel?.checkoutMode !== 'merchant_vibe') {
      document.getElementById('checkout-main')?.classList.remove('hidden');
      if (!document.getElementById('pay-content')?.children.length) {
        renderCheckoutFull();
      }
    }
  } catch (e) {
    if (!opts.silent) ErrorController.show('Unable to refresh payment methods.');
  }
}

window.startVibe = async function startVibe() {
  const norm = document.getElementById('vibe-number').value.replace(/\D/g, '').replace(/^880/, '').slice(-11);
  if (norm.length !== 11) { showVibeStatus('সঠিক ১১-সংখ্যার নাম্বার দিন।', 'err'); return; }
  document.getElementById('vibe-start-btn').disabled = true;
  try {
    const r = await fetch(`/api/checkout/${apiKey}/vibe-init`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ customerNumber: norm, amount: parseFloat(amount), session: sessionToken }),
    });
    const res = await r.json();
    if (!res.success) {
      showVibeStatus(res.error || 'ব্যর্থ', 'err');
      document.getElementById('vibe-start-btn').disabled = false;
      return;
    }
    vibeRequestId = res.requestId;
    document.getElementById('vibe-step').classList.add('hidden');
    document.getElementById('checkout-main').classList.remove('hidden');
    VerifyUxController.onVibeStarted();
    renderCheckoutFull();
    startVibePolling();
  } catch (e) {
    showVibeStatus('সার্ভার ত্রুটি।', 'err');
    document.getElementById('vibe-start-btn').disabled = false;
  }
};

function startVibePolling() {
  if (vibePollTimer) clearInterval(vibePollTimer);
  vibePollTimer = setInterval(async () => {
    if (!vibeRequestId) return;
    try {
      const r = await fetch(`/api/checkout/vibe-status/${vibeRequestId}`);
      const res = await r.json();
      if (res.status === 'matched') {
        stopVibe();
        VerifyUxController.onPaymentMatched?.();
        showStatus('পেমেন্ট স্বয়ংক্রিয়ভাবে নিশ্চিত! ✓', 'ok');
        redirectSuccess(res.redirectUrl, res.trxId);
      } else if (res.status === 'expired') {
        stopVibe();
        showStatus('সময় শেষ। ম্যানুয়ালি ভেরিফাই করুন।', 'err');
        VerifyUxController.onVibeExpired();
      }
    } catch (_) { /* ignore */ }
  }, 2500);
}

function stopVibe() {
  if (vibePollTimer) { clearInterval(vibePollTimer); vibePollTimer = null; }
  VerifyUxController.onVibeStopped();
}

async function startLivePay(provider) {
  try {
    const r = await fetch(`/api/checkout/${apiKey}/live-init`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ provider, amount: parseFloat(amount) }),
    });
    const res = await r.json();
    if (res.success && res.redirectUrl) window.location.href = res.redirectUrl;
    else showStatus(res.error || 'লাইভ পেমেন্ট শুরু করা যায়নি।', 'err');
  } catch (e) {
    showStatus('সংযোগ ত্রুটি।', 'err');
  }
}

function resolveTrxInput() {
  const sheetPanel = document.getElementById('sheet-manual-panel');
  if (sheetPanel && !sheetPanel.classList.contains('hidden')) {
    return document.getElementById('trx-input-sheet');
  }
  const vibeManual = document.getElementById('vibe-tab-manual');
  if (vibeManual && !vibeManual.classList.contains('hidden')) {
    return document.getElementById('trx-input-vibe');
  }
  return document.getElementById('trx-input');
}

function resolveStatusPanel() {
  const sheetPanel = document.getElementById('sheet-manual-panel');
  if (sheetPanel && !sheetPanel.classList.contains('hidden')) {
    return document.getElementById('status-panel-sheet');
  }
  const modal = document.getElementById('verify-modal');
  if (modal?.classList.contains('open')) {
    return document.getElementById('status-panel-modal');
  }
  return document.getElementById('status-panel');
}

function resolveVerifyButton() {
  const sheetPanel = document.getElementById('sheet-manual-panel');
  if (sheetPanel && !sheetPanel.classList.contains('hidden')) {
    return document.getElementById('verify-btn-sheet');
  }
  const vibeManual = document.getElementById('vibe-tab-manual');
  if (vibeManual && !vibeManual.classList.contains('hidden')) {
    return document.getElementById('verify-btn-vibe');
  }
  return document.getElementById('verify-btn');
}

window.triggerVerification = async function triggerVerification() {
  const input = resolveTrxInput();
  const trx = input?.value?.trim() || '';
  if (!trx) {
    showStatus(`${t('trx_label')} লিখুন।`, 'err');
    return;
  }
  const btn = resolveVerifyButton();
  if (btn) btn.disabled = true;
  showStatus(t('verifying'), 'load');
  try {
    const r = await fetch('/api/checkout/verify', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ apiKey, trxId: trx, amount: parseFloat(amount), session: sessionToken }),
    });
    const res = await r.json();
    if (res.success) {
      stopVibe();
      VerifyUxController.onPaymentSuccess?.();
      showStatus(t('success_title'), 'success-anim');
      VerifyUxController.closeVerifyModal();
      if (res.redirectUrl) setTimeout(() => { location.href = res.redirectUrl; }, 2000);
    } else {
      const friendly = `${t('err_trx_not_found_title')} ${t('err_try_again')}`;
      showStatus(friendly, 'err');
      if (btn) btn.disabled = false;
    }
  } catch (e) {
    showStatus('সার্ভার ত্রুটি।', 'err');
    if (btn) btn.disabled = false;
  }
};

function redirectSuccess(redirectUrlOrTrx, trxId) {
  let url = (typeof redirectUrlOrTrx === 'string' && redirectUrlOrTrx.startsWith('http'))
    ? redirectUrlOrTrx
    : null;
  if (!url && successUrl) {
    const sep = successUrl.includes('?') ? '&' : '?';
    url = `${successUrl}${sep}trxId=${encodeURIComponent(trxId || redirectUrlOrTrx || '')}&amount=${encodeURIComponent(amount)}&status=success`;
    if (sessionToken) url += `&session=${encodeURIComponent(sessionToken)}`;
  }
  if (!url) return;
  setTimeout(() => { location.href = url; }, 2000);
}

function showStatus(t, cls) {
  const p = resolveStatusPanel();
  if (!p) return;
  p.className = 'status ' + cls;
  p.textContent = t;
}

function showVibeStatus(t, cls) {
  const p = document.getElementById('vibe-step-status');
  if (!p) return;
  p.className = 'status ' + cls;
  p.textContent = t;
}

function bindVerifyButtons() {
  ['verify-btn', 'verify-btn-sheet', 'verify-btn-vibe'].forEach((id) => {
    document.getElementById(id)?.addEventListener('click', () => triggerVerification());
  });
}

InteractionController.init({
  getModel: () => checkoutModel,
  getActiveTabId: () => activeTabId,
  onLivePay: startLivePay,
  onRetry: () => refreshCheckout({ silent: false }),
});

document.addEventListener('checkout:tab-change', (e) => onTabChange(e.detail.tabId));

window.addEventListener('load', () => {
  bindVerifyButtons();
  fetchLayout();
});

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(() => {});
}
