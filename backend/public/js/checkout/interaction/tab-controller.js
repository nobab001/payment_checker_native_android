import { fadeIn, fadeOut } from './animation-controller.js';
import { setActiveTabOnBar } from '../components/tabs.js';

export const TabController = {
  setActive(bar, tabId) {
    setActiveTabOnBar(bar, tabId);
  },

  async animateContentSwap(contentEl, renderFn) {
    if (!contentEl) return;
    await fadeOut(contentEl, 180);
    renderFn();
    await fadeIn(contentEl, 180);
  },

  bindGroupSwitcher(root, onTabChange) {
    if (!root) return;
    root.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-tab-switch]');
      if (!btn) return;
      onTabChange(btn.getAttribute('data-tab-switch'));
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  },
};
