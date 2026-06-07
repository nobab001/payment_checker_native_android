const { query } = require('../db/connection');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

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
    // Query users table
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
 * Sends/generates OTP for an existing user.
 */
async function sendOtp(req, res) {
  try {
    const { contact, deviceId } = req.body;
    if (!contact) {
      return res.status(400).json({ error: 'Contact is required' });
    }

    const cleanedContact = contact.trim();
    // Verify user exists
    const users = await query(
      'SELECT id FROM users WHERE phone = ? OR email = ? LIMIT 1',
      [cleanedContact, cleanedContact]
    );

    if (users.length === 0) {
      return res.status(404).json({ success: false, message: 'অ্যাকাউন্টটি খুঁজে পাওয়া যায়নি।' });
    }

    const otpCode = generateOtpCode();
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes expiration

    await query(
      'INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, ?)',
      [cleanedContact, otpCode, expiresAt]
    );

    // In a real application, you would integrate an SMS gateway or SMTP service here.
    // For local testing/XAMPP, we print it to console and return success.
    console.log(`[OTP SENT] To: ${cleanedContact} | Code: ${otpCode}`);

    return res.json({ success: true, message: 'ওটিপি কোড সফলভাবে পাঠানো হয়েছে।' });
  } catch (error) {
    console.error('Error sending OTP:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * 3. POST /api/send-otp-new
 * Sends/generates OTP for new user registration.
 */
async function sendOtpNew(req, res) {
  try {
    const { contact, deviceId } = req.body;
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
      return res.status(400).json({ success: false, message: 'এই মোবাইল বা ইমেইল দিয়ে ইতিমধ্যে অ্যাকাউন্ট খোলা হয়েছে।' });
    }

    const otpCode = generateOtpCode();
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes

    await query(
      'INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, ?)',
      [cleanedContact, otpCode, expiresAt]
    );

    console.log(`[REGISTRATION OTP SENT] To: ${cleanedContact} | Code: ${otpCode}`);

    return res.json({ success: true, message: 'রেজিস্ট্রেশন ওটিপি সফলভাবে পাঠানো হয়েছে।' });
  } catch (error) {
    console.error('Error sending signup OTP:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
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

    // Map fields to match client DTO structures
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
      return res.status(400).json({ error: 'Name and 6-digit PIN are required' });
    }

    if (pin.length !== 6 || isNaN(pin)) {
      return res.status(400).json({ error: 'PIN must be exactly 6 digits' });
    }

    // Hash the 6-digit PIN
    const hashedPin = await bcrypt.hash(pin, 10);

    // Update user record
    await query(
      'UPDATE users SET name = ?, pin = ?, phone = COALESCE(phone, ?), email = COALESCE(email, ?), profile_complete = 1 WHERE id = ?',
      [name.trim(), hashedPin, phone || null, email || null, userId]
    );

    // Auto-insert 8 default gateway methods if they do not exist
    const existingMethods = await query(
      'SELECT id FROM gateway_methods WHERE user_id = ? LIMIT 1',
      [userId]
    );

    if (existingMethods.length === 0) {
      await query(
        `INSERT INTO gateway_methods (user_id, sim_slot, provider, priority) VALUES 
          (?, 1, 'bKash', 1),
          (?, 1, 'Nagad', 2),
          (?, 1, 'Rocket', 3),
          (?, 1, 'Upay', 4),
          (?, 2, 'bKash', 5),
          (?, 2, 'Nagad', 6),
          (?, 2, 'Rocket', 7),
          (?, 2, 'Upay', 8)`,
        [userId, userId, userId, userId, userId, userId, userId, userId]
      );
    }

    // Fetch updated user details
    const updatedUsers = await query('SELECT * FROM users WHERE id = ? LIMIT 1', [userId]);
    const user = updatedUsers[0];

    return res.json({
      success: true,
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
    const { deviceId, fingerprint } = req.body;
    if (!deviceId) {
      return res.status(400).json({ error: 'Device ID is required' });
    }

    // Query device based on deviceId
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

      // Check if trial has expired or is manual block
      return res.json({
        trialAllowed: true, // Still allowed if trial is currently active (not expired/locked)
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

// Helper: Generates a 6-digit verification code
function generateOtpCode() {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

module.exports = {
  checkContact,
  sendOtp,
  sendOtpNew,
  verifyOtp,
  completeProfile,
  checkDeviceTrial
};
