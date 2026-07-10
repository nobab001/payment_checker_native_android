/**
 * websiteLogoService.js — secure per-account/per-website merchant logo storage.
 *
 * Storage layout:
 *   uploads/accounts/{accountId}/websites/{websiteId}/logo.webp
 *
 * Server-side: validate type + size, resize, compress, convert to WEBP.
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

let sharp = null;
try {
  sharp = require('sharp');
} catch (_) {
  console.warn('[websiteLogo] sharp not installed — logos will be stored without optimization.');
}

const UPLOAD_ROOT = path.join(__dirname, '..', 'public', 'uploads');
const MAX_BYTES = 5 * 1024 * 1024;
const OUTPUT_SIZE = 512;

const ALLOWED_MIME = new Set(['image/png', 'image/jpeg', 'image/jpg', 'image/webp']);

function managedLogoPrefix(accountId, websiteId) {
  return `uploads/accounts/${accountId}/websites/${websiteId}/`;
}

function isManagedLogoPath(logoPath, accountId, websiteId) {
  if (!logoPath || typeof logoPath !== 'string') return false;
  const normalized = logoPath.replace(/^\/+/, '');
  return normalized.startsWith(managedLogoPrefix(accountId, websiteId));
}

function isExternalLogoUrl(logoPath) {
  if (!logoPath) return false;
  return /^https?:\/\//i.test(logoPath.trim());
}

function detectImageType(buf) {
  if (!buf || buf.length < 12) return null;
  if (buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4e && buf[3] === 0x47) return 'png';
  if (buf[0] === 0xff && buf[1] === 0xd8) return 'jpeg';
  if (buf[0] === 0x52 && buf[1] === 0x49 && buf[2] === 0x46 && buf[3] === 0x46
      && buf[8] === 0x57 && buf[9] === 0x45 && buf[10] === 0x42 && buf[11] === 0x50) {
    return 'webp';
  }
  return null;
}

function validateUpload(buffer, mimeType) {
  if (!buffer || buffer.length === 0) throw new Error('EMPTY_IMAGE');
  if (buffer.length > MAX_BYTES) throw new Error('FILE_TOO_LARGE');

  const mime = String(mimeType || '').toLowerCase();
  if (!ALLOWED_MIME.has(mime)) throw new Error('INVALID_FILE_TYPE');

  const detected = detectImageType(buffer);
  if (!detected) throw new Error('INVALID_IMAGE_CONTENT');

  // Reject MIME/extension spoofing (e.g. executable renamed as .png).
  if (mime.includes('png') && detected !== 'png') throw new Error('INVALID_IMAGE_CONTENT');
  if ((mime.includes('jpeg') || mime.includes('jpg')) && detected !== 'jpeg') throw new Error('INVALID_IMAGE_CONTENT');
  if (mime.includes('webp') && detected !== 'webp') throw new Error('INVALID_IMAGE_CONTENT');

  return detected;
}

function logoDir(accountId, websiteId) {
  return path.join(UPLOAD_ROOT, 'accounts', String(accountId), 'websites', String(websiteId));
}

function logoRelativePath(accountId, websiteId) {
  return `${managedLogoPrefix(accountId, websiteId)}logo.webp`;
}

function deleteLogoFile(relativePath) {
  if (!relativePath) return;
  const normalized = String(relativePath).replace(/^\/+/, '');
  const rel = normalized.replace(/^uploads\//, '');
  const abs = path.join(UPLOAD_ROOT, rel);
  const resolved = path.resolve(abs);
  const uploadsResolved = path.resolve(UPLOAD_ROOT);
  if (!resolved.startsWith(uploadsResolved)) return;
  try {
    if (fs.existsSync(resolved)) fs.unlinkSync(resolved);
  } catch (_) { /* ignore */ }
}

function clearBrandingDir(accountId, websiteId) {
  const dir = logoDir(accountId, websiteId);
  try {
    if (!fs.existsSync(dir)) return;
    for (const f of fs.readdirSync(dir)) {
      if (f.startsWith('logo')) {
        try { fs.unlinkSync(path.join(dir, f)); } catch (_) { /* ignore */ }
      }
    }
  } catch (_) { /* ignore */ }
}

/**
 * Process and persist a merchant website logo.
 * @returns {Promise<string>} relative public path (uploads/accounts/.../logo.webp)
 */
async function saveWebsiteLogo(buffer, mimeType, accountId, websiteId) {
  validateUpload(buffer, mimeType);

  const dir = logoDir(accountId, websiteId);
  fs.mkdirSync(dir, { recursive: true });
  clearBrandingDir(accountId, websiteId);

  let outBuf;
  if (sharp) {
    outBuf = await sharp(buffer)
      .rotate()
      .resize(OUTPUT_SIZE, OUTPUT_SIZE, { fit: 'cover', position: 'centre' })
      .webp({ quality: 85, effort: 4 })
      .toBuffer();
  } else {
    outBuf = buffer;
  }

  const filePath = path.join(dir, 'logo.webp');
  fs.writeFileSync(filePath, outBuf);
  return logoRelativePath(accountId, websiteId);
}

module.exports = {
  MAX_BYTES,
  ALLOWED_MIME,
  managedLogoPrefix,
  isManagedLogoPath,
  isExternalLogoUrl,
  saveWebsiteLogo,
  deleteLogoFile,
  validateUpload,
};
