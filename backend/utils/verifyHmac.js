/**
 * verifyHmac.js — Cryptographic HMAC-SHA256 Verification Utility
 * =============================================================================
 * Purpose  : ক্লায়েন্টের পাঠানো RAW SMS body ক্রিপ্টোগ্রাফিক্যালি যাচাই করা।
 *            Timing-safe comparison ব্যবহার করে timing-attack রোধ করা হয়েছে।
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  CANONICAL HMAC FORMULA  (Android ও Node.js উভয়েই IDENTICAL)          │
 * │                                                                          │
 * │  HMAC-SHA256(                                                            │
 * │      key     = UTF-8(secretKey),    ← secretKey string, UTF-8 encoded   │
 * │      message = UTF-8(rawSmsBody)    ← raw SMS text, UTF-8 encoded       │
 * │  ) → lowercase hexadecimal string                                        │
 * │                                                                          │
 * │  ⛔ DO NOT change:                                                        │
 * │     • key-first / message-second order                                   │
 * │     • UTF-8 encoding (never use hex/base64 for encoding input)           │
 * │     • lowercase hex output format                                        │
 * │  Any change here MUST be mirrored in Android HmacHelper.kt              │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Usage    : const { verifyHmac } = require('./utils/verifyHmac');
 * =============================================================================
 */
const crypto = require('crypto');

/**
 * generateHmac — একটি RAW SMS body এবং secretKey থেকে HMAC-SHA256 তৈরি করে।
 * @param {string} rawBody     — Original unmodified SMS text
 * @param {string} secretKey   — Per-user 64-char hex secret
 * @returns {string}           — Hex-encoded HMAC signature
 */
function generateHmac(rawBody, secretKey) {
  return crypto
    .createHmac('sha256', secretKey)
    .update(rawBody, 'utf8')
    .digest('hex');
}

/**
 * verifyHmac — ক্লায়েন্টের পাঠানো signature যাচাই করে।
 * Timing-safe comparison ব্যবহার করা হয়েছে যাতে timing attack সম্ভব না হয়।
 *
 * @param {string} rawBody     — ক্লায়েন্টের পাঠানো মূল RAW SMS body
 * @param {string} signature   — ক্লায়েন্টের পাঠানো HMAC hex string
 * @param {string} secretKey   — ডাটাবেজ থেকে পাওয়া per-user secretKey
 * @returns {{ valid: boolean, error?: string }}
 */
function verifyHmac(rawBody, signature, secretKey) {
  // Input validation
  if (!rawBody || typeof rawBody !== 'string') {
    return { valid: false, error: 'rawBody is missing or invalid' };
  }
  if (!signature || typeof signature !== 'string') {
    return { valid: false, error: 'HMAC signature is missing' };
  }
  if (!secretKey || typeof secretKey !== 'string') {
    return { valid: false, error: 'secretKey is missing — user may need to re-login' };
  }

  try {
    const expectedSig = generateHmac(rawBody, secretKey);

    // Timing-safe byte comparison (prevents timing attacks)
    const sigBuffer      = Buffer.from(signature,    'hex');
    const expectedBuffer = Buffer.from(expectedSig,  'hex');

    if (sigBuffer.length !== expectedBuffer.length) {
      return { valid: false, error: 'Signature length mismatch' };
    }

    const isValid = crypto.timingSafeEqual(sigBuffer, expectedBuffer);
    return { valid: isValid };
  } catch (err) {
    return { valid: false, error: `HMAC verification error: ${err.message}` };
  }
}

module.exports = { verifyHmac, generateHmac };
