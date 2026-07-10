/** Skeleton loader — no spinners on initial checkout load. */

let el = null;

export const SkeletonController = {
  show() {
    this.hide();
    el = document.createElement('div');
    el.id = 'checkout-skeleton';
    el.className = 'checkout-skeleton';
    el.setAttribute('aria-busy', 'true');
    el.setAttribute('aria-label', 'Loading checkout');
    el.innerHTML = `
      <div class="skel skel-header"></div>
      <div class="skel skel-tabs"></div>
      <div class="skel skel-card"></div>
      <div class="skel skel-card"></div>
      <div class="skel skel-row"></div>
      <div class="skel skel-row"></div>`;
    const main = document.getElementById('checkout-main');
    const page = document.querySelector('.page');
    if (main) {
      main.classList.add('hidden');
      main.before(el);
    } else if (page) {
      page.appendChild(el);
    }
  },

  hide() {
    el?.remove();
    el = null;
  },
};
