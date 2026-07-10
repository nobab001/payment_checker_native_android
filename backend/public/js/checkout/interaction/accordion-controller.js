import { expandPanel, collapsePanel } from './animation-controller.js';

export const AccordionController = {
  _root: null,
  _openId: null,
  _delegated: false,

  bind(root, { reset = true } = {}) {
    this._root = root;
    if (!root) return;

    if (!this._delegated) {
      root.addEventListener('click', (e) => {
        const head = e.target.closest('[data-acc-toggle]');
        if (!head || !this._root?.contains(head)) return;
        const item = head.closest('[data-collapsible="true"]');
        if (item) this.toggle(item);
      });
      root.addEventListener('keydown', (e) => {
        const head = e.target.closest('[data-acc-toggle]');
        if (!head || !this._root?.contains(head)) return;
        if (e.key !== 'Enter' && e.key !== ' ') return;
        e.preventDefault();
        const item = head.closest('[data-collapsible="true"]');
        if (item) this.toggle(item);
      });
      this._delegated = true;
    }

    const items = [...root.querySelectorAll('[data-collapsible="true"]')];
    items.forEach((item, index) => {
      const head = item.querySelector('[data-acc-toggle]');
      const panel = item.querySelector('[data-acc-panel]');
      if (!head || !panel) return;
      if (!panel.id) panel.id = `acc-panel-${index}`;
      head.setAttribute('aria-controls', panel.id);
      if (reset || item.getAttribute('data-provider-id') !== this._openId) {
        head.classList.remove('open');
        panel.classList.remove('is-open');
        panel.setAttribute('aria-hidden', 'true');
        head.setAttribute('aria-expanded', 'false');
      }
    });

    if (!items.length) {
      this._openId = null;
      return;
    }

    const keepOpen = !reset && this._openId
      ? items.find((i) => i.getAttribute('data-provider-id') === this._openId)
      : null;
    const target = keepOpen || items[0];
    if (target.getAttribute('data-provider-id') === this._openId && target.querySelector('[data-acc-panel].is-open')) {
      return;
    }
    this._openId = null;
    this.open(target);
  },

  async toggle(item) {
    const id = item.getAttribute('data-provider-id');
    if (this._openId === id) return;
    const prev = this._root?.querySelector(`[data-provider-id="${this._openId}"]`);
    if (prev) await this.close(prev);
    await this.open(item);
  },

  async open(item) {
    const head = item.querySelector('[data-acc-toggle]');
    const panel = item.querySelector('[data-acc-panel]');
    if (!head || !panel) return;
    this._openId = item.getAttribute('data-provider-id');
    head.classList.add('open');
    head.setAttribute('aria-expanded', 'true');
    await expandPanel(panel);
  },

  async close(item) {
    const head = item.querySelector('[data-acc-toggle]');
    const panel = item.querySelector('[data-acc-panel]');
    if (!head || !panel) return;
    head.classList.remove('open');
    head.setAttribute('aria-expanded', 'false');
    await collapsePanel(panel);
    if (this._openId === item.getAttribute('data-provider-id')) this._openId = null;
  },

  unbind() {
    this._openId = null;
  },
};
