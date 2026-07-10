/** HTML / URL sanitization for checkout presentation layer. */

const UNSAFE_PROTOCOL = /^\s*(javascript|vbscript|data:text\/html)/i;

/** Escape text and attribute values for HTML context. */
export function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** Alias for attribute-heavy templates. */
export const escAttr = esc;

/**
 * Sanitize image/logo URLs — blocks script URLs and non-image data: URIs.
 * @returns {string} safe URL or empty string
 */
export function safeImgSrc(u) {
  u = String(u ?? '').trim();
  if (!u || UNSAFE_PROTOCOL.test(u)) return '';

  if (/^data:/i.test(u)) {
    return /^data:image\/(png|jpe?g|gif|webp|svg\+xml);/i.test(u) ? u : '';
  }

  if (/^https?:\/\//i.test(u)) {
    try {
      const parsed = new URL(u);
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return '';
      return parsed.href;
    } catch (_) {
      return '';
    }
  }

  if (u.startsWith('/')) return u.replace(/[\s<>"']/g, '');
  return '/' + u.replace(/^\/+/, '').replace(/[\s<>"']/g, '');
}

/** @deprecated use safeImgSrc */
export function imgSrc(u) {
  return safeImgSrc(u);
}

/** Plain-text sanitizer for textContent paths (strip control chars). */
export function safeText(s, maxLen = 500) {
  return String(s ?? '').replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, '').slice(0, maxLen);
}
