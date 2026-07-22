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
export const demoSessionId = urlParams.get('demoSession') || null;

let checkoutModel = null;
let activeTabId = 'send_money';
let vibeRequestId = null;
let vibePollTimer = null;
let successUrl = null;
let cancelUrl = null;
let initialLoaded = false;
/** Provider currently focused (accordion / sheet) — drives charge surcharge pay amount. */
let activePayProviderId = null;

function findProviderById(id) {
  if (!checkoutModel || id == null) return null;
  for (const bucket of Object.values(checkoutModel.tabTree || {})) {
    const hit = (bucket.providers || []).find((p) => String(p.id) === String(id));
    if (hit) return hit;
  }
  return null;
}

/**
 * Amount the customer should actually send (includes commission/charge).
 * Used for verify / live-init / redirects.
 */
function resolveCheckoutPayAmount(providerId = activePayProviderId) {
  const base = parseFloat(amount) || 0;
  let id = providerId;
  if (!id) {
    const sheet = document.querySelector('.checkout-sheet[data-provider-id]');
    if (sheet && document.body.classList.contains('sheet-open')) {
      id = sheet.getAttribute('data-provider-id');
    }
  }
  if (!id) {
    const openAcc = document.querySelector(
      '.accordion.provider-block [data-acc-toggle][aria-expanded="true"]',
    );
    id = openAcc?.closest('[data-provider-id]')?.getAttribute('data-provider-id');
  }
  const p = findProviderById(id);
  const pay = p?.incentive?.payAmount;
  return (Number.isFinite(Number(pay)) && Number(pay) > 0) ? Number(pay) : base;
}

/** Header display amount — payment purpose locks to order base; add_balance follows pay amount. */
function resolveHeaderAmount(providerId = activePayProviderId) {
  const base = parseFloat(amount) || 0;
  if (checkoutModel?.purpose === 'payment') return base;
  return resolveCheckoutPayAmount(providerId);
}

function syncPayableHeader(providerId) {
  if (providerId != null) activePayProviderId = String(providerId);
  if (!checkoutModel) return;
  HeaderComponent.mount(checkoutModel.merchant, resolveHeaderAmount(activePayProviderId));
}

document.addEventListener('checkout:provider-focus', (e) => {
  syncPayableHeader(e.detail?.id);
});

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
  const demoQ = demoSessionId
    ? `${sessionQ ? '&' : '?'}demoSession=${encodeURIComponent(demoSessionId)}`
    : '';
  const r = await fetch(`/api/checkout/${apiKey}${sessionQ}${demoQ}`);
  if (!r.ok) throw new Error('load failed');
  const data = await r.json();
  const prevSnap = checkoutModel ? snapshotTab(checkoutModel, activeTabId) : null;
  checkoutModel = buildCheckoutModel(data, amount);
  successUrl = checkoutModel.urls.successUrl;
  cancelUrl = checkoutModel.urls.cancelUrl;

  if (!initialLoaded) {
    HeaderComponent.mount(checkoutModel.merchant, resolveHeaderAmount());
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
    } else if (checkoutModel.checkoutMode === 'live') {
      document.getElementById('checkout-main')?.classList.remove('hidden');
      renderLivePaymentPage();
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
      body: JSON.stringify({ customerNumber: norm, amount: resolveCheckoutPayAmount(), session: sessionToken }),
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

/**
 * Live mode: renders the dedicated "Live Payment" page with smart auto-routing.
 * 1. If only 1 active provider with only 1 merchant → auto-redirect.
 * 2. If multiple providers → show provider selection.
 * 3. If single provider with multiple merchants → show merchant selection.
 */
function renderLivePaymentPage() {
  const groups = checkoutModel?.merchantAccountsGroups || [];
  const content = document.getElementById('pay-content');
  if (!content) return;

  // First, count total active providers and total merchants
  const totalProviders = groups.length;
  const totalMerchants = groups.reduce((sum, g) => sum + (g.accounts?.length || 0), 0);

  if (totalMerchants === 0) {
    content.innerHTML = `<div class="checkout-empty" data-empty-state>
      <div class="checkout-empty-illus" aria-hidden="true">💳</div>
      <p class="checkout-empty-msg">কোনো লাইভ পেমেন্ট গেটওয়ে যুক্ত নেই।</p>
    </div>`;
    return;
  }

  // Case 1: Only one provider, and only one merchant → auto-redirect
  if (totalProviders === 1 && groups[0].accounts.length === 1) {
    const singleAcct = groups[0].accounts[0];
    content.innerHTML = `<div class="live-pay-loading" style="text-align:center;padding:32px 16px;">
      <div class="checkout-empty-illus" aria-hidden="true">🔒</div>
      <p style="margin-top:12px;color:#475569;">${escapeHtml(singleAcct.merchantName || singleAcct.provider)} এ রিডাইরেক্ট হচ্ছে…</p>
    </div>`;
    startLivePay(groups[0].provider, singleAcct.id);
    return;
  }

  // Case 2: Multiple providers → show provider selection
  if (totalProviders > 1) {
    renderProviderSelection(groups, content);
    return;
  }

  // Case 3: Single provider, multiple merchants → show merchant selection
  renderMerchantSelection(groups[0].provider, groups[0].accounts, content);
}

function renderProviderSelection(groups, contentEl) {
  const providerCards = groups.map((group) => {
    const { provider, accounts } = group;
    const defaultAcct = accounts.find(a => a.isDefault) || accounts[0];
    return `
    <button type="button" class="live-pay-card" data-provider="${escapeHtml(provider)}"
      style="display:flex;align-items:center;gap:12px;width:100%;padding:16px;margin-bottom:12px;
             border:1.5px solid #e2e8f0;border-radius:14px;background:#fff;cursor:pointer;text-align:left;">
      ${defaultAcct.logoUrl ? `<img src="${escapeHtml(defaultAcct.logoUrl)}" alt="" style="width:36px;height:36px;border-radius:8px;object-fit:cover;">` : `<span aria-hidden="true" style="font-size:32px;">💳</span>`}
      <div style="flex:1;">
        <div style="font-weight:600;color:#0f172a;">${escapeHtml(provider)}</div>
        <div style="font-size:13px;color:#94a3b8;">${accounts.length} account(s)</div>
      </div>
      <span aria-hidden="true" style="color:#94a3b8;">›</span>
    </button>`;
  }).join('');

  contentEl.innerHTML = `<div class="live-pay-list" style="padding:8px 4px;">
    <p style="margin:0 0 12px;color:#475569;font-weight:600;">পেমেন্ট প্রোভাইডার নির্বাচন করুন</p>
    ${providerCards}
  </div>`;

  contentEl.querySelectorAll('[data-provider]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const provider = btn.getAttribute('data-provider');
      const group = (checkoutModel?.merchantAccountsGroups || []).find(g => g.provider === provider);
      if (!group) return;

      if (group.accounts.length === 1) {
        // Only one merchant → auto-redirect
        btn.disabled = true;
        startLivePay(provider, group.accounts[0].id);
      } else {
        // Multiple merchants → show merchant selection
        renderMerchantSelection(provider, group.accounts, contentEl);
      }
    });
  });
}

function renderMerchantSelection(provider, accounts, contentEl) {
  const merchantCards = accounts.map((acct) => `
    <button type="button" class="live-pay-card" data-merchant-id="${acct.id}"
      style="display:flex;align-items:center;gap:12px;width:100%;padding:16px;margin-bottom:12px;
             border:1.5px solid #e2e8f0;border-radius:14px;background:#fff;cursor:pointer;text-align:left;">
      ${acct.logoUrl ? `<img src="${escapeHtml(acct.logoUrl)}" alt="" style="width:36px;height:36px;border-radius:8px;object-fit:cover;">` : `<span aria-hidden="true" style="font-size:32px;">💳</span>`}
      <div style="flex:1;">
        <div style="font-weight:600;color:#0f172a;">${escapeHtml(acct.merchantName || provider)}</div>
        ${acct.merchantRef ? `<div style="font-size:13px;color:#94a3b8;">${escapeHtml(acct.merchantRef)}</div>` : ''}
        ${acct.isDefault ? `<div style="font-size:12px;color:#16a34a;font-weight:500;">Default</div>` : ''}
      </div>
      <span aria-hidden="true" style="color:#94a3b8;">›</span>
    </button>`).join('');

  contentEl.innerHTML = `<div class="live-pay-list" style="padding:8px 4px;">
    <button type="button" class="btn-back" style="display:flex;align-items:center;gap:8px;border:none;background:none;color:#475569;margin-bottom:12px;cursor:pointer;padding:4px 0;">
      <span aria-hidden="true">←</span>
      <span>Back</span>
    </button>
    <p style="margin:0 0 12px;color:#475569;font-weight:600;">মার্চেন্ট নির্বাচন করুন</p>
    ${merchantCards}
  </div>`;

  // Back button
  contentEl.querySelector('.btn-back').addEventListener('click', () => {
    renderLivePaymentPage();
  });

  // Merchant cards
  contentEl.querySelectorAll('[data-merchant-id]').forEach((btn) => {
    btn.addEventListener('click', () => {
      btn.disabled = true;
      const merchantAccountId = parseInt(btn.getAttribute('data-merchant-id'), 10);
      startLivePay(provider, merchantAccountId);
    });
  });
}

function escapeHtml(str) {
  return String(str == null ? '' : str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

async function startLivePay(provider, merchantAccountId) {
  try {
    const body = { provider, amount: resolveCheckoutPayAmount() };
    if (merchantAccountId != null && Number.isFinite(Number(merchantAccountId))) {
      body.merchantAccountId = Number(merchantAccountId);
    }
    if (successUrl) body.successUrl = successUrl;
    if (cancelUrl) body.cancelUrl = cancelUrl;
    if (demoSessionId) body.demoSessionId = demoSessionId;

    const r = await fetch(`/api/checkout/${apiKey}/live-init`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const res = await r.json();
    if (res.success && res.redirectUrl) {
      // bKash blocks iframe embedding — always navigate the top window
      const topWin = window.top || window;
      topWin.location.href = res.redirectUrl;
      return;
    }
    const errMsg = res.message || res.error || 'লাইভ পেমেন্ট শুরু করা যায়নি।';
    showStatus(errMsg, 'err');
    console.warn('[live-init]', errMsg, res);
  } catch (e) {
    showStatus('সংযোগ ত্রুটি।', 'err');
    console.warn('[live-init]', e);
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
      body: JSON.stringify({ apiKey, trxId: trx, amount: resolveCheckoutPayAmount(), session: sessionToken }),
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
    url = `${successUrl}${sep}trxId=${encodeURIComponent(trxId || redirectUrlOrTrx || '')}&amount=${encodeURIComponent(String(resolveCheckoutPayAmount()))}&status=success`;
    if (sessionToken) url += `&session=${encodeURIComponent(sessionToken)}`;
  }
  if (!url) return;
  setTimeout(() => { location.href = url; }, 2000);
}

function showStatus(t, cls) {
  const p = resolveStatusPanel();
  if (p) {
    p.className = 'status ' + cls;
    p.textContent = t;
    return;
  }
  if (cls === 'err') {
    const toast = document.createElement('div');
    toast.setAttribute('role', 'alert');
    toast.style.cssText = 'position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:9999;background:#991b1b;color:#fff;padding:10px 16px;border-radius:10px;font-size:14px;max-width:90%;box-shadow:0 8px 24px rgba(0,0,0,.25)';
    toast.textContent = t;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 4500);
  }
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
