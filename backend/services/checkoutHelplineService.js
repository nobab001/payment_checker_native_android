/**
 * Checkout helpline — single entry with icon prefix URLs.
 */

const ICONS = new Set([
  'whatsapp', 'telegram', 'facebook', 'messenger', 'phone', 'mail', 'support', 'instagram',
]);

const PREFIX = {
  whatsapp: 'https://wa.me/',
  telegram: 'https://t.me/',
  facebook: 'https://facebook.com/',
  messenger: 'https://m.me/',
  instagram: 'https://instagram.com/',
  mail: 'mailto:',
  phone: 'tel:',
  support: '',
};

function digitsOnly(s) {
  return String(s || '').replace(/\D/g, '');
}

function stripPrefix(icon, raw) {
  const v = String(raw || '').trim();
  if (!v) return '';
  if (/^https?:\/\//i.test(v) || v.startsWith('mailto:') || v.startsWith('tel:')) {
    const p = PREFIX[icon] || '';
    if (p && v.toLowerCase().startsWith(p.toLowerCase())) {
      return v.slice(p.length).replace(/^\/+/, '');
    }
    if (icon === 'whatsapp' && v.includes('wa.me/')) {
      return digitsOnly(v.split('wa.me/').pop());
    }
    if (icon === 'telegram' && v.includes('t.me/')) {
      return v.split('t.me/').pop().replace(/^\/+/, '');
    }
    return v;
  }
  return v;
}

function buildUrl(icon, value) {
  const v = String(value || '').trim();
  if (!v) return '';
  if (/^https?:\/\//i.test(v) || v.startsWith('mailto:') || v.startsWith('tel:')) return v;

  switch (icon) {
    case 'whatsapp': {
      const d = digitsOnly(v);
      return d ? `https://wa.me/${d}` : '';
    }
    case 'telegram': {
      const slug = v.replace(/^@/, '').trim();
      return slug ? `https://t.me/${slug}` : '';
    }
    case 'phone': {
      const d = digitsOnly(v);
      return d ? `tel:+${d.replace(/^880/, '880')}` : '';
    }
    case 'mail':
      return `mailto:${v}`;
    case 'facebook':
      return `https://facebook.com/${v.replace(/^\/+/, '')}`;
    case 'messenger':
      return `https://m.me/${v.replace(/^\/+/, '')}`;
    case 'instagram':
      return `https://instagram.com/${v.replace(/^@/, '').replace(/^\/+/, '')}`;
    default:
      return v;
  }
}

/** Normalize stored layout_config.checkout_helpline (object or legacy array). */
function normalizeHelplineConfig(raw) {
  if (!raw) {
    return { enabled: false, icon: 'whatsapp', label: '', value: '', url: '' };
  }
  let src = raw;
  if (Array.isArray(raw)) {
    src = raw[0] || {};
    if (!src.enabled && src.url) src = { ...src, enabled: true };
  }
  if (typeof src !== 'object') {
    return { enabled: false, icon: 'whatsapp', label: '', value: '', url: '' };
  }
  const icon = ICONS.has(String(src.icon || '').toLowerCase())
    ? String(src.icon).toLowerCase()
    : 'whatsapp';
  const value = stripPrefix(icon, src.value || src.url || src.link || '');
  const label = String(src.label || src.title || '').trim();
  const enabled = src.enabled === true || src.enabled === 1 || src.isActive === true || src.is_active === 1;
  const url = buildUrl(icon, value);
  return { enabled: enabled && !!url, icon, label, value, url };
}

/** Customer checkout payload — array with 0–1 link (backward compatible). */
function parseHelplineForCheckout(raw) {
  const cfg = normalizeHelplineConfig(raw);
  if (!cfg.enabled || !cfg.url) return [];
  return [{
    icon: cfg.icon,
    label: cfg.label || '',
    url: cfg.url,
  }];
}

function prefixForIcon(icon) {
  return PREFIX[icon] || '';
}

module.exports = {
  normalizeHelplineConfig,
  parseHelplineForCheckout,
  buildUrl,
  prefixForIcon,
  PREFIX,
};
