/** Live payment click delegation — verify/vibe logic untouched. */

export const LiveController = {
  bind(root, onLivePay) {
    if (!root || !onLivePay) return;
    root.addEventListener('click', (e) => {
      const el = e.target.closest('[data-live]');
      if (!el) return;
      e.preventDefault();
      onLivePay(el.getAttribute('data-live'));
    });
  },
};
