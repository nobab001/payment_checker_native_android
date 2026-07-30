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

// Brand-colored SVG icons — help sheet link এবং FAB button উভয়ের জন্য
const HELPLINE_BRAND_SVG = {
  whatsapp:
    '<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="12" fill="#25D366"/><path fill="#fff" d="M17.5 14.4c-.3-.1-1.6-.8-1.8-.9-.2-.1-.4-.1-.6.1-.2.2-.7.9-.8 1-.2.1-.3.2-.6.1-.3-.1-1.2-.4-2.3-1.5-1-.9-1.5-1.9-1.7-2.2-.2-.3 0-.4.1-.6.1-.1.3-.3.4-.5.1-.2.2-.3.3-.5.1-.2 0-.4 0-.5 0-.1-.6-1.5-.8-2-.2-.5-.4-.4-.6-.4h-.5c-.2 0-.5.1-.7.3-.2.3-.9.9-.9 2.1s.9 2.4 1 2.6c.1.2 1.8 2.8 4.4 3.9 1.5.7 2.1.7 2.8.6.4-.1 1.6-.6 1.8-1.3.2-.6.2-1.2.1-1.3-.1-.1-.3-.2-.6-.3z"/><path fill="#fff" d="M12 4a8 8 0 0 0-6.9 12l-.9 3.3 3.4-.9A8 8 0 1 0 12 4zm0 14.6a6.5 6.5 0 0 1-3.3-.9l-.2-.1-2 .5.6-1.9-.2-.2a6.6 6.6 0 1 1 5.1 2.6z"/></svg>',
  telegram:
    '<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="12" fill="#0088CC"/><path fill="#fff" d="M5 11.6 17.3 7c.6-.2 1.1.1.9.9l-2 9.5c-.1.6-.5.8-1 .5l-2.8-2.1-1.3 1.3c-.2.2-.3.3-.6.3l.2-2.9 5.2-4.7c.2-.2 0-.3-.3-.1l-6.4 4-2.7-.9c-.6-.2-.6-.6.5-1z"/></svg>',
  facebook:
    '<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="12" fill="#1877F2"/><path fill="#fff" d="M15 8h-2c-.6 0-1 .4-1 1v2h3l-.4 3H12v7H9v-7H7v-3h2V9a4 4 0 0 1 4-4h2v3z"/></svg>',
  messenger:
    '<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><defs><linearGradient id="msg-g" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#0099FF"/><stop offset="100%" stop-color="#A033FF"/></linearGradient></defs><circle cx="12" cy="12" r="12" fill="url(#msg-g)"/><path fill="#fff" d="M12 4C7.6 4 4 7.3 4 11.4c0 2.3 1.1 4.3 2.9 5.7v2.9l2.7-1.5c.7.2 1.5.3 2.4.3 4.4 0 8-3.3 8-7.4S16.4 4 12 4zm.8 10-2-2.1-3.9 2.1 4.3-4.6 2 2.1 3.8-2.1-4.2 4.6z"/></svg>',
  instagram:
    '<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><defs><radialGradient id="ig-g" cx="30%" cy="107%" r="150%"><stop offset="0%" stop-color="#fdf497"/><stop offset="10%" stop-color="#fdf497"/><stop offset="50%" stop-color="#fd5949"/><stop offset="68%" stop-color="#d6249f"/><stop offset="100%" stop-color="#285AEB"/></radialGradient></defs><rect width="24" height="24" rx="6" fill="url(#ig-g)"/><rect x="7" y="7" width="10" height="10" rx="3" fill="none" stroke="#fff" stroke-width="1.5"/><circle cx="12" cy="12" r="2.5" fill="none" stroke="#fff" stroke-width="1.5"/><circle cx="16.2" cy="7.8" r="0.9" fill="#fff"/></svg>',
  phone:
    '<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="12" fill="#34A853"/><path fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round" d="M8.5 4.5c.3.8 1 2.5 1.5 3.2-.5.5-1.2 1-1.1 1.6.3 1.4 3.4 4.5 4.8 4.8.6.1 1.1-.6 1.6-1.1.7.5 2.4 1.2 3.2 1.5-.1 1-.5 2.8-1.8 3a9.5 9.5 0 0 1-9.3-9.3c.2-1.3 2-1.7 3.1-1.7z"/></svg>',
  mail:
    '<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="12" fill="#EA4335"/><rect x="5" y="7.5" width="14" height="10" rx="1.5" fill="none" stroke="#fff" stroke-width="1.5"/><polyline points="5,8 12,14 19,8" fill="none" stroke="#fff" stroke-width="1.5"/></svg>',
  support:
    '<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="12" fill="#607D8B"/><path fill="none" stroke="#fff" stroke-width="1.6" d="M6 11a6 6 0 0 1 12 0"/><path fill="none" stroke="#fff" stroke-width="1.6" stroke-linecap="round" d="M18 11v2a1.5 1.5 0 0 1-1.5 1.5H16m-8 0H7.5A1.5 1.5 0 0 1 6 13v-2"/><path fill="none" stroke="#fff" stroke-width="1.6" stroke-linecap="round" d="M10 18h4M12 15.5V18"/></svg>',
};

const HELPLINE_SVG = {
  whatsapp:
    '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M17.5 14.4c-.3-.1-1.6-.8-1.8-.9-.2-.1-.4-.1-.6.1-.2.2-.7.9-.8 1-.2.1-.3.2-.6.1-.3-.1-1.2-.4-2.3-1.5-1-.9-1.5-1.9-1.7-2.2-.2-.3 0-.4.1-.6.1-.1.3-.3.4-.5.1-.2.2-.3.3-.5.1-.2 0-.4 0-.5 0-.1-.6-1.5-.8-2-.2-.5-.4-.4-.6-.4h-.5c-.2 0-.5.1-.7.3-.2.3-.9.9-.9 2.1s.9 2.4 1 2.6c.1.2 1.8 2.8 4.4 3.9 1.5.7 2.1.7 2.8.6.4-.1 1.6-.6 1.8-1.3.2-.6.2-1.2.1-1.3-.1-.1-.3-.2-.6-.3z"/><path d="M12 2a10 10 0 0 0-8.7 15l-1.1 4 4.1-1.1A10 10 0 1 0 12 2zm0 18.2a8.2 8.2 0 0 1-4.2-1.1l-.3-.2-2.5.7.7-2.4-.2-.3a8.2 8.2 0 1 1 6.5 3.3z"/></svg>',
  telegram:
    '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M9.8 14.4 9.5 18c.4 0 .6-.2.8-.4l1.9-1.8 4 2.9c.7.4 1.3.2 1.5-.7l2.7-12.7c.2-.9-.3-1.3-1-.9L3.9 10.1c-.9.3-.9.8-.2 1l4.1 1.3 9.5-6c.4-.3.8-.1.5.2l-7.9 8z"/></svg>',
  facebook:
    '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M14 9h3V6h-3c-2.2 0-4 1.8-4 4v2H8v3h2v7h3v-7h2.6l.4-3H13v-2c0-.6.4-1 1-1z"/></svg>',
  messenger:
    '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2C6.5 2 2 6.1 2 11.2c0 2.9 1.4 5.4 3.7 7.1V22l3.4-1.9c.9.3 1.9.4 2.9.4 5.5 0 10-4.1 10-9.3S17.5 2 12 2zm1 12.4-2.5-2.7-4.9 2.7 5.4-5.7 2.6 2.7 4.8-2.7-5.4 5.7z"/></svg>',
  instagram:
    '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7 2h10a5 5 0 0 1 5 5v10a5 5 0 0 1-5 5H7a5 5 0 0 1-5-5V7a5 5 0 0 1 5-5zm10 2H7a3 3 0 0 0-3 3v10a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3V7a3 3 0 0 0-3-3zm-5 3.5A4.5 4.5 0 1 1 7.5 12 4.5 4.5 0 0 1 12 7.5zm0 2A2.5 2.5 0 1 0 14.5 12 2.5 2.5 0 0 0 12 9.5zM17.5 6.8a1 1 0 1 1-1 1 1 1 0 0 1 1-1z"/></svg>',
  phone:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6 19.8 19.8 0 0 1-3.1-8.7A2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.3 1.8.6 2.6a2 2 0 0 1-.4 2.1L8.1 9.9a16 16 0 0 0 6 6l1.5-1.2a2 2 0 0 1 2.1-.4c.8.3 1.7.5 2.6.6a2 2 0 0 1 1.7 2z"/></svg>',
  mail:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 4h16v16H4z"/><path d="m22 6-10 7L2 6"/></svg>',
  support:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 11a9 9 0 0 1 18 0"/><path d="M21 11v2a2 2 0 0 1-2 2h-1"/><path d="M3 11v2a2 2 0 0 0 2 2h1"/><path d="M8 21h8"/><path d="M12 17v4"/></svg>',
};

/**
 * Helpline URL open করার helper — Browser এবং সব Android WebView-এর জন্য।
 *
 * সমস্যা:
 *   - window.open('_blank') WebView-এ silent fail করে।
 *   - window.location.href দিয়ে https://wa.me/ navigate করলে
 *     merchant-এর WebView shouldOverrideUrlLoading কনফিগার না থাকলে
 *     wa.me ওয়েবসাইট হিসেবে load করে।
 *
 * সমাধান — Android-এ intent:// URI scheme:
 *   Android WebView built-in ভাবে intent:// handle করে।
 *   কোনো merchant-side shouldOverrideUrlLoading configuration ছাড়াই
 *   সঠিক native app open হয়।
 *
 *   Android → intent:// (সব merchant WebView-এ কাজ করে)
 *   Browser / iOS → https:// universal link
 */

const _isAndroid = /Android/i.test(typeof navigator !== 'undefined' ? navigator.userAgent : '');

/** Android WebView intent:// URI builder */
function _buildIntent(pkg, scheme, path) {
  return `intent://${path}#Intent;scheme=${scheme};package=${pkg};end`;
}

function openHelplineUrl(url) {
  if (!url) return;

  try {
    // ── WhatsApp ──────────────────────────────────────────────────────────
    // Backend → https://wa.me/{phone}  |  Legacy → whatsapp://send?phone=
    const waWebPhone = (url.match(/^https?:\/\/wa\.me\/([0-9+]+)/) || [])[1];
    const waSchemePhone = url.startsWith('whatsapp://')
      ? (url.match(/[?&]phone=([0-9+]+)/) || [])[1]
      : null;
    const waPhone = waWebPhone || waSchemePhone;

    if (waPhone || url.includes('wa.me') || url.startsWith('whatsapp://')) {
      if (_isAndroid && waPhone) {
        // intent:// — সব Android WebView-এ কাজ করে, merchant config দরকার নেই
        window.location.href = _buildIntent('com.whatsapp', 'whatsapp', `send?phone=${waPhone}`);
      } else {
        // Browser / iOS — universal link
        window.location.href = waPhone ? `https://wa.me/${waPhone}` : 'https://wa.me/';
      }
      return;
    }

    // ── Telegram ──────────────────────────────────────────────────────────
    // Backend → https://t.me/{username}
    const tgUsername = (url.match(/^https?:\/\/t\.me\/([^/?#]+)/) || [])[1];
    if (tgUsername || url.startsWith('tg://') || url.includes('t.me/')) {
      if (_isAndroid && tgUsername) {
        window.location.href = _buildIntent('org.telegram.messenger', 'tg', `resolve?domain=${tgUsername}`);
      } else {
        window.location.href = tgUsername ? `https://t.me/${tgUsername}` : url;
      }
      return;
    }

    // ── tel: / mailto: / sms: ─────────────────────────────────────────────
    // Android, Browser, iOS — সব জায়গায় location.href কাজ করে
    if (url.startsWith('tel:') || url.startsWith('mailto:') || url.startsWith('sms:')) {
      window.location.href = url;
      return;
    }

    // ── অন্য সব URL (Facebook, Instagram, Messenger, generic https://) ────
    window.location.href = url;
  } catch (_) {
    window.location.href = url;
  }
}


function mountHelpline(links) {
  const root = $('checkout-helpline-fab');
  const btn = $('checkout-helpline-btn');
  const helpRoot = $('help-helpline-links');
  const items = Array.isArray(links) ? links.filter((x) => x?.url) : [];
  const item = items[0];

  if (helpRoot) {
    helpRoot.innerHTML = '';
    if (!item) {
      helpRoot.style.display = 'none';
    } else {
      helpRoot.style.display = 'grid';
      const iconSvg = HELPLINE_BRAND_SVG[item.icon] || HELPLINE_BRAND_SVG.support;
      const a = document.createElement('a');
      // href এ raw URL রাখা হয়েছে কিন্তু click intercept করা হয়েছে
      a.href = item.url;
      a.rel = 'noopener noreferrer';
      a.style.cssText = 'display:flex;align-items:center;gap:12px;padding:12px 14px;border-radius:12px;border:1px solid var(--border);text-decoration:none;color:inherit;font-weight:700;font-size:14px;background:#fff';
      a.innerHTML = `<span style="display:flex;align-items:center;flex-shrink:0">${iconSvg}</span><span>${item.label || ''}</span>`;
      a.addEventListener('click', (e) => {
        e.preventDefault();
        openHelplineUrl(item.url);
      });
      helpRoot.appendChild(a);
    }
  }

  if (!root || !btn) return;
  if (!item?.url) {
    root.classList.add('hidden');
    return;
  }

  const icon = item.icon || 'whatsapp';
  root.dataset.icon = icon;
  btn.innerHTML = HELPLINE_BRAND_SVG[icon] || HELPLINE_BRAND_SVG.support;
  btn.setAttribute('aria-label', item.label || icon);
  root.classList.remove('hidden');
  btn.onclick = () => {
    openHelplineUrl(item.url);
  };
}

function bindEvents() {
  $('verify-fab')?.addEventListener('click', () => openVerifyModal());
  $('transaction-open-btn')?.addEventListener('click', () => openVerifyModal());
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
    mountHelpline(model?.checkoutHelpline);
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
