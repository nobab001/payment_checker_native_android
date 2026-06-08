'use strict';

const bcrypt = require('bcryptjs');
const { query } = require('../db/connection');
const { sendOtpDispatch } = require('./authController');

/**
 * POST /api/pin/change
 * Changes the security PIN after verifying the old PIN.
 * Body: { oldPin: String, newPin: String }
 * Header: Authorization Bearer <token>
 */
async function changePin(req, res) {
  try {
    const userId = req.user.userId;
    const { oldPin, newPin } = req.body;

    if (!oldPin || !newPin) {
      return res.status(400).json({ error: 'oldPin এবং newPin উভয়ই প্রয়োজন।' });
    }
    if (newPin.length !== 6 || !/^\d{6}$/.test(newPin)) {
      return res.status(400).json({ error: 'নতুন PIN অবশ্যই ৬-ডিজিটের সংখ্যা হতে হবে।' });
    }

    // Fetch current PIN hash
    const users = await query('SELECT pin FROM users WHERE id = ? LIMIT 1', [userId]);
    if (users.length === 0) {
      return res.status(404).json({ error: 'ইউজার খুঁজে পাওয়া যায়নি।' });
    }

    const currentHash = users[0].pin;

    // If user has no PIN yet (profile_complete = 0), oldPin check is skipped
    const hasPinSet = currentHash && currentHash.length > 0;
    if (hasPinSet) {
      const isMatch = await bcrypt.compare(oldPin, currentHash);
      if (!isMatch) {
        return res.status(401).json({
          success: false,
          action: 'WRONG_OLD_PIN',
          message: 'পুরানো PIN সঠিক নয়।'
        });
      }
    }

    // Hash and save new PIN
    const saltRounds = 10;
    const newHash = await bcrypt.hash(newPin, saltRounds);
    await query('UPDATE users SET pin = ? WHERE id = ?', [newHash, userId]);

    console.log(`[PIN] User ${userId} changed PIN successfully.`);
    return res.json({ success: true, message: 'PIN সফলভাবে পরিবর্তন করা হয়েছে।' });
  } catch (err) {
    console.error('[PinController] changePin error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * POST /api/pin/reset-send-otp
 * Sends OTP to the user's primary contact for PIN reset.
 * (No auth required — user is locked out of their PIN)
 * Body: { contact: String }
 */
async function resetPinSendOtp(req, res) {
  try {
    const { contact } = req.body;
    if (!contact) {
      return res.status(400).json({ error: 'Contact is required' });
    }
    const cleanContact = contact.trim();

    // Verify the contact belongs to an existing account
    const users = await query(
      'SELECT id FROM users WHERE phone = ? OR email = ? LIMIT 1',
      [cleanContact, cleanContact]
    );
    if (users.length === 0) {
      return res.status(404).json({ error: 'এই নম্বর বা ইমেইল দিয়ে কোনো অ্যাকাউন্ট নেই।' });
    }

    // Also check user_credentials table
    const credCheck = await query(
      'SELECT user_id FROM user_credentials WHERE value = ? AND verified_at IS NOT NULL LIMIT 1',
      [cleanContact]
    );
    const resolvedUserId = users[0]?.id || credCheck[0]?.user_id;
    if (!resolvedUserId) {
      return res.status(404).json({ error: 'এই নম্বর বা ইমেইল দিয়ে কোনো অ্যাকাউন্ট নেই।' });
    }

    const otpCode = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000);
    await query(
      'INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, ?)',
      [cleanContact, otpCode, expiresAt]
    );

    const sent = await sendOtpDispatch(cleanContact, otpCode);
    if (!sent) {
      return res.status(503).json({ error: 'OTP পাঠানো সম্ভব হয়নি।' });
    }

    console.log(`[PIN RESET OTP] → ${cleanContact} | Code: ${otpCode}`);
    return res.json({ success: true, message: 'OTP পাঠানো হয়েছে।' });
  } catch (err) {
    console.error('[PinController] resetPinSendOtp error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * POST /api/pin/reset-verify
 * Verifies OTP and sets a new PIN.
 * Body: { contact: String, code: String, newPin: String }
 */
async function resetPinVerify(req, res) {
  try {
    const { contact, code, newPin } = req.body;

    if (!contact || !code || !newPin) {
      return res.status(400).json({ error: 'contact, code এবং newPin প্রয়োজন।' });
    }
    if (newPin.length !== 6 || !/^\d{6}$/.test(newPin)) {
      return res.status(400).json({ error: 'নতুন PIN অবশ্যই ৬-ডিজিটের সংখ্যা হতে হবে।' });
    }

    const cleanContact = contact.trim();
    const cleanCode = code.trim();

    // Validate OTP
    const otps = await query(
      'SELECT id FROM otps WHERE contact = ? AND code = ? AND expires_at > NOW() AND used_at IS NULL LIMIT 1',
      [cleanContact, cleanCode]
    );
    if (otps.length === 0) {
      return res.status(400).json({ error: 'ভুল OTP কোড অথবা মেয়াদ শেষ।' });
    }
    await query('UPDATE otps SET used_at = NOW() WHERE id = ?', [otps[0].id]);

    // Resolve user
    const users = await query(
      'SELECT id FROM users WHERE phone = ? OR email = ? LIMIT 1',
      [cleanContact, cleanContact]
    );
    let resolvedUserId = users[0]?.id;
    if (!resolvedUserId) {
      const credRow = await query(
        'SELECT user_id FROM user_credentials WHERE value = ? AND verified_at IS NOT NULL LIMIT 1',
        [cleanContact]
      );
      resolvedUserId = credRow[0]?.user_id;
    }
    if (!resolvedUserId) {
      return res.status(404).json({ error: 'ইউজার খুঁজে পাওয়া যায়নি।' });
    }

    // Set new PIN
    const newHash = await bcrypt.hash(newPin, 10);
    await query('UPDATE users SET pin = ? WHERE id = ?', [newHash, resolvedUserId]);

    console.log(`[PIN RESET] User ${resolvedUserId} PIN reset via OTP.`);
    return res.json({ success: true, message: 'PIN সফলভাবে রিসেট হয়েছে।' });
  } catch (err) {
    console.error('[PinController] resetPinVerify error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

module.exports = { changePin, resetPinSendOtp, resetPinVerify };
