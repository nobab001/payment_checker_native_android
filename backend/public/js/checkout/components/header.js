import { safeImgSrc, safeText } from '../sanitize.js';

/** Merchant branding + amount header (DOM mount, no innerHTML on full page). */
export const HeaderComponent = {
  mount(merchant, amount) {
    const nameEl = document.getElementById('merchant-name');
    const subEl = document.getElementById('merchant-sub');
    const logoEl = document.getElementById('merchant-logo');
    const amtEl = document.getElementById('payment-amount');

    if (nameEl) nameEl.textContent = safeText(merchant.companyName || 'Paychek', 120);
    if (amtEl) amtEl.textContent = Number(amount || 0).toFixed(2);

    if (subEl) {
      if (merchant.siteUrl) {
        subEl.textContent = safeText(merchant.siteUrl.replace(/^https?:\/\//, ''), 200);
        subEl.style.display = 'block';
      } else {
        subEl.textContent = '';
        subEl.style.display = 'none';
      }
    }

    if (logoEl) {
      const src = safeImgSrc(merchant.logoUrl);
      if (src) {
        logoEl.onerror = () => logoEl.classList.add('hidden');
        logoEl.src = src;
        logoEl.classList.remove('hidden');
      } else {
        logoEl.classList.add('hidden');
        logoEl.removeAttribute('src');
      }
    }
  },
};
