/**
 * imageUploadService.js — accepts a base64 (or data-URI) image, optimizes &
 * compresses it with sharp, writes it under public/uploads/<folder>/ using a
 * content-hashed filename (so URLs are immutable + cache-bustable), and returns
 * the public relative path stored in the DB.
 *
 * Logos/icons are kept lossless (PNG) so they stay crisp/HD, but downscaled to a
 * sane max dimension to keep them light for slow connections.
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

let sharp = null;
try {
  sharp = require('sharp');
} catch (_) {
  console.warn('[imageUpload] sharp not installed — images will be stored without optimization.');
}

const UPLOAD_ROOT = path.join(__dirname, '..', 'public', 'uploads');

function decodeBase64(dataUri) {
  let b64 = String(dataUri || '');
  const idx = b64.indexOf(';base64,');
  if (idx !== -1) b64 = b64.slice(idx + ';base64,'.length);
  return Buffer.from(b64, 'base64');
}

function sanitizeKey(key) {
  return String(key || 'img').toLowerCase().replace(/[^a-z0-9_-]/g, '').slice(0, 40) || 'img';
}

/**
 * @param {string} dataUri base64 or data-URI image payload
 * @param {{ folder: string, key: string, maxSize?: number }} opts
 * @returns {Promise<string>} public relative path e.g. "uploads/branding/bkash_ab12cd34ef.png"
 */
async function saveOptimizedImage(dataUri, opts) {
  const { folder, key } = opts || {};
  const maxSize = (opts && opts.maxSize) || 512;
  if (!dataUri) throw new Error('NO_IMAGE_DATA');
  if (!folder) throw new Error('NO_FOLDER');

  const raw = decodeBase64(dataUri);
  if (!raw || raw.length === 0) throw new Error('EMPTY_IMAGE_DATA');

  const dir = path.join(UPLOAD_ROOT, folder);
  fs.mkdirSync(dir, { recursive: true });

  let outBuf;
  if (sharp) {
    // rotate() honours EXIF orientation; PNG is lossless (crisp/HD) with max
    // compression; withoutEnlargement avoids blurry upscaling of small sources.
    outBuf = await sharp(raw)
      .rotate()
      .resize(maxSize, maxSize, { fit: 'inside', withoutEnlargement: true })
      .png({ compressionLevel: 9 })
      .toBuffer();
  } else {
    outBuf = raw;
  }

  const hash = crypto.createHash('sha1').update(outBuf).digest('hex').slice(0, 10);
  const safeKey = sanitizeKey(key || folder);
  const fileName = `${safeKey}_${hash}.png`;
  const filePath = path.join(dir, fileName);

  // Remove stale files for the same key so old logos don't pile up on disk.
  try {
    for (const f of fs.readdirSync(dir)) {
      if (f.startsWith(`${safeKey}_`) && f !== fileName) {
        try { fs.unlinkSync(path.join(dir, f)); } catch (_) { /* ignore */ }
      }
    }
  } catch (_) { /* ignore */ }

  fs.writeFileSync(filePath, outBuf);
  return `uploads/${folder}/${fileName}`;
}

module.exports = { saveOptimizedImage, sanitizeKey };
