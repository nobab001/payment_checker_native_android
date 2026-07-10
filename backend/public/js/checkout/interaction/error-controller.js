/** Error banner with auto-retry — checkout shell stays visible. */

let banner = null;
let retryTimer = null;
let retryFn = null;
let attempt = 0;

function ensureBanner() {
  if (banner) return banner;
  banner = document.createElement('div');
  banner.id = 'checkout-error-banner';
  banner.className = 'checkout-error-banner hidden';
  banner.setAttribute('role', 'alert');
  banner.innerHTML = `
    <span class="checkout-error-text">Unable to refresh payment methods.</span>
    <span class="checkout-error-sub">Retrying...</span>
    <button type="button" class="checkout-error-retry" aria-label="Retry loading payment methods">Retry</button>`;
  const page = document.querySelector('.page');
  page?.insertBefore(banner, page.firstChild?.nextSibling || null);
  banner.querySelector('.checkout-error-retry').addEventListener('click', () => {
    attempt = 0;
    retryFn?.();
  });
  return banner;
}

function scheduleRetry() {
  clearTimeout(retryTimer);
  const delay = Math.min(30000, 3000 * Math.pow(1.5, attempt));
  retryTimer = setTimeout(() => retryFn?.(), delay);
}

export const ErrorController = {
  init(onRetry) {
    retryFn = onRetry;
    ensureBanner();
  },

  show(message) {
    const b = ensureBanner();
    b.querySelector('.checkout-error-text').textContent = message || 'Unable to refresh payment methods.';
    b.querySelector('.checkout-error-sub').textContent = 'Retrying...';
    b.classList.remove('hidden');
    attempt += 1;
    scheduleRetry();
  },

  hide() {
    banner?.classList.add('hidden');
    clearTimeout(retryTimer);
    attempt = 0;
  },

  destroy() {
    clearTimeout(retryTimer);
    banner?.remove();
    banner = null;
  },
};
