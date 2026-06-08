const { query } = require('../db/connection');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const nodemailer = require('nodemailer');

const JWT_SECRET = process.env.JWT_SECRET || 'paychek_super_secret_jwt_key_987654321';
const TRIAL_DEFAULT_DAYS = parseInt(process.env.TRIAL_DEFAULT_DAYS || '7', 10);

/**
 * 1. POST /api/check-contact
 * Checks if a contact (phone or email) is registered.
 * ⚠️  SECURITY: Device-binding gatekeeper runs FIRST.
 *     Even if the contact is not found, a bound device gets
 *     TRIAL_EXPIRED_FOR_DEVICE — never a plain { exists: false }.
 */
async function checkContact(req, res) {
  try {
    const { contact, deviceId, androidId, hardwareFingerprint, simSlotIds } = req.body;
    if (!contact) {
      return res.status(400).json({ error: 'Contact field is required' });
    }

    const cleanedContact = contact.trim();
    if (cleanedContact === 'admin') {
      return res.json({ exists: true });
    }

    // ── DEVICE-BINDING GATEKEEPER (সবার আগে) ─────────────────────────────
    // deviceId পাঠালেই এই চেকটি রান হবে। যদি ডিভাইসটি বাউন্ড থাকে,
    // তাহলে contact DB-তে থাকুক বা না থাকুক — সরাসরি 403 ফেরত দেব।
    if (deviceId) {
      const { abused, userId: abuseUserId } = await isTrialAbused(
        deviceId, androidId, hardwareFingerprint, simSlotIds
      );
      if (abused) {
        const { boundPhones, boundEmails } = await getBoundCredentials(abuseUserId);
        return res.status(403).json({
          success: false,
          action:  'TRIAL_EXPIRED_FOR_DEVICE',
          message: 'দুঃখিত, আপনার এই ডিভাইসটি থেকে ইতিমধ্যে একবার ট্রায়াল অ্যাকাউন্ট ব্যবহার করা হয়েছে। আমাদের গেটওয়ে সার্ভিসটি পুনরায় সচল করতে অনুগ্রহ করে আপনার পূর্বের অ্যাকাউন্টে লগইন করুন অথবা একটি প্রিমিয়াম সাবস্ক্রিপশন প্ল্যান সক্রিয় করুন।',
          boundPhones,
          boundEmails
        });
      }
    }

    // ── Contact DB Lookup ──────────────────────────────────────────────────
    const users = await query(
      'SELECT id FROM users WHERE phone = ? OR email = ? LIMIT 1',
      [cleanedContact, cleanedContact]
    );

    return res.json({ exists: users.length > 0 });
  } catch (error) {
    console.error('Error checking contact:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * 2. POST /api/send-otp
 * Sends/generates OTP for an existing user (Login Flow).
 * Returns HTTP 404 with action SHOW_NOT_FOUND_DIALOG if account is missing.
 */
async function sendOtp(req, res) {
  try {
    const { contact } = req.body;
    if (!contact) {
      return res.status(400).json({ error: 'Contact is required' });
    }

    const cleanedContact = contact.trim();
    if (cleanedContact === 'admin') {
      return res.json({ success: true, message: 'এডমিন ওটিপি বাইপাস সক্রিয়। অনুগ্রহ করে পাসওয়ার্ড দিন।' });
    }

    // Verify user exists
    const users = await query(
      'SELECT id FROM users WHERE phone = ? OR email = ? LIMIT 1',
      [cleanedContact, cleanedContact]
    );

    if (users.length === 0) {
      return res.status(404).json({
        success: false,
        action: 'SHOW_NOT_FOUND_DIALOG',
        message: 'অ্যাকাউন্টটি খুঁজে পাওয়া যায়নি।'
      });
    }

    const otpCode = generateOtpCode();
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes expiration

    await query(
      'INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, ?)',
      [cleanedContact, otpCode, expiresAt]
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
          message: 'SMS OTP পাঠানো সম্ভব হয়নি। Gmail বা ইমেইল দিয়ে লগইন করুন অথবা সাপোর্টে যোগাযোগ করুন।'
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
    if (!contact) {
      return res.status(400).json({ error: 'Contact is required' });
    }

    // Trial abuse prevention check
    if (deviceId) {
      const { abused, userId: abuseUserId } = await isTrialAbused(deviceId, androidId, hardwareFingerprint, simSlotIds);
      if (abused) {
        const { boundPhones, boundEmails } = await getBoundCredentials(abuseUserId);
        return res.status(403).json({
          success: false,
          action: 'TRIAL_EXPIRED_FOR_DEVICE',
          message: 'দুঃখিত, আপনার এই ডিভাইসটি থেকে ইতিমধ্যে একবার ট্রায়াল অ্যাকাউন্ট ব্যবহার করা হয়েছে। আমাদের গেটওয়ে সার্ভিসটি পুনরায় সচল করতে অনুগ্রহ করে আপনার পূর্বের অ্যাকাউন্টে লগইন করুন অথবা একটি প্রিমিয়াম সাবস্ক্রিপশন প্ল্যান সক্রিয় করুন।',
          boundPhones,
          boundEmails
        });
      }
    }

    const cleanedContact = contact.trim();
    // Ensure contact is NOT already registered
    const users = await query(
      'SELECT id FROM users WHERE phone = ? OR email = ? LIMIT 1',
      [cleanedContact, cleanedContact]
    );

    if (users.length > 0) {
      return res.status(400).json({
        success: false,
        message: 'এই মোবাইল বা ইমেইল দিয়ে ইতিমধ্যে অ্যাকাউন্ট খোলা হয়েছে।'
      });
    }

    const otpCode = generateOtpCode();
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

    await query(
      'INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, ?)',
      [cleanedContact, otpCode, expiresAt]
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
          message: 'SMS OTP পাঠানো সম্ভব হয়নি। Gmail বা ইমেইল দিয়ে লগইন করুন অথবা সাপোর্টে যোগাযোগ করুন।'
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
    if (!contact || !code || !deviceId) {
      return res.status(400).json({ error: 'Missing required fields' });
    }

    const cleanedContact = contact.trim();
    const cleanedCode = code.trim();

    if (cleanedContact === 'admin') {
      const adminPass = process.env.ADMIN_PASS || 'admin1234';
      if (cleanedCode !== adminPass) {
        return res.status(400).json({ error: 'ভুল এডমিন পাসওয়ার্ড। অনুগ্রহ করে আবার চেষ্টা করুন।' });
      }

      // Generate JWT token
      const token = jwt.sign(
        { userId: 0, role: 'admin', deviceId: deviceId || 'admin-device' },
        JWT_SECRET,
        { expiresIn: '30d' }
      );

      return res.json({
        token,
        requiresSecurityPin: false,
        user: {
          id: 0,
          name: 'Global Admin',
          phone: 'admin',
          email: 'admin@paychek.online',
          role: 'admin',
          balance: 0.00,
          blocked: false,
          profileComplete: true,
          smsEnabled: true,
          gmailEnabled: true
        },
        device: {
          id: 0,
          userId: 0,
          deviceId: deviceId || 'admin-device',
          deviceName: 'Admin Dashboard Console',
          status: 'active',
          isParent: true,
          isTrialLocked: false,
          trialExpiresAt: null,
          lockReason: null
        }
      });
    }

    // Check OTP record
    const otps = await query(
      'SELECT * FROM otps WHERE contact = ? AND code = ? AND expires_at > NOW() AND used_at IS NULL ORDER BY id DESC LIMIT 1',
      [cleanedContact, cleanedCode]
    );

    if (otps.length === 0) {
      return res.status(400).json({ error: 'ভুল ওটিপি কোড অথবা ওটিপির মেয়াদ শেষ হয়ে গেছে।' });
    }

    // Mark OTP as used
    await query('UPDATE otps SET used_at = NOW() WHERE id = ?', [otps[0].id]);

    // Check if user exists, if not, create new pending user
    let user;
    const existingUsers = await query(
      'SELECT * FROM users WHERE phone = ? OR email = ? LIMIT 1',
      [cleanedContact, cleanedContact]
    );

    // If it's a new registration flow, verify that they are not using a device that consumed trial
    if (existingUsers.length === 0) {
      const { abused, userId: abuseUserId } = await isTrialAbused(deviceId, androidId, hardwareFingerprint, simSlotIds);
      if (abused) {
        const { boundPhones, boundEmails } = await getBoundCredentials(abuseUserId);
        return res.status(403).json({
          success: false,
          action: 'TRIAL_EXPIRED_FOR_DEVICE',
          message: 'দুঃখিত, আপনার এই ডিভাইসটি থেকে ইতিমধ্যে একবার ট্রায়াল অ্যাকাউন্ট ব্যবহার করা হয়েছে। আমাদের গেটওয়ে সার্ভিসটি পুনরায় সচল করতে অনুগ্রহ করে আপনার পূর্বের অ্যাকাউন্টে লগইন করুন অথবা একটি প্রিমিয়াম সাবস্ক্রিপশন প্ল্যান সক্রিয় করুন।',
          boundPhones,
          boundEmails
        });
      }
    }

    if (existingUsers.length === 0) {
      const isEmail = cleanedContact.includes('@');
      const insertResult = await query(
        'INSERT INTO users (name, phone, email, role, profile_complete) VALUES (?, ?, ?, ?, ?)',
        ['', isEmail ? null : cleanedContact, isEmail ? cleanedContact : null, 'user', 0]
      );

      const newUserId = insertResult.insertId;
      const newUsers = await query('SELECT * FROM users WHERE id = ?', [newUserId]);
      user = newUsers[0];
    } else {
      user = existingUsers[0];
    }

    // Check blocked status
    if (user.blocked) {
      return res.status(403).json({ error: 'আপনার অ্যাকাউন্টটি ব্লক করা হয়েছে। অনুগ্রহ করে অ্যাডমিনের সাথে যোগাযোগ করুন।' });
    }

    // Retrieve default trial days from global configs
    const trialDaysConfig = await query(
      "SELECT config_value FROM global_config WHERE config_key = 'trial_days' LIMIT 1"
    );
    const trialDays = trialDaysConfig.length > 0 ? parseInt(trialDaysConfig[0].config_value, 10) : TRIAL_DEFAULT_DAYS;

    // Check if device is already registered for this user
    let device;
    const devices = await query(
      'SELECT * FROM registered_devices WHERE user_id = ? AND device_id = ? LIMIT 1',
      [user.id, deviceId]
    );

    if (devices.length === 0) {
      // First device for user becomes parent and auto-approved, others become child and pending approval
      const userDevicesCount = await query(
        'SELECT COUNT(*) as count FROM registered_devices WHERE user_id = ?',
        [user.id]
      );
      
      const isParent = userDevicesCount[0].count === 0 ? 1 : 0;
      const initialStatus = isParent ? 'active' : 'pending';

      const trialStartedAt = new Date();
      const trialExpiresAt = new Date();
      trialExpiresAt.setDate(trialStartedAt.getDate() + trialDays);

      const insertDeviceResult = await query(
        `INSERT INTO registered_devices 
          (user_id, device_id, device_name, device_model, android_version, status, is_parent, trial_started_at, trial_expires_at, is_trial_locked) 
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          user.id,
          deviceId,
          isParent ? 'Main Phone' : 'Child Device',
          deviceModel || 'Unknown Model',
          androidVersion || 'Android',
          initialStatus,
          isParent,
          trialStartedAt,
          trialExpiresAt,
          0
        ]
      );

      // Record device trial logs
      try {
        const logAndroidId = androidId || deviceId;
        const logFingerprint = hardwareFingerprint || fingerprint || 'unknown_fingerprint';
        const logSimSlots = simSlotIds || 'no_sim';
        await query(
          `INSERT INTO device_trial_logs (android_id, hardware_fingerprint, sim_slot_ids, has_used_trial) 
           VALUES (?, ?, ?, 1)
           ON DUPLICATE KEY UPDATE has_used_trial = 1`,
          [logAndroidId, logFingerprint, logSimSlots]
        );
        console.log(`[DB] Recorded device trial log for device: ${logAndroidId}`);
      } catch (logErr) {
        console.error('[DB] Failed to insert device trial log:', logErr);
      }

      const freshDevices = await query('SELECT * FROM registered_devices WHERE id = ?', [insertDeviceResult.insertId]);
      device = freshDevices[0];
    } else {
      device = devices[0];

      // Update last seen & model
      await query(
        'UPDATE registered_devices SET last_seen_at = NOW(), device_model = ?, android_version = ? WHERE id = ?',
        [deviceModel, androidVersion, device.id]
      );

      // Verify trial locked status
      if (device.trial_expires_at && new Date(device.trial_expires_at) < new Date() && !device.is_trial_locked) {
        await query(
          "UPDATE registered_devices SET is_trial_locked = 1, lock_reason = 'trial_expired' WHERE id = ?",
          [device.id]
        );
        device.is_trial_locked = 1;
        device.lock_reason = 'trial_expired';
      }
    }

    // Generate JWT token
    const token = jwt.sign(
      { userId: user.id, role: user.role, deviceId: device.device_id },
      JWT_SECRET,
      { expiresIn: '30d' }
    );

    return res.json({
      token,
      requiresSecurityPin: !!user.pin,
      user: {
        id: user.id,
        name: user.name,
        phone: user.phone,
        email: user.email,
        role: user.role,
        balance: parseFloat(user.balance),
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
        isTrialLocked: !!device.is_trial_locked,
        trialExpiresAt: device.trial_expires_at,
        lockReason: device.lock_reason
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
    const { name, pin, phone, email } = req.body;
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

    // Hash the 6-digit PIN
    const hashedPin = await bcrypt.hash(pin, 10);

    // Update user record
    await query(
      'UPDATE users SET name = ?, pin = ?, phone = ?, email = ?, profile_complete = 1 WHERE id = ?',
      [name.trim(), hashedPin, finalPhone, finalEmail, userId]
    );

    // Auto-insert 8 default gateway methods (4 Personal on SIM 1, 4 Agent on SIM 2)
    const existingMethods = await query(
      'SELECT id FROM gateway_methods WHERE user_id = ? LIMIT 1',
      [userId]
    );

    if (existingMethods.length === 0) {
      await query(
        `INSERT INTO gateway_methods (user_id, template_id, sim_slot, provider, priority) VALUES 
          (?, 1, 1, 'bKash', 1),
          (?, 2, 1, 'Nagad', 2),
          (?, 3, 1, 'Rocket', 3),
          (?, 4, 1, 'Upay', 4),
          (?, 5, 2, 'bKash', 5),
          (?, 6, 2, 'Nagad', 6),
          (?, 7, 2, 'Rocket', 7),
          (?, 8, 2, 'Upay', 8)`,
        [userId, userId, userId, userId, userId, userId, userId, userId]
      );
    }

    // Fetch updated user details
    const updatedUsers = await query('SELECT * FROM users WHERE id = ? LIMIT 1', [userId]);
    const updatedUser = updatedUsers[0];

    return res.json({
      success: true,
      user: {
        id: updatedUser.id,
        name: updatedUser.name,
        phone: updatedUser.phone,
        email: updatedUser.email,
        role: updatedUser.role,
        balance: parseFloat(updatedUser.balance),
        blocked: !!updatedUser.blocked,
        profileComplete: !!updatedUser.profile_complete,
        smsEnabled: !!updatedUser.sms_enabled,
        gmailEnabled: !!updatedUser.gmail_enabled
      }
    });
  } catch (error) {
    console.error('Error completing profile:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * Helper: Checks if the device signature matches any registered device trial lock,
 * or if it is logged in device_trial_logs as having consumed a trial.
 * Returns: { abused: Boolean, userId: Number|null }
 */
async function isTrialAbused(deviceId, androidId, hardwareFingerprint, simSlotIds) {
  try {
    const safeDeviceId = deviceId || '';
    const safeAndroidId = androidId || '';
    const safeHardwareFingerprint = hardwareFingerprint || '';
    const safeSimSlotIds = simSlotIds || '';

    // 1. Check if the device exists in registered_devices and is trial locked or expired
    if (safeDeviceId) {
      const devices = await query(
        'SELECT id, user_id, trial_expires_at, is_trial_locked FROM registered_devices WHERE device_id = ? LIMIT 1',
        [safeDeviceId]
      );
      if (devices && devices.length > 0) {
        const dev = devices[0];
        const expired = dev.trial_expires_at && new Date(dev.trial_expires_at) < new Date();
        if (dev.is_trial_locked || expired) {
          return { abused: true, userId: dev.user_id || null };
        }
      }
    }

    // 2. Check if any of the three identifiers match device_trial_logs with has_used_trial = 1
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
    
    // Ignore empty or dummy sim_slot_ids
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
      const queryStr = `SELECT id, user_id FROM device_trial_logs WHERE (${checkFields.join(' OR ')}) AND has_used_trial = 1 LIMIT 1`;
      const logs = await query(queryStr, checkParams);
      if (logs && logs.length > 0) {
        return { abused: true, userId: logs[0].user_id || null };
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
        action: 'TRIAL_EXPIRED_FOR_DEVICE',
        message: 'দুঃখিত, আপনার এই ডিভাইসটি থেকে ইতিমধ্যে একবার ট্রায়াল অ্যাকাউন্ট ব্যবহার করা হয়েছে। আমাদের গেটওয়ে সার্ভিসটি পুনরায় সচল করতে অনুগ্রহ করে আপনার পূর্বের অ্যাকাউন্টে লগইন করুন অথবা একটি প্রিমিয়াম সাবস্ক্রিপশন প্ল্যান সক্রিয় করুন।',
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

      if (device.is_trial_locked) {
        const { boundPhones: lkPhones, boundEmails: lkEmails } = await getBoundCredentials(device.user_id);
        return res.status(403).json({
          success: false,
          action: 'TRIAL_EXPIRED_FOR_DEVICE',
          message: 'দুঃখিত, আপনার এই ডিভাইসটি থেকে ইতিমধ্যে একবার ট্রায়াল অ্যাকাউন্ট ব্যবহার করা হয়েছে। আমাদের গেটওয়ে সার্ভিসটি পুনরায় সচল করতে অনুগ্রহ করে আপনার পূর্বের অ্যাকাউন্টে লগইন করুন অথবা একটি প্রিমিয়াম সাবস্ক্রিপশন প্ল্যান সক্রিয় করুন।',
          boundPhones: lkPhones,
          boundEmails: lkEmails
        });
      }

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

  // ══════════════════════════════════════════════════════════════════════════
  // EMAIL DISPATCH
  // ══════════════════════════════════════════════════════════════════════════
  if (isEmail) {

    // ── LAYER 1: Sequential Gmail Round-Robin (DB-driven) ──────────────────
    // সবচেয়ে কম ID-র অ্যাকাউন্ট থেকে শুরু করব এবং sent_today < daily_limit
    // না হলে পরবর্তী অ্যাকাউন্টে যাব। এটি সিকোয়েন্সিয়াল ফাসলাইন লজিক।
    try {
      const dbAccounts = await query(
        `SELECT * FROM email_accounts
          WHERE is_active = 1
            AND COALESCE(sent_today, 0) < COALESCE(daily_limit, 500)
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
            text:    `আপনার Paychek যাচাই কোড: ${otpCode}\n\nএটি ৫ মিনিট পর্যন্ত বৈধ।\n\nYour Paychek verification code is: ${otpCode}\nValid for 5 minutes only.`
          });

          // সফলভাবে পাঠানো হয়েছে — sent_today বাড়াই
          await query(
            'UPDATE email_accounts SET sent_today = COALESCE(sent_today, 0) + 1 WHERE id = ?',
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
          text:    `আপনার Paychek যাচাই কোড: ${otpCode}\n\nএটি ৫ মিনিট পর্যন্ত বৈধ।\n\nYour Paychek verification code is: ${otpCode}\nValid for 5 minutes only.`
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
    try {
      // শুধুমাত্র একটি মাত্র অ্যাক্টিভ SMS গেটওয়ে থাকবে (One-Active Policy)
      const settings = await query(
        'SELECT * FROM sms_settings WHERE is_active = 1 LIMIT 1'
      );

      if (settings.length === 0) {
        console.warn('[SMS-GATEWAY] No active SMS Gateway found in sms_settings.');
        return false;
      }

      const setting = settings[0];
      const msg     = `Your Paychek OTP is ${otpCode}. Valid for 5 minutes.`;

      let url = setting.gateway_url
        .replace('{to}',   contact)
        .replace('{msg}',  encodeURIComponent(msg))
        .replace('{code}', otpCode);

      if (setting.api_key && !url.includes('api_key')) {
        url += `${url.includes('?') ? '&' : '?'}api_key=${encodeURIComponent(setting.api_key)}`;
      }
      if (setting.sender_id && !url.includes('sender')) {
        url += `${url.includes('?') ? '&' : '?'}sender=${encodeURIComponent(setting.sender_id)}`;
      }

      const method  = (setting.http_method || 'GET').toUpperCase();
      const options = { method, signal: AbortSignal.timeout(15000) };

      if (method === 'POST' && setting.post_body_template) {
        const bodyText = setting.post_body_template
          .replace('{to}',   contact)
          .replace('{msg}',  msg)
          .replace('{code}', otpCode);
        options.headers = { 'Content-Type': 'application/json' };
        options.body    = bodyText;
      }

      console.log(`[SMS-GATEWAY] Dispatching to: ${url.split('?')[0]}...`);
      const response = await fetch(url, options);

      if (response.ok) {
        console.log(`[SMS-GATEWAY] OTP sent successfully. Status: ${response.status}`);
        return true;
      } else {
        const body = await response.text().catch(() => '');
        console.error(`[SMS-GATEWAY] Primary DB gateway failed. HTTP ${response.status}: ${body}`);
        // Primary ব্যর্থ — Backup SMS Gateway try করব
      }

    } catch (smsErr) {
      console.error('[SMS-GATEWAY] Primary dispatch error:', smsErr.message);
      // Primary error — Backup SMS Gateway try করব
    }

    // ── LAYER 2 (SMS): .env Backup SMS Gateway ────────────────────────────
    // Key names: BACKUP_SMS_GATEWAY_URL, BACKUP_SMS_HTTP_METHOD,
    //            BACKUP_SMS_API_KEY, BACKUP_SMS_USERNAME, BACKUP_SMS_SENDER_ID
    try {
      const backupUrl    = process.env.BACKUP_SMS_GATEWAY_URL;
      const backupMethod = (process.env.BACKUP_SMS_HTTP_METHOD || 'POST').toUpperCase();
      const backupApiKey = process.env.BACKUP_SMS_API_KEY;
      const backupSender = process.env.BACKUP_SMS_SENDER_ID;

      const isPlaceholder = !backupUrl ||
        backupUrl === 'https://api.smsprovider.com/v1/send' ||
        backupApiKey === 'your_secret_backup_api_key_here';

      if (!isPlaceholder) {
        const msg = `Your Paychek OTP is ${otpCode}. Valid for 5 minutes.`;

        let bUrl = backupUrl
          .replace('{to}',   contact)
          .replace('{msg}',  encodeURIComponent(msg))
          .replace('{code}', otpCode);

        if (backupApiKey && !bUrl.includes('api_key')) {
          bUrl += `${bUrl.includes('?') ? '&' : '?'}api_key=${encodeURIComponent(backupApiKey)}`;
        }
        if (backupSender && !bUrl.includes('sender')) {
          bUrl += `${bUrl.includes('?') ? '&' : '?'}sender=${encodeURIComponent(backupSender)}`;
        }

        const bOptions = {
          method: backupMethod,
          signal: AbortSignal.timeout(15000)
        };

        if (backupMethod === 'POST') {
          bOptions.headers = { 'Content-Type': 'application/json' };
          bOptions.body    = JSON.stringify({
            to:      contact,
            message: msg,
            api_key: backupApiKey,
            sender:  backupSender,
            username: process.env.BACKUP_SMS_USERNAME || ''
          });
        }

        console.log(`[BACKUP-SMS] Dispatching to backup gateway: ${bUrl.split('?')[0]}...`);
        const bResponse = await fetch(bUrl, bOptions);

        if (bResponse.ok) {
          console.log(`[BACKUP-SMS] OTP sent via backup SMS gateway. Status: ${bResponse.status}`);
          return true;
        } else {
          const bBody = await bResponse.text().catch(() => '');
          console.error(`[BACKUP-SMS] Backup gateway failed. HTTP ${bResponse.status}: ${bBody}`);
        }
      } else {
        console.warn('[BACKUP-SMS] BACKUP_SMS_GATEWAY_URL not configured (still using placeholder).');
      }
    } catch (backupSmsErr) {
      console.error('[BACKUP-SMS] Backup SMS dispatch error:', backupSmsErr.message);
    }

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
    return res.json({ success: true, configs: configMap });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
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
  sendOtpDispatch   // exported for reuse by credentialController & pinController
};
