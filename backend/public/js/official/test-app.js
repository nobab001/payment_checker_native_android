/**
 * Official Test Experience — temporary demo session + real checkout preview/pay.
 * No login. Visitor edits never touch the main merchant account.
 */

const API = '/api/official/test';

let state = {
  sessionId: null,
  apiKey: null,
  layout: null,
  baseLayout: null,
  overrides: {},
  activeTab: 'edit',
};

const $ = (sel) => document.querySelector(sel);

function setPill(text, isErr = false) {
  const el = $('#sessionPill');
  if (!el) return;
  el.textContent = text;
  el.classList.toggle('err', isErr);
}

async function api(path, options = {}) {
  const res = await fetch(`${API}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || data.success === false) {
    const err = new Error(data.message || data.error || `HTTP ${res.status}`);
    err.data = data;
    throw err;
  }
  return data;
}

function amountValue() {
  const n = Number($('#amountInput')?.value || 10);
  return Math.min(100, Math.max(10, n || 10));
}

function buildOverridesFromUi() {
  const overrides = { ...state.overrides };

  const modeBtn = $('#modeSeg button.active');
  if (modeBtn) overrides.checkoutMode = modeBtn.dataset.mode;

  const designBtn = $('#designSeg button.active');
  if (designBtn) overrides.checkoutTheme = designBtn.dataset.design;

  const tabs = {};
  document.querySelectorAll('#tabsList input[type=checkbox]').forEach((cb) => {
    tabs[cb.dataset.tabId] = { enabled: cb.checked };
  });
  if (Object.keys(tabs).length) overrides.tabs = tabs;

  const enabled = [];
  const disabled = [];
  document.querySelectorAll('#gatewayList input[type=checkbox]').forEach((cb) => {
    if (cb.checked) enabled.push(cb.dataset.gatewayId);
    else disabled.push(cb.dataset.gatewayId);
  });
  // Prefer disabled list so newly added merchant numbers stay visible by default
  overrides.disabledGatewayIds = disabled;
  delete overrides.enabledGatewayIds;

  return overrides;
}

function renderEditor(layoutForModes, baseForLists) {
  const layout = layoutForModes || state.layout;
  const base = baseForLists || state.baseLayout || layout;
  if (!layout || !base) return;

  document.querySelectorAll('#modeSeg button').forEach((b) => {
    b.classList.toggle('active', b.dataset.mode === (layout.checkoutMode || 'transaction'));
  });
  const theme = layout.checkoutTheme || layout.checkoutDesign || 'design-1';
  document.querySelectorAll('#designSeg button').forEach((b) => {
    b.classList.toggle('active', b.dataset.design === theme);
  });

  const tabsAll = base.checkoutTabsAll || {};
  const tabOverrides = state.overrides.tabs || {};
  const tabsList = $('#tabsList');
  tabsList.innerHTML = '';
  const tabEntries = Object.entries(tabsAll);
  if (!tabEntries.length) {
    tabsList.innerHTML = '<div style="color:var(--muted);font-size:0.85rem">No tabs from merchant layout.</div>';
  } else {
    for (const [id, tab] of tabEntries) {
      const enabled = tabOverrides[id]
        ? tabOverrides[id].enabled !== false
        : tab.enabled !== false;
      const row = document.createElement('label');
      row.className = 'toggle-row';
      row.innerHTML = `
        <div>
          <div>${tab.label || tab.name || id}</div>
          <span>${id}</span>
        </div>
        <span class="switch">
          <input type="checkbox" data-tab-id="${id}" ${enabled ? 'checked' : ''} />
          <span class="slider"></span>
        </span>`;
      tabsList.appendChild(row);
    }
  }

  const gateways = base.activeGateways || [];
  const gwList = $('#gatewayList');
  gwList.innerHTML = '';
  if (!gateways.length) {
    gwList.innerHTML = '<div style="color:var(--muted);font-size:0.85rem">No active numbers on the main merchant yet.</div>';
  } else {
    const disabled = new Set((state.overrides.disabledGatewayIds || []).map(String));
    for (const g of gateways) {
      const row = document.createElement('label');
      row.className = 'toggle-row';
      const title = g.displayName || g.providerTag || 'Number';
      const sub = [g.providerTag, g.number].filter(Boolean).join(' · ');
      const checked = !disabled.has(String(g.id));
      row.innerHTML = `
        <div>
          <div>${title}</div>
          <span>${sub || g.id}</span>
        </div>
        <span class="switch">
          <input type="checkbox" data-gateway-id="${g.id}" ${checked ? 'checked' : ''} />
          <span class="slider"></span>
        </span>`;
      gwList.appendChild(row);
    }
  }
}

let saveTimer = null;
async function persistOverridesAndPreview() {
  if (!state.sessionId) return;
  const overrides = buildOverridesFromUi();
  try {
    const data = await api(`/session/${encodeURIComponent(state.sessionId)}`, {
      method: 'PATCH',
      body: JSON.stringify(overrides),
    });
    state.overrides = data.overrides || overrides;
    state.layout = data.layout;
    state.baseLayout = data.baseLayout || state.baseLayout;
    await loadPreview();
  } catch (e) {
    setPill(e.message || 'Save failed', true);
  }
}

function scheduleSave() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(persistOverridesAndPreview, 280);
}

async function loadPreview() {
  if (!state.sessionId || !state.apiKey) return;
  const amount = amountValue();
  const q = new URLSearchParams({
    apiKey: state.apiKey,
    amount: String(amount),
    demoSession: state.sessionId,
  });
  const url = `/checkout.html?${q}`;
  const frame = $('#checkoutFrame');
  // Bust iframe cache so edit → instant preview
  frame.src = `${url}&_=${Date.now()}`;
  $('#previewLabel').textContent =
    state.activeTab === 'test'
      ? `Real checkout · ৳${amount}`
      : `Preview · ৳${amount} (not paid yet)`;
}

async function startRealPayment() {
  if (!state.sessionId) return;
  const amount = amountValue();
  setPill('Creating payment session…');
  try {
    // Ensure latest edits are saved first
    await persistOverridesAndPreview();
    const data = await api('/pay', {
      method: 'POST',
      body: JSON.stringify({ demoSessionId: state.sessionId, amount }),
    });
    setPill(`Payment session ready · ৳${amount}`);
    if (data.checkoutUrl) {
      $('#checkoutFrame').src = data.checkoutUrl;
      state.activeTab = 'test';
      document.querySelectorAll('.tab-btn').forEach((b) => {
        b.classList.toggle('active', b.dataset.tab === 'test');
      });
      $('#previewLabel').textContent = `Live payment · ৳${amount}`;
    }
  } catch (e) {
    setPill(e.message || 'Payment init failed', true);
  }
}

function wireUi() {
  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.activeTab = btn.dataset.tab;
      document.querySelectorAll('.tab-btn').forEach((b) => {
        b.classList.toggle('active', b.dataset.tab === state.activeTab);
      });
      if (state.activeTab === 'test') loadPreview();
    });
  });

  $('#modeSeg')?.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-mode]');
    if (!btn) return;
    $('#modeSeg').querySelectorAll('button').forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    scheduleSave();
  });

  $('#designSeg')?.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-design]');
    if (!btn) return;
    $('#designSeg').querySelectorAll('button').forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    scheduleSave();
  });

  $('#tabsList')?.addEventListener('change', scheduleSave);
  $('#gatewayList')?.addEventListener('change', scheduleSave);
  $('#amountInput')?.addEventListener('change', () => loadPreview());
  $('#refreshPreviewBtn')?.addEventListener('click', () => persistOverridesAndPreview());
  $('#startPayBtn')?.addEventListener('click', () => startRealPayment());
}

async function boot() {
  wireUi();
  try {
    const status = await api('/status');
    if (status.notice?.lines) {
      $('#noticeBox').innerHTML =
        `<strong>${status.notice.title || 'PayCheck Test Environment'}</strong>` +
        status.notice.lines.slice(1).join('<br />');
    }
    if (!status.configured) {
      setPill('Server missing OFFICIAL_TEST_PAYCHEK_API_KEY / SECRET', true);
      return;
    }

    const urlSession = new URLSearchParams(location.search).get('demoSession');
    let data;
    if (urlSession) {
      try {
        data = await api(`/session/${encodeURIComponent(urlSession)}`);
      } catch {
        data = await api('/session', { method: 'POST', body: '{}' });
      }
    } else {
      data = await api('/session', { method: 'POST', body: '{}' });
    }

    state.sessionId = data.sessionId;
    state.apiKey = data.apiKey;
    state.layout = data.layout;
    state.baseLayout = data.baseLayout || data.layout;
    state.overrides = data.overrides || {};
    setPill(`Demo session · ${state.sessionId.slice(0, 14)}…`);
    renderEditor(state.layout, state.baseLayout);
    await loadPreview();

    // Keep URL shareable without creating an account
    const u = new URL(location.href);
    u.searchParams.set('demoSession', state.sessionId);
    history.replaceState({}, '', u);
  } catch (e) {
    setPill(e.message || 'Failed to start test session', true);
  }
}

boot();
