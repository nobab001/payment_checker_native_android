/**
 * PayCheck Official Web Interface — Core JS Engine (Phase 0 - Phase 4)
 * Contains premium parallax mouse tracking, infinite marques, real-time verification timelines,
 * live activity feeds, multi-language i18n switcher, and OTP portal setup.
 */
(function () {
  const THEME_KEY = 'paychek_theme';
  const LANG_KEY = 'paychek_lang';

  // Fallback translation databases in case async files fail
  let i18n = {};

  const MOCK_COMPANIES = [
    { name: "HostBD", logo_url: "https://paycheckbd.com/assets/brand/favicon-48.png", website_url: "https://hostbd.net", industry: "Hosting", country: "BD", merchant_since: "2024", is_verified: 1 },
    { name: "Alpha Tech", logo_url: "https://paycheckbd.com/assets/brand/favicon-48.png", website_url: "#", industry: "Software", country: "BD", merchant_since: "2025", is_verified: 1 },
    { name: "Dhaka Store", logo_url: "https://paycheckbd.com/assets/brand/favicon-48.png", website_url: "#", industry: "E-Commerce", country: "BD", merchant_since: "2024", is_verified: 1 },
    { name: "Bengal Soft", logo_url: "https://paycheckbd.com/assets/brand/favicon-48.png", website_url: "#", industry: "FinTech", country: "BD", merchant_since: "2023", is_verified: 1 }
  ];

  const MOCK_REVIEWS = [
    { rating: 5, author_name: "Tanvir Rahman", company: "HostBD CEO", merchant_type: "Enterprise", country_flag: "🇧🇩", comment: "PayChek integration has cut our invoice reconciliation time to zero. The client checkout Vibe mode is phenomenal!", helpful_count: 12, admin_reply: "Thank you Tanvir! Glad to support HostBD scaling.", status: "approved" },
    { rating: 5, author_name: "Sajjad Hossain", company: "Bengal Digital", merchant_type: "Standard", country_flag: "🇧🇩", comment: "We were looking for a reliable bKash automation. PayChek's signed webhooks works like a charm. 10/10 recommended.", helpful_count: 8, admin_reply: null, status: "approved" }
  ];

  const ICON_SVG = {
    whatsapp:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M17.5 14.4c-.3-.1-1.6-.8-1.8-.9-.2-.1-.4-.1-.6.1-.2.2-.7.9-.8 1-.2.1-.3.2-.6.1-.3-.1-1.2-.4-2.3-1.5-1-.9-1.5-1.9-1.7-2.2-.2-.3 0-.4.1-.6.1-.1.3-.3.4-.5.1-.2.2-.3.3-.5.1-.2 0-.4 0-.5 0-.1-.6-1.5-.8-2-.2-.5-.4-.4-.6-.4h-.5c-.2 0-.5.1-.7.3-.2.3-.9.9-.9 2.1s.9 2.4 1 2.6c.1.2 1.8 2.8 4.4 3.9 1.5.7 2.1.7 2.8.6.4-.1 1.6-.6 1.8-1.3.2-.6.2-1.2.1-1.3-.1-.1-.3-.2-.6-.3z"/><path d="M12 2a10 10 0 0 0-8.7 15l-1.1 4 4.1-1.1A10 10 0 1 0 12 2zm0 18.2a8.2 8.2 0 0 1-4.2-1.1l-.3-.2-2.5.7.7-2.4-.2-.3a8.2 8.2 0 1 1 6.5 3.3z"/></svg>',
    phone:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6 19.8 19.8 0 0 1-3.1-8.7A2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.3 1.8.6 2.6a2 2 0 0 1-.4 2.1L8.1 9.9a16 16 0 0 0 6 6l1.5-1.2a2 2 0 0 1 2.1-.4c.8.3 1.7.5 2.6.6a2 2 0 0 1 1.7 2z"/></svg>',
    mail:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 4h16v16H4z"/><path d="m22 6-10 7L2 6"/></svg>',
    support:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 11a9 9 0 0 1 18 0"/><path d="M21 11v2a2 2 0 0 1-2 2h-1"/><path d="M3 11v2a2 2 0 0 0 2 2h1"/><path d="M8 21h8"/><path d="M12 17v4"/></svg>'
  };

  // Setup i18n system
  async function loadLocales() {
    const lang = localStorage.getItem(LANG_KEY) || (navigator.language.startsWith('bn') ? 'bn' : 'en');
    document.documentElement.lang = lang;
    try {
      const res = await fetch(`/locales/${lang}/common.json`);
      if (res.ok) {
        i18n = await res.json();
      }
    } catch (_) {
      // Fallback english
      i18n = {};
    }
  }

  function t(path, fallback = '') {
    const parts = path.split('.');
    let cur = i18n;
    for (const p of parts) {
      if (cur && typeof cur === 'object') cur = cur[p];
      else return fallback;
    }
    return cur || fallback;
  }

  // Theme Manager
  function systemTheme() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  }

  function applyTheme(theme) {
    const t = theme === 'light' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', t);
    const btn = document.getElementById('themeToggle');
    if (btn) {
      btn.setAttribute('aria-label', t === 'light' ? 'Switch to dark mode' : 'Switch to light mode');
      btn.innerHTML = t === 'light' ? '<i data-lucide="moon"></i>' : '<i data-lucide="sun"></i>';
      if (window.lucide) lucide.createIcons({ nodes: [btn] });
    }
  }

  function initTheme() {
    const saved = localStorage.getItem(THEME_KEY);
    applyTheme(saved === 'light' || saved === 'dark' ? saved : systemTheme());
    document.getElementById('themeToggle')?.addEventListener('click', () => {
      const cur = document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
      const next = cur === 'light' ? 'dark' : 'light';
      localStorage.setItem(THEME_KEY, next);
      applyTheme(next);
    });
  }

  // Navigation glass scrollspy & Hide-on-scroll-down
  let lastScrollY = window.scrollY;
  function initNavigationScroll() {
    const navbar = document.querySelector('.nav');
    if (!navbar) return;
    
    // Create progress bar at the very top of navbar
    const progressBar = document.createElement('div');
    progressBar.className = 'scroll-progress';
    navbar.appendChild(progressBar);

    window.addEventListener('scroll', () => {
      const currentScrollY = window.scrollY;
      const docHeight = document.documentElement.scrollHeight - window.innerHeight;
      const progress = (currentScrollY / (docHeight || 1)) * 100;
      progressBar.style.width = `${progress}%`;

      // Hide or show navbar based on scroll direction
      if (currentScrollY > 150) {
        navbar.classList.add('nav-scrolled');
        if (currentScrollY > lastScrollY) {
          navbar.classList.add('nav-hidden');
        } else {
          navbar.classList.remove('nav-hidden');
        }
      } else {
        navbar.classList.remove('nav-scrolled', 'nav-hidden');
      }
      lastScrollY = currentScrollY;
    }, { passive: true });
  }

  // Trusted Companies marquee builder
  async function loadTrustedCompanies() {
    const marquee = document.querySelector('.trusted-marquee-track');
    if (!marquee) return;
    try {
      const res = await fetch('/api/official/companies');
      const data = await res.json();
      const list = data?.success && data.companies?.length ? data.companies : MOCK_COMPANIES;
      
      marquee.innerHTML = list.map(c => `
        <a class="marquee-card" href="${escapeAttr(c.website_url || '#')}" target="_blank" rel="noopener noreferrer">
          <img src="${escapeAttr(c.logo_url)}" alt="${escapeAttr(c.name)}" loading="lazy" />
          <div class="company-meta">
            <strong>${escapeHtml(c.name)}</strong>
            <span>${escapeHtml(c.industry || 'MFS')} · ${escapeHtml(c.country || 'BD')}</span>
          </div>
          ${c.is_verified ? '<span class="verified-badge" title="Verified Merchant">✓</span>' : ''}
        </a>
      `).join('') + marquee.innerHTML; // duplicate to guarantee seamless loop
    } catch (_) {
      // Graceful fallback
    }
  }

  // Testimonials Reviews grid builder with public form
  async function loadReviews() {
    const grid = document.querySelector('.reviews-grid');
    if (!grid) return;
    try {
      const res = await fetch('/api/official/reviews');
      const data = await res.json();
      const list = data?.success && data.reviews?.length ? data.reviews : MOCK_REVIEWS;

      grid.innerHTML = list.map(r => `
        <div class="review-card ${r.status === 'pinned' ? 'pinned' : ''}">
          <div class="rating-row">
            ${'★'.repeat(r.rating)}${'☆'.repeat(5 - r.rating)}
          </div>
          <p class="review-comment">"${escapeHtml(r.comment)}"</p>
          <div class="review-author">
            <span class="flag">${escapeHtml(r.country_flag || '🇧🇩')}</span>
            <div>
              <strong>${escapeHtml(r.author_name)}</strong>
              <span>${escapeHtml(r.company || 'Merchant')} · ${escapeHtml(r.merchant_type || 'Standard')}</span>
            </div>
          </div>
          ${r.admin_reply ? `
            <div class="admin-reply">
              <strong>PayCheck Support:</strong>
              <p>${escapeHtml(r.admin_reply)}</p>
            </div>
          ` : ''}
        </div>
      `).join('');
    } catch (_) {}
  }

  // FAQ Accordion Setup
  function initFaqAccordion() {
    document.querySelectorAll('.faq-item').forEach(item => {
      const header = item.querySelector('.faq-header');
      header.addEventListener('click', () => {
        const isOpen = item.classList.contains('active');
        document.querySelectorAll('.faq-item').forEach(i => i.classList.remove('active'));
        if (!isOpen) item.classList.add('active');
      });
    });
  }

  // Interactive Live Verification Timeline widget
  function initVerificationWidget() {
    const input = document.getElementById('demoTrxId');
    const button = document.getElementById('demoVerifyBtn');
    const timeline = document.getElementById('demoTimeline');
    if (!button || !timeline) return;

    const steps = [
      { key: 'request', label: 'Request Sent' },
      { key: 'gateway', label: 'Gateway Syncing' },
      { key: 'verification', label: 'Verification Engine Match' },
      { key: 'db', label: 'Database Match Successful' },
      { key: 'completed', label: 'Completed & Settled' }
    ];

    button.addEventListener('click', async () => {
      const val = input?.value?.trim() || 'TRX897126BD';
      button.disabled = true;
      timeline.innerHTML = '';
      timeline.style.display = 'block';

      for (let i = 0; i < steps.length; i++) {
        const step = steps[i];
        const stepEl = document.createElement('div');
        stepEl.className = 'timeline-step current';
        stepEl.innerHTML = `
          <div class="step-icon"><div class="spinner"></div></div>
          <div class="step-content">
            <strong>${t('widget.steps.' + step.key, step.label)}</strong>
            <span>Processing...</span>
          </div>
        `;
        timeline.appendChild(stepEl);
        
        // Scroll to the bottom of timeline
        stepEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

        await new Promise(r => setTimeout(r, 900));

        // Mark current step as completed
        stepEl.className = 'timeline-step done';
        stepEl.querySelector('.step-icon').innerHTML = '✓';
        stepEl.querySelector('span').textContent = 'Completed';
      }

      // Append success final card
      const finalCard = document.createElement('div');
      finalCard.className = 'timeline-success-card';
      finalCard.innerHTML = `
        <div class="success-header">✓ verified</div>
        <div class="success-row"><span>Amount:</span> <strong>৳1,250.00</strong></div>
        <div class="success-row"><span>Method:</span> <strong>bKash Personal</strong></div>
        <div class="success-row"><span>ID:</span> <strong>${escapeHtml(val)}</strong></div>
        <div class="success-row"><span>Latency:</span> <strong>1.82s (99.9% Uptime)</strong></div>
      `;
      timeline.appendChild(finalCard);
      finalCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      button.disabled = false;
    });
  }

  // Live activity notifications feed loop
  function initLiveActivityFeed() {
    const panel = document.createElement('div');
    panel.className = 'live-activity-feed';
    document.body.appendChild(panel);

    const feeds = [
      { method: 'bKash', amount: '৳১২৫০', time: '২ সেকেন্ড আগে' },
      { method: 'Nagad', amount: '৳৫০০', time: '১২ সেকেন্ড আগে' },
      { method: 'Rocket', amount: '৳৩০০০', time: '১ মিনিট আগে' },
      { method: 'Upay', amount: '৳৪৫০', time: '২ মিনিট আগে' }
    ];

    let index = 0;
    setInterval(() => {
      const item = feeds[index];
      panel.innerHTML = `
        <div class="feed-item-content">
          <span class="feed-badge">✓ verified</span>
          <span>পেমেন্ট সফল: <strong>${item.amount}</strong> (${item.method})</span>
          <span class="feed-time">${item.time}</span>
        </div>
      `;
      panel.classList.add('visible');
      setTimeout(() => {
        panel.classList.remove('visible');
      }, 3500);

      index = (index + 1) % feeds.length;
    }, 5500);
  }

  // Subtle Mouse Parallax hero tracking
  function initHeroParallax() {
    const hero = document.querySelector('.hero');
    const visual = document.querySelector('.hero-visual-wrapper');
    if (!hero || !visual) return;

    hero.addEventListener('mousemove', (e) => {
      const rect = hero.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width - 0.5;
      const y = (e.clientY - rect.top) / rect.height - 0.5;
      
      visual.style.transform = `perspective(1000px) rotateY(${x * 12}deg) rotateX(${-y * 12}deg) translateZ(10px)`;
    });

    hero.addEventListener('mouseleave', () => {
      visual.style.transform = 'perspective(1000px) rotateY(0deg) rotateX(0deg) translateZ(0)';
    });
  }

  // Help support pipeline WhatsApp and helpline FAB
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

    const first = list[0] || { icon: 'whatsapp' };
    main.innerHTML = ICON_SVG[first.icon] || ICON_SVG.whatsapp;
    main.classList.toggle('is-open', false);
    root.classList.remove('is-open');

    main.onclick = (e) => {
      e.preventDefault();
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

  function applyDownload(download) {
    const cfg = download && typeof download === 'object' ? download : {};
    const enabled = !(cfg.enabled === false || cfg.enabled === 0);
    const url = cfg.url || '/downloads/paycheck.apk';
    const labelText = cfg.label || 'Download App';

    document.querySelectorAll('[data-cms-download-href]').forEach((el) => {
      el.setAttribute('href', url);
      if (!enabled) {
        el.classList.add('is-hidden');
        el.setAttribute('aria-hidden', 'true');
      } else {
        el.classList.remove('is-hidden');
        if (!el.classList.contains('app-download-dock')) {
          el.removeAttribute('aria-hidden');
        }
      }
    });

    document.querySelectorAll('[data-cms="download-label"], [data-cms="download-label-hero"]').forEach((label) => {
      label.textContent = labelText;
    });
  }

  function initDownloadMorph() {
    const hero = document.getElementById('appDownloadFabHero');
    const dock = document.getElementById('appDownloadFabDock');
    if (!dock) return;

    const isHome = document.body?.dataset?.page === 'home';
    const isMobile = () => window.matchMedia('(max-width: 720px)').matches;

    const showDockOnly = () => {
      if (hero) {
        hero.classList.add('is-hidden');
        hero.setAttribute('aria-hidden', 'true');
        hero.style.opacity = '0';
        hero.style.pointerEvents = 'none';
      }
      dock.style.opacity = '1';
      dock.style.pointerEvents = 'auto';
      dock.removeAttribute('aria-hidden');
    };

    const applyMobileIconMode = (scrolled) => {
      showDockOnly();
      dock.classList.toggle('is-icon', scrolled);
    };

    if (!isHome) {
      const sync = () => {
        if (isMobile()) {
          applyMobileIconMode(window.scrollY > 2);
        } else {
          showDockOnly();
          dock.classList.remove('is-icon');
        }
      };
      window.addEventListener('scroll', sync, { passive: true });
      window.addEventListener('resize', sync, { passive: true });
      sync();
      return;
    }

    const range = 80;
    window.addEventListener('scroll', () => {
      const y = window.scrollY || 0;
      if (isMobile()) {
        applyMobileIconMode(y > 2);
        return;
      }
      const t = Math.min(1, Math.max(0, y / range));
      if (hero) {
        hero.style.opacity = String(1 - t);
        hero.style.pointerEvents = t > 0.85 ? 'none' : 'auto';
      }
      dock.style.opacity = String(t);
      dock.style.pointerEvents = t < 0.15 ? 'none' : 'auto';
    }, { passive: true });
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

  window.PaychekSite = {
    async init() {
      initTheme();
      await loadLocales();
      initNavigationScroll();
      initDownloadMorph();
      initVerificationWidget();
      initLiveActivityFeed();
      initHeroParallax();
      initFaqAccordion();
      
      const isHome = document.body?.dataset?.page === 'home';
      fetch('/api/official/site', { credentials: 'same-origin' })
        .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
        .then(async (data) => {
          if (!data?.success || !data.content) throw new Error('bad cms');
          
          renderHelpline(data.content.helpline);
          applyDownload(data.content.download);
          
          await loadTrustedCompanies();
          await loadReviews();
        })
        .catch((err) => {
          console.warn('[PaychekSite] CMS load failed', err);
          renderHelpline([
            { icon: 'whatsapp', label: 'WhatsApp', url: 'https://wa.me/8801700000000' },
          ]);
          applyDownload({
            enabled: true,
            label: 'Download App',
            url: '/downloads/paycheck.apk',
          });
          loadTrustedCompanies();
          loadReviews();
        });

      // Handle Review Submission form
      const reviewForm = document.getElementById('publicReviewForm');
      if (reviewForm) {
        reviewForm.addEventListener('submit', async (e) => {
          e.preventDefault();
          const btn = reviewForm.querySelector('button[type="submit"]');
          btn.disabled = true;
          try {
            const res = await fetch('/api/official/reviews', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                author_name: reviewForm.author_name.value,
                company: reviewForm.company.value,
                merchant_type: reviewForm.merchant_type.value,
                rating: parseInt(reviewForm.rating.value, 10),
                comment: reviewForm.comment.value,
                country_flag: "🇧🇩"
              })
            });
            const data = await res.json();
            if (data.success) {
              alert('রিভিউ সফলভাবে জমা দেওয়া হয়েছে এবং অনুমোদনের অপেক্ষায় রয়েছে। ধন্যবাদ!');
              reviewForm.reset();
            } else {
              alert('ভুল হয়েছে: ' + data.error);
            }
          } catch (err) {
            alert('কমিউনিকেশন এরর: ' + err.message);
          } finally {
            btn.disabled = false;
          }
        });
      }
    }
  };
})();
