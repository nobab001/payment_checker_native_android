/** Material-style motion — grid accordion + fade/FLIP, 220ms default. */

export const DURATION_MS = 220;

export function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

export function fadeIn(el, duration = DURATION_MS) {
  if (!el) return Promise.resolve();
  if (prefersReducedMotion()) {
    el.style.opacity = '1';
    return Promise.resolve();
  }
  el.style.transition = `opacity ${duration}ms ease`;
  el.style.opacity = '0';
  return new Promise((resolve) => {
    requestAnimationFrame(() => {
      el.style.opacity = '1';
      setTimeout(resolve, duration);
    });
  });
}

export function fadeOut(el, duration = DURATION_MS) {
  if (!el) return Promise.resolve();
  if (prefersReducedMotion()) {
    el.style.opacity = '0';
    return Promise.resolve();
  }
  el.style.transition = `opacity ${duration}ms ease`;
  return new Promise((resolve) => {
    el.style.opacity = '0';
    setTimeout(resolve, duration);
  });
}

/** CSS grid 0fr→1fr accordion — GPU-friendly, no scrollHeight thrash. */
export function expandPanel(panel, duration = DURATION_MS) {
  if (!panel) return Promise.resolve();
  panel.setAttribute('aria-hidden', 'false');
  if (prefersReducedMotion()) {
    panel.classList.add('is-open');
    return Promise.resolve();
  }
  panel.classList.add('is-open');
  return new Promise((resolve) => setTimeout(resolve, duration));
}

export function collapsePanel(panel, duration = DURATION_MS) {
  if (!panel) return Promise.resolve();
  panel.setAttribute('aria-hidden', 'true');
  panel.classList.remove('is-open');
  if (prefersReducedMotion()) return Promise.resolve();
  return new Promise((resolve) => setTimeout(resolve, duration));
}

export function flipReorder(container, selector, orderedIds, duration = DURATION_MS) {
  if (!container || prefersReducedMotion()) return;
  const items = [...container.querySelectorAll(selector)];
  const first = new Map(items.map((el) => [el.getAttribute('data-provider-id'), el.getBoundingClientRect()]));

  orderedIds.forEach((id) => {
    const el = container.querySelector(`${selector}[data-provider-id="${id}"]`);
    if (el) container.appendChild(el);
  });

  items.forEach((el) => {
    const id = el.getAttribute('data-provider-id');
    const a = first.get(id);
    const b = el.getBoundingClientRect();
    if (!a) return;
    const dy = a.top - b.top;
    if (Math.abs(dy) < 1) return;
    el.style.transition = 'none';
    el.style.transform = `translate3d(0,${dy}px,0)`;
    requestAnimationFrame(() => {
      el.style.transition = `transform ${duration}ms ease`;
      el.style.transform = '';
    });
  });
}
