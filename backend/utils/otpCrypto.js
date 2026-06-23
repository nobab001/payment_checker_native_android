'use strict';

const crypto = require('crypto');

// Use JWT_SECRET as source of key material, fallback if not set
const secretMaterial = process.env.JWT_SECRET || 'paychek_super_secret_jwt_key_987654321';
const ENCRYPTION_KEY = crypto.scryptSync(secretMaterial, 'otp-salt', 32); // 32 bytes key for AES-256
const IV_LENGTH = 16; // AES block size

/**
 * Encrypts plaintext OTP code
 * @param {string} text 
 * @returns {string} iv:ciphertext (hex)
 */
function encryptOtp(text) {
  if (!text) return '';
  const iv = crypto.randomBytes(IV_LENGTH);
  const cipher = crypto.createCipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
  let encrypted = cipher.update(text, 'utf8');
  encrypted = Buffer.concat([encrypted, cipher.final()]);
  return iv.toString('hex') + ':' + encrypted.toString('hex');
}

/**
 * Decrypts encrypted OTP code back to plaintext
 * @param {string} encryptedData iv:ciphertext (hex)
 * @returns {string} plaintext OTP
 */
function decryptOtp(encryptedData) {
  if (!encryptedData || !encryptedData.includes(':')) return '';
  try {
    const parts = encryptedData.split(':');
    const iv = Buffer.from(parts.shift(), 'hex');
    const encryptedText = Buffer.from(parts.join(':'), 'hex');
    const decipher = crypto.createDecipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
    let decrypted = decipher.update(encryptedText);
    decrypted = Buffer.concat([decrypted, decipher.final()]);
    return decrypted.toString('utf8');
  } catch (err) {
    console.error('[otpCrypto] Decryption failed:', err.message);
    return '';
  }
}

module.exports = { encryptOtp, decryptOtp };
