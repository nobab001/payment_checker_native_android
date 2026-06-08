const { query } = require('../db/connection');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const nodemailer = require('nodemailer');

const JWT_SECRET = process.env.JWT_SECRET || 'paychek_super_secret_jwt_key_987654321';
const TRIAL_DEFAULT_DAYS = parseInt(process.env.TRIAL_DEFAULT_DAYS || '7', 10);

/**
 * 1. POST /api/check-contact
 * Checks if a contact (phone or email) is registered.
 */
async function checkContact(req, res) {
  try {
    const { contact } = req.body;
    if (!contact) {
      return res.status(400).json({ error: 'Contact field is required' });
    }

    const cleanedContact = contact.trim();
    if (cleanedContact === 'admin') {
      return res.json({ exists: true });
    }

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
    const { contact } = req.body;
    if (!contact) {
      return res.status(400).json({ error: 'Contact is required' });
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
    const { contact, code, deviceId, deviceModel, androidVersion, fingerprint } = req.body;
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
 * 6. POST /api/check-device-trial
 * Verifies if the device is trial-locked or if a trial was already claimed.
 */
async function checkDeviceTrial(req, res) {
  try {
    const { deviceId } = req.body;
    if (!deviceId) {
      return res.status(400).json({ error: 'Device ID is required' });
    }

    const devices = await query(
      'SELECT * FROM registered_devices WHERE device_id = ? LIMIT 1',
      [deviceId]
    );

    if (devices.length > 0) {
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
        return res.json({
          trialAllowed: false,
          isLocked: true,
          lockReason: device.lock_reason || 'trial_expired'
        });
      }

      return res.json({
        trialAllowed: true,
        isLocked: false,
        lockReason: null
      });
    }

    // Device is brand new
    return res.json({
      trialAllowed: true,
      isLocked: false,
      lockReason: null
    });
  } catch (error) {
    console.error('Error checking device trial:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * Helper: Dispatches OTP via Round-Robin Email or SMS Gateways
 */
async function sendOtpDispatch(contact, otpCode) {
  const isEmail = contact.includes('@');
  
  // Log locally for debugging ease
  console.log(`[OTP GENERATED] To: ${contact} | Code: ${otpCode}`);

  if (isEmail) {
    // 1. Try Database-driven Round-Robin SMTP Dispatcher first
    try {
      const accounts = await query(
        `SELECT * FROM email_accounts 
          WHERE is_active = 1 AND COALESCE(sent_today, 0) < COALESCE(daily_limit, 500) 
       ORDER BY COALESCE(sent_today, 0) ASC, updated_at ASC 
          LIMIT 1`
      );

      if (accounts.length > 0) {
        const acc = accounts[0];
        console.log(`[ROUND-ROBIN] Using SMTP Account: ${acc.email} (Sent today: ${acc.sent_today}/${acc.daily_limit})`);
        
        const smtpConfig = {
          host: acc.host,
          port: acc.port,
          secure: acc.secure === 1,
          auth: {
            user: acc.email,
            pass: acc.password
          }
        };

        // Enable Gmail helper if applicable
        if (acc.host.toLowerCase().includes('gmail.com')) {
          smtpConfig.service = 'gmail';
        }

        const transporter = nodemailer.createTransport(smtpConfig);

        await transporter.sendMail({
          from: acc.email,
          to: contact,
          subject: 'Paychek Verification Code',
          text: `Your Paychek login/registration verification code is: ${otpCode}. It is valid for 5 minutes.`
        });

        // Increment usage safely
        await query('UPDATE email_accounts SET sent_today = COALESCE(sent_today, 0) + 1 WHERE id = ?', [acc.id]);
        return true;
      } else {
        console.log(`[ROUND-ROBIN] No active database SMTP accounts with capacity found.`);
      }
    } catch (dbErr) {
      console.error('[ROUND-ROBIN] SMTP DB Dispatch failed with error:', dbErr);
    }

    // 2. Fallback to .env SMTP settings
    try {
      const fallbackUser = process.env.EMAIL_USER || process.env.SMTP_USER;
      const fallbackPass = process.env.EMAIL_PASS || process.env.SMTP_PASS;
      const fallbackHost = process.env.SMTP_HOST;
      const fallbackPort = parseInt(process.env.SMTP_PORT || '465', 10);
      const fallbackSecure = process.env.SMTP_SECURE === 'true' || fallbackPort === 465;

      if (fallbackUser && fallbackPass && fallbackHost) {
        console.log(`[SMTP-BACKUP] Using backup SMTP: ${fallbackUser}`);
        
        const smtpConfig = {
          host: fallbackHost,
          port: fallbackPort,
          secure: fallbackSecure,
          auth: {
            user: fallbackUser,
            pass: fallbackPass
          }
        };

        if (fallbackHost.toLowerCase().includes('gmail.com')) {
          smtpConfig.service = 'gmail';
        }

        const transporter = nodemailer.createTransport(smtpConfig);

        await transporter.sendMail({
          from: fallbackUser,
          to: contact,
          subject: 'Paychek Verification Code',
          text: `Your Paychek verification code is: ${otpCode}. It is valid for 5 minutes.`
        });
        return true;
      } else {
        console.warn(`[SMTP-BACKUP] Backup SMTP environment variables (EMAIL_USER/SMTP_USER, EMAIL_PASS/SMTP_PASS, SMTP_HOST) are incomplete.`);
      }
    } catch (envErr) {
      console.error('[SMTP-BACKUP] Backup SMTP Dispatch failed with error:', envErr);
    }

    return false;
  } else {
    // SMS Dispatcher
    try {
      const settings = await query('SELECT * FROM sms_settings WHERE is_active = 1 LIMIT 1');
      if (settings.length > 0) {
        const setting = settings[0];
        let url = setting.gateway_url
          .replace('{to}', contact)
          .replace('{msg}', encodeURIComponent(`Your Paychek OTP is ${otpCode}`))
          .replace('{code}', otpCode);
        
        if (setting.api_key && !url.includes('api_key')) {
          url += `${url.includes('?') ? '&' : '?'}api_key=${encodeURIComponent(setting.api_key)}`;
        }
        if (setting.sender_id && !url.includes('sender')) {
          url += `${url.includes('?') ? '&' : '?'}sender=${encodeURIComponent(setting.sender_id)}`;
        }

        const method = setting.http_method || 'GET';
        const options = { method };
        
        if (method.toUpperCase() === 'POST' && setting.post_body_template) {
          const bodyText = setting.post_body_template
            .replace('{to}', contact)
            .replace('{msg}', `Your Paychek OTP is ${otpCode}`)
            .replace('{code}', otpCode);
          options.headers = { 'Content-Type': 'application/json' };
          options.body = bodyText;
        }

        console.log(`[SMS-GATEWAY] Sending request to: ${url}`);
        const res = await fetch(url, options);
        if (res.ok) {
          return true;
        } else {
          console.error(`[SMS-GATEWAY] HTTP dispatch failed with status: ${res.status}`);
        }
      } else {
        console.warn(`[SMS-GATEWAY] No active SMS Gateway config found in sms_settings.`);
      }
    } catch (smsErr) {
      console.error('[SMS-GATEWAY] Dispatch failed with error:', smsErr);
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
  getPublicConfig
};
