let bound = false;

const COPY_ICON_SVG = `<svg class="btn-copy-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="9" y="9" width="11" height="11" rx="2" stroke="currentColor" stroke-width="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>`;

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

function hintFromCopyButton(btn) {
  const block = btn.closest('[data-pay-hint], [data-provider-id]');
  const hint = block?.getAttribute('data-pay-hint')?.trim();
  return hint || null;
}

function setCopyLabel(btn, text) {
  const lbl = btn.querySelector('.btn-copy-lbl');
  if (lbl) {
    lbl.textContent = text;
    return;
  }
  btn.innerHTML = `${COPY_ICON_SVG}<span class="btn-copy-lbl">${text}</span>`;
}

export const CopyController = {
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
      const original = btn.getAttribute('data-copy-label') || 'কপি করুন';
      btn.setAttribute('data-copy-label', original);
      setCopyLabel(btn, 'কপি হয়েছে');
      btn.classList.add('copied');
      btn.setAttribute('aria-label', `কপি হয়েছে ${text}`);

      const payHint = hintFromCopyButton(btn);
      document.dispatchEvent(new CustomEvent('checkout:number-copied', {
        detail: { number: text, payHint },
      }));
      setTimeout(() => {
        setCopyLabel(btn, original);
        btn.classList.remove('copied');
        btn.setAttribute('aria-label', `Copy ${text}`);
      }, 1000);
    } catch (_) {
      setCopyLabel(btn, 'ব্যর্থ');
      setTimeout(() => {
        setCopyLabel(btn, btn.getAttribute('data-copy-label') || 'কপি করুন');
      }, 1000);
    }
  },
};
