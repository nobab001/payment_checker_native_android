/**
 * Official site — theme (system default + toggle) + CMS tabs + helpline FAB.
 */
(function () {
  const THEME_KEY = 'paychek_theme';

  const ICON_SVG = {
    whatsapp:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M17.5 14.4c-.3-.1-1.6-.8-1.8-.9-.2-.1-.4-.1-.6.1-.2.2-.7.9-.8 1-.2.1-.3.2-.6.1-.3-.1-1.2-.4-2.3-1.5-1-.9-1.5-1.9-1.7-2.2-.2-.3 0-.4.1-.6.1-.1.3-.3.4-.5.1-.2.2-.3.3-.5.1-.2 0-.4 0-.5 0-.1-.6-1.5-.8-2-.2-.5-.4-.4-.6-.4h-.5c-.2 0-.5.1-.7.3-.2.3-.9.9-.9 2.1s.9 2.4 1 2.6c.1.2 1.8 2.8 4.4 3.9 1.5.7 2.1.7 2.8.6.4-.1 1.6-.6 1.8-1.3.2-.6.2-1.2.1-1.3-.1-.1-.3-.2-.6-.3z"/><path d="M12 2a10 10 0 0 0-8.7 15l-1.1 4 4.1-1.1A10 10 0 1 0 12 2zm0 18.2a8.2 8.2 0 0 1-4.2-1.1l-.3-.2-2.5.7.7-2.4-.2-.3a8.2 8.2 0 1 1 6.5 3.3z"/></svg>',
    telegram:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M9.8 14.4 9.5 18c.4 0 .6-.2.8-.4l1.9-1.8 4 2.9c.7.4 1.3.2 1.5-.7l2.7-12.7c.2-.9-.3-1.3-1-.9L3.9 10.1c-.9.3-.9.8-.2 1l4.1 1.3 9.5-6c.4-.3.8-.1.5.2l-7.9 8z"/></svg>',
    youtube:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M23 12.2s0-3.3-.4-4.9c-.2-1-1-1.8-2-2C18.8 5 12 5 12 5s-6.8 0-8.6.3c-1 .2-1.8 1-2 2C1 8.9 1 12.2 1 12.2s0 3.3.4 4.9c.2 1 1 1.8 2 2C5.2 19.4 12 19.4 12 19.4s6.8 0 8.6-.3c1-.2 1.8-1 2-2 .4-1.6.4-4.9.4-4.9zM9.8 15.5v-6.6l5.7 3.3-5.7 3.3z"/></svg>',
    facebook:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M14 9h3V6h-3c-2.2 0-4 1.8-4 4v2H8v3h2v7h3v-7h2.6l.4-3H13v-2c0-.6.4-1 1-1z"/></svg>',
    messenger:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2C6.5 2 2 6.1 2 11.2c0 2.9 1.4 5.4 3.7 7.1V22l3.4-1.9c.9.3 1.9.4 2.9.4 5.5 0 10-4.1 10-9.3S17.5 2 12 2zm1 12.4-2.5-2.7-4.9 2.7 5.4-5.7 2.6 2.7 4.8-2.7-5.4 5.7z"/></svg>',
    instagram:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7 2h10a5 5 0 0 1 5 5v10a5 5 0 0 1-5 5H7a5 5 0 0 1-5-5V7a5 5 0 0 1 5-5zm10 2H7a3 3 0 0 0-3 3v10a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3V7a3 3 0 0 0-3-3zm-5 3.5A4.5 4.5 0 1 1 7.5 12 4.5 4.5 0 0 1 12 7.5zm0 2A2.5 2.5 0 1 0 14.5 12 2.5 2.5 0 0 0 12 9.5zM17.5 6.8a1 1 0 1 1-1 1 1 1 0 0 1 1-1z"/></svg>',
    discord:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M19.3 5.2A16.7 16.7 0 0 0 15.1 4l-.3.6c1.4.3 2.7.9 3.9 1.6a12.6 12.6 0 0 0-9.4 0c1.2-.7 2.5-1.3 3.9-1.6L12.9 4A16.7 16.7 0 0 0 8.7 5.2C5.1 10.5 4.3 15.6 4.6 20.5a16.8 16.8 0 0 0 5 2.5l1-1.7a11 11 0 0 1-1.6-.8l.4-.3c3.3 1.5 6.9 1.5 10.2 0l.4.3c-.5.3-1 .6-1.6.8l1 1.7a16.8 16.8 0 0 0 5-2.5c.4-5.5-.6-10.5-4.1-15.3zM9.7 16.4c-1 0-1.8-.9-1.8-2s.8-2 1.8-2 1.9.9 1.8 2-.8 2-1.8 2zm4.6 0c-1 0-1.8-.9-1.8-2s.8-2 1.8-2 1.9.9 1.8 2-.8 2-1.8 2z"/></svg>',
    twitter:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M18.2 2H21l-6.6 7.5L22 22h-6.2l-4.9-6.4L5.4 22H2.6l7-8L2 2h6.4l4.4 5.8L18.2 2zm-1.1 18h1.7L7 3.9H5.2L17.1 20z"/></svg>',
    linkedin:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M6.9 8.8H3.7V21h3.2V8.8zM5.3 3.5a1.9 1.9 0 1 0 0 3.8 1.9 1.9 0 0 0 0-3.8zM20.3 21h-3.2v-6.2c0-1.7-.7-2.3-1.8-2.3s-2 .9-2 2.5V21H10V8.8h3.1v1.6h.1c.5-.9 1.8-1.9 3.7-1.9 2.6 0 4.4 1.6 4.4 5.2V21z"/></svg>',
    phone:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6 19.8 19.8 0 0 1-3.1-8.7A2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.3 1.8.6 2.6a2 2 0 0 1-.4 2.1L8.1 9.9a16 16 0 0 0 6 6l1.5-1.2a2 2 0 0 1 2.1-.4c.8.3 1.7.5 2.6.6a2 2 0 0 1 1.7 2z"/></svg>',
    mail:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 4h16v16H4z"/><path d="m22 6-10 7L2 6"/></svg>',
    support:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 11a9 9 0 0 1 18 0"/><path d="M21 11v2a2 2 0 0 1-2 2h-1"/><path d="M3 11v2a2 2 0 0 0 2 2h1"/><path d="M8 21h8"/><path d="M12 17v4"/></svg>',
  };

  function systemTheme() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches
      ? 'light'
      : 'dark';
  }

  function applyTheme(theme) {
    const t = theme === 'light' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', t);
    const btn = document.getElementById('themeToggle');
    if (btn) {
      btn.setAttribute('aria-label', t === 'light' ? 'Switch to dark mode' : 'Switch to light mode');
      btn.innerHTML =
        t === 'light'
          ? '<i data-lucide="moon"></i>'
          : '<i data-lucide="sun"></i>';
      if (window.lucide) lucide.createIcons({ nodes: [btn] });
    }
  }

  function initTheme() {
    const saved = localStorage.getItem(THEME_KEY);
    applyTheme(saved === 'light' || saved === 'dark' ? saved : systemTheme());

    const mq = window.matchMedia('(prefers-color-scheme: light)');
    const onChange = () => {
      if (!localStorage.getItem(THEME_KEY)) applyTheme(systemTheme());
    };
    if (mq.addEventListener) mq.addEventListener('change', onChange);
    else if (mq.addListener) mq.addListener(onChange);

    document.getElementById('themeToggle')?.addEventListener('click', () => {
      const cur = document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
      const next = cur === 'light' ? 'dark' : 'light';
      localStorage.setItem(THEME_KEY, next);
      applyTheme(next);
    });
  }

  function setText(sel, value) {
    const el = document.querySelector(sel);
    if (el && value != null) el.textContent = value;
  }

  function applyCms(content) {
    if (!content) return;
    const hero = content.hero || {};
    setText('[data-cms="hero-kicker"]', hero.kicker);
    setText('[data-cms="hero-title"]', hero.title);
    setText('[data-cms="hero-lead"]', hero.lead);
    setText('[data-cms="hero-cta-primary"]', hero.ctaPrimary);
    setText('[data-cms="hero-cta-secondary"]', hero.ctaSecondary);

    const tabs = Array.isArray(content.tabs) ? [...content.tabs].sort((a, b) => a.order - b.order) : [];
    const nav = document.getElementById('navLinks');
    if (nav) {
      const home = nav.querySelector('[data-nav="home"]');
      const test = nav.querySelector('[data-nav="test"]');
      const extras = [];
      nav.querySelectorAll('a').forEach((a) => {
        if (a.dataset.nav === 'home' || a.dataset.nav === 'test') return;
        a.remove();
      });
      tabs.forEach((tab) => {
        if (!tab.enabled) return;
        const a = document.createElement('a');
        if (tab.id === 'documentation') {
          a.href = '/docs';
          a.dataset.nav = 'documentation';
        } else {
          a.href = '#' + tab.id;
          a.dataset.nav = tab.id;
        }
        a.textContent = tab.navLabel || tab.id;
        extras.push(a);
      });
      const anchor = test || null;
      extras.forEach((a) => {
        if (anchor) nav.insertBefore(a, anchor);
        else nav.appendChild(a);
      });
      if (home && nav.firstChild !== home) nav.insertBefore(home, nav.firstChild);
    }

    tabs.forEach((tab) => {
      const section = document.getElementById(tab.id);
      if (!section) return;
      section.classList.toggle('section-is-hidden', !tab.enabled);
      const label = section.querySelector('.section-label');
      const title = section.querySelector('h2');
      const lead = section.querySelector('.section-lead');
      if (label) label.textContent = tab.sectionLabel || tab.navLabel || '';
      if (title) title.textContent = tab.title || '';
      if (lead) {
        lead.textContent = tab.lead || '';
        lead.style.display = tab.lead ? '' : 'none';
      }
      const grid = section.querySelector('[data-cms-cards]');
      if (grid && Array.isArray(tab.cards) && tab.cards.length) {
        // Keep pricing special markup if present; otherwise rebuild cards.
        if (tab.id === 'pricing' && grid.classList.contains('pricing-grid')) {
          const cards = grid.querySelectorAll('.pricing-card, .card');
          tab.cards.forEach((c, i) => {
            const el = cards[i];
            if (!el) return;
            const h = el.querySelector('strong, h3');
            if (h) h.textContent = c.title || '';
            const p = el.querySelector('p');
            if (p) p.textContent = c.body || '';
          });
        } else if (tab.id !== 'contact') {
          grid.innerHTML = tab.cards
            .map(
              (c) =>
                `<article class="card"><div class="icon"><i data-lucide="${escapeAttr(
                  c.icon || 'circle',
                )}"></i></div><h3>${escapeHtml(c.title || '')}</h3><p>${escapeHtml(
                  c.body || '',
                )}</p></article>`,
            )
            .join('');
          if (window.lucide) lucide.createIcons({ nodes: [grid] });
        } else if (tab.cards[0]) {
          const emailLine = section.querySelector('[data-cms="contact-email"]');
          if (emailLine) {
            const body = tab.cards[0].body || '';
            emailLine.innerHTML =
              'Email: <a href="mailto:' +
              escapeAttr(body) +
              '" style="color:var(--accent)">' +
              escapeHtml(body) +
              '</a>';
          }
        }
      }
    });

    // Reorder sections in main to match tab order
    const main = document.querySelector('main');
    if (main) {
      const heroEl = main.querySelector('.hero');
      tabs.forEach((tab) => {
        const sec = document.getElementById(tab.id);
        if (sec) main.appendChild(sec);
      });
      if (heroEl) main.insertBefore(heroEl, main.firstChild);
    }
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
  function escapeAttr(s) {
    return escapeHtml(s).replace(/'/g, '&#39;');
  }

  function renderHelpline(items) {
    const root = document.getElementById('helpline');
    if (!root) return;
    const list = Array.isArray(items) && items.length ? items : [];
    const stack = root.querySelector('.helpline-stack');
    const main = root.querySelector('.helpline-main');
    if (!stack || !main) return;

    stack.innerHTML = list
      .map((h) => {
        const icon = h.icon || 'whatsapp';
        const svg = ICON_SVG[icon] || ICON_SVG.support;
        const url = h.url || '#';
        return (
          `<a class="helpline-item" data-icon="${escapeAttr(icon)}" href="${escapeAttr(
            url,
          )}" target="_blank" rel="noopener noreferrer" title="${escapeAttr(h.label || icon)}">` +
          `<span>${escapeHtml(h.label || icon)}</span>` +
          `<span class="hi-btn">${svg}</span></a>`
        );
      })
      .join('');

    // Main button uses first item icon (usually WhatsApp)
    const first = list[0] || { icon: 'whatsapp' };
    main.innerHTML = ICON_SVG[first.icon] || ICON_SVG.whatsapp;
    main.classList.toggle('is-open', false);
    root.classList.remove('is-open');

    main.onclick = (e) => {
      e.preventDefault();
      // Single item → open link directly; multiple → expand
      if (list.length <= 1 && list[0]?.url) {
        window.open(list[0].url, '_blank', 'noopener,noreferrer');
        return;
      }
      root.classList.toggle('is-open');
      main.classList.toggle('is-open', root.classList.contains('is-open'));
    };

    document.addEventListener('click', (ev) => {
      if (!root.contains(ev.target)) {
        root.classList.remove('is-open');
        main.classList.remove('is-open');
      }
    });
  }

  window.PaychekSite = {
    init() {
      initTheme();
      const isHome = document.body?.dataset?.page === 'home';
      fetch('/api/official/site', { credentials: 'same-origin' })
        .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
        .then((data) => {
          if (!data?.success || !data.content) throw new Error('bad cms');
          if (isHome) applyCms(data.content);
          renderHelpline(data.content.helpline);
        })
        .catch((err) => {
          console.warn('[PaychekSite] CMS load failed', err);
          renderHelpline([
            { icon: 'whatsapp', label: 'WhatsApp', url: 'https://wa.me/8801700000000' },
          ]);
        });
    },
    ICON_SVG,
  };
})();
