/** Live payment click delegation — verify/vibe logic untouched. */

export const LiveController = {
  bind(root, onLivePay) {
    if (!root || !onLivePay) return;
    root.addEventListener('click', (e) => {
      const el = e.target.closest('[data-live]');
      if (!el) return;
      e.preventDefault();
      const provider = el.getAttribute('data-live');
      const midRaw = el.getAttribute('data-merchant-id');
      const merchantAccountId = midRaw != null && midRaw !== '' ? parseInt(midRaw, 10) : null;
      onLivePay(provider, Number.isFinite(merchantAccountId) ? merchantAccountId : null);
    });
  },
};
