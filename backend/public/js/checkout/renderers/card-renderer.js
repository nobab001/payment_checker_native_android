import { renderProviderCardPreview } from '../components/provider-shell.js';
import { renderEmptyTab } from './shared.js';

/** Card View — responsive provider card grid (details via bottom sheet). */
export const CardRenderer = {
  id: 'card',

  renderTab({ bucket, branding }) {
    const providers = bucket.providers || [];
    if (!providers.length) return renderEmptyTab();

    let cards = '<div class="card-grid">';
    for (const provider of providers) {
      cards += renderProviderCardPreview(provider, branding);
    }
    cards += '</div>';
    return cards;
  },
};
