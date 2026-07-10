import { esc, tabIconHtml } from '../utils.js';

/**
 * Dynamic tab bar — hidden for Group design (tabs switch via bottom list).
 */
export function renderTabBar(container, tabs, activeTabId, design, onTabChange) {
  if (!container) return;
  if (design === 'group') {
    container.classList.add('hidden');
    container.innerHTML = '';
    return;
  }
  container.classList.remove('hidden');
  container.innerHTML = '';
  container.__onTabChange = onTabChange;

  if (!container.dataset.tabDelegated) {
    container.addEventListener('click', (e) => {
      const btn = e.target.closest('.tab-btn');
      if (!btn || !container.contains(btn)) return;
      container.__onTabChange?.(btn.dataset.tabId);
    });
    container.dataset.tabDelegated = '1';
  }

  const list = tabs.length ? tabs : [{ id: 'send_money', label: 'Send Money' }];
  const active = list.some((t) => t.id === activeTabId) ? activeTabId : list[0].id;

  for (const t of list) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'tab-btn' + (t.id === active ? ' active' : '');
    btn.dataset.tabId = t.id;
    btn.setAttribute('aria-label', t.label || t.id);
    btn.setAttribute('aria-selected', t.id === active ? 'true' : 'false');
    btn.innerHTML = `<span class="ico">${tabIconHtml(t)}</span><span class="lbl">${esc(t.label || t.id)}</span>`;
    container.appendChild(btn);
  }
}

export function setActiveTabOnBar(container, activeTabId) {
  if (!container) return;
  container.querySelectorAll('.tab-btn').forEach((btn) => {
    const on = btn.dataset.tabId === activeTabId;
    btn.classList.toggle('active', on);
    btn.setAttribute('aria-selected', on ? 'true' : 'false');
  });
}

/** Group design: inactive tab switcher rows at bottom of content. */
export function renderTabSwitcher(tabs, activeTabId) {
  const inactive = tabs.filter((t) => t.id !== activeTabId);
  if (!inactive.length) return '';

  let html = '<div class="d3-tab-switcher">';
  for (const t of inactive) {
    html += `<button type="button" class="d3-tab-bar" data-tab-switch="${esc(t.id)}">
      <div class="left"><span class="ico">${tabIconHtml(t)}</span><span>${esc(t.label || t.id)}</span></div>
      <span class="chev">▼</span>
    </button>`;
  }
  html += '</div>';
  return html;
}
