import {
  renderProviderShell,
  renderProviderLiveBody,
} from '../components/provider-shell.js';
import { renderNumberList, renderEmptyTab } from './shared.js';
import { PROVIDER_TYPE } from '../provider-constants.js';

/** List View — Tab → Provider → Number rows */
export const ListRenderer = {
  id: 'list',

  renderTab({ bucket, branding }) {
    const providers = bucket.providers || [];
    if (!providers.length) return renderEmptyTab();

    let html = '';
    for (const provider of providers) {
      const body = provider.type === PROVIDER_TYPE.SIM
        ? renderNumberList(provider, { branding })
        : renderProviderLiveBody(provider);
      html += renderProviderShell(provider, branding, body);
    }
    return html;
  },
};
