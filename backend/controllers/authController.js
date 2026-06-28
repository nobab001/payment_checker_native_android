const prisma = require('../db/prisma');
async function query(sql, params = []) {
  const isSelect = /^\s*(SELECT|SHOW|DESCRIBE)/i.test(sql);
  if (isSelect) {
    const results = await prisma.$queryRawUnsafe(sql, ...params);
    return JSON.parse(JSON.stringify(results, (key, value) => {
      if (typeof value === 'bigint') return Number(value);
      return value;
    }));
  } else {
    return await prisma.$transaction(async (tx) => {
      const affectedRows = await tx.$executeRawUnsafe(sql, ...params);
      let insertId = 0;
      if (/^\s*INSERT/i.test(sql)) {
         const idRes = await tx.$queryRawUnsafe('SELECT LAST_INSERT_ID() as insertId');
         if (idRes && idRes.length > 0) {
           insertId = Number(idRes[0].insertId);
         }
      }
      return { affectedRows, insertId };
    });
  }
}
const DeviceBindingService = require('../services/DeviceBindingService');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const nodemailer = require('nodemailer');
const axios = require('axios');
const crypto = require('crypto');
const { encryptOtp, decryptOtp } = require('../utils/otpCrypto');

const JWT_SECRET = process.env.JWT_SECRET || 'paychek_super_secret_jwt_key_987654321';
const TRIAL_DEFAULT_DAYS = parseInt(process.env.TRIAL_DEFAULT_DAYS || '7', 10);

/**
 * Helper: findUserByContact
 * Queries unified user_credentials table for any phone or email contact.
 * Returns user record from users table if found, otherwise null.
 */
async function findUserByContact(contact) {
  const cleaned = contact.trim();
  // Lookup exclusively in user_credentials
  const cred = await query(
    'SELECT user_id FROM user_credentials WHERE value = ? AND verified_at IS NOT NULL LIMIT 1',
    [cleaned]
  );
  if (cred.length > 0) {
    const userId = cred[0].user_id;
    const users = await query('SELECT * FROM users WHERE id = ? LIMIT 1', [userId]);
    return users.length > 0 ? users[0] : null;
  }
  return null;
}

/**
 * Helper: getRawCredentials
 * Fetches all unmasked verified credentials (phone/email) for a user.
 */
async function getRawCredentials(userId) {
  if (!userId) return [];
  const credentials = [];
  try {
    const users = await query('SELECT phone, email FROM users WHERE id = ? LIMIT 1', [userId]);
    if (users.length > 0) {
      if (users[0].phone) credentials.push(users[0].phone.trim());
      if (users[0].email) credentials.push(users[0].email.trim());
    }
  } catch (_) {}

  try {
    const creds = await query(
      'SELECT value FROM user_credentials WHERE user_id = ? AND verified_at IS NOT NULL',
      [userId]
    );
    for (const cred of creds) {
      if (cred.value) {
        credentials.push(cred.value.trim());
      }
    }
  } catch (_) {}

  return credentials;
}

/**
 * 1. POST /api/check-contact
 * Checks if a contact (phone or email) is registered.
 * ⚠️  SECURITY: Device-binding gatekeeper runs FIRST.
 *     Even if the contact is not found, a bound device gets
 *     DEVICE_ALREADY_BOUND — never a plain { exists: false }.
 */
async function checkContact(req, res) {
  try {
    const { contact, deviceId, androidId, hardwareFingerprint, simSlotIds } = req.body;
    const cleanedContact = contact ? contact.trim() : '';

    const adminSecretUsername = process.env.ADMIN_SECRET_USERNAME || 'admin';

    if (cleanedContact === adminSecretUsername) {
      return res.json({ exists: true });
    }

    // ১. সবার আগে ডিভাইস ওনারশিপ চেক করো (Device ID Guard)
    let linkedUserId = null;
    if (deviceId) {
      // Check registered_devices
      const devices = await query(
        'SELECT user_id FROM registered_devices WHERE device_id = ? LIMIT 1',
        [deviceId]
      );
      if (devices.length > 0 && devices[0].user_id) {
        linkedUserId = devices[0].user_id;
      }

      // Check device_trial_logs if not found in registered_devices
      if (!linkedUserId) {
        const checkFields = [];
        const checkParams = [];
        const safeAndroidId = androidId || '';
        const safeHardwareFingerprint = hardwareFingerprint || '';
        const safeSimSlotIds = simSlotIds || '';

        if (safeAndroidId && safeAndroidId !== 'unknown_android_id' && safeAndroidId !== '') {
          checkFields.push('android_id = ?');
          checkParams.push(safeAndroidId);
        }
        if (safeHardwareFingerprint && safeHardwareFingerprint !== 'unknown_fingerprint' && safeHardwareFingerprint !== '') {
          checkFields.push('hardware_fingerprint = ?');
          checkParams.push(safeHardwareFingerprint);
        }

        const isValidSimSlotId = safeSimSlotIds &&
                                 safeSimSlotIds !== 'no_sims' &&
                                 safeSimSlotIds !== 'no_sim' &&
                                 safeSimSlotIds !== 'permission_denied' &&
                                 safeSimSlotIds !== 'unknown' &&
                                 safeSimSlotIds !== '';
        if (isValidSimSlotId) {
          checkFields.push('sim_slot_ids = ?');
          checkParams.push(safeSimSlotIds);
        }

        if (checkFields.length > 0) {
          const queryStr = `SELECT user_id FROM device_trial_logs WHERE (${checkFields.join(' OR ')}) AND has_used_trial = 1 LIMIT 1`;
          const logs = await query(queryStr, checkParams);
          if (logs && logs.length > 0) {
            linkedUserId = logs[0].user_id || null;
          }
        }
      }
    }

    // যদি এই deviceId দিয়ে কোনো অ্যাকাউন্ট অলরেডি লিংকড পাওয়া যায়
    if (linkedUserId) {
      const userExists = await query('SELECT id FROM users WHERE id = ? LIMIT 1', [linkedUserId]);
      if (userExists.length === 0) {
        console.log(`[Device Binding] User ${linkedUserId} does not exist (deleted). Unbinding device...`);
        if (deviceId) {
          await query('DELETE FROM registered_devices WHERE user_id = ?', [linkedUserId]);
        }
        await query('DELETE FROM device_trial_logs WHERE user_id = ?', [linkedUserId]);
        linkedUserId = null;
      }
    }

    if (linkedUserId) {
      const rawCreds = await getRawCredentials(linkedUserId);
      const isOwner = rawCreds.some(c => c.toLowerCase() === cleanedContact.toLowerCase());
      if (isOwner) {
        // ওনার নিজের -> গেটওয়ে পাস
        return res.json({ exists: true });
      } else {
        // অন্য বা নতুন -> সরাসরি ব্লক! DEVICE_ALREADY_BOUND রেসপন্স থ্রো করো
        const { boundPhones, boundEmails } = await getBoundCredentials(linkedUserId);
        return res.status(403).json({
          success: false,
          action: 'DEVICE_ALREADY_BOUND',
          message: 'ডিভাইস লিংক নোটিশ: নিরাপত্তা ও পলিসিগত কারণে আমাদের সিস্টেমে একটি hardware ডিভাইসের সাথে কেবল একটি মূল অ্যাকাউন্টই যুক্ত রাখা সম্ভব। আপনার এই ডিভাইসটি ইতিমধ্যে নিচের অ্যাকাউন্টটির সাথে নিবন্ধিত ও লিংক করা রয়েছে। অনুগ্রহ করে পূর্বের লিংক করা অ্যাকাউন্টটি ব্যবহার করে লগইন সম্পন্ন করুন। এই ডিভাইসে নতুন কোনো অ্যাকাউন্ট তৈরি বা অন্য অ্যাকাউন্ট ব্যবহার করা যাবে না।',
          boundPhones,
          boundEmails
        });
      }
    }

    if (!contact) {
      return res.status(400).json({ error: 'Contact field is required' });
    }

    // ২. ডিভাইস সম্পূর্ণ ক্লিন বা ওনার নিজে হলে, এবার চেক করো নম্বর/জিমেইলটি সিস্টেমে আছে কি না
    const userRecord = await findUserByContact(cleanedContact);
    return res.json({ exists: userRecord !== null });
  } catch (error) {
    console.error('Error checking contact:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * Helper: cleanupIncompleteAccounts  [Fix 2 — Option B]
 * Deletes users where profile_complete=0 AND created_at > 24 hours ago.
 * ON DELETE CASCADE on user_credentials and registered_devices handles child rows.
 * Non-fatal — logged only, never throws.
 */
async function cleanupIncompleteAccounts() {
  try {
    const result = await query(
      `DELETE FROM users
       WHERE profile_complete = 0
         AND created_at < NOW() - INTERVAL 24 HOUR`
    );
    if (result.affectedRows > 0) {
      console.log(`[Cleanup] Removed ${result.affectedRows} incomplete account(s) older than 24h`);
    }
  } catch (err) {
    console.error('[Cleanup] cleanupIncompleteAccounts error:', err.message);
  }
}

/**
 * 2. POST /api/send-otp
 * Sends/generates OTP for an existing user (Login Flow).
 * Returns HTTP 404 with action SHOW_NOT_FOUND_DIALOG if account is missing.
 */
async function sendOtp(req, res) {
  try {
    const { contact, deviceId, androidId, hardwareFingerprint, simSlotIds } = req.body;

    const cleanedContact = contact ? contact.trim() : '';

    const adminSecretUsername = process.env.ADMIN_SECRET_USERNAME || 'admin';

    // Fix 2: Run cleanup on every login attempt (non-blocking)
    cleanupIncompleteAccounts().catch(() => {});

    // 👑 ADMIN BYPASS & GMAIL LOGIN GATEWAY
    const adminEmail = process.env.ADMIN_EMAIL || '';
    if (adminEmail && cleanedContact === adminEmail.trim()) {
      const otpCode = generateOtpCode();
      await query(
        'INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 5 MINUTE))',
        [cleanedContact, encryptOtp(otpCode)]
      );
      const success = await sendOtpDispatch(cleanedContact, otpCode);
      if (!success) {
        return res.status(503).json({
          success: false,
          action: 'EMAIL_ALL_FAILED',
          message: 'ইমেইল OTP পাঠানো সম্ভব হয়নি। মোবাইল নম্বর দিয়ে লগইন করুন বা পুনরায় চেষ্টা করুন।'
        });
      }
      return res.json({ success: true, message: 'এডমিন ওটিপি পাঠানো হয়েছে আপনার জিমেইলে।' });
    }

    if (cleanedContact === adminSecretUsername) {
      return res.json({ success: true, message: 'এডমিন ওটিপি বাইপাস সক্রিয়। অনুগ্রহ করে পাসওয়ার্ড দিন।' });
    }

    let targetUserId = null;
    if (cleanedContact) {
      const userRecord = await findUserByContact(cleanedContact);
      if (userRecord) {
        targetUserId = userRecord.id;
      }
    }

    // ── DEVICE-BINDING GATEKEEPER ────────────────────────────
    if (deviceId) {
      const boundDevices = await query('SELECT user_id FROM registered_devices WHERE device_id = ? LIMIT 1', [deviceId]);
      let linkedUserId = boundDevices.length > 0 ? boundDevices[0].user_id : null;
      if (linkedUserId) {
        const userExists = await query('SELECT id FROM users WHERE id = ? LIMIT 1', [linkedUserId]);
        if (userExists.length === 0) {
          console.log(`[Device Binding] User ${linkedUserId} does not exist (deleted). Cleaning up registered_devices...`);
          await query('DELETE FROM registered_devices WHERE user_id = ?', [linkedUserId]);
          await query('DELETE FROM device_trial_logs WHERE user_id = ?', [linkedUserId]);
          linkedUserId = null;
        }
      }
      if (linkedUserId && parseInt(linkedUserId, 10) !== parseInt(targetUserId, 10)) {
        const { boundPhones, boundEmails } = await getBoundCredentials(linkedUserId);
        return res.status(403).json({
          success: false,
          action: 'DEVICE_ALREADY_BOUND',
          message: 'ডিভাইস লিংক নোটিশ: নিরাপত্তা ও পলিসিগত কারণে আমাদের সিস্টেমে একটি hardware ডিভাইসের সাথে কেবল একটি মূল অ্যাকাউন্টই যুক্ত রাখা সম্ভব। আপনার এই ডিভাইসটি ইতিমধ্যে নিচের অ্যাকাউন্টটির সাথে নিবন্ধিত ও লিংক করা রয়েছে। অনুগ্রহ করে পূর্বের লিংক করা অ্যাকাউন্টটি ব্যবহার করে লগইন সম্পন্ন করুন। এই ডিভাইসে নতুন কোনো অ্যাকাউন্ট তৈরি বা অন্য অ্যাকাউন্ট ব্যবহার করা যাবে না।',
          boundPhones,
          boundEmails
        });
      }
    }

    if (!contact) {
      return res.status(400).json({ error: 'Contact is required' });
    }

    // Verify user exists via unified credentials
    const userRecord = await findUserByContact(cleanedContact);
    if (!userRecord) {
      return res.status(404).json({
        success: false,
        action: 'SHOW_NOT_FOUND_DIALOG',
        message: 'অ্যাকাউন্টটি খুঁজে পাওয়া যায়নি।'
      });
    }

    const otpCode = generateOtpCode();
    await query(
      'INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 5 MINUTE))',
      [cleanedContact, encryptOtp(otpCode)]
    );

    // Dispatch OTP via SMS or Email
    const success = await sendOtpDispatch(cleanedContact, otpCode);
    if (!success) {
      const isEmail = cleanedContact.includes('@');
      if (isEmail) {
        return res.status(503).json({
          success: false,
          action: 'EMAIL_ALL_FAILED',
          message: 'ইমেইল OTP পাঠানো সম্ভব হয়নি। মোবাইল নম্বর দিয়ে লগইন করুন অথবা সাপোর্টে যোগাযোগ করুন।'
        });
      } else {
        return res.status(503).json({
          success: false,
          action: 'SMS_ALL_FAILED',
          message: 'এসএমএস সার্ভারে কাজ চলছে, অনুগ্রহ করে জিমেইল মেথড ব্যবহার করুন অথবা সাপোর্টে যোগাযোগ করুন।'
        });
      }
    }

    return res.json({ success: true, message: 'ওটিপি কোড সফলভাবে পাঠানো হয়েছে।' });
  } catch (error) {
    console.error('Error sending OTP:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * 3. POST /api/auth/register-send-otp
 * Sends/generates OTP for new user registration.
 */
async function registerSendOtp(req, res) {
  try {
    const { contact, deviceId, androidId, hardwareFingerprint, simSlotIds } = req.body;

    const cleanedContact = contact ? contact.trim() : '';

    // Fix 2: Non-blocking cleanup of stale incomplete accounts on every OTP request
    cleanupIncompleteAccounts().catch(() => {});

    let targetUserId = null;
    if (cleanedContact) {
      const userRecord = await findUserByContact(cleanedContact);
      if (userRecord) {
        targetUserId = userRecord.id;
      }
    }

    // ── DEVICE-BINDING GATEKEEPER ────────────────────────────
    if (deviceId) {
      const boundDevices = await query('SELECT user_id FROM registered_devices WHERE device_id = ? LIMIT 1', [deviceId]);
      let linkedUserId = boundDevices.length > 0 ? boundDevices[0].user_id : null;
      if (linkedUserId) {
        const userExists = await query('SELECT id FROM users WHERE id = ? LIMIT 1', [linkedUserId]);
        if (userExists.length === 0) {
          console.log(`[Device Binding] User ${linkedUserId} does not exist (deleted). Cleaning up registered_devices...`);
          await query('DELETE FROM registered_devices WHERE user_id = ?', [linkedUserId]);
          await query('DELETE FROM device_trial_logs WHERE user_id = ?', [linkedUserId]);
          linkedUserId = null;
        }
      }
      if (linkedUserId && parseInt(linkedUserId, 10) !== parseInt(targetUserId, 10)) {
        const { boundPhones, boundEmails } = await getBoundCredentials(linkedUserId);
        return res.status(403).json({
          success: false,
          action: 'DEVICE_ALREADY_BOUND',
          message: 'ডিভাইস লিংক নোটিশ: নিরাপত্তা ও পলিসিগত কারণে আমাদের সিস্টেমে একটি hardware ডিভাইসের সাথে কেবল একটি মূল অ্যাকাউন্টই যুক্ত রাখা সম্ভব। আপনার এই ডিভাইসটি ইতিমধ্যে নিচের অ্যাকাউন্টটির সাথে নিবন্ধিত ও লিংক করা রয়েছে। অনুগ্রহ করে পূর্বের লিংক করা অ্যাকাউন্টটি ব্যবহার করে লগইন সম্পন্ন করুন। এই ডিভাইসে নতুন কোনো অ্যাকাউন্ট তৈরি বা অন্য অ্যাকাউন্ট ব্যবহার করা যাবে না।',
          boundPhones,
          boundEmails
        });
      }
    }

    if (!contact) {
      return res.status(400).json({ error: 'Contact is required' });
    }

    // Fix 3: Same-contact retry — check profile_complete before blocking
    const existingUser = await findUserByContact(cleanedContact);

    if (existingUser) {
      if (existingUser.profile_complete === 0) {
        // Incomplete signup: OTP verified but signup form was never submitted.
        // Delete the stale record so the user can retry with a fresh OTP.
        await query('DELETE FROM users WHERE id = ?', [existingUser.id]);
        console.log('[Register] Deleted incomplete account id=' + existingUser.id + ' — contact: ' + cleanedContact);
        // Fall through → send fresh OTP below
      } else {
        // profile_complete = 1 → active, fully completed account
        return res.status(400).json({
          success: false,
          message: 'এই মোবাইল বা ইমেইল দিয়ে ইতিমধ্যে অ্যাকাউন্ট খোলা হয়েছে।'
        });
      }
    }

    const otpCode = generateOtpCode();
    await query(
      'INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 5 MINUTE))',
      [cleanedContact, encryptOtp(otpCode)]
    );

    // Dispatch OTP
    const success = await sendOtpDispatch(cleanedContact, otpCode);
    if (!success) {
      const isEmail = cleanedContact.includes('@');
      if (isEmail) {
        return res.status(503).json({
          success: false,
          action: 'EMAIL_ALL_FAILED',
          message: 'ইমেইল OTP পাঠানো সম্ভব হয়নি। মোবাইল নম্বর দিয়ে লগইন করুন অথবা সাপোর্টে যোগাযোগ করুন।'
        });
      } else {
        return res.status(503).json({
          success: false,
          action: 'SMS_ALL_FAILED',
          message: 'এসএমএস সার্ভারে কাজ চলছে, অনুগ্রহ করে জিমেইল মেথড ব্যবহার করুন অথবা সাপোর্টে যোগাযোগ করুন।'
        });
      }
    }

    return res.json({ success: true, message: 'রেজিস্ট্রেশন ওটিপি সফলভাবে পাঠানো হয়েছে।' });
  } catch (error) {
    console.error('Error sending registration OTP:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * 3.2. POST /api/send-otp-new
 * Legacy fallback endpoint for registration OTP.
 */
async function sendOtpNew(req, res) {
  return registerSendOtp(req, res);
}

/**
 * 4. POST /api/verify-otp
 * Verifies OTP and registers user/device profiles.
 */
async function verifyOtp(req, res) {
  try {
    const { contact, code, deviceId, deviceModel, androidVersion, fingerprint, androidId, hardwareFingerprint, simSlotIds } = req.body;
    const cleanedContact = contact ? contact.trim() : '';

    const adminSecretUsername = process.env.ADMIN_SECRET_USERNAME || 'admin';

    // 👑 ADMIN BYPASS GATEKEEPER & TIME-SLOT PASS ENGINE
    const adminEmail = process.env.ADMIN_EMAIL || '';
    const isAdminEmail = (adminEmail && cleanedContact === adminEmail.trim());

    if (cleanedContact === adminSecretUsername || isAdminEmail) {
      if (isAdminEmail) {
        if (!code) {
          return res.status(400).json({ error: 'Code is required' });
        }
        
        const records = await query(
          'SELECT * FROM otps WHERE contact = ? AND used_at IS NULL AND expires_at > NOW() ORDER BY id DESC LIMIT 1',
          [cleanedContact]
        );

        if (records.length === 0) {
          return res.status(401).json({ error: 'Wrong Code', message: 'Wrong OTP Code' });
        }

        const record = records[0];
        const decryptedCode = decryptOtp(record.code);
        if (code.trim() !== decryptedCode) {
          return res.status(401).json({ error: 'Wrong Code', message: 'Wrong OTP Code' });
        }

        // Mark OTP as used
        await query('UPDATE otps SET used_at = NOW() WHERE id = ?', [record.id]);
      } else {
        if (!code) {
          return res.status(401).json({ error: 'Wrong Password', message: 'Wrong Password' });
        }
        const cleanedCode = code.trim();
        const duration = req.body.duration !== undefined ? parseInt(req.body.duration, 10) : NaN;

        let expectedPass = '';
        if (!isNaN(duration)) {
          if (duration >= 0 && duration <= 5) {
            expectedPass = process.env.ADMIN_SLOT1_PASS || 'boss_gate_0_30';
          } else if (duration >= 6 && duration <= 30) {
            expectedPass = process.env.ADMIN_SLOT2_PASS || 'boss_gate_31_60';
          } else if (duration >= 31 && duration <= 60) {
            expectedPass = process.env.ADMIN_SLOT3_PASS || 'boss_gate_61_120';
          }
        }

        if (!expectedPass || cleanedCode !== expectedPass) {
          return res.status(401).json({ error: 'Wrong Password', message: 'Wrong Password' });
        }
      }

      const token = jwt.sign(
        { userId: 0, role: 'admin', deviceId: deviceId || 'admin-device' },
        JWT_SECRET,
        { expiresIn: '99y' }
      );

      return res.json({
        token,
        requiresSecurityPin: false,
        user: {
          id: 0, name: 'Global Admin', phone: adminSecretUsername,
          email: adminEmail || 'admin@paychek.online', role: 'admin', balance: 0.00,
          blocked: 0, profileComplete: 1, smsEnabled: 1, gmailEnabled: 1
        },
        device: {
          id: 0, userId: 0, deviceId: deviceId || 'admin-device',
          deviceName: 'Admin Dashboard Console', status: 'active',
          isParent: true, isApproved: true, deviceRole: 'owner',
          isTrialLocked: false, trialExpiresAt: null, lockReason: null,
          isOwnerDevice: true, deviceSpecificPin: null
        }
      });
    }

    // --- REGULAR USER CHECK ---
    if (!deviceId) {
      return res.status(400).json({ error: 'Missing required deviceId field' });
    }

    let targetUserId = null;
    if (cleanedContact) {
      const userRecord = await findUserByContact(cleanedContact);
      if (userRecord) {
        targetUserId = userRecord.id;
      }
    }

    // ── DEVICE-BINDING GATEKEEPER ────────────────────────────
    if (deviceId) {
      const boundDevices = await query('SELECT user_id FROM registered_devices WHERE device_id = ? LIMIT 1', [deviceId]);
      let linkedUserId = boundDevices.length > 0 ? boundDevices[0].user_id : null;
      if (linkedUserId) {
        const userExists = await query('SELECT id FROM users WHERE id = ? LIMIT 1', [linkedUserId]);
        if (userExists.length === 0) {
          console.log(`[Device Binding] User ${linkedUserId} does not exist (deleted). Cleaning up registered_devices...`);
          await query('DELETE FROM registered_devices WHERE user_id = ?', [linkedUserId]);
          await query('DELETE FROM device_trial_logs WHERE user_id = ?', [linkedUserId]);
          linkedUserId = null;
        }
      }
      if (linkedUserId && parseInt(linkedUserId, 10) !== parseInt(targetUserId, 10)) {
        const { boundPhones, boundEmails } = await getBoundCredentials(linkedUserId);
        return res.status(403).json({
          success: false,
          action: 'DEVICE_ALREADY_BOUND',
          message: 'ডিভাইস লিঙ্ক নোটিশ: নিরাপত্তা ও পলিসিগত কারণে আমাদের সিস্টেমে একটি hardware ডিভাইসের সাথে কেবল একটি মূল অ্যাকাউন্টই যুক্ত রাখা সম্ভব।',
          boundPhones,
          boundEmails
        });
      }
    }

    if (!contact || !code) {
      return res.status(400).json({ error: 'Missing required contact or code fields' });
    }

    const cleanedCode = code.trim();
    const activeOtps = await query(
      'SELECT * FROM otps WHERE contact = ? AND expires_at > NOW() AND used_at IS NULL ORDER BY id DESC',
      [cleanedContact]
    );
    const matchedOtp = activeOtps.find(otp => decryptOtp(otp.code) === cleanedCode);
    if (!matchedOtp) {
      return res.status(400).json({ error: 'ভুল ওটিপি কোড অথবা ওটিপির মেয়াদ শেষ হয়ে গেছে।' });
    }
    await query('UPDATE otps SET used_at = NOW() WHERE id = ?', [matchedOtp.id]);

    // Check if user exists, if not, create new pending user (profile_complete=0)
    let user = await findUserByContact(cleanedContact);
    let isNewUser = false;
    if (!user) {
      isNewUser = true;
      const isEmail = cleanedContact.includes('@');
      const insertResult = await query(
        "INSERT INTO users (name, phone, email, role, profile_complete, is_paid, active_plan_name, expiry_date) VALUES (?, ?, ?, ?, 0, 0, 'FREE_LEVEL', NULL)",
        ['', isEmail ? null : cleanedContact, isEmail ? cleanedContact : null, 'user']
      );
      const newUserId = insertResult.insertId;
      await query(
        'INSERT INTO user_credentials (user_id, type, value, verified_at) VALUES (?, ?, ?, NOW())',
        [newUserId, isEmail ? 'email' : 'phone', cleanedContact]
      );
      const newUsers = await query('SELECT * FROM users WHERE id = ? LIMIT 1', [newUserId]);
      user = newUsers[0];
    }

    // Check blocked status
    if (user.blocked) {
      return res.status(403).json({ error: 'আপনার অ্যাকাউন্টটি ব্লক করা হয়েছে। অনুগ্রহ করে অ্যাডমিনের সাথে যোগাযোগ করুন।' });
    }

    // ── Fix 1: Device binding gated on profile completion ────────────────────
    // NEW user (profile_complete=0): Do NOT bind device yet.
    //   Return a PROFILE_PENDING token — completeProfile() will bind the device.
    // EXISTING user (profile_complete=1): Proceed with full device registration.
    // ─────────────────────────────────────────────────────────────────────────
    if (isNewUser || user.profile_complete === 0) {
      // Generate a short-lived pending-signup token (no device, no secretKey yet)
      const pendingToken = jwt.sign(
        {
          userId:   user.id,
          role:     user.role,
          deviceId: deviceId, // stored so completeProfile can use it
          phase:    'PROFILE_PENDING'
        },
        JWT_SECRET,
        { expiresIn: '2h' }  // short-lived — just for the signup form session
      );
      console.log(`[AUTH] New user OTP verified — returning PROFILE_PENDING token (userId=${user.id})`);
      return res.json({
        token:              pendingToken,
        requiresSecurityPin: false,
        phase:              'PROFILE_PENDING', // App checks this to navigate to SignupScreen
        user: {
          id:              user.id,
          name:            user.name,
          phone:           user.phone,
          email:           user.email,
          role:            user.role,
          profileComplete: 0
        }
      });
    }

    // ── Existing user: full device registration flow (unchanged) ─────────────
    const trialDaysConfig = await query(
      "SELECT config_value FROM global_config WHERE config_key = 'trial_days' LIMIT 1"
    );
    const trialDays = trialDaysConfig.length > 0 ? parseInt(trialDaysConfig[0].config_value, 10) : TRIAL_DEFAULT_DAYS;

    let device;
    const devices = await query(
      'SELECT * FROM registered_devices WHERE user_id = ? AND device_id = ? LIMIT 1',
      [user.id, deviceId]
    );

    if (devices.length === 0) {
      const userDevicesCount = await query(
        'SELECT COUNT(*) as count FROM registered_devices WHERE user_id = ?',
        [user.id]
      );
      
      const isParent = userDevicesCount[0].count === 0 ? 1 : 0;

      if (isParent === 0) {
        if (user.is_paid === 0 || user.active_plan_name === 'FREE_LEVEL') {
          return res.status(403).json({
            success: false,
            error: 'LIMIT_EXCEEDED',
            message: '👑 লিমিট শেষ! আরও সাইট বা ডিভাইস যুক্ত করতে অনুগ্রহ করে আপনার প্যাকেজটি আপগ্রেড করুন।'
          });
        }
        
        let maxDevices = 1;
        if (user.active_plan_name === 'Trial Package') {
          const configVal = await query("SELECT config_value FROM global_config WHERE config_key = 'trial_max_devices' LIMIT 1");
          maxDevices = configVal.length > 0 ? parseInt(configVal[0].config_value, 10) : 1;
        } else {
          const plans = await query(
            "SELECT max_devices FROM subscription_plans WHERE plan_name = ? LIMIT 1",
            [user.active_plan_name]
          );
          maxDevices = plans.length > 0 ? plans[0].max_devices : 1;
        }
        
        if (userDevicesCount[0].count >= maxDevices) {
          return res.status(403).json({
            success: false,
            error: 'LIMIT_EXCEEDED',
            message: '👑 লিমিট শেষ! আরও সাইট বা ডিভাইস যুক্ত করতে অনুগ্রহ করে আপনার প্যাকেজটি আপগ্রেড করুন।'
          });
        }
      }

      const initialStatus = isParent ? 'active' : 'pending';
      const trialStartedAt = new Date();
      const trialExpiresAt = new Date();
      trialExpiresAt.setDate(trialStartedAt.getDate() + trialDays);

      const insertDeviceResult = await query(
        `INSERT INTO registered_devices 
          (user_id, device_id, device_name, device_model, android_version, status, is_parent, is_approved, device_role, trial_started_at, trial_expires_at, is_trial_locked, is_owner_device) 
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          user.id, deviceId,
          isParent ? 'Main Phone' : 'Co-Parent Device',
          deviceModel || 'Unknown Model',
          androidVersion || 'Android',
          initialStatus, isParent, isParent ? 1 : 0,
          isParent ? 'owner' : 'pending',
          trialStartedAt, trialExpiresAt, 0,
          isParent ? 1 : 0
        ]
      );

      const freshDevices = await query('SELECT * FROM registered_devices WHERE id = ?', [insertDeviceResult.insertId]);
      device = freshDevices[0];

      if (isParent === 1) {
        const existingKey = await query("SELECT secretKey FROM users WHERE id = ? LIMIT 1", [user.id]);
        if (!existingKey[0]?.secretKey) {
          const newSecretKey = crypto.randomBytes(32).toString('hex');
          await query("UPDATE users SET secretKey = ? WHERE id = ?", [newSecretKey, user.id]);
          user.secretKey = newSecretKey;
          console.log(`[AUTH] ✅ Generated secretKey for existing parent re-login (userId=${user.id})`);
        }
      }
    } else {
      device = devices[0];

      await query(
        'UPDATE registered_devices SET last_seen_at = NOW(), device_model = ?, android_version = ? WHERE id = ?',
        [deviceModel, androidVersion, device.id]
      );

      if (device.trial_expires_at && new Date(device.trial_expires_at) < new Date() && !device.is_trial_locked) {
        await query(
          "UPDATE registered_devices SET is_trial_locked = 1, lock_reason = 'trial_expired' WHERE id = ?",
          [device.id]
        );
        device.is_trial_locked = 1;
        device.lock_reason = 'trial_expired';
      }

      if (device.is_parent === 1) {
        const keyRow = await query(
          'SELECT secretKey, secretKeyVersion FROM users WHERE id = ? LIMIT 1',
          [user.id]
        );
        const existingSecretKey = keyRow[0]?.secretKey;
        if (existingSecretKey) {
          user.secretKey        = existingSecretKey;
          user.secretKeyVersion = keyRow[0]?.secretKeyVersion || 1;
          console.log(`[AUTH] 🔑 Returning existing secretKey (v${user.secretKeyVersion}) for parent re-login — userId: ${user.id}`);
        } else {
          const newKey = crypto.randomBytes(32).toString('hex');
          await query(
            'UPDATE users SET secretKey = ?, secretKeyCreatedAt = NOW(), secretKeyVersion = 1 WHERE id = ?',
            [newKey, user.id]
          );
          user.secretKey        = newKey;
          user.secretKeyVersion = 1;
          console.log(`[AUTH] ⚠️ Generated missing secretKey for legacy parent device — userId: ${user.id}`);
        }
      }
    }

    // Generate JWT token
    const token = jwt.sign(
       { userId: user.id, role: user.role, deviceId: device.device_id },
       JWT_SECRET,
       { expiresIn: '99y' }
     );

    return res.json({
      token,
      requiresSecurityPin: !!user.pin,
      // ── v2.1.0: secretKey — parent device login/reinstall সময়ে return করা হয় ──
      // নতুন bind: verifyOtp এর is_parent===1 branch এ generate ও return হয়
      // Reinstall recovery: existing-device branch এ DB থেকে তুলে return হয়
      // Child device: secretKey পাঠানো হয় না (is_parent === 0)
      ...(device.is_parent === 1 && user.secretKey
        ? { secretKey: user.secretKey, secretKeyVersion: user.secretKeyVersion || 1 }
        : {}),
      user: {
        id: user.id,
        name: user.name,
        phone: user.phone,
        email: user.email,
        role: user.role,
        is_paid: !!user.is_paid,
        active_plan_name: user.active_plan_name,
        expiry_date: user.expiry_date,
        blocked: !!user.blocked,
        profileComplete: !!user.profile_complete,
        smsEnabled: !!user.sms_enabled,
        gmailEnabled: !!user.gmail_enabled
      },
      device: {
        id: device.id,
        userId: device.user_id,
        deviceId: device.device_id,
        deviceName: device.custom_name || device.device_name,
        status: device.status,
        isParent: !!device.is_parent,
        isApproved: device.is_approved === 1 || device.is_approved === true,
        deviceRole: device.device_role || 'pending',
        isTrialLocked: !!device.is_trial_locked,
        trialExpiresAt: device.trial_expires_at,
        lockReason: device.lock_reason,
        isOwnerDevice: device.is_owner_device === 1,
        deviceSpecificPin: device.device_specific_pin
      }
    });

  } catch (error) {
    console.error('Error verifying OTP:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * 5. POST /api/complete-profile
 * Completes profile details (Name, Security PIN) for new registrations.
 */
async function completeProfile(req, res) {
  try {
    const { name, pin, phone, email, deviceId, androidId, hardwareFingerprint, simSlotIds, fingerprint } = req.body;
    const userId = req.user.userId; // Populated by JWT authentication middleware

    if (!name || !pin) {
      return res.status(400).json({ error: 'Name and PIN are required' });
    }

    if (pin.length < 4 || pin.length > 6 || isNaN(pin)) {
      return res.status(400).json({ error: 'PIN must be between 4 and 6 digits' });
    }

    // Fetch current pending user details
    const users = await query('SELECT * FROM users WHERE id = ? LIMIT 1', [userId]);
    if (users.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }
    const user = users[0];

    let finalPhone = user.phone;
    let finalEmail = user.email;

    // Conditional Validation logic
    if (user.email && !user.phone) {
      // Email signup: mobile number (phone) is MANDATORY
      if (!phone || !phone.trim()) {
        return res.status(400).json({ error: 'মোবাইল নম্বর প্রদান করা বাধ্যতামূলক।' });
      }
      finalPhone = phone.trim();
      if (!/^01[3-9]\d{8}$/.test(finalPhone)) {
        return res.status(400).json({ error: 'অনুগ্রহ করে সঠিক মোবাইল নম্বর দিন।' });
      }

      // Check if phone number is already registered by someone else
      const duplicateUsers = await query(
        'SELECT id FROM users WHERE phone = ? AND id != ? LIMIT 1',
        [finalPhone, userId]
      );
      if (duplicateUsers.length > 0) {
        return res.status(400).json({ error: 'এই মোবাইল নম্বরটি ইতিমধ্যে অন্য অ্যাকাউন্টে ব্যবহার করা হয়েছে।' });
      }
    } else if (user.phone && !user.email) {
      // Mobile signup: email is OPTIONAL. If provided, validate formatting & uniqueness
      if (email && email.trim()) {
        finalEmail = email.trim().toLowerCase();
        if (!finalEmail.includes('@')) {
          return res.status(400).json({ error: 'অনুগ্রহ করে সঠিক ইমেইল ঠিকানা দিন।' });
        }
        
        const duplicateUsers = await query(
          'SELECT id FROM users WHERE email = ? AND id != ? LIMIT 1',
          [finalEmail, userId]
        );
        if (duplicateUsers.length > 0) {
          return res.status(400).json({ error: 'এই ইমেইল ঠিকানাটি ইতিমধ্যে অন্য অ্যাকাউন্টে ব্যবহার করা হয়েছে।' });
        }
      }
    }

    // Fetch Trial days config from global_config
    const trialDaysConfig = await query("SELECT config_value FROM global_config WHERE config_key = 'trial_days' LIMIT 1");
    const trialDays = trialDaysConfig.length > 0 ? parseInt(trialDaysConfig[0].config_value, 10) : 7;

    // Hash the 6-digit PIN
    const hashedPin = await bcrypt.hash(pin, 10);

    // Update user record and auto-activate Trial Package
    if (trialDays === 0) {
      await query(
        `UPDATE users SET name = ?, pin = ?, phone = ?, email = ?, profile_complete = 1, 
         is_paid = 0, active_plan_name = 'Trial Package', expiry_date = NOW() WHERE id = ?`,
        [name.trim(), hashedPin, finalPhone, finalEmail, userId]
      );
    } else {
      await query(
        `UPDATE users SET name = ?, pin = ?, phone = ?, email = ?, profile_complete = 1, 
         is_paid = 1, active_plan_name = 'Trial Package', expiry_date = DATE_ADD(NOW(), INTERVAL ? DAY) WHERE id = ?`,
        [name.trim(), hashedPin, finalPhone, finalEmail, trialDays, userId]
      );
    }

    // Sync verified phone/email to user_credentials table so the user can log in with either
    if (finalPhone) {
      await query(
        "INSERT INTO user_credentials (user_id, type, value, verified_at) VALUES (?, 'phone', ?, NOW()) ON DUPLICATE KEY UPDATE verified_at = NOW()",
        [userId, finalPhone]
      );
    }
    if (finalEmail) {
      await query(
        "INSERT INTO user_credentials (user_id, type, value, verified_at) VALUES (?, 'email', ?, NOW()) ON DUPLICATE KEY UPDATE verified_at = NOW()",
        [userId, finalEmail]
      );
    }

    // Removed: Auto-insert default gateway methods dynamically from official templates

    // Fetch updated user details
    const updatedUsers = await query('SELECT * FROM users WHERE id = ? LIMIT 1', [userId]);
    const updatedUser = updatedUsers[0];

    // ── Fix 1: Device bind happens HERE (not in verifyOtp) for new users ─────
    // JWT phase='PROFILE_PENDING' path: deviceId was embedded in the token.
    // Bind the device now that profile is fully complete.
    const jwtPhase = req.user.phase || null;
    if (jwtPhase === 'PROFILE_PENDING' && deviceId) {
      try {
        const existingDevice = await query(
          'SELECT id FROM registered_devices WHERE user_id = ? AND device_id = ? LIMIT 1',
          [userId, deviceId]
        );
        if (existingDevice.length === 0) {
          const trialDaysConfig = await query(
            "SELECT config_value FROM global_config WHERE config_key = 'trial_days' LIMIT 1"
          );
          const trialDays = trialDaysConfig.length > 0
            ? parseInt(trialDaysConfig[0].config_value, 10)
            : TRIAL_DEFAULT_DAYS;

          const trialStartedAt = new Date();
          const trialExpiresAt = new Date();
          trialExpiresAt.setDate(trialStartedAt.getDate() + trialDays);

          await query(
            `INSERT INTO registered_devices
              (user_id, device_id, device_name, device_model, android_version,
               status, is_parent, is_approved, device_role,
               trial_started_at, trial_expires_at, is_trial_locked, is_owner_device)
             VALUES (?, ?, 'Main Phone', 'Unknown Model', 'Android', 'active', 1, 1, 'owner', ?, ?, 0, 1)`,
            [userId, deviceId, trialStartedAt, trialExpiresAt]
          );
          console.log(`[AUTH] ✅ Device bound on completeProfile (userId=${userId}, deviceId=${deviceId})`);

          // Generate secretKey now that device is bound
          const keyCheck = await query('SELECT secretKey FROM users WHERE id = ? LIMIT 1', [userId]);
          if (!keyCheck[0]?.secretKey) {
            const newSecretKey = crypto.randomBytes(32).toString('hex');
            await query(
              'UPDATE users SET secretKey = ?, secretKeyCreatedAt = NOW(), secretKeyVersion = 1 WHERE id = ?',
              [newSecretKey, userId]
            );
            console.log(`[AUTH] ✅ secretKey generated on completeProfile (userId=${userId})`);
          }
        }
      } catch (bindErr) {
        console.error('[AUTH] Device bind in completeProfile failed:', bindErr.message);
        // Non-fatal for the response — user profile is already saved
      }
    }

    // Record device trial logs on completion of profile
    if (androidId || deviceId || hardwareFingerprint || fingerprint) {
      try {
        const logAndroidId = androidId || deviceId || 'unknown_android_id';
        const logFingerprint = hardwareFingerprint || fingerprint || 'unknown_fingerprint';
        const logSimSlots = simSlotIds || 'no_sim';
        await query(
          `INSERT INTO device_trial_logs (user_id, android_id, hardware_fingerprint, sim_slot_ids, has_used_trial) 
           VALUES (?, ?, ?, ?, 1)
           ON DUPLICATE KEY UPDATE user_id = ?, has_used_trial = 1`,
          [userId, logAndroidId, logFingerprint, logSimSlots, userId]
        );
        console.log(`[DB] Recorded device trial log in completeProfile for user: ${userId}`);
      } catch (logErr) {
        console.error('[DB] Failed to insert device trial log in completeProfile:', logErr);
      }
    }

    const finalKeyCheck = await query('SELECT secretKey, secretKeyVersion FROM users WHERE id = ? LIMIT 1', [userId]);

    return res.json({
      success: true,
      secretKey: finalKeyCheck[0]?.secretKey || null,
      secretKeyVersion: finalKeyCheck[0]?.secretKeyVersion || 1,
      user: {
        id: updatedUser.id,
        name: updatedUser.name,
        phone: updatedUser.phone,
        email: updatedUser.email,
        role: updatedUser.role,
        is_paid: !!updatedUser.is_paid,
        active_plan_name: updatedUser.active_plan_name,
        expiry_date: updatedUser.expiry_date,
        blocked: !!updatedUser.blocked,
        profileComplete: !!updatedUser.profile_complete,
        smsEnabled: !!updatedUser.sms_enabled,
        gmailEnabled: !!updatedUser.gmail_enabled
      }
    });
  } catch (error) {
    console.error('Error completing profile:', error);
    try {
      console.log(`[AUTH] Rolling back profile completion for user ${userId} due to error...`);
      await query('UPDATE users SET profile_complete = 0 WHERE id = ?', [userId]);
      await query('DELETE FROM gateway_methods WHERE user_id = ?', [userId]);
      await query('DELETE FROM registered_devices WHERE user_id = ? AND status = "active"', [userId]);
    } catch (cleanupErr) {
      console.error('[AUTH] Failed to cleanup incomplete profile record:', cleanupErr.message);
    }
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function isTrialAbused(deviceId, androidId, hardwareFingerprint, simSlotIds, targetUserId = null) {
  try {
    const safeDeviceId = deviceId || '';
    const safeAndroidId = androidId || '';
    const safeHardwareFingerprint = hardwareFingerprint || '';
    const safeSimSlotIds = simSlotIds || '';

    let linkedUserId = null;
    let isLockedOrExpired = false;

    // 1. Check if the device exists in registered_devices
    if (safeDeviceId) {
      const devices = await query(
        'SELECT user_id, trial_expires_at, is_trial_locked FROM registered_devices WHERE device_id = ? LIMIT 1',
        [safeDeviceId]
      );
      if (devices && devices.length > 0) {
        const dev = devices[0];
        linkedUserId = dev.user_id || null;
        const expired = dev.trial_expires_at && new Date(dev.trial_expires_at) < new Date();
        if (dev.is_trial_locked || expired) {
          isLockedOrExpired = true;
        }
      }
    }

    // 2. Check if the device exists in device_trial_logs
    if (!linkedUserId) {
      const checkFields = [];
      const checkParams = [];
      
      if (safeAndroidId && safeAndroidId !== 'unknown_android_id' && safeAndroidId !== '') {
        checkFields.push('android_id = ?');
        checkParams.push(safeAndroidId);
      }
      if (safeHardwareFingerprint && safeHardwareFingerprint !== 'unknown_fingerprint' && safeHardwareFingerprint !== '') {
        checkFields.push('hardware_fingerprint = ?');
        checkParams.push(safeHardwareFingerprint);
      }
      
      const isValidSimSlotId = safeSimSlotIds && 
                               safeSimSlotIds !== 'no_sims' && 
                               safeSimSlotIds !== 'no_sim' && 
                               safeSimSlotIds !== 'permission_denied' && 
                               safeSimSlotIds !== 'unknown' &&
                               safeSimSlotIds !== '';
      if (isValidSimSlotId) {
        checkFields.push('sim_slot_ids = ?');
        checkParams.push(safeSimSlotIds);
      }

      if (checkFields.length > 0) {
        const queryStr = `SELECT user_id FROM device_trial_logs WHERE (${checkFields.join(' OR ')}) AND has_used_trial = 1 LIMIT 1`;
        const logs = await query(queryStr, checkParams);
        if (logs && logs.length > 0) {
          linkedUserId = logs[0].user_id || null;
        }
      }
    }

    // If there is a linked user for this device:
    if (linkedUserId) {
      const userExists = await query('SELECT id FROM users WHERE id = ? LIMIT 1', [linkedUserId]);
      if (userExists.length === 0) {
        console.log(`[Device Binding] User ${linkedUserId} does not exist (deleted). Cleaning up trial logs...`);
        if (safeDeviceId) {
          await query('DELETE FROM registered_devices WHERE user_id = ?', [linkedUserId]);
        }
        await query('DELETE FROM device_trial_logs WHERE user_id = ?', [linkedUserId]);
        linkedUserId = null;
      }
    }

    if (linkedUserId) {
      if (targetUserId !== null) {
        // If a contact ID is provided, verify ownership
        if (parseInt(targetUserId, 10) === parseInt(linkedUserId, 10)) {
          // Owner is allowed to bypass and log back in
          return { abused: false, userId: null };
        } else {
          // Trying to access another account or register a new one: Block!
          console.log(`[Device Binding] Blocked device usage. Target user ${targetUserId} !== Linked owner ${linkedUserId}`);
          return { abused: true, userId: linkedUserId };
        }
      } else {
        // If checking device trial / login on app startup (targetUserId is null):
        // Do not block at GATE 1 (checkDeviceLogin) because we don't know the contact yet.
        // Let them pass to GATE 2 (checkContact) where they will be verified by contact ownership.
        return { abused: false, userId: null };
      }
    }

    return { abused: false, userId: null };
  } catch (error) {
    console.error('Error in isTrialAbused:', error);
    return { abused: false, userId: null };
  }
}

/**
 * Helper: Masks phone/email for safe client display.
 * Phone  : keeps first 3 and last 3 digits → 017****541
 * Email  : keeps first 2 chars + domain part → de*****er@gmail.com
 */
function maskCredential(value, type) {
  if (!value) return '';
  if (type === 'phone') {
    if (value.length <= 6) return value;
    const start = value.slice(0, 3);
    const end   = value.slice(-3);
    const stars = '*'.repeat(Math.max(4, value.length - 6));
    return `${start}${stars}${end}`;
  }
  if (type === 'email') {
    const atIdx = value.indexOf('@');
    if (atIdx <= 2) return value;
    const localPart  = value.slice(0, atIdx);
    const domainPart = value.slice(atIdx); // includes @
    const visible    = localPart.slice(0, 2);
    const stars      = '*'.repeat(Math.max(3, localPart.length - 2));
    return `${visible}${stars}${domainPart}`;
  }
  return value;
}

/**
 * Helper: Fetches all verified credentials for a user and returns masked arrays.
 * Sources: users.phone / users.email  +  user_credentials table.
 * Returns: { boundPhones: String[], boundEmails: String[] }
 */
async function getBoundCredentials(userId) {
  if (!userId) return { boundPhones: [], boundEmails: [] };

  const phones = new Set();
  const emails = new Set();

  // Primary credentials from users table
  try {
    const users = await query('SELECT phone, email FROM users WHERE id = ? LIMIT 1', [userId]);
    if (users.length > 0) {
      if (users[0].phone) phones.add(maskCredential(users[0].phone, 'phone'));
      if (users[0].email) emails.add(maskCredential(users[0].email, 'email'));
    }
  } catch (_) { /* continue */ }

  // Additional credentials from user_credentials table
  try {
    const creds = await query(
      "SELECT type, value FROM user_credentials WHERE user_id = ? AND verified_at IS NOT NULL",
      [userId]
    );
    for (const cred of creds) {
      if (cred.type === 'phone') phones.add(maskCredential(cred.value, 'phone'));
      if (cred.type === 'email') emails.add(maskCredential(cred.value, 'email'));
    }
  } catch (_) { /* continue */ }

  return {
    boundPhones: [...phones].filter(Boolean),
    boundEmails: [...emails].filter(Boolean)
  };
}

/**
 * 6. POST /api/check-device-trial
 * Verifies if the device is trial-locked or if a trial was already claimed.
 */
async function checkDeviceTrial(req, res) {
  try {
    const { deviceId, androidId, hardwareFingerprint, simSlotIds } = req.body;
    
    const safeDeviceId = deviceId || '';
    const safeAndroidId = androidId || '';
    const safeHardwareFingerprint = hardwareFingerprint || '';
    const safeSimSlotIds = simSlotIds || '';

    if (!safeDeviceId) {
      return res.status(400).json({ error: 'Device ID is required' });
    }

    const { abused, userId: abuseUserId } = await isTrialAbused(safeDeviceId, safeAndroidId, safeHardwareFingerprint, safeSimSlotIds);
    if (abused) {
      const { boundPhones, boundEmails } = await getBoundCredentials(abuseUserId);
      return res.status(403).json({
        success: false,
        action: 'DEVICE_ALREADY_BOUND',
        message: 'ডিভাইস লিংক নোটিশ: নিরাপত্তা ও পলিসিগত কারণে আমাদের সিস্টেমে একটি hardware ডিভাইসের সাথে কেবল একটি মূল অ্যাকাউন্টই যুক্ত রাখা সম্ভব। আপনার এই ডিভাইসটি ইতিমধ্যে নিচের অ্যাকাউন্টটির সাথে নিবন্ধিত ও লিংক করা রয়েছে। অনুগ্রহ করে পূর্বের লিংক করা অ্যাকাউন্টটি ব্যবহার করে লগইন সম্পন্ন করুন। এই ডিভাইসে নতুন কোনো অ্যাকাউন্ট তৈরি বা অন্য অ্যাকাউন্ট ব্যবহার করা যাবে না।',
        boundPhones,
        boundEmails
      });
    }

    const devices = await query(
      'SELECT * FROM registered_devices WHERE device_id = ? LIMIT 1',
      [safeDeviceId]
    );

    if (devices && devices.length > 0) {
      const device = devices[0];

      // Auto update lock if trial expired
      if (device.trial_expires_at && new Date(device.trial_expires_at) < new Date() && !device.is_trial_locked) {
        await query(
          "UPDATE registered_devices SET is_trial_locked = 1, lock_reason = 'trial_expired' WHERE id = ?",
          [device.id]
        );
        device.is_trial_locked = 1;
        device.lock_reason = 'trial_expired';
      }

      // Do NOT block the owner at GATE 1 (login page) even if the trial is expired/locked.
      // The owner must be able to log in to add balance and purchase a package.
      // GATE 2 (checkContact) will block unauthorized users from this device.

      return res.json({
        success: true,
        abused: false,
        message: 'device-clean',
        trialAllowed: true,
        isLocked: false,
        lockReason: null
      });
    }

    // Device is brand new
    return res.json({
      success: true,
      abused: false,
      message: 'device-clean',
      trialAllowed: true,
      isLocked: false,
      lockReason: null
    });
  } catch (error) {
    console.error('Error checking device trial:', error);
    return res.status(200).json({
      success: true,
      abused: false,
      message: 'device-clean',
      trialAllowed: true,
      isLocked: false,
      lockReason: null
    });
  }
}

/**
 * Formats a Bangladeshi phone number based on gateway requirements.
 */
function formatBdNumber(contact, gatewayUrl, template, providerName) {
  let cleaned = contact.replace(/\D/g, '');
  const base11 = cleaned.startsWith('88') ? cleaned.substring(2) : cleaned;
  const with88 = '88' + base11;

  if (base11.length !== 11 || !base11.startsWith('01')) {
    return cleaned;
  }

  const urlStr = (gatewayUrl || '').toLowerCase();
  const tempStr = (template || '').toLowerCase();
  const nameStr = (providerName || '').toLowerCase();

  const demands11Digit = 
    urlStr.includes('88{to}') || 
    tempStr.includes('88{to}') || 
    urlStr.includes('88{msg}') ||
    tempStr.includes('88{msg}') ||
    nameStr.includes('11digit') || 
    nameStr.includes('trim') ||
    process.env.SMS_FORMAT_11_DIGIT === 'true';

  if (demands11Digit) {
    console.log(`[BD-FORMAT] Gateway demands 11 digits. Formatted number: ${base11}`);
    return base11;
  } else {
    console.log(`[BD-FORMAT] Gateway demands country code. Formatted number: ${with88}`);
    return with88;
  }
}

/**
 * Replaces placeholders in a template string.
 */
function replacePlaceholders(template, { to, message, code, apiKey, senderId, username }) {
  if (!template) return '';
  return template
    .replace(/{to}/g, to)
    .replace(/{message}/g, message)
    .replace(/{msg}/g, message)
    .replace(/{code}/g, code)
    .replace(/{api_key}/g, apiKey || '')
    .replace(/{sender_id}/g, senderId || '')
    .replace(/{sender}/g, senderId || '')
    .replace(/{username}/g, username || '');
}

/**
 * Executes a POST request using Axios with both application/x-www-form-urlencoded and application/json.
 */
async function executePostRequest(url, payloadObj, headers = {}, timeout = 15000) {
  // Try 1: application/x-www-form-urlencoded
  try {
    console.log(`[SMS-POST] Attempting POST to ${url.split('?')[0]} via application/x-www-form-urlencoded...`);
    const qs = require('querystring');
    const formData = qs.stringify(payloadObj);
    
    const res = await axios({
      method: 'POST',
      url,
      data: formData,
      headers: {
        ...headers,
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      timeout
    });
    if (res.status >= 200 && res.status < 300) {
      return { success: true, response: res };
    }
  } catch (err) {
    console.warn(`[SMS-POST] POST URL-encoded attempt failed:`, err.message);
  }
  
  // Try 2: application/json
  try {
    console.log(`[SMS-POST] Retrying POST to ${url.split('?')[0]} via application/json...`);
    const res = await axios({
      method: 'POST',
      url,
      data: payloadObj,
      headers: {
        ...headers,
        'Content-Type': 'application/json'
      },
      timeout
    });
    if (res.status >= 200 && res.status < 300) {
      return { success: true, response: res };
    }
  } catch (err) {
    console.error(`[SMS-POST] POST JSON retry attempt failed:`, err.message);
  }
  
  return { success: false };
}

/**
 * Helper: Dispatches OTP via Sequential Gmail Round-Robin or Single Active SMS Gateway
 *
 * EMAIL FLOW (Sequential Multi-Gmail):
 *  Layer 1 — DB Accounts (sequential by id ASC, skip if sent_today >= daily_limit)
 *  Layer 2 — .env SMTP Fallback (EMAIL_USER + EMAIL_PASS + SMTP_HOST)
 *  Layer 3 — Return false → caller sends EMAIL_ALL_FAILED
 *
 * SMS FLOW (One-Active Policy):
 *  Use whichever single gateway has is_active = 1
 *  Return false → caller sends SMS_ALL_FAILED
 */
async function sendOtpDispatch(contact, otpCode) {
  const isEmail = contact.includes('@');

  // ── Log locally always ───────────────────────────────────────────────────
  console.log(`[OTP] To: ${contact} | Code: ${otpCode}`);

  // ── LOAD DYNAMIC OTP FORMAT TEMPLATE FROM DATABASE ────────────────────────
  let otpTemplate = "আপনার প্রিয় পে-চেক অ্যাপ ভেরিফিকেশন কোড হলো: {otp}। কোডটি গোপন রাখুন।";
  try {
    const systemSettings = await query(
      "SELECT setting_value FROM otp_sms_templates WHERE setting_key = 'otp_format_template' LIMIT 1"
    );
    if (systemSettings && systemSettings.length > 0 && systemSettings[0].setting_value) {
      otpTemplate = systemSettings[0].setting_value;
    }
  } catch (dbErr) {
    console.error('[sendOtpDispatch] Error reading otp_format_template:', dbErr.message);
  }

  const customMessage = otpTemplate.replace(/{otp}/g, otpCode);

  // ══════════════════════════════════════════════════════════════════════════
  // EMAIL DISPATCH
  // ══════════════════════════════════════════════════════════════════════════
  if (isEmail) {

    // ── LAYER 1: Sequential Gmail Round-Robin (DB-driven) ──────────────────
    // সবচেয়ে কম ID-র অ্যাকাউন্ট থেকে শুরু করব এবং sent_today < daily_limit
    // না হলে পরবর্তী অ্যাকাউন্টে যাব। এটি সিকোয়েন্সিয়াল ফাসলাইন লজিক।
    try {
      const dbAccounts = await query(
        `SELECT * FROM smtp_gateways
         WHERE is_active = 1 AND (sent_today < daily_limit OR daily_limit IS NULL)
          ORDER BY id ASC
          LIMIT 10`  // max 10 accounts to iterate over
      );

      for (const acc of dbAccounts) {
        const sentToday = acc.sent_today || 0;
        const limit     = acc.daily_limit || 500;

        if (sentToday >= limit) {
          // এই অ্যাকাউন্টের দৈনিক কোটা পূর্ণ — পরবর্তীতে যাই
          console.log(`[GMAIL-SEQ] ${acc.email} quota full (${sentToday}/${limit}), skipping...`);
          continue;
        }

        console.log(`[GMAIL-SEQ] Attempting via: ${acc.email} (${sentToday}/${limit})`);

        try {
          const smtpConfig = {
            host:   acc.host,
            port:   acc.port,
            secure: acc.secure === 1,
            auth: {
              user: acc.email,
              pass: acc.password
            },
            // Connection timeout settings
            connectionTimeout: 10000,
            greetingTimeout:   5000,
            socketTimeout:     10000
          };

          if (acc.host && acc.host.toLowerCase().includes('gmail')) {
            smtpConfig.service = 'gmail';
          }

          const transporter = nodemailer.createTransport(smtpConfig);

          await transporter.sendMail({
            from:    `"Paychek" <${acc.email}>`,
            to:      contact,
            subject: 'Paychek Verification Code',
            text:    customMessage,
            html:    `<html><head><meta charset="utf-8"></head><body><p>${customMessage}</p></body></html>`
          });

          // সফলভাবে পাঠানো হয়েছে — sent_today বাড়াই
          await query(
            'UPDATE smtp_gateways SET sent_today = COALESCE(sent_today, 0) + 1 WHERE id = ?',
            [acc.id]
          );
          console.log(`[GMAIL-SEQ] Sent via ${acc.email} (now ${sentToday + 1}/${limit})`);
          return true;

        } catch (smtpErr) {
          // এই অ্যাকাউন্ট দিয়ে পাঠানো সম্ভব হয়নি — পরবর্তী অ্যাকাউন্টে যাই
          console.error(`[GMAIL-SEQ] ${acc.email} failed:`, smtpErr.message);
          // continue to next account
        }
      }

      // সব DB অ্যাকাউন্ট ব্যর্থ বা কোটা পূর্ণ
      if (dbAccounts.length === 0) {
        console.log('[GMAIL-SEQ] No active DB email accounts with remaining quota.');
      } else {
        console.log('[GMAIL-SEQ] All active DB accounts exhausted or failed. Falling back to .env...');
      }

    } catch (dbFetchErr) {
      console.error('[GMAIL-SEQ] DB fetch error:', dbFetchErr.message);
    }

    // ── LAYER 2: .env SMTP Failsafe Backup ────────────────────────────────
    // DB-র সমস্ত অ্যাকাউন্টের কোটা শেষ বা সব ব্যর্থ হলে এখানে আসব।
    // Key names: BACKUP_EMAIL_HOST, BACKUP_EMAIL_PORT, BACKUP_EMAIL_SECURE,
    //            BACKUP_EMAIL_USER, BACKUP_EMAIL_PASS
    try {
      const envUser   = process.env.BACKUP_EMAIL_USER;
      const envPass   = process.env.BACKUP_EMAIL_PASS;
      const envHost   = process.env.BACKUP_EMAIL_HOST   || 'smtp.gmail.com';
      const envPort   = parseInt(process.env.BACKUP_EMAIL_PORT   || '465', 10);
      const envSecure = process.env.BACKUP_EMAIL_SECURE === 'true' || envPort === 465;

      if (envUser && envPass &&
          envUser !== 'your_backup_email@gmail.com' &&
          envPass !== 'your_16_character_app_password') {
        console.log(`[BACKUP-EMAIL] Using .env backup account: ${envUser}`);

        const smtpConfig = {
          host:   envHost,
          port:   envPort,
          secure: envSecure,
          auth: { user: envUser, pass: envPass },
          connectionTimeout: 10000,
          socketTimeout:     10000
        };
        if (envHost.toLowerCase().includes('gmail')) {
          smtpConfig.service = 'gmail';
        }

        const transporter = nodemailer.createTransport(smtpConfig);
        await transporter.sendMail({
          from:    `"Paychek" <${envUser}>`,
          to:      contact,
          subject: 'Paychek Verification Code',
          text:    customMessage,
          html:    `<html><head><meta charset="utf-8"></head><body><p>${customMessage}</p></body></html>`
        });

        console.log(`[BACKUP-EMAIL] OTP sent successfully via .env backup account.`);
        return true;
      } else {
        console.warn('[BACKUP-EMAIL] BACKUP_EMAIL_USER/BACKUP_EMAIL_PASS not configured in .env.');
      }
    } catch (envErr) {
      console.error('[BACKUP-EMAIL] .env SMTP backup failed:', envErr.message);
    }

    // ── LAYER 3: All layers exhausted ─────────────────────────────────────
    console.error('[EMAIL-DISPATCH] All email dispatch layers failed. Returning false.');
    return false;

  // ══════════════════════════════════════════════════════════════════════════
  // SMS DISPATCH (One-Active Policy)
  // ══════════════════════════════════════════════════════════════════════════
  } else {
    const msgText = customMessage;

    // ── LAYER 1 (SMS): Active Database Gateway ──
    let layer1Success = false;
    try {
      const settings = await query(
        'SELECT * FROM sms_gateways WHERE is_active = 1 LIMIT 1'
      );

      if (settings && settings.length > 0) {
        const setting = settings[0];
        const formattedNumber = formatBdNumber(contact, setting.gateway_url, setting.post_body_template, setting.provider_name);
        
        const context = {
          to: formattedNumber,
          message: msgText,
          code: otpCode,
          apiKey: setting.api_key,
          senderId: setting.sender_id,
          username: setting.username
        };

        let resolvedUrl = replacePlaceholders(setting.gateway_url, context);
        const method = (setting.http_method || 'GET').toUpperCase();

        if (method === 'POST') {
          if (setting.post_body_template) {
            const replacedTemplate = replacePlaceholders(setting.post_body_template, context);
            let postData;
            let isJson = false;
            try {
              postData = JSON.parse(replacedTemplate);
              isJson = true;
            } catch (e) {
              postData = replacedTemplate;
            }

            try {
              console.log(`[SMS-GATEWAY] POST to ${resolvedUrl} with template...`);
              const res = await axios({
                method: 'POST',
                url: resolvedUrl,
                data: postData,
                headers: {
                  'Content-Type': isJson ? 'application/json' : 'application/x-www-form-urlencoded'
                },
                timeout: 15000
              });
              if (res.status >= 200 && res.status < 300) {
                layer1Success = true;
                console.log(`[SMS-GATEWAY] Primary DB gateway sent OTP successfully.`);
              }
            } catch (postErr) {
              console.error(`[SMS-GATEWAY] Primary POST template attempt failed:`, postErr.message);
              // Fallback content-type retry if it was JSON
              if (isJson) {
                try {
                  console.log(`[SMS-GATEWAY] Retrying primary POST template as urlencoded form...`);
                  const res = await axios({
                    method: 'POST',
                    url: resolvedUrl,
                    data: replacedTemplate,
                    headers: {
                      'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    timeout: 15000
                  });
                  if (res.status >= 200 && res.status < 300) {
                    layer1Success = true;
                    console.log(`[SMS-GATEWAY] Primary POST template retry success.`);
                  }
                } catch (retryErr) {
                  console.error(`[SMS-GATEWAY] Primary POST template retry failed:`, retryErr.message);
                }
              }
            }
          } else {
            // No template, build payload dynamically
            const payloadObj = {
              api_key: setting.api_key,
              apikey: setting.api_key,
              senderid: setting.sender_id,
              sender_id: setting.sender_id,
              sender: setting.sender_id,
              username: setting.username || '',
              to: formattedNumber,
              number: formattedNumber,
              message: msgText,
              msg: msgText
            };
            const postResult = await executePostRequest(resolvedUrl, payloadObj);
            layer1Success = postResult.success;
          }
        } else {
          // GET request
          let separator = resolvedUrl.includes('?') ? '&' : '?';
          if (setting.api_key && !resolvedUrl.includes('api_key') && !resolvedUrl.includes('apikey')) {
            resolvedUrl += `${separator}api_key=${encodeURIComponent(setting.api_key)}&apikey=${encodeURIComponent(setting.api_key)}`;
            separator = '&';
          }
          if (setting.sender_id && !resolvedUrl.includes('sender')) {
            resolvedUrl += `${separator}senderid=${encodeURIComponent(setting.sender_id)}&sender=${encodeURIComponent(setting.sender_id)}&sender_id=${encodeURIComponent(setting.sender_id)}`;
            separator = '&';
          }
          if (setting.username && !resolvedUrl.includes('username')) {
            resolvedUrl += `${separator}username=${encodeURIComponent(setting.username)}`;
            separator = '&';
          }
          if (!setting.gateway_url.includes('{to}')) {
            resolvedUrl += `${separator}number=${encodeURIComponent(formattedNumber)}&to=${encodeURIComponent(formattedNumber)}`;
            separator = '&';
          }
          if (!setting.gateway_url.includes('{msg}') && !setting.gateway_url.includes('{message}')) {
            resolvedUrl += `${separator}message=${encodeURIComponent(msgText)}&msg=${encodeURIComponent(msgText)}`;
            separator = '&';
          }

          try {
            console.log(`[SMS-GATEWAY] GET to ${resolvedUrl}...`);
            const res = await axios.get(resolvedUrl, { timeout: 15000 });
            if (res.status >= 200 && res.status < 300) {
              layer1Success = true;
              console.log(`[SMS-GATEWAY] Primary DB gateway sent OTP successfully.`);
            }
          } catch (getErr) {
            console.error(`[SMS-GATEWAY] Primary GET request failed:`, getErr.message);
          }
        }
      } else {
        console.warn('[SMS-GATEWAY] No active SMS Gateway found in database settings.');
      }
    } catch (layer1Err) {
      console.error('[SMS-GATEWAY] Layer 1 database query or execution failed:', layer1Err.message);
    }

    if (layer1Success) {
      return true;
    }

    // ── LAYER 2 (SMS): .env Backup SMS Gateway ──
    console.log('[SMS-GATEWAY] Layer 1 failed. Falling back to Layer 2 (.env backup gateway)...');
    try {
      const backupUrl    = process.env.BACKUP_SMS_GATEWAY_URL;
      const backupMethod = (process.env.BACKUP_SMS_HTTP_METHOD || 'POST').toUpperCase();
      const backupApiKey = process.env.BACKUP_SMS_API_KEY;
      const backupSender = process.env.BACKUP_SMS_SENDER_ID;
      const backupUser   = process.env.BACKUP_SMS_USERNAME;

      const isPlaceholder = !backupUrl ||
        backupUrl === 'https://api.smsprovider.com/v1/send' ||
        backupApiKey === 'your_secret_backup_api_key_here';

      if (!isPlaceholder) {
        const formattedBackupNumber = formatBdNumber(contact, backupUrl, null, 'backup');
        
        const backupContext = {
          to: formattedBackupNumber,
          message: msgText,
          code: otpCode,
          apiKey: backupApiKey,
          senderId: backupSender,
          username: backupUser
        };

        let resolvedBackupUrl = replacePlaceholders(backupUrl, backupContext);

        if (backupMethod === 'POST') {
          const backupPayload = {
            api_key: backupApiKey,
            apikey: backupApiKey,
            senderid: backupSender,
            sender_id: backupSender,
            sender: backupSender,
            username: backupUser || '',
            to: formattedBackupNumber,
            number: formattedBackupNumber,
            message: msgText,
            msg: msgText
          };

          const postResult = await executePostRequest(resolvedBackupUrl, backupPayload);
          if (postResult.success) {
            console.log(`[BACKUP-SMS] OTP sent successfully via backup gateway.`);
            return true;
          }
        } else {
          // GET
          let separator = resolvedBackupUrl.includes('?') ? '&' : '?';
          if (backupApiKey && !resolvedBackupUrl.includes('api_key') && !resolvedBackupUrl.includes('apikey')) {
            resolvedBackupUrl += `${separator}api_key=${encodeURIComponent(backupApiKey)}&apikey=${encodeURIComponent(backupApiKey)}`;
            separator = '&';
          }
          if (backupSender && !resolvedBackupUrl.includes('sender')) {
            resolvedBackupUrl += `${separator}senderid=${encodeURIComponent(backupSender)}&sender=${encodeURIComponent(backupSender)}&sender_id=${encodeURIComponent(backupSender)}`;
            separator = '&';
          }
          if (backupUser && !resolvedBackupUrl.includes('username')) {
            resolvedBackupUrl += `${separator}username=${encodeURIComponent(backupUser)}`;
            separator = '&';
          }
          if (!backupUrl.includes('{to}')) {
            resolvedBackupUrl += `${separator}number=${encodeURIComponent(formattedBackupNumber)}&to=${encodeURIComponent(formattedBackupNumber)}`;
            separator = '&';
          }
          if (!backupUrl.includes('{msg}') && !backupUrl.includes('{message}')) {
            resolvedBackupUrl += `${separator}message=${encodeURIComponent(msgText)}&msg=${encodeURIComponent(msgText)}`;
            separator = '&';
          }

          try {
            console.log(`[BACKUP-SMS] GET to ${resolvedBackupUrl}...`);
            const res = await axios.get(resolvedBackupUrl, { timeout: 15000 });
            if (res.status >= 200 && res.status < 300) {
              console.log(`[BACKUP-SMS] OTP sent successfully via backup gateway GET.`);
              return true;
            }
          } catch (backupGetErr) {
            console.error(`[BACKUP-SMS] Backup GET request failed:`, backupGetErr.message);
          }
        }
      } else {
        console.warn('[BACKUP-SMS] Backup SMS Gateway URL is not configured (or is placeholder).');
      }
    } catch (layer2Err) {
      console.error('[SMS-GATEWAY] Layer 2 backup gateway execution failed:', layer2Err.message);
    }

    // ── LAYER 3 (SMS): Both Layers Failed ──
    console.error('[SMS-GATEWAY] Layer 1 & 2 failed. SMS dispatch failed completely.');
    return false;
  }
}

// Helper: Generates a 6-digit verification code
function generateOtpCode() {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

async function getPublicConfig(req, res) {
  try {
    const configs = await query(
      "SELECT * FROM global_config WHERE config_key IN ('maintenance_mode', 'whatsapp_support_link', 'telegram_support_link', 'facebook_support_link', 'youtube_support_link')"
    );
    const configMap = {};
    configs.forEach(c => {
      configMap[c.config_key] = c.config_value;
    });
    configMap['admin_secret_username'] = process.env.ADMIN_SECRET_USERNAME || 'admin';
    return res.json({ success: true, configs: configMap });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function getChildDevices(req, res) {
  try {
    const userId = req.user.userId;
    const currentDeviceId = req.user.deviceId || '';
    const devices = await query(
      `SELECT id, device_id, custom_device_name, is_parent, is_approved, device_role,
              sim_one_number, sim_one_active, sim_two_number, sim_two_active, is_app_active 
       FROM registered_devices 
       WHERE user_id = ? AND device_id != ?`,
      [userId, currentDeviceId]
    );
    return res.json({ success: true, data: devices });
  } catch (error) {
    console.error('Error fetching child devices:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function remoteUpdateDevice(req, res) {
  try {
    const userId = req.user.userId;
    const {
      deviceId,
      custom_device_name,
      sim_one_number,
      sim_one_active,
      sim_two_number,
      sim_two_active,
      is_app_active,
      is_owner,
      device_specific_pin
    } = req.body;

    if (!deviceId) {
      return res.status(400).json({ success: false, error: 'deviceId is required' });
    }

    const result = await query(
      `UPDATE registered_devices 
       SET custom_device_name = ?, 
           sim_one_number = ?, 
           sim_one_active = ?, 
           sim_two_number = ?, 
           sim_two_active = ?, 
           is_app_active = ?,
           is_owner = COALESCE(?, is_owner),
           device_specific_pin = COALESCE(?, device_specific_pin)
       WHERE user_id = ? AND device_id = ?`,
      [
        custom_device_name || '',
        sim_one_number || null,
        sim_one_active !== undefined ? sim_one_active : 1,
        sim_two_number || null,
        sim_two_active !== undefined ? sim_two_active : 1,
        is_app_active !== undefined ? is_app_active : 1,
        is_owner !== undefined ? is_owner : null,
        device_specific_pin !== undefined ? device_specific_pin : null,
        userId,
        deviceId
      ]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ success: false, error: 'Device not found or unauthorized' });
    }

    return res.json({ success: true, message: 'Device configuration updated successfully' });
  } catch (error) {
    console.error('Error updating child device:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function getMyDeviceConfig(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.user.deviceId;

    if (!deviceId) {
      return res.status(400).json({ success: false, error: 'Device ID missing in token context' });
    }

    const devices = await query(
      `SELECT id, device_id, custom_device_name, is_parent, is_approved, device_role,
              sim_one_number, sim_one_active, sim_two_number, sim_two_active, is_app_active 
       FROM registered_devices 
       WHERE user_id = ? AND device_id = ? LIMIT 1`,
      [userId, deviceId]
    );

    if (devices.length === 0) {
      return res.status(404).json({ success: false, error: 'Device not found' });
    }

    return res.json({ success: true, data: devices[0] });
  } catch (error) {
    console.error('Error fetching own device config:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function getProfile(req, res) {
  try {
    const userId = req.user.userId;
    const users = await query(
      'SELECT id, name, phone, email, role, is_paid, active_plan_name, expiry_date, avatar, has_custom_sender_addon FROM users WHERE id = ? LIMIT 1',
      [userId]
    );

    if (users.length === 0) {
      return res.status(404).json({ error: 'ইউজার খুঁজে পাওয়া যায়নি।' });
    }

    const dbUser = users[0];
    if (dbUser.active_plan_name && dbUser.has_custom_sender_addon === 0) {
      const plans = await query(
        'SELECT is_custom_sender_allowed FROM subscription_plans WHERE plan_name = ? LIMIT 1',
        [dbUser.active_plan_name]
      );
      if (plans.length > 0 && plans[0].is_custom_sender_allowed === 1) {
        await query('UPDATE users SET has_custom_sender_addon = 1 WHERE id = ?', [userId]);
        dbUser.has_custom_sender_addon = 1;
      }
    }

    if (dbUser.has_custom_sender_addon === 1) {
      // 1. Ensure all custom templates of this user are parseable
      await query(
        'UPDATE sms_templates SET is_parseable = 1 WHERE user_id = ? AND is_official = 0',
        [userId]
      );
      // 2. Ensure all custom gateway methods of this user are enabled
      await query(
        "UPDATE gateway_methods SET is_enabled = 1 WHERE user_id = ? AND provider LIKE 'Custom-%' AND is_enabled = 0",
        [String(userId)]
      );
    }

    return res.json({
      success: true,
      user: {
        id: dbUser.id,
        name: dbUser.name,
        phone: dbUser.phone,
        email: dbUser.email,
        role: dbUser.role,
        isPaid: !!dbUser.is_paid,
        activePlanName: dbUser.active_plan_name,
        expiryDate: dbUser.expiry_date,
        avatar: dbUser.avatar
      }
    });
  } catch (err) {
    console.error('[AuthController] getProfile error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function uploadAvatar(req, res) {
  try {
    const fs = require('fs');
    const path = require('path');
    const userId = req.user.userId;
    const { avatarData } = req.body;

    if (!avatarData) {
      return res.status(400).json({ error: 'Avatar data (base64 string) is required.' });
    }

    let base64Image = avatarData;
    if (avatarData.includes(';base64,')) {
      base64Image = avatarData.split(';base64,').pop();
    }

    const buffer = Buffer.from(base64Image, 'base64');
    const dir = path.join(__dirname, '..', 'public', 'uploads', 'avatars');
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }

    const fileName = `avatar_${userId}.jpg`;
    const filePath = path.join(dir, fileName);
    fs.writeFileSync(filePath, buffer);

    const relativePath = `uploads/avatars/${fileName}`;

    await query('UPDATE users SET avatar = ? WHERE id = ?', [relativePath, userId]);

    return res.json({
      success: true,
      message: 'প্রোফাইল ছবি সফলভাবে আপলোড হয়েছে।',
      avatar: relativePath
    });
  } catch (err) {
    console.error('[AuthController] uploadAvatar error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function getPendingApprovals(req, res) {
  try {
    const userId = req.user.userId;
    const devices = await query(
      `SELECT id, device_id, custom_device_name, device_model, android_version, status, is_approved, device_role, created_at 
       FROM registered_devices 
       WHERE user_id = ? AND is_approved = 0`,
      [userId]
    );
    return res.json({ success: true, data: devices });
  } catch (error) {
    console.error('Error fetching pending approvals:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function approveByPin(req, res) {
  try {
    const userId = req.user.userId;
    const { deviceId, pin, deviceRole } = req.body;

    if (!deviceId || !pin) {
      return res.status(400).json({ success: false, error: 'deviceId and pin are required' });
    }

    // 1. Fetch user to verify PIN
    const users = await query('SELECT pin FROM users WHERE id = ? LIMIT 1', [userId]);
    if (users.length === 0) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    const user = users[0];
    if (!user.pin) {
      return res.status(400).json({ success: false, error: 'Security PIN not configured' });
    }

    // Verify PIN
    const pinMatch = await bcrypt.compare(pin, user.pin);
    if (!pinMatch) {
      return res.status(400).json({ success: false, error: 'ভুল পিন কোড, অনুগ্রহ করে আবার চেষ্টা করুন।' });
    }

    // Validate deviceRole
    const role = (deviceRole === 'owner' || deviceRole === 'restricted') ? deviceRole : 'restricted';
    const isOwnerDeviceVal = role === 'owner' ? 1 : 0;

    // 2. Update device status to approved, active, and set the role
    const result = await query(
      `UPDATE registered_devices 
       SET is_approved = 1, status = 'active', device_role = ?, is_owner_device = ? 
       WHERE user_id = ? AND device_id = ?`,
      [role, isOwnerDeviceVal, userId, deviceId]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ success: false, error: 'Device not found' });
    }

    return res.json({ success: true, message: 'ডিভাইসটি সফলভাবে অনুমোদন করা হয়েছে।' });
  } catch (error) {
    console.error('Error approving device by PIN:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function submitRole(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.user.deviceId;
    const { role } = req.body;

    if (!role || (role !== 'owner' && role !== 'restricted')) {
      return res.status(400).json({ success: false, error: 'Valid role (owner or restricted) is required' });
    }

    const result = await query(
      `UPDATE registered_devices 
       SET device_role = ? 
       WHERE user_id = ? AND device_id = ?`,
      [role, userId, deviceId]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ success: false, error: 'Device not found' });
    }

    return res.json({ success: true, message: 'ডিভাইস রোল সফলভাবে সেট করা হয়েছে।' });
  } catch (error) {
    console.error('Error submitting device role:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function checkApprovalStatus(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.user.deviceId;

    const devices = await query(
      `SELECT is_approved, device_role, status, device_specific_pin 
       FROM registered_devices 
       WHERE user_id = ? AND device_id = ? LIMIT 1`,
      [userId, deviceId]
    );

    if (devices.length === 0) {
      return res.status(404).json({ success: false, error: 'Device not registered' });
    }

    const device = devices[0];
    return res.json({
      success: true,
      isApproved: device.is_approved === 1 || device.is_approved === true,
      deviceRole: device.device_role || 'pending',
      status: device.status,
      deviceSpecificPin: device.device_specific_pin
    });
  } catch (error) {
    console.error('Error checking approval status:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function toggleRemoteRole(req, res) {
  try {
    const userId = req.user.userId;
    const { remoteDeviceId, newRole, pin } = req.body;

    if (!remoteDeviceId || !newRole || !pin) {
      return res.status(400).json({ success: false, error: 'remoteDeviceId, newRole, and pin are required' });
    }

    if (newRole !== 'owner' && newRole !== 'restricted') {
      return res.status(400).json({ success: false, error: 'Invalid role' });
    }

    // 1. Fetch user to verify PIN
    const users = await query('SELECT pin FROM users WHERE id = ? LIMIT 1', [userId]);
    if (users.length === 0) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    const user = users[0];
    if (!user.pin) {
      return res.status(400).json({ success: false, error: 'Security PIN not configured' });
    }

    // Verify PIN
    const pinMatch = await bcrypt.compare(pin, user.pin);
    if (!pinMatch) {
      return res.status(401).json({ success: false, error: 'ভুল পিন কোড, অনুগ্রহ করে আবার চেষ্টা করুন।' });
    }

    // 2. Update device role
    const isOwnerDeviceVal = newRole === 'owner' ? 1 : 0;
    const result = await query(
      `UPDATE registered_devices 
       SET device_role = ?, is_owner_device = ? 
       WHERE user_id = ? AND device_id = ?`,
      [newRole, isOwnerDeviceVal, userId, remoteDeviceId]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ success: false, error: 'Device not found' });
    }

    return res.json({ success: true, message: 'রিমোট ডিভাইসের রোল সফলভাবে পরিবর্তন করা হয়েছে।' });
  } catch (error) {
    console.error('Error toggling remote device role:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

module.exports = {
  checkContact,
  sendOtp,
  registerSendOtp,
  sendOtpNew,
  verifyOtp,
  completeProfile,
  checkDeviceTrial,
  getPublicConfig,
  getChildDevices,
  remoteUpdateDevice,
  getMyDeviceConfig,
  getPendingApprovals,
  approveByPin,
  submitRole,
  checkApprovalStatus,
  getProfile,
  uploadAvatar,
  toggleRemoteRole,
  sendOtpDispatch   // exported for reuse by credentialController & pinController
};

