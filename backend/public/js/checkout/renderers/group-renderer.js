import { esc } from '../utils.js';
import {
  renderProviderShell,
  renderProviderLiveBody,
  renderProviderAccordion,
} from '../components/provider-shell.js';
import { renderNumberList, renderEmptyTab } from './shared.js';
import { PROVIDER_TYPE } from '../provider-constants.js';

/** Group View — provider accordion blocks (collapse-ready, open by default via controller) */
export const GroupRenderer = {
  id: 'group',

  renderTab({ bucket, branding, sectionTitle }) {
    const providers = bucket.providers || [];
    if (!providers.length) return renderEmptyTab();

    let html = `<div class="group-section-title">${esc(sectionTitle || bucket.tab?.label || 'Payment')}</div>`;
    html += '<div class="checkout-providers" data-checkout-providers>';

    for (const provider of providers) {
      if (provider.type !== PROVIDER_TYPE.SIM) {
        html += renderProviderShell(provider, branding, renderProviderLiveBody(provider));
        continue;
      }
      html += renderProviderAccordion(provider, branding, renderNumberList(provider, { layout: 'group', branding }));
    }

    html += '</div>';
    return html;
  },
};
