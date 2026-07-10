import { ListRenderer } from './renderers/list-renderer.js';
import { CardRenderer } from './renderers/card-renderer.js';
import { GroupRenderer } from './renderers/group-renderer.js';
import { renderTabBar, renderTabSwitcher, setActiveTabOnBar } from './components/tabs.js';
import { getActiveTabBucket } from './model.js';

const RENDERERS = {
  list: ListRenderer,
  card: CardRenderer,
  group: GroupRenderer,
};

function patchGroupContent(content, parts) {
  const wrap = document.createElement('div');
  wrap.innerHTML = parts.providersHtml + parts.switcherHtml;

  const newTitle = wrap.querySelector('.group-section-title');
  const newProv = wrap.querySelector('[data-checkout-providers]');
  const newSw = wrap.querySelector('.d3-tab-switcher');

  const title = content.querySelector('.group-section-title');
  const prov = content.querySelector('[data-checkout-providers]');
  const sw = content.querySelector('.d3-tab-switcher');

  if (!newProv) return false;
  if (title && newTitle) title.replaceWith(newTitle);
  else if (newTitle) content.prepend(newTitle);
  if (prov) prov.replaceWith(newProv);
  if (newSw) {
    if (sw) sw.replaceWith(newSw);
    else content.appendChild(newSw);
  } else if (sw) {
    sw.remove();
  }
  return true;
}

export const CheckoutRenderer = {
  pick(design) {
    return RENDERERS[design] || ListRenderer;
  },

  buildTabContentHtml(model, activeTabId) {
    const design = model.design || 'list';
    const renderer = this.pick(design);
    const bucket = getActiveTabBucket(model, activeTabId);

    const providersHtml = renderer.renderTab({
      bucket,
      branding: model.providerBranding,
      sectionTitle: bucket.tab?.label,
    });

    const switcherHtml = design === 'group'
      ? renderTabSwitcher(model.tabs, activeTabId)
      : '';

    return { html: providersHtml + switcherHtml, providersHtml, switcherHtml, design, bucket };
  },

  render(ctx, options = {}) {
    const { model, activeTabId, onTabChange } = ctx;
    const { contentOnly = false } = options;
    const design = model.design || 'list';
    const tabBar = document.getElementById('tab-bar');
    const content = document.getElementById('pay-content');
    if (!content) return;

    if (!contentOnly) {
      renderTabBar(tabBar, model.tabs, activeTabId, design, onTabChange);
    } else if (tabBar && design !== 'group') {
      setActiveTabOnBar(tabBar, activeTabId);
    }

    const parts = this.buildTabContentHtml(model, activeTabId);

    if (contentOnly && parts.design === 'group' && patchGroupContent(content, parts)) {
      return;
    }

    content.innerHTML = parts.html;
  },

  renderProviderHtml(model, activeTabId, providerId) {
    const bucket = getActiveTabBucket(model, activeTabId);
    const provider = (bucket.providers || []).find((p) => p.id === providerId);
    if (!provider) return '';
    const renderer = this.pick(model.design || 'list');
    return renderer.renderTab({
      bucket: { ...bucket, providers: [provider] },
      branding: model.providerBranding,
      sectionTitle: bucket.tab?.label,
    });
  },
};
