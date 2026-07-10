import { flipReorder } from './animation-controller.js';

export function snapshotTab(model, tabId) {
  const bucket = model.tabTree[tabId];
  if (!bucket) return { providers: [], numbers: {} };
  const numbers = {};
  for (const p of bucket.providers || []) {
    numbers[p.id] = (p.numbers || []).map((n) => n.methodId);
  }
  return {
    providers: (bucket.providers || []).map((p) => p.id),
    numbers,
  };
}

export const SortController = {
  /** Animate provider reorder; update numbers in-place when order unchanged. */
  applyContentUpdate(contentEl, prevSnap, nextSnap, renderProviderHtml, branding) {
    if (!contentEl || !prevSnap || !nextSnap) return false;

    const providerChanged = prevSnap.providers.join() !== nextSnap.providers.join();
    if (providerChanged) {
      const grid = contentEl.querySelector('.card-grid');
      if (grid) {
        flipReorder(grid, '.prov-card[data-provider-id]', nextSnap.providers);
      } else {
        flipReorder(contentEl, '[data-provider-id]', nextSnap.providers);
      }
    }

    let numbersPatched = false;
    for (const id of nextSnap.providers) {
      const prevNums = prevSnap.numbers[id] || [];
      const nextNums = nextSnap.numbers[id] || [];
      if (prevNums.join() === nextNums.join()) continue;
      const block = contentEl.querySelector(`[data-provider-id="${id}"]`);
      if (!block) continue;
      const html = renderProviderHtml(id);
      if (!html) continue;
      const wrap = document.createElement('div');
      wrap.innerHTML = html;
      const fresh = wrap.firstElementChild;
      if (fresh) {
        block.replaceWith(fresh);
        numbersPatched = true;
      }
    }

    return providerChanged || numbersPatched;
  },
};
