'use strict';

/**
 * Merchant credential encryption (AES-256-CBC).
 *
 * Same construction as utils/otpCrypto.js but with a dedicated salt so that a
 * leaked OTP key never exposes merchant secrets. Stored format: iv:ciphertext
 * (both hex). Sensitive fields (api_secret, password, app_secret) must always be
 * encrypted at rest and NEVER returned to any client in plaintext — use mask().
 */

const crypto = require('crypto');

const secretMaterial = process.env.JWT_SECRET || 'paychek_super_secret_jwt_key_987654321';
const ENCRYPTION_KEY = crypto.scryptSync(secretMaterial, 'merchant-cred-salt', 32); // AES-256
const IV_LENGTH = 16;

/**
 * Encrypts a plaintext secret.
 * @param {string} text
 * @returns {string} iv:ciphertext (hex), or '' when input is empty
 */
function encrypt(text) {
  if (text == null || text === '') return '';
  const iv = crypto.randomBytes(IV_LENGTH);
  const cipher = crypto.createCipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
  let enc = cipher.update(String(text), 'utf8');
  enc = Buffer.concat([enc, cipher.final()]);
  return iv.toString('hex') + ':' + enc.toString('hex');
}

/**
 * Decrypts an iv:ciphertext string back to plaintext.
 * @param {string} data
 * @returns {string} plaintext, or '' on failure
 */
function decrypt(data) {
  if (!data || !String(data).includes(':')) return '';
  try {
    const parts = String(data).split(':');
    const iv = Buffer.from(parts.shift(), 'hex');
    const encryptedText = Buffer.from(parts.join(':'), 'hex');
    const decipher = crypto.createDecipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
    let dec = decipher.update(encryptedText);
    dec = Buffer.concat([dec, decipher.final()]);
    return dec.toString('utf8');
  } catch (err) {
    console.error('[merchantCrypto] decrypt failed:', err.message);
    return '';
  }
}

/**
 * Produces a safe masked preview of a secret for display (e.g. "••••1234").
 * Accepts either plaintext or an encrypted iv:ciphertext string.
 * @param {string} value encrypted or plaintext
 * @returns {string} masked hint, or '' when empty
 */
function mask(value) {
  if (!value) return '';
  const plain = String(value).includes(':') ? decrypt(value) : String(value);
  if (!plain) return '';
  const last4 = plain.slice(-4);
  return '••••' + last4;
}

/** True when a stored secret is present (encrypted or not). */
function hasSecret(value) {
  return !!(value && String(value).length);
}

module.exports = { encrypt, decrypt, mask, hasSecret };
