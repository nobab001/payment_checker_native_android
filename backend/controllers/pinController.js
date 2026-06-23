'use strict';

const bcrypt = require('bcryptjs');
const prisma = require('../db/prisma');
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
    if (newPin.length < 4 || newPin.length > 6 || !/^\\d{4,6}$/.test(newPin)) {
      return res.status(400).json({ error: 'নতুন PIN অবশ্যই ৪ থেকে ৬-ডিজিটের সংখ্যা হতে হবে।' });
    }

    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { pin: true }
    });

    if (!user) {
      return res.status(404).json({ error: 'ইউজার খুঁজে পাওয়া যায়নি।' });
    }

    const currentHash = user.pin;
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
    await prisma.users.update({
      where: { id: userId },
      data: { pin: newHash }
    });

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
    let resolvedUserId = null;
    const user = await prisma.users.findFirst({
      where: { OR: [{ phone: cleanContact }, { email: cleanContact }] },
      select: { id: true }
    });
    
    if (user) {
      resolvedUserId = user.id;
    } else {
      const cred = await prisma.user_credentials.findFirst({
        where: { value: cleanContact, verified_at: { not: null } },
        select: { user_id: true }
      });
      if (cred) resolvedUserId = cred.user_id;
    }

    if (!resolvedUserId) {
      return res.status(404).json({ error: 'এই নম্বর বা ইমেইল দিয়ে কোনো অ্যাকাউন্ট নেই।' });
    }

    const otpCode = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000);
    
    await prisma.otps.create({
      data: {
        contact: cleanContact,
        code: otpCode,
        expires_at: expiresAt
      }
    });

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
    if (newPin.length < 4 || newPin.length > 6 || !/^\\d{4,6}$/.test(newPin)) {
      return res.status(400).json({ error: 'নতুন PIN অবশ্যই ৪ থেকে ৬-ডিজিটের সংখ্যা হতে হবে।' });
    }

    const cleanContact = contact.trim();
    const cleanCode = code.trim();

    // Validate OTP
    const otpRec = await prisma.otps.findFirst({
      where: {
        contact: cleanContact,
        code: cleanCode,
        expires_at: { gt: new Date() },
        used_at: null
      },
      select: { id: true }
    });

    if (!otpRec) {
      return res.status(400).json({ error: 'ভুল OTP কোড অথবা মেয়াদ শেষ।' });
    }

    await prisma.otps.update({
      where: { id: otpRec.id },
      data: { used_at: new Date() }
    });

    // Resolve user
    let resolvedUserId = null;
    const user = await prisma.users.findFirst({
      where: { OR: [{ phone: cleanContact }, { email: cleanContact }] },
      select: { id: true }
    });
    
    if (user) {
      resolvedUserId = user.id;
    } else {
      const cred = await prisma.user_credentials.findFirst({
        where: { value: cleanContact, verified_at: { not: null } },
        select: { user_id: true }
      });
      if (cred) resolvedUserId = cred.user_id;
    }

    if (!resolvedUserId) {
      return res.status(404).json({ error: 'ইউজার খুঁজে পাওয়া যায়নি।' });
    }

    // Set new PIN
    const newHash = await bcrypt.hash(newPin, 10);
    await prisma.users.update({
      where: { id: resolvedUserId },
      data: { pin: newHash }
    });

    console.log(`[PIN RESET] User ${resolvedUserId} PIN reset via OTP.`);
    return res.json({ success: true, message: 'PIN সফলভাবে রিসেট হয়েছে।' });
  } catch (err) {
    console.error('[PinController] resetPinVerify error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * POST /api/pin/verify
 * Verifies the user security PIN.
 * Body: { pin: String }
 */
async function verifyPin(req, res) {
  try {
    const userId = req.user.userId;
    const { pin } = req.body;

    if (!pin) {
      return res.status(400).json({ error: 'PIN প্রয়োজন।' });
    }

    if (req.user.role === 'admin') {
      const adminPin = process.env.ADMIN_PIN || '5566';
      if (pin === adminPin) {
         return res.json({ success: true, message: 'পিন সফলভাবে যাচাই করা হয়েছে।' });
      } else {
         return res.status(401).json({ success: false, error: 'ভুল পিন নম্বর।' });
      }
    }

    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { pin: true }
    });

    if (!user) {
      return res.status(404).json({ error: 'ইউজার খুঁজে পাওয়া যায়নি।' });
    }

    const currentHash = user.pin;
    if (!currentHash) {
      return res.status(400).json({ error: 'ইউজারের কোনো PIN সেট করা নেই।' });
    }

    const isMatch = await bcrypt.compare(pin, currentHash);
    if (!isMatch) {
      return res.status(401).json({ success: false, error: 'ভুল পিন নম্বর।' });
    }

    return res.json({ success: true, message: 'পিন সফলভাবে যাচাই করা হয়েছে।' });
  } catch (err) {
    console.error('[PinController] verifyPin error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

module.exports = { changePin, resetPinSendOtp, resetPinVerify, verifyPin };
