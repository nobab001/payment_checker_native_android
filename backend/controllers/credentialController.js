'use strict';

const crypto = require('crypto');
const prisma = require('../db/prisma');
const { encryptOtp, decryptOtp } = require('../utils/otpCrypto');

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
    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { phone: true, email: true }
    });
    
    const primaryPhone = user?.phone || null;
    const primaryEmail = user?.email || null;

    // Additional from user_credentials table
    const creds = await prisma.user_credentials.findMany({
      where: { user_id: userId },
      orderBy: { id: 'asc' }
    });

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
    const total = await prisma.user_credentials.count({
      where: { user_id: userId, type: resolvedType }
    });

    if (total >= MAX_PER_TYPE) {
      return res.status(400).json({
        success: false,
        action: 'LIMIT_REACHED',
        message: `দুঃখিত ভাই! সর্বোচ্চ ৫টি ${resolvedType === 'phone' ? 'মোবাইল নম্বর' : 'জিমেইল'} লিঙ্ক করা সম্ভব।`
      });
    }

    // Global uniqueness check — contact must not exist anywhere
    const existsInUsers = await prisma.users.findFirst({
      where: isEmail ? { email: cleanContact } : { phone: cleanContact },
      select: { id: true }
    });
    
    const existsInCreds = await prisma.user_credentials.findFirst({
      where: { value: cleanContact },
      select: { id: true }
    });

    if (existsInUsers || existsInCreds) {
      return res.status(400).json({
        success: false,
        action: 'ALREADY_EXISTS',
        message: 'এই নম্বর বা ইমেইলটি ইতিমধ্যে অন্য একটি অ্যাকাউন্টে নিবন্ধিত আছে।'
      });
    }

    // Generate OTP and store
    const otpCode = crypto.randomInt(100000, 1000000).toString();
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000);
    
    await prisma.otps.create({
      data: {
        contact: cleanContact,
        code: encryptOtp(otpCode),
        expires_at: expiresAt
      }
    });

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
    const activeOtps = await prisma.otps.findMany({
      where: {
        contact: cleanContact,
        expires_at: { gt: new Date() },
        used_at: null
      },
      orderBy: { id: 'desc' }
    });
    const matchedOtp = activeOtps.find(otp => decryptOtp(otp.code) === cleanCode);

    if (!matchedOtp) {
      return res.status(400).json({ error: 'ভুল OTP কোড অথবা মেয়াদ শেষ হয়ে গেছে।' });
    }

    // Mark OTP as used
    await prisma.otps.update({
      where: { id: matchedOtp.id },
      data: { used_at: new Date() }
    });

    const type = cleanContact.includes('@') ? 'email' : 'phone';
    
    // Check if the user's primary email/phone is null
    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { phone: true, email: true }
    });

    if (user) {
      if (type === 'email' && !user.email) {
        await prisma.users.update({
          where: { id: userId },
          data: { email: cleanContact }
        });
      } else if (type === 'phone' && !user.phone) {
        await prisma.users.update({
          where: { id: userId },
          data: { phone: cleanContact }
        });
      }
    }

    // Always insert/update in user_credentials table so it appears in the list
    const exists = await prisma.user_credentials.findFirst({
      where: { user_id: userId, value: cleanContact },
      select: { id: true }
    });

    if (exists) {
      await prisma.user_credentials.update({
        where: { id: exists.id },
        data: { verified_at: new Date() }
      });
    } else {
      await prisma.user_credentials.create({
        data: {
          user_id: userId,
          type,
          value: cleanContact,
          verified_at: new Date()
        }
      });
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

    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { pin: true, phone: true, email: true }
    });

    if (!user) {
      return res.status(404).json({ error: 'ইউজার খুঁজে পাওয়া যায়নি।' });
    }

    const currentHash = user.pin;
    if (!currentHash) {
      return res.status(400).json({ error: 'ইউজারের কোনো PIN সেট করা নেই।' });
    }

    const bcrypt = require('bcryptjs');
    const isMatch = await bcrypt.compare(pin, currentHash);
    if (!isMatch) {
      return res.status(401).json({ error: 'ভুল পিন নম্বর।' });
    }

    // Check if user is attempting to delete primary phone or email
    const targetCred = await prisma.user_credentials.findFirst({
      where: { id: credId, user_id: userId },
      select: { type: true, value: true }
    });

    if (!targetCred) {
      return res.status(404).json({ error: 'Credential খুঁজে পাওয়া যায়নি।' });
    }

    const primaryPhone = user.phone;
    const primaryEmail = user.email;
    const credType = targetCred.type;
    const credValue = targetCred.value;

    const isPrimaryPhone = credType === 'phone' && primaryPhone &&
      credValue.replace(/[^0-9]/g, '').slice(-10) === primaryPhone.replace(/[^0-9]/g, '').slice(-10);

    const isPrimaryEmail = credType === 'email' && primaryEmail &&
      credValue.trim().toLowerCase() === primaryEmail.trim().toLowerCase();

    if (isPrimaryPhone || isPrimaryEmail) {
      return res.status(400).json({ error: 'প্রধান/মেইন ক্রেডেনশিয়াল মুছে ফেলা সম্ভব নয়।' });
    }

    const result = await prisma.user_credentials.deleteMany({
      where: { id: credId, user_id: userId }
    });

    if (result.count === 0) {
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
