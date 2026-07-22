import { ToastController } from './toast-controller.js';
import { t } from '../i18n.js';

let bound = false;

function hapticLight() {
  try {
    navigator.vibrate?.(12);
  } catch (_) { /* unsupported */ }
}

async function writeClipboard(text) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.setAttribute('readonly', '');
  ta.style.position = 'fixed';
  ta.style.left = '-9999px';
  document.body.appendChild(ta);
  ta.select();
  document.execCommand('copy');
  ta.remove();
}

/** Design 1/3: after copy, show pay hint if this provider has charge/commission. */
function hintFromCopyButton(btn) {
  const block = btn.closest('[data-pay-hint], [data-provider-id]');
  const hint = block?.getAttribute('data-pay-hint')?.trim();
  return hint || null;
}

export const CopyController = {
  /** Document-level delegation — works in #pay-content and bottom sheet portal. */
  bind() {
    if (bound) return;
    bound = true;
    document.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-copy]');
      if (!btn || btn.disabled) return;
      e.preventDefault();
      e.stopPropagation();
      const num = btn.getAttribute('data-copy');
      if (!num) return;
      this.copy(btn, num);
    });
  },

  async copy(btn, text) {
    try {
      await writeClipboard(text);
      hapticLight();
      const original = btn.getAttribute('data-copy-label') || btn.textContent;
      btn.setAttribute('data-copy-label', original);
      btn.textContent = 'Copied';
      btn.classList.add('copied');
      btn.setAttribute('aria-label', `Copied ${text}`);

      const payHint = hintFromCopyButton(btn);
      if (payHint) {
        // Small popup: copy ack + how much to send (charge/commission only).
        ToastController.show(`${t('copied')} · ${payHint}`, { duration: 3200 });
      } else {
        ToastController.show(t('copied'));
      }

      document.dispatchEvent(new CustomEvent('checkout:number-copied', {
        detail: { number: text, payHint },
      }));
      setTimeout(() => {
        btn.textContent = original;
        btn.classList.remove('copied');
        btn.setAttribute('aria-label', `Copy ${text}`);
      }, 1000);
    } catch (_) {
      ToastController.show(t('copy_failed'));
    }
  },
};
