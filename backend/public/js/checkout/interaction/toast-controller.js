/** Lightweight toast — not a snackbar. */

let host = null;

function ensureHost() {
  if (host) return host;
  host = document.createElement('div');
  host.id = 'checkout-toast-host';
  host.className = 'checkout-toast-host';
  host.setAttribute('aria-live', 'polite');
  host.setAttribute('role', 'status');
  document.body.appendChild(host);
  return host;
}

export const ToastController = {
  show(message, { duration = 1800 } = {}) {
    const root = ensureHost();
    const el = document.createElement('div');
    el.className = 'checkout-toast';
    el.textContent = message;
    root.appendChild(el);
    requestAnimationFrame(() => el.classList.add('visible'));
    setTimeout(() => {
      el.classList.remove('visible');
      setTimeout(() => el.remove(), 220);
    }, duration);
  },
};
