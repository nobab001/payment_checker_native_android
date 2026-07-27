/**
 * Official Test Experience — demo visitor auth + Hybrid/Live editor + Pay actions.
 * credentials: include (HttpOnly cookie). Never writes to real merchant rows.
 */

const API = '/api/official/test';

let state = {
  visitor: null,
  apiKey: null,
  layout: null,
  baseLayout: null,
  overrides: {},
  dirty: false,
  saving: false,
  countdownTimer: null,
  amountMin: 10,
  amountMax: 1000,
};

const $ = (sel) => document.querySelector(sel);

function setPill(text, isErr = false) {
  const el = $('#sessionPill');
  if (!el) return;
  el.textContent = text;
  el.classList.toggle('err', isErr);
}

function setLoginStatus(text, isErr = false) {
  const el = $('#loginStatus');
  if (!el) return;
  el.textContent = text;
  el.style.color = isErr ? 'var(--danger)' : 'var(--muted)';
}

async function api(path, options = {}) {
  const res = await fetch(`${API}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || data.success === false) {
    const err = new Error(data.message || data.error || `HTTP ${res.status}`);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

function clampAmount(raw) {
  const n = Number(raw);
  if (!Number.isFinite(n)) return state.amountMin;
  return Math.min(state.amountMax, Math.max(state.amountMin, Math.round(n)));
}

function parseAmount(raw) {
  const n = Number(raw);
  if (!Number.isFinite(n)) return null;
  return Math.round(n);
}

function validateAmount(raw) {
  const n = parseAmount(raw);
  if (n == null) {
    return { ok: false, message: `৳${state.amountMin} থেকে ৳${state.amountMax} এর মধ্যে দিন` };
  }
  if (n < state.amountMin) {
    return { ok: false, message: `সর্বনিম্ন ৳${state.amountMin}` };
  }
  if (n > state.amountMax) {
    return { ok: false, message: `সর্বোচ্চ ৳${state.amountMax}` };
  }
  return { ok: true, amount: n };
}

function setAmountError(inputId, message) {
  const err = $(`${inputId}Error`);
  if (!err) return;
  if (message) {
    err.textContent = message;
    err.classList.remove('hidden');
  } else {
    err.textContent = '';
    err.classList.add('hidden');
  }
}

function applyAmountRange(range) {
  if (!range) return;
  const min = Number(range.min);
  const max = Number(range.max);
  if (Number.isFinite(min) && min > 0) state.amountMin = min;
  if (Number.isFinite(max) && max >= state.amountMin) state.amountMax = max;

  ['#payAmount', '#balanceAmount'].forEach((sel) => {
    const el = $(sel);
    if (!el) return;
    el.min = String(state.amountMin);
    el.max = String(state.amountMax);
    const n = parseAmount(el.value);
    if (n != null && (n < state.amountMin || n > state.amountMax)) {
      el.value = String(state.amountMin);
    }
  });

  const hint = $('#amountHint');
  if (hint) {
    hint.textContent = `ডিফল্ট ৳${state.amountMin} · সর্বনিম্ন ৳${state.amountMin} · সর্বোচ্চ ৳${state.amountMax}`;
  }
}

function currentSurface() {
  return $('#surfaceSeg button.active')?.dataset.surface || 'hybrid';
}

function currentHybridType() {
  return $('#hybridTypeSeg button.active')?.dataset.hybridType || 'transaction';
}

function currentDesign() {
  return $('#designSeg button.active')?.dataset.design || 'design-1';
}

function buildOverridesFromUi() {
  const surface = currentSurface();
  const overrides = { ...state.overrides, editorSurface: surface };
  if (surface === 'live') {
    overrides.checkoutMode = 'live';
  } else {
    overrides.checkoutMode = currentHybridType();
    overrides.checkoutTheme = currentDesign();
  }
  return overrides;
}

function setDirty(dirty) {
  state.dirty = dirty;
  const row = $('#saveRow');
  const hint = $('#saveHint');
  if (!row) return;
  row.classList.toggle('hidden', !dirty && !state.saving);
  if (hint) {
    hint.textContent = dirty ? 'আনসেভড পরিবর্তন আছে' : 'সেভ হয়েছে';
    hint.classList.toggle('ok', !dirty);
  }
}

function setSegActive(rootSel, attr, value) {
  const root = $(rootSel);
  if (!root) return;
  root.querySelectorAll('button').forEach((b) => {
    b.classList.toggle('active', b.dataset[attr] === value);
  });
}

function providerShort(provider) {
  const p = String(provider || '').toLowerCase();
  if (p.includes('bkash')) return 'bK';
  if (p.includes('nagad')) return 'Ng';
  if (p.includes('rocket')) return 'Rk';
  if (p.includes('upay')) return 'Up';
  if (p.includes('card')) return 'Cd';
  if (p.includes('bank')) return 'Bk';
  return (provider || 'LV').slice(0, 2).toUpperCase();
}

function escapeHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function collectLiveCards(base) {
  const cards = [];
  const seen = new Set();
  for (const group of base?.merchantAccountsGroups || []) {
    for (const acct of group.accounts || []) {
      const id = `ma_${acct.id}`;
      if (seen.has(id)) continue;
      seen.add(id);
      cards.push({
        key: id,
        provider: acct.provider || group.provider,
        title: acct.displayName || acct.provider || group.provider || 'Live payment',
        subtitle: acct.accountLabel || group.provider || 'Merchant account',
      });
    }
  }
  for (const og of base?.officialGateways || []) {
    const id = `og_${og.id || og.provider}`;
    if (seen.has(id)) continue;
    const providerKey = String(og.provider || '').toLowerCase();
    if (cards.some((c) => String(c.provider).toLowerCase() === providerKey)) continue;
    seen.add(id);
    cards.push({
      key: id,
      provider: og.provider,
      title: og.displayName || og.provider || 'Live gateway',
      subtitle: 'Official gateway',
    });
  }
  return cards;
}

function renderLiveCards() {
  const host = $('#liveCards');
  if (!host) return;
  const cards = collectLiveCards(state.baseLayout);
  if (!cards.length) {
    host.innerHTML = '<div class="live-empty">এই মার্চেন্টে এখনো কোনো লাইভ পেমেন্ট অ্যাটাচ নেই।</div>';
    return;
  }
  host.innerHTML = cards.map((c) => `
    <div class="live-card">
      <div class="live-card-badge">${providerShort(c.provider)}</div>
      <div class="live-card-body">
        <strong>${escapeHtml(c.title)}</strong>
        <span>${escapeHtml(c.subtitle)}</span>
      </div>
    </div>
  `).join('');
}

function syncSurfaceBlocks() {
  const surface = currentSurface();
  $('#hybridBlock')?.classList.toggle('hidden', surface !== 'hybrid');
  $('#liveBlock')?.classList.toggle('hidden', surface !== 'live');
  if (surface === 'live') renderLiveCards();
}

function renderEditor() {
  const o = state.overrides || {};
  let surface = o.editorSurface;
  if (!surface) surface = o.checkoutMode === 'live' ? 'live' : 'hybrid';
  setSegActive('#surfaceSeg', 'surface', surface);
  const hybridType = o.checkoutMode === 'merchant_vibe' ? 'merchant_vibe' : 'transaction';
  setSegActive('#hybridTypeSeg', 'hybridType', hybridType);
  const theme = o.checkoutTheme || state.layout?.checkoutTheme || 'design-1';
  setSegActive('#designSeg', 'design', theme);
  syncSurfaceBlocks();
  setDirty(false);
}

function formatCountdown(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = String(Math.floor(total / 3600)).padStart(2, '0');
  const m = String(Math.floor((total % 3600) / 60)).padStart(2, '0');
  const s = String(total % 60).padStart(2, '0');
  return `${h}:${m}:${s}`;
}

function startCountdown() {
  clearInterval(state.countdownTimer);
  const tick = () => {
    if (!state.visitor?.expiresAt) return;
    const ms = new Date(state.visitor.expiresAt).getTime() - Date.now();
    const el = $('#accountCountdown');
    if (el) el.textContent = formatCountdown(ms);
    if (ms <= 0) {
      clearInterval(state.countdownTimer);
      setPill('ডেমো অ্যাকাউন্ট এক্সপায়ার হয়েছে', true);
      showLogin(true);
    }
  };
  tick();
  state.countdownTimer = setInterval(tick, 1000);
}

function renderPayments(payments) {
  const host = $('#payHistory');
  if (!host) return;
  if (!payments?.length) {
    host.innerHTML = '';
    return;
  }
  const refundLabel = (value) => {
    switch (String(value || 'none').toLowerCase()) {
      case 'refunded': return 'রিফান্ড সম্পন্ন';
      case 'pending': return 'রিফান্ড চলছে';
      default: return 'রিফান্ড বাকি';
    }
  };
  host.innerHTML = `
    <h3>সাম্প্রতিক টেস্ট পেমেন্ট</h3>
    <ul>
      ${payments.map((p) => `
        <li>
          <div>
            <strong>৳${escapeHtml(p.amount)} · ${escapeHtml(p.purpose || 'payment')}</strong>
            <span>${escapeHtml(p.status || 'unknown')} · ${escapeHtml(refundLabel(p.refundStatus))}</span>
          </div>
          ${p.trxId ? `<div>Trx ID: ${escapeHtml(p.trxId)}</div>` : ''}
          ${(p.senderNumber || p.receiverNumber || p.provider) ? `
            <div>
              ${p.provider ? `${escapeHtml(p.provider)} · ` : ''}${p.senderNumber ? `From ${escapeHtml(p.senderNumber)}` : ''}${p.senderNumber && p.receiverNumber ? ' · ' : ''}${p.receiverNumber ? `To ${escapeHtml(p.receiverNumber)}` : ''}
            </div>
          ` : ''}
          ${p.fullSms ? `<div class="pay-history-sms">${escapeHtml(p.fullSms)}</div>` : ''}
        </li>
      `).join('')}
    </ul>`;
}

function showLogin(expired = false) {
  $('#loginGate')?.classList.remove('hidden');
  $('#workspaceRoot')?.classList.add('hidden');
  if (expired) setLoginStatus('অ্যাকাউন্ট এক্সপায়ার হয়েছে — নতুন করে শুরু করুন', true);
}

function showWorkspace() {
  $('#loginGate')?.classList.add('hidden');
  $('#workspaceRoot')?.classList.remove('hidden');
  if ($('#accountName')) $('#accountName').textContent = state.visitor?.displayName || 'Demo';
  startCountdown();
  renderEditor();
  const locked = state.visitor && state.visitor.canTransact === false;
  $('#payNowBtn') && ($('#payNowBtn').disabled = locked);
  $('#addBalanceBtn') && ($('#addBalanceBtn').disabled = locked);
  if (locked) {
    setPill('পেমেন্ট সময় শেষ, তবে হিস্টরি এখনো দেখা যাবে');
  }
  if (window.lucide) lucide.createIcons();
}

async function saveOverrides() {
  if (!state.visitor || state.saving) return;
  state.saving = true;
  const btn = $('#saveBtn');
  if (btn) btn.disabled = true;
  setPill('সেভ হচ্ছে…');
  try {
    const overrides = buildOverridesFromUi();
    const data = await api('/settings', {
      method: 'PATCH',
      body: JSON.stringify(overrides),
    });
    applyWorkspaceData(data);
    setDirty(false);
    setPill('সেভ হয়েছে');
  } catch (e) {
    setPill(e.message || 'Save failed', true);
  } finally {
    state.saving = false;
    if (btn) btn.disabled = false;
  }
}

function applyWorkspaceData(data) {
  state.visitor = data.visitor || state.visitor;
  state.apiKey = data.apiKey || state.apiKey;
  state.layout = data.layout;
  state.baseLayout = data.baseLayout || state.baseLayout;
  state.overrides = data.overrides || data.visitor?.settings || {};
  if (data.payments) renderPayments(data.payments);
}

async function ensureSavedBeforePay() {
  if (state.dirty) await saveOverrides();
}

async function startAction(purpose) {
  const inputId = purpose === 'add_balance' ? '#balanceAmount' : '#payAmount';
  const check = validateAmount($(inputId)?.value);
  setAmountError(inputId, check.ok ? null : check.message);
  if (!check.ok) {
    setPill(check.message, true);
    return;
  }
  const amount = check.amount;
  if ($(inputId)) $(inputId).value = String(amount);

  const payBtn = $('#payNowBtn');
  const balBtn = $('#addBalanceBtn');
  if (payBtn) payBtn.disabled = true;
  if (balBtn) balBtn.disabled = true;
  setPill(purpose === 'add_balance' ? 'এড ব্যালেন্স শুরু…' : 'পেমেন্ট শুরু…');

  try {
    await ensureSavedBeforePay();
    const data = await api('/pay', {
      method: 'POST',
      body: JSON.stringify({ amount, purpose }),
    });
    setPill(`${purpose === 'add_balance' ? 'এড ব্যালেন্স' : 'পেমেন্ট'} · ৳${amount}`);
    if (data.checkoutUrl) {
      window.location.href = data.checkoutUrl;
      return;
    }
    throw new Error('Checkout URL missing');
  } catch (e) {
    setPill(e.message || 'Payment init failed', true);
  } finally {
    if (payBtn) payBtn.disabled = false;
    if (balBtn) balBtn.disabled = false;
  }
}

function markDirtyFromUi() {
  syncSurfaceBlocks();
  setDirty(true);
}

function wireUi() {
  $('#startDemoBtn')?.addEventListener('click', () => enterDemo(false));
  $('#resumeDemoBtn')?.addEventListener('click', () => enterDemo(false));
  $('#freshDemoBtn')?.addEventListener('click', () => enterDemo(true));
  $('#newAccountBtn')?.addEventListener('click', () => enterDemo(true));
  $('#logoutBtn')?.addEventListener('click', async () => {
    try { await api('/auth/logout', { method: 'POST', body: '{}' }); } catch { /* ignore */ }
    state.visitor = null;
    showLogin();
    setLoginStatus('লগআউট হয়েছে — আবার শুরু করতে পারেন');
    updateLoginButtons(false);
  });

  $('#surfaceSeg')?.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-surface]');
    if (!btn) return;
    $('#surfaceSeg').querySelectorAll('button').forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    markDirtyFromUi();
  });

  $('#hybridTypeSeg')?.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-hybrid-type]');
    if (!btn) return;
    $('#hybridTypeSeg').querySelectorAll('button').forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    markDirtyFromUi();
  });

  $('#designSeg')?.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-design]');
    if (!btn) return;
    $('#designSeg').querySelectorAll('button').forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    markDirtyFromUi();
  });

  $('#saveBtn')?.addEventListener('click', () => saveOverrides());

  const bindAmount = (sel) => {
    const el = $(sel);
    if (!el) return;
    const validate = () => {
      const check = validateAmount(el.value);
      setAmountError(sel, check.ok ? null : check.message);
      // Out of range: keep typed value so user can fix — do not silently clamp past max.
      if (check.ok) el.value = String(check.amount);
    };
    el.addEventListener('change', validate);
    el.addEventListener('blur', validate);
    el.addEventListener('input', () => {
      const check = validateAmount(el.value);
      setAmountError(sel, check.ok || el.value === '' ? null : check.message);
    });
  };
  bindAmount('#payAmount');
  bindAmount('#balanceAmount');

  $('#payNowBtn')?.addEventListener('click', () => startAction('pay'));
  $('#addBalanceBtn')?.addEventListener('click', () => startAction('add_balance'));
}

function updateLoginButtons(hasExisting) {
  $('#resumeDemoBtn')?.classList.toggle('hidden', !hasExisting);
  $('#freshDemoBtn')?.classList.toggle('hidden', !hasExisting);
  $('#startDemoBtn')?.classList.toggle('hidden', hasExisting);
}

async function enterDemo(forceNew) {
  setLoginStatus(forceNew ? 'নতুন অ্যাকাউন্ট তৈরি হচ্ছে…' : 'লগইন হচ্ছে…');
  try {
    const path = forceNew ? '/auth/new' : '/auth/start';
    const data = await api(path, {
      method: 'POST',
      body: JSON.stringify(forceNew ? { forceNew: true } : {}),
    });
    applyWorkspaceData(data);
    showWorkspace();
    setPill(data.created ? 'নতুন ডেমো অ্যাকাউন্ট তৈরি' : 'ডেমো অ্যাকাউন্টে প্রবেশ');
  } catch (e) {
    setLoginStatus(e.message || 'লগইন ব্যর্থ', true);
  }
}

async function boot() {
  wireUi();
  try {
    const status = await api('/status');
    applyAmountRange(status.amountRange);
    if (status.notice?.lines) {
      const html = `<strong>${status.notice.title || 'PayCheck Test Environment'}</strong>`
        + status.notice.lines.slice(1).join('<br />');
      if ($('#noticeBox')) $('#noticeBox').innerHTML = html;
    }
    if (!status.configured) {
      setLoginStatus('Server missing OFFICIAL_TEST_PAYCHEK_API_KEY / SECRET', true);
      return;
    }

    const me = await api('/me');
    if (me.authenticated && me.visitor) {
      updateLoginButtons(true);
      setLoginStatus(`${me.visitor.displayName} · বাকি ~${me.visitor.hoursLeft} ঘণ্টা`);
      // Auto-resume into workspace for returning visitors
      const ws = await api('/workspace');
      applyWorkspaceData(ws);
      showWorkspace();
      setPill('ডেমো অ্যাকাউন্টে ফিরে এসেছেন');
    } else {
      updateLoginButtons(false);
      setLoginStatus('এক ক্লিকে ডেমো অ্যাকাউন্ট তৈরি করুন');
      showLogin();
    }

    // Clean return URL noise
    const u = new URL(location.href);
    if (u.searchParams.has('status') || u.searchParams.has('demoSession')) {
      u.searchParams.delete('status');
      u.searchParams.delete('demoSession');
      u.hash = '';
      history.replaceState({}, '', u.pathname + u.search);
    }
  } catch (e) {
    setLoginStatus(e.message || 'Failed to load test experience', true);
    showLogin();
  }
}

boot();
