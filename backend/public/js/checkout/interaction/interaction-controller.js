import { AccordionController } from './accordion-controller.js';
import { BottomSheetController } from './bottomsheet-controller.js';
import { CopyController } from './copy-controller.js';
import { LiveController } from './live-controller.js';
import { TabController } from './tab-controller.js';
import { getActiveTabBucket } from '../model.js';

/**
 * InteractionController — orchestrates customer UX (no business logic).
 */
export const InteractionController = {
  _content: null,
  _getModel: null,
  _getActiveTabId: null,
  _onLivePay: null,
  _onRetry: null,
  _mounted: false,

  init({ getModel, getActiveTabId, onLivePay, onRetry }) {
    this._getModel = getModel;
    this._getActiveTabId = getActiveTabId;
    this._onLivePay = onLivePay;
    this._onRetry = onRetry;
    this._content = document.getElementById('pay-content');
    BottomSheetController.init();
    CopyController.bind();
  },

  mount() {
    if (!this._content) return;
    if (!this._mounted) {
      LiveController.bind(this._content, (p, mid) => this._onLivePay?.(p, mid));
      BottomSheetController.bindCardGrid(
        this._content,
        (id) => this._findProvider(id),
        () => this._getModel()?.providerBranding || {},
      );
      this._content.addEventListener('click', (e) => {
        const retry = e.target.closest('[data-checkout-retry]');
        if (retry) {
          e.preventDefault();
          this._onRetry?.();
        }
      });
      TabController.bindGroupSwitcher(this._content, (tabId) => {
        document.dispatchEvent(new CustomEvent('checkout:tab-change', { detail: { tabId } }));
      });
      this._mounted = true;
    }
    this._bindDesignInteractions({ reset: true });
  },

  remountContent({ resetAccordion = false } = {}) {
    this._bindDesignInteractions({ reset: resetAccordion });
  },

  async switchTab(tabId, renderContentFn) {
    TabController.setActive(document.getElementById('tab-bar'), tabId);
    await TabController.animateContentSwap(this._content, renderContentFn);
    this._bindDesignInteractions({ reset: true });
  },

  _bindDesignInteractions({ reset = true } = {}) {
    const model = this._getModel?.();
    if (!model) return;
    if (model.design === 'group') {
      AccordionController.bind(this._content, { reset });
    }
  },

  _findProvider(id) {
    const model = this._getModel?.();
    const tabId = this._getActiveTabId?.();
    if (!model || !tabId) return null;
    const bucket = getActiveTabBucket(model, tabId);
    return (bucket.providers || []).find((p) => p.id === id) || null;
  },
};
