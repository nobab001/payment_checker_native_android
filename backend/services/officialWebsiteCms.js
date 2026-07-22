/**
 * Official marketing site CMS — tabs + helpline stored in global_config.
 */
const prisma = require('../db/prisma');

const CONFIG_KEY = 'official_website_cms';

const HELPLINE_ICON_IDS = [
  'whatsapp',
  'telegram',
  'youtube',
  'facebook',
  'messenger',
  'instagram',
  'discord',
  'twitter',
  'linkedin',
  'phone',
  'mail',
  'support',
];

const DEFAULT_CMS = Object.freeze({
  hero: {
    kicker: 'Real-time payment verification',
    title: 'PayCheck',
    lead:
      'The payment layer for Bangladesh merchants — verify bKash, Nagad, Rocket and more from SMS, with checkout that feels as polished as Stripe.',
    ctaPrimary: 'Open Test Experience',
    ctaSecondary: 'Explore features',
  },
  tabs: [
    {
      id: 'features',
      enabled: true,
      order: 0,
      navLabel: 'Features',
      sectionLabel: 'Features',
      title: 'Built for speed and trust',
      lead: 'Everything you need to accept mobile banking payments without reconciling spreadsheets.',
      cards: [
        {
          icon: 'message-square',
          title: 'SMS Payment Monitor',
          body: 'Device agents capture and match transaction SMS with millisecond precision and secure HMAC ingest.',
        },
        {
          icon: 'layout-template',
          title: 'Premium Checkout',
          body: 'Design 1–3, Hybrid / Transaction / Vibe modes — one API, many experiences.',
        },
        {
          icon: 'webhook',
          title: 'Merchant Callbacks',
          body: 'Signed webhooks, idempotent sessions, and an outbox that retries until delivery succeeds.',
        },
      ],
    },
    {
      id: 'solutions',
      enabled: true,
      order: 1,
      navLabel: 'Solutions',
      sectionLabel: 'Solutions',
      title: 'Modes that match your storefront',
      lead: 'Choose how customers prove payment — or let them pick on Hybrid checkout.',
      cards: [
        {
          icon: 'hash',
          title: 'Transaction Mode',
          body: 'Customer pays, enters TrxID, PayCheck verifies against live SMS history.',
        },
        {
          icon: 'sparkles',
          title: 'Vibe Mode',
          body: 'No TrxID typing — soft matching on amount + timing for frictionless checkout.',
        },
        {
          icon: 'layers',
          title: 'Hybrid Mode',
          body: 'Transaction + Vibe + optional live provider tabs in a single checkout shell.',
        },
      ],
    },
    {
      id: 'pricing',
      enabled: true,
      order: 2,
      navLabel: 'Pricing',
      sectionLabel: 'Pricing',
      title: 'Simple plans that scale',
      lead: 'Start with a trial, upgrade when volume grows. Exact packages are managed in the PayCheck app.',
      cards: [
        {
          icon: 'check',
          title: 'Trial',
          body: 'Free start · Full checkout preview · Device monitoring · Official Test Experience',
        },
        {
          icon: 'check',
          title: 'Growth',
          body: 'App / in-product · Multi-website gateways · API Center & webhooks · Priority support',
        },
        {
          icon: 'check',
          title: 'Enterprise',
          body: 'Custom · Dedicated capacity · Custom integrations · SLA & onboarding',
        },
      ],
    },
    {
      id: 'documentation',
      enabled: true,
      order: 3,
      navLabel: 'Documentation',
      sectionLabel: 'Documentation',
      title: 'Developer guide — A to Z',
      lead:
        'Website Purpose, HMAC pay/init, Add Balance vs Payment, webhooks, settlement, and framework samples — one place for integrators.',
      cards: [
        {
          icon: 'flag',
          title: 'Purpose lock',
          body: 'Add Balance, Payment, or Both — confirm once, then lock. Super Admin unlock only.',
        },
        {
          icon: 'file-code-2',
          title: 'Signed init',
          body: 'POST /api/v1/pay/init with X-Signature HMAC — Node, PHP, Laravel, Python, Go, and more.',
        },
        {
          icon: 'book-open',
          title: 'Full docs',
          body: 'Step-by-step callbacks, walletCredit, multi-Trx settlement, and unlock rules.',
        },
      ],
    },
    {
      id: 'resources',
      enabled: true,
      order: 4,
      navLabel: 'Resources',
      sectionLabel: 'Resources',
      title: 'Operate with confidence',
      lead: '',
      cards: [
        {
          icon: 'shield',
          title: 'Security',
          body: 'HMAC request signing, hashed secrets, device binding, and audit-friendly event logs.',
        },
        {
          icon: 'battery-charging',
          title: 'Background Guard',
          body: 'Accessibility + battery optimization guidance so SMS capture stays reliable.',
        },
        {
          icon: 'smartphone',
          title: 'Android App',
          body: 'Merchant control plane for devices, websites, billing, and checkout designer.',
        },
      ],
    },
    {
      id: 'contact',
      enabled: true,
      order: 5,
      navLabel: 'Contact',
      sectionLabel: 'Contact',
      title: 'Let’s build your checkout',
      lead: 'Reach the PayCheck team for onboarding, enterprise packaging, or integration help.',
      cards: [
        {
          icon: 'mail',
          title: 'Email',
          body: 'helaldada510@gmail.com',
        },
      ],
    },
  ],
  helpline: [
    {
      id: 'hl_default_wa',
      icon: 'whatsapp',
      label: 'WhatsApp',
      url: 'https://wa.me/8801700000000',
      sortOrder: 0,
    },
  ],
});

function deepClone(obj) {
  return JSON.parse(JSON.stringify(obj));
}

function sanitizeCard(c) {
  if (!c || typeof c !== 'object') return null;
  return {
    icon: String(c.icon || 'circle').slice(0, 48),
    title: String(c.title || '').slice(0, 120),
    body: String(c.body || '').slice(0, 2000),
  };
}

function sanitizeTab(t, index) {
  if (!t || typeof t !== 'object') return null;
  const id = String(t.id || `tab_${index}`).replace(/[^a-z0-9_-]/gi, '').slice(0, 40) || `tab_${index}`;
  const cards = Array.isArray(t.cards)
    ? t.cards.map(sanitizeCard).filter(Boolean).slice(0, 12)
    : [];
  return {
    id,
    enabled: t.enabled !== false && t.enabled !== 0,
    order: Number.isFinite(Number(t.order)) ? Number(t.order) : index,
    navLabel: String(t.navLabel || id).slice(0, 40),
    sectionLabel: String(t.sectionLabel || t.navLabel || id).slice(0, 60),
    title: String(t.title || '').slice(0, 160),
    lead: String(t.lead || '').slice(0, 2000),
    cards,
  };
}

function sanitizeHelplineItem(h, index) {
  if (!h || typeof h !== 'object') return null;
  let icon = String(h.icon || 'whatsapp').toLowerCase().slice(0, 32);
  if (!HELPLINE_ICON_IDS.includes(icon)) icon = 'support';
  let url = String(h.url || '').trim().slice(0, 500);
  if (url && !/^https?:\/\//i.test(url) && !/^mailto:/i.test(url) && !/^tel:/i.test(url)) {
    url = `https://${url}`;
  }
  return {
    id: String(h.id || `hl_${Date.now()}_${index}`).slice(0, 64),
    icon,
    label: String(h.label || icon).slice(0, 60),
    url,
    sortOrder: Number.isFinite(Number(h.sortOrder)) ? Number(h.sortOrder) : index,
  };
}

function normalizeCms(raw) {
  const base = deepClone(DEFAULT_CMS);
  if (!raw || typeof raw !== 'object') return base;

  if (raw.hero && typeof raw.hero === 'object') {
    base.hero = {
      kicker: String(raw.hero.kicker ?? base.hero.kicker).slice(0, 120),
      title: String(raw.hero.title ?? base.hero.title).slice(0, 80),
      lead: String(raw.hero.lead ?? base.hero.lead).slice(0, 2000),
      ctaPrimary: String(raw.hero.ctaPrimary ?? base.hero.ctaPrimary).slice(0, 80),
      ctaSecondary: String(raw.hero.ctaSecondary ?? base.hero.ctaSecondary).slice(0, 80),
    };
  }

  if (Array.isArray(raw.tabs) && raw.tabs.length) {
    const tabs = raw.tabs.map(sanitizeTab).filter(Boolean);
    tabs.sort((a, b) => a.order - b.order || a.navLabel.localeCompare(b.navLabel));
    tabs.forEach((t, i) => {
      t.order = i;
    });
    if (tabs.length) base.tabs = tabs;
  }

  if (Array.isArray(raw.helpline)) {
    const items = raw.helpline.map(sanitizeHelplineItem).filter(Boolean);
    items.sort((a, b) => a.sortOrder - b.sortOrder);
    items.forEach((h, i) => {
      h.sortOrder = i;
    });
    base.helpline = items.length ? items : deepClone(DEFAULT_CMS.helpline);
  }

  return base;
}

async function loadOfficialWebsiteCms() {
  try {
    const row = await prisma.global_config.findUnique({ where: { config_key: CONFIG_KEY } });
    if (row?.config_value) {
      try {
        return normalizeCms(JSON.parse(row.config_value));
      } catch (_) {
        /* fall through */
      }
    }
  } catch (err) {
    console.warn('[OfficialWebsiteCms] load failed:', err.message);
  }
  return deepClone(DEFAULT_CMS);
}

async function saveOfficialWebsiteCms(incoming) {
  const cms = normalizeCms(incoming);
  await prisma.global_config.upsert({
    where: { config_key: CONFIG_KEY },
    create: { config_key: CONFIG_KEY, config_value: JSON.stringify(cms) },
    update: { config_value: JSON.stringify(cms) },
  });
  return cms;
}

module.exports = {
  CONFIG_KEY,
  HELPLINE_ICON_IDS,
  DEFAULT_CMS,
  loadOfficialWebsiteCms,
  saveOfficialWebsiteCms,
  normalizeCms,
};
