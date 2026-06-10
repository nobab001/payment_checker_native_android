'use strict';

const { query } = require('../db/connection');

// Max credentials per user per type (phones & emails separately)
const MAX_PER_TYPE = 5;

/**
 * GET /api/credentials
 * Returns all verified credentials for the logged-in user.
 */
async function listCredentials(req, res) {
  try {
    const userId = req.user.userId;

    // Primary phone/email from users table
    const userRows = await query('SELECT phone, email FROM users WHERE id = ? LIMIT 1', [userId]);
    const primaryPhone = userRows[0]?.phone || null;
    const primaryEmail = userRows[0]?.email || null;

    // Additional from user_credentials table
    const creds = await query(
      "SELECT id, type, value, verified_at FROM user_credentials WHERE user_id = ? ORDER BY id ASC",
      [userId]
    );

    const mappedCreds = creds.map(c => ({
      id: c.id,
      type: c.type,
      value: c.value,
      verifiedAt: c.verified_at
    }));

    return res.json({
      success: true,
      primaryPhone,
      primaryEmail,
      credentials: mappedCreds,
      phones: mappedCreds.filter(c => c.type === 'phone'),
      emails: mappedCreds.filter(c => c.type === 'email')
    });
  } catch (err) {
    console.error('[CredentialController] listCredentials error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * POST /api/credentials/send-otp
 * Sends OTP to a new phone/email before adding it as a credential.
 * Body: { value: String, type: String } (Supports fallback { contact: String })
 */
async function sendCredentialOtp(req, res) {
  try {
    const userId = req.user.userId;
    const contact = req.body.value || req.body.contact;

    if (!contact || contact.trim() === '') {
      return res.status(400).json({ error: 'Contact is required' });
    }
    const cleanContact = contact.trim();
    const isEmail = cleanContact.includes('@');
    const resolvedType = req.body.type || (isEmail ? 'email' : 'phone');

    // Count existing credentials of this resolvedType
    const countRows = await query(
      "SELECT COUNT(*) as cnt FROM user_credentials WHERE user_id = ? AND type = ?",
      [userId, resolvedType]
    );
    const total = countRows[0].cnt;

    if (total >= MAX_PER_TYPE) {
      return res.status(400).json({
        success: false,
        action: 'LIMIT_REACHED',
        message: `দুঃখিত ভাই! সর্বোচ্চ ৫টি ${resolvedType === 'phone' ? 'মোবাইল নম্বর' : 'জিমেইল'} লিঙ্ক করা সম্ভব।`
      });
    }

    // Global uniqueness check — contact must not exist anywhere
    const existsInUsers = await query(
      isEmail
        ? 'SELECT id FROM users WHERE email = ? LIMIT 1'
        : 'SELECT id FROM users WHERE phone = ? LIMIT 1',
      [cleanContact]
    );
    const existsInCreds = await query(
      'SELECT id FROM user_credentials WHERE value = ? LIMIT 1',
      [cleanContact]
    );
    if (existsInUsers.length > 0 || existsInCreds.length > 0) {
      return res.status(400).json({
        success: false,
        action: 'ALREADY_EXISTS',
        message: 'এই নম্বর বা ইমেইলটি ইতিমধ্যে অন্য একটি অ্যাকাউন্টে নিবন্ধিত আছে।'
      });
    }

    // Generate OTP and store
    const otpCode = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000);
    await query(
      'INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, ?)',
      [cleanContact, otpCode, expiresAt]
    );

    // Insert a pending (unverified) credential record
    await query(
      "INSERT INTO user_credentials (user_id, type, value, verified_at) VALUES (?, ?, ?, NULL) ON DUPLICATE KEY UPDATE verified_at = NULL",
      [userId, resolvedType, cleanContact]
    );

    // Dispatch OTP (reuse existing dispatcher from authController)
    const { sendOtpDispatch } = require('./authController');
    const sent = await sendOtpDispatch(cleanContact, otpCode);
    if (!sent) {
      return res.status(503).json({
        success: false,
        message: 'OTP পাঠানো সম্ভব হয়নি। পরে আবার চেষ্টা করুন।'
      });
    }

    console.log(`[CREDENTIAL OTP] User ${userId} → ${cleanContact} | Code: ${otpCode}`);
    return res.json({ success: true, message: 'OTP সফলভাবে পাঠানো হয়েছে।' });
  } catch (err) {
    console.error('[CredentialController] sendCredentialOtp error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * POST /api/credentials/verify
 * Verifies OTP and marks credential as verified.
 * Body: { value: String, type: String, otp: String } (Supports fallback { contact: String, code: String })
 */
async function verifyCredential(req, res) {
  try {
    const userId = req.user.userId;
    const contact = req.body.value || req.body.contact;
    const code = req.body.otp || req.body.code;

    if (!contact || !code) {
      return res.status(400).json({ error: 'Contact and code are required' });
    }
    const cleanContact = contact.trim();
    const cleanCode = code.trim();

    // Validate OTP
    const otps = await query(
      'SELECT id FROM otps WHERE contact = ? AND code = ? AND expires_at > NOW() AND used_at IS NULL LIMIT 1',
      [cleanContact, cleanCode]
    );
    if (otps.length === 0) {
      return res.status(400).json({ error: 'ভুল OTP কোড অথবা মেয়াদ শেষ হয়ে গেছে।' });
    }

    // Mark OTP as used
    await query('UPDATE otps SET used_at = NOW() WHERE id = ?', [otps[0].id]);

    // Mark credential as verified
    const result = await query(
      "UPDATE user_credentials SET verified_at = NOW() WHERE user_id = ? AND value = ? AND verified_at IS NULL",
      [userId, cleanContact]
    );

    if (result.affectedRows === 0) {
      return res.status(400).json({ error: 'Credential খুঁজে পাওয়া যায়নি অথবা আগে থেকেই verified।' });
    }

    return res.json({ success: true, message: 'Credential সফলভাবে যাচাই ও যোগ করা হয়েছে।' });
  } catch (err) {
    console.error('[CredentialController] verifyCredential error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * DELETE /api/credentials/:id
 * Removes a credential (cannot remove primary phone/email from users table via this route).
 */
async function removeCredential(req, res) {
  try {
    const userId = req.user.userId;
    const credId = parseInt(req.params.id, 10);

    if (!credId || isNaN(credId)) {
      return res.status(400).json({ error: 'Invalid credential ID' });
    }

    const pin = req.query.pin || req.body.pin;
    if (!pin) {
      return res.status(400).json({ error: 'নিরাপত্তা PIN প্রদান করা আবশ্যক।' });
    }

    const users = await query('SELECT pin FROM users WHERE id = ? LIMIT 1', [userId]);
    if (users.length === 0) {
      return res.status(404).json({ error: 'ইউজার খুঁজে পাওয়া যায়নি।' });
    }

    const currentHash = users[0].pin;
    if (!currentHash) {
      return res.status(400).json({ error: 'ইউজারের কোনো PIN সেট করা নেই।' });
    }

    const bcrypt = require('bcryptjs');
    const isMatch = await bcrypt.compare(pin, currentHash);
    if (!isMatch) {
      return res.status(401).json({ error: 'ভুল পিন নম্বর।' });
    }

    const result = await query(
      'DELETE FROM user_credentials WHERE id = ? AND user_id = ?',
      [credId, userId]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ error: 'Credential খুঁজে পাওয়া যায়নি।' });
    }

    return res.json({ success: true, message: 'Credential সফলভাবে মুছে ফেলা হয়েছে।' });
  } catch (err) {
    console.error('[CredentialController] removeCredential error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

module.exports = {
  listCredentials,
  sendCredentialOtp,
  verifyCredential,
  removeCredential
};
