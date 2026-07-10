/**
 * Verify UX — Normal (modal primary), Vibe (auto first, manual fallback), Live (no verify UI).
 */

import { PROVIDER_TYPE } from '../provider-constants.js';
import { applyI18n, getLang, setLang, t, LANG } from '../i18n.js';

const VIBE_FALLBACK_SEC = 45;

let isVibeMode = false;
let hasSimProviders = false;
let waitTimerInterval = null;
let waitSecondsLeft = 0;
let vibeFallbackShown = false;
let sheetMode = 'auto';
let smartStepTimer = null;

function $(id) {
  return document.getElementById(id);
}

function isVibe() {
  return isVibeMode;
}

function hasCopyNumbers(model) {
  if (!model?.tabTree) return false;
  for (const bucket of Object.values(model.tabTree)) {
    for (const p of bucket.providers || []) {
      if (p.type === PROVIDER_TYPE.SIM && (p.numbers?.length > 0)) return true;
    }
  }
  return false;
}

function applyModeLayout() {
  const fab = $('verify-fab');
  const inlineBox = $('inline-verify-box');
  const vibeAuto = $('vibe-auto-panel');

  if (!hasSimProviders) {
    fab?.classList.add('hidden');
    inlineBox?.classList.add('hidden');
    vibeAuto?.classList.add('hidden');
    return;
  }

  if (isVibeMode) {
    fab?.classList.add('hidden');
    inlineBox?.classList.add('hidden');
  } else {
    // Normal: FAB appears after copy (not immediately)
    fab?.classList.add('hidden');
    inlineBox?.classList.add('hidden');
    vibeAuto?.classList.add('hidden');
  }
}

function setProgressCard({ titleKey, subKey } = {}) {
  const card = $('progress-card');
  if (!card) return;
  if (!titleKey) {
    card.classList.add('hidden');
    return;
  }
  $('progress-title').textContent = t(titleKey);
  $('progress-sub').textContent = t(subKey);
  card.classList.remove('hidden');
}

function setSmartStep(activeId) {
  const ids = ['step-ready', 'step-wait', 'step-detect', 'step-verify', 'step-done'];
  ids.forEach((id) => {
    const el = $(id);
    if (!el) return;
    el.classList.toggle('active', id === activeId || id === 'step-ready' || (id === 'step-wait' && activeId !== 'step-ready'));
  });
}

function startSmartStatusLoop() {
  if (!isVibeMode) return;
  if (smartStepTimer) clearInterval(smartStepTimer);
  const seq = ['step-wait', 'step-detect', 'step-verify'];
  let i = 0;
  setSmartStep(seq[i]);
  smartStepTimer = setInterval(() => {
    i = (i + 1) % seq.length;
    setSmartStep(seq[i]);
  }, 2200);
}

function stopSmartStatusLoop() {
  if (smartStepTimer) clearInterval(smartStepTimer);
  smartStepTimer = null;
}

function openVerifyModal() {
  const modal = $('verify-modal');
  if (!modal) return;
  applyI18n(modal);
  const fab = $('verify-fab');
  const panel = modal.querySelector('.verify-modal-panel');

  // Morph (FAB -> Modal) using FLIP transform on panel
  if (fab && panel && !fab.classList.contains('hidden')) {
    const first = fab.getBoundingClientRect();
    modal.classList.remove('hidden');
    modal.classList.add('open');
    document.body.classList.add('verify-modal-open');
    const last = panel.getBoundingClientRect();
    const dx = first.left - last.left;
    const dy = first.top - last.top;
    const sx = first.width / last.width;
    const sy = first.height / last.height;
    panel.style.transformOrigin = 'top left';
    panel.style.transform = `translate3d(${dx}px, ${dy}px, 0) scale(${sx}, ${sy})`;
    panel.style.transition = 'transform 0s';
    requestAnimationFrame(() => {
      panel.style.transition = 'transform 360ms cubic-bezier(.2,.8,.2,1)';
      panel.style.transform = 'translate3d(0,0,0) scale(1,1)';
    });
    $('trx-input')?.focus();
    return;
  }
  modal.classList.remove('hidden');
  modal.classList.add('open');
  document.body.classList.add('verify-modal-open');
  $('trx-input')?.focus();
}

function closeVerifyModal() {
  const modal = $('verify-modal');
  if (!modal) return;
  const fab = $('verify-fab');
  const panel = modal.querySelector('.verify-modal-panel');
  if (fab && panel && !fab.classList.contains('hidden')) {
    const first = panel.getBoundingClientRect();
    modal.classList.remove('open');
    document.body.classList.remove('verify-modal-open');
    const last = fab.getBoundingClientRect();
    const dx = last.left - first.left;
    const dy = last.top - first.top;
    const sx = last.width / first.width;
    const sy = last.height / first.height;
    panel.style.transformOrigin = 'top left';
    panel.style.transition = 'transform 360ms cubic-bezier(.2,.8,.2,1)';
    panel.style.transform = `translate3d(${dx}px, ${dy}px, 0) scale(${sx}, ${sy})`;
    setTimeout(() => {
      panel.style.transition = '';
      panel.style.transform = '';
      modal.classList.add('hidden');
    }, 380);
    return;
  }
  modal.classList.remove('open');
  document.body.classList.remove('verify-modal-open');
  setTimeout(() => modal.classList.add('hidden'), 220);
}

function showVibeAutoPanel() {
  $('vibe-auto-panel')?.classList.remove('hidden');
  $('vibe-waiting-main')?.classList.remove('hidden');
  $('vibe-mode-tabs')?.classList.add('hidden');
  $('vibe-tab-manual')?.classList.add('hidden');
  vibeFallbackShown = false;
  sheetMode = 'auto';
  $('vibe-manual-link')?.classList.add('hidden');
  startSmartStatusLoop();
}

function showVibeManualFallback() {
  if (vibeFallbackShown) return;
  vibeFallbackShown = true;

  const sheetOpen = $('checkout-sheet-overlay')?.classList.contains('open');
  if (sheetOpen) {
    $('sheet-mode-tabs')?.classList.remove('hidden');
    setSheetMode('manual');
  } else {
    $('vibe-mode-tabs')?.classList.remove('hidden');
    $('vibe-manual-link')?.classList.add('hidden');
    setSheetMode('manual');
  }
}

function setSheetMode(mode) {
  sheetMode = mode;
  const tabs = $('sheet-mode-tabs');
  const modalTabs = $('verify-modal-tabs');
  tabs?.querySelectorAll('[data-sheet-mode]').forEach((btn) => {
    btn.classList.toggle('active', btn.getAttribute('data-sheet-mode') === mode);
  });
  modalTabs?.querySelectorAll('[data-sheet-mode]').forEach((btn) => {
    btn.classList.toggle('active', btn.getAttribute('data-sheet-mode') === mode);
  });

  const isManual = mode === 'manual';
  $('sheet-manual-panel')?.classList.toggle('hidden', !isManual);
  $('sheet-wait-footer')?.classList.toggle('hidden', isManual);
  $('vibe-tab-manual')?.classList.toggle('hidden', !isManual);
  $('vibe-tab-auto')?.classList.toggle('hidden', isManual);
  $('verify-modal-manual')?.classList.toggle('hidden', !isManual);
  $('verify-modal-auto')?.classList.toggle('hidden', isManual);

  if (isManual) {
    $('trx-input-sheet')?.focus();
    if (!$('checkout-sheet-overlay')?.classList.contains('open')) {
      $('trx-input')?.focus();
    }
  }
}

function formatTimer(sec) {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function stopWaitTimer() {
  if (waitTimerInterval) {
    clearInterval(waitTimerInterval);
    waitTimerInterval = null;
  }
}

function startWaitTimer() {
  if (!isVibeMode) return;
  stopWaitTimer();
  waitSecondsLeft = VIBE_FALLBACK_SEC;
  const footer = $('sheet-wait-footer');
  const timerEl = $('sheet-wait-timer');
  footer?.classList.remove('hidden');
  if (timerEl) timerEl.textContent = formatTimer(waitSecondsLeft);
  $('vibe-manual-link')?.classList.remove('hidden');

  waitTimerInterval = setInterval(() => {
    waitSecondsLeft -= 1;
    if (timerEl) timerEl.textContent = formatTimer(Math.max(0, waitSecondsLeft));
    if (waitSecondsLeft <= 0) {
      stopWaitTimer();
      showVibeManualFallback();
    }
  }, 1000);
}

function onNumberCopied() {
  if (isVibeMode) {
    startWaitTimer();
    $('vibe-waiting-main')?.classList.remove('hidden');
    $('vibe-manual-link')?.classList.remove('hidden');
    setProgressCard({ titleKey: 'progress_copied_title', subKey: 'progress_copied_sub' });
  }
  if (!isVibeMode) {
    // Normal Mode: FAB appears only after at least one copy
    const fab = $('verify-fab');
    if (fab) {
      fab.classList.remove('hidden');
      fab.animate(
        [{ transform: 'translate3d(0,-10px,0) scale(.92)', opacity: 0 }, { transform: 'translate3d(0,0,0) scale(1)', opacity: 1 }],
        { duration: 240, easing: 'ease-in-out', fill: 'both' },
      );
    }
    setProgressCard({ titleKey: 'progress_copied_title', subKey: 'copy_hint' });
  }
}

function bindEvents() {
  $('verify-fab')?.addEventListener('click', () => openVerifyModal());
  $('verify-modal-close')?.addEventListener('click', () => closeVerifyModal());
  $('verify-modal-backdrop')?.addEventListener('click', () => closeVerifyModal());

  // Help sheet
  $('help-btn')?.addEventListener('click', () => {
    const m = $('help-modal');
    if (!m) return;
    applyI18n(m);
    m.classList.remove('hidden');
    m.classList.add('open');
  });
  $('help-modal-close')?.addEventListener('click', () => {
    const m = $('help-modal');
    if (!m) return;
    m.classList.remove('open');
    setTimeout(() => m.classList.add('hidden'), 220);
  });
  $('help-modal-backdrop')?.addEventListener('click', () => $('help-modal-close')?.click());

  // Language switcher
  function syncLangButtons() {
    const lang = getLang();
    $('lang-bn')?.classList.toggle('active', lang === LANG.BN);
    $('lang-en')?.classList.toggle('active', lang === LANG.EN);
    applyI18n(document);
  }
  $('lang-bn')?.addEventListener('click', () => { setLang(LANG.BN); syncLangButtons(); });
  $('lang-en')?.addEventListener('click', () => { setLang(LANG.EN); syncLangButtons(); });
  document.addEventListener('checkout:lang-change', () => syncLangButtons());
  syncLangButtons();

  document.querySelectorAll('[data-sheet-mode]').forEach((btn) => {
    btn.addEventListener('click', () => setSheetMode(btn.getAttribute('data-sheet-mode')));
  });

  $('sheet-manual-link')?.addEventListener('click', (e) => {
    e.preventDefault();
    stopWaitTimer();
    showVibeManualFallback();
  });

  $('vibe-manual-link')?.addEventListener('click', (e) => {
    e.preventDefault();
    stopWaitTimer();
    showVibeManualFallback();
  });

  document.addEventListener('checkout:number-copied', onNumberCopied);

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && $('verify-modal')?.classList.contains('open')) {
      closeVerifyModal();
    }
    if (e.key === 'Escape' && $('help-modal')?.classList.contains('open')) {
      $('help-modal-close')?.click();
    }
  });
}

export const VerifyUxController = {
  init(model) {
    isVibeMode = model?.checkoutMode === 'merchant_vibe';
    hasSimProviders = hasCopyNumbers(model);
    applyModeLayout();
    bindEvents();
  },

  onVibeStarted() {
    showVibeAutoPanel();
    applyModeLayout();
    setProgressCard({ titleKey: 'checkout_ready', subKey: 'waiting_title' });
  },

  onVibeStopped() {
    stopWaitTimer();
    stopSmartStatusLoop();
  },

  onVibeExpired() {
    stopWaitTimer();
    showVibeManualFallback();
    stopSmartStatusLoop();
  },

  onPaymentMatched() {
    stopWaitTimer();
    setProgressCard({ titleKey: 'progress_received_title', subKey: 'progress_received_sub' });
    setSmartStep('step-verify');
  },

  onPaymentSuccess() {
    stopWaitTimer();
    stopSmartStatusLoop();
    setProgressCard({ titleKey: 'progress_success_title', subKey: 'progress_success_sub' });
    setSmartStep('step-done');
  },

  openVerifyModal,
  closeVerifyModal,
  showVibeManualFallback,
  isVibe,
};
