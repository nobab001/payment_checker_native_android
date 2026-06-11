const { query } = require('../db/connection');
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'paychek_super_secret_jwt_key_987654321';

// ---------------------------------------------------------
// Middleware: verifyAdmin
// Extracts token, verifies role === 'admin'
// ---------------------------------------------------------
async function verifyAdmin(req, res, next) {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Authorization header is missing or invalid' });
    }
    const token = authHeader.split(' ')[1];
    const decoded = jwt.verify(token, JWT_SECRET);

    if (decoded.role === 'admin') {
      req.user = decoded;
      return next();
    }

    // Double check from db
    const users = await query('SELECT role FROM users WHERE id = ? LIMIT 1', [decoded.userId]);
    if (users.length === 0 || users[0].role !== 'admin') {
      return res.status(403).json({ error: 'Access forbidden. Admin role required.' });
    }

    req.user = decoded;
    next();
  } catch (error) {
    console.error('verifyAdmin error:', error);
    return res.status(401).json({ error: 'Unauthorized token session expired' });
  }
}

// 1. App Configs (global_config)
async function getConfigs(req, res) {
  try {
    const configs = await query('SELECT * FROM global_config');
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

async function updateConfig(req, res) {
  try {
    const { key, value } = req.body;
    if (!key) return res.status(400).json({ error: 'config_key is required' });
    
    await query(
      'INSERT INTO global_config (config_key, config_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE config_value = ?',
      [key, String(value), String(value)]
    );
    return res.json({ success: true, message: 'Configuration updated successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// 2. Official SMS Templates
async function getSmsTemplates(req, res) {
  try {
    const templates = await query('SELECT * FROM sms_templates WHERE is_official = 1');
    return res.json({ success: true, templates });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function saveSmsTemplate(req, res) {
  try {
    const { id, template_name, sender_id, matching_keyword, regex_pattern, is_active } = req.body;
    if (!template_name || !sender_id || !regex_pattern) {
      return res.status(400).json({ error: 'Missing required template fields' });
    }

    if (id) {
      await query(
        `UPDATE sms_templates 
         SET template_name = ?, sender_id = ?, matching_keyword = ?, regex_pattern = ?, is_active = ? 
         WHERE id = ? AND is_official = 1`,
        [template_name, sender_id, matching_keyword || '', regex_pattern, is_active === undefined ? 1 : (is_active ? 1 : 0), id]
      );
    } else {
      await query(
        `INSERT INTO sms_templates (template_name, sender_id, matching_keyword, regex_pattern, is_official, is_active) 
         VALUES (?, ?, ?, ?, 1, ?)`,
        [template_name, sender_id, matching_keyword || '', regex_pattern, is_active === undefined ? 1 : (is_active ? 1 : 0)]
      );
    }
    return res.json({ success: true, message: 'SMS Template saved successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function deleteSmsTemplate(req, res) {
  try {
    const { id } = req.params;
    await query('DELETE FROM sms_templates WHERE id = ? AND is_official = 1', [id]);
    return res.json({ success: true, message: 'Official SMS Template deleted successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// 3. Checkout View Templates
async function getCheckoutTemplates(req, res) {
  try {
    const templates = await query(
      `SELECT cvt.*, st.template_name 
       FROM checkout_view_templates cvt
       JOIN sms_templates st ON cvt.sms_template_id = st.id`
    );
    return res.json({ success: true, templates });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function saveCheckoutTemplate(req, res) {
  try {
    const { sms_template_id, single_number_instruction, multiple_number_instruction } = req.body;
    if (!sms_template_id || !single_number_instruction || !multiple_number_instruction) {
      return res.status(400).json({ error: 'Missing required instructions fields' });
    }

    await query(
      `INSERT INTO checkout_view_templates (sms_template_id, single_number_instruction, multiple_number_instruction) 
       VALUES (?, ?, ?) 
       ON DUPLICATE KEY UPDATE single_number_instruction = ?, multiple_number_instruction = ?`,
      [sms_template_id, single_number_instruction, multiple_number_instruction, single_number_instruction, multiple_number_instruction]
    );
    return res.json({ success: true, message: 'Checkout instructions saved successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// 4. SMTP (Email Round-Robin) Accounts
async function getEmailAccounts(req, res) {
  try {
    const accounts = await query('SELECT * FROM email_accounts');
    return res.json({ success: true, accounts });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function saveEmailAccount(req, res) {
  try {
    const { id, email, password, host, port, secure, daily_limit, is_active } = req.body;
    if (!email || !password || !host || !port) {
      return res.status(400).json({ error: 'Missing required SMTP configurations' });
    }

    if (id) {
      await query(
        `UPDATE email_accounts 
         SET email = ?, password = ?, host = ?, port = ?, secure = ?, daily_limit = ?, is_active = ? 
         WHERE id = ?`,
        [email, password, host, port, secure ? 1 : 0, daily_limit || 500, is_active ? 1 : 0, id]
      );
    } else {
      await query(
        `INSERT INTO email_accounts (email, password, host, port, secure, daily_limit, is_active) 
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
        [email, password, host, port, secure ? 1 : 0, daily_limit || 500, is_active ? 1 : 0]
      );
    }
    return res.json({ success: true, message: 'SMTP Profile saved successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function deleteEmailAccount(req, res) {
  try {
    const { id } = req.params;
    await query('DELETE FROM email_accounts WHERE id = ?', [id]);
    return res.json({ success: true, message: 'SMTP Profile deleted successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// 5. SMS Gateway Settings
async function getSmsSettings(req, res) {
  try {
    const settings = await query('SELECT * FROM sms_settings');
    return res.json({ success: true, settings });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function saveSmsSettings(req, res) {
  try {
    const { id, gateway_url, http_method, post_body_template, api_key, username, sender_id, is_active, provider_name } = req.body;
    if (!gateway_url) {
      return res.status(400).json({ error: 'gateway_url is required' });
    }

    const activeFlag = is_active ? 1 : 0;

    if (id) {
      await query(
        `UPDATE sms_settings 
         SET gateway_url = ?, http_method = ?, post_body_template = ?, api_key = ?, username = ?, sender_id = ?, is_active = ?, provider_name = ? 
         WHERE id = ?`,
        [gateway_url, http_method || 'GET', post_body_template || null, api_key || null, username || null, sender_id || null, activeFlag, provider_name || null, id]
      );
    } else {
      await query(
        `INSERT INTO sms_settings (gateway_url, http_method, post_body_template, api_key, username, sender_id, is_active, provider_name) 
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        [gateway_url, http_method || 'GET', post_body_template || null, api_key || null, username || null, sender_id || null, activeFlag, provider_name || null]
      );
    }

    // ─── ONE-ACTIVE SMS POLICY ────────────────────────────────────────────────
    // যদি এই প্রোভাইডারটি activate করা হয়, তাহলে বাকি সমস্ত প্রোভাইডারকে
    // স্বয়ংক্রিয়ভাবে deactivate করতে হবে।
    if (activeFlag === 1) {
      // সদ্য সেভ হওয়া row-এর id বের করা
      const [savedRow] = await query(
        'SELECT id FROM sms_settings WHERE gateway_url = ? ORDER BY id DESC LIMIT 1',
        [gateway_url]
      );
      if (savedRow) {
        await query(
          'UPDATE sms_settings SET is_active = 0 WHERE id != ?',
          [savedRow.id]
        );
        console.log(`[SMS-POLICY] One-Active enforced. Only provider id=${savedRow.id} is now active.`);
      }
    }

    return res.json({ success: true, message: 'SMS Gateway configurations saved successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function deleteSmsSettings(req, res) {
  try {
    const { id } = req.params;
    if (!id) return res.status(400).json({ error: 'SMS provider id is required' });
    await query('DELETE FROM sms_settings WHERE id = ?', [id]);
    return res.json({ success: true, message: 'SMS Gateway provider deleted successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// Reset daily sent_today counter for a specific email account
async function resetEmailCounter(req, res) {
  try {
    const { id } = req.params;
    if (!id) return res.status(400).json({ error: 'Email account id is required' });
    await query('UPDATE email_accounts SET sent_today = 0 WHERE id = ?', [id]);
    return res.json({ success: true, message: 'Daily email counter reset to 0 successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// Reset ALL email account daily counters at once
async function resetAllEmailCounters(req, res) {
  try {
    await query('UPDATE email_accounts SET sent_today = 0');
    return res.json({ success: true, message: 'All daily email counters reset to 0.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// 6. User and Device Listing
async function listUsers(req, res) {
  try {
    // 1. Fetch all users
    const users = await query(
      `SELECT id, name, phone, email, role, is_paid, active_plan_name, expiry_date, blocked, profile_complete, created_at FROM users`
    );

    // 2. Fetch all registered devices
    const devices = await query(
      `SELECT id, user_id, device_id AS deviceId, device_name AS deviceName, custom_name AS customName, 
              device_model AS deviceModel, android_version AS androidVersion, status, is_parent AS isParent, 
              last_seen_at AS lastSeenAt, last_battery_percent AS lastBatteryPercent, 
              trial_expires_at AS trialExpiresAt, is_trial_locked AS isTrialLocked, lock_reason AS lockReason 
       FROM registered_devices`
    );

    // 3. Group devices by user_id
    const devicesByUserId = {};
    devices.forEach(d => {
      const userId = d.user_id;
      const deviceObj = { ...d };
      delete deviceObj.user_id;
      
      if (!devicesByUserId[userId]) {
        devicesByUserId[userId] = [];
      }
      devicesByUserId[userId].push(deviceObj);
    });

    // 4. Map devices to their respective users
    const result = users.map(u => {
      return {
        ...u,
        is_paid: !!u.is_paid,
        blocked: !!u.blocked,
        profile_complete: !!u.profile_complete,
        devices: devicesByUserId[u.id] || []
      };
    });

    return res.json({ success: true, users: result });
  } catch (err) {
    console.error('listUsers error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function toggleUserBlock(req, res) {
  try {
    const { id } = req.params;
    const { blocked } = req.body;
    if (blocked === undefined) return res.status(400).json({ error: 'blocked status is required' });

    await query('UPDATE users SET blocked = ? WHERE id = ?', [blocked ? 1 : 0, id]);
    return res.json({ success: true, message: `User status changed successfully.` });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function updateDeviceTrial(req, res) {
  try {
    const { id } = req.params;
    const { trial_expires_at, is_trial_locked, lock_reason } = req.body;

    if (trial_expires_at === undefined && is_trial_locked === undefined && lock_reason === undefined) {
      return res.status(400).json({ error: 'Missing update fields' });
    }

    const updates = [];
    const params = [];

    if (trial_expires_at !== undefined) {
      updates.push('trial_expires_at = ?');
      params.push(trial_expires_at ? new Date(trial_expires_at) : null);
    }
    if (is_trial_locked !== undefined) {
      updates.push('is_trial_locked = ?');
      params.push(is_trial_locked ? 1 : 0);
    }
    if (lock_reason !== undefined) {
      updates.push('lock_reason = ?');
      params.push(lock_reason || null);
    }

    params.push(id);

    await query(
      `UPDATE registered_devices SET ${updates.join(', ')} WHERE id = ?`,
      params
    );

    return res.json({ success: true, message: 'Device trial parameters updated successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function getOtpFormat(req, res) {
  try {
    const rows = await query(
      "SELECT setting_value FROM system_settings WHERE setting_key = 'otp_format_template' LIMIT 1"
    );
    const template = rows.length > 0 ? rows[0].setting_value : "আপনার প্রিয় পে-চেক অ্যাপ ভেরিফিকেশন কোড হলো: {otp}। কোডটি গোপন রাখুন।";
    return res.json({ success: true, template });
  } catch (err) {
    console.error('getOtpFormat error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function updateOtpFormat(req, res) {
  try {
    const { template } = req.body;
    if (template === undefined || template === null) {
      return res.status(400).json({ error: 'template value is required' });
    }
    await query(
      "INSERT INTO system_settings (setting_key, setting_value) VALUES ('otp_format_template', ?) ON DUPLICATE KEY UPDATE setting_value = ?",
      [template, template]
    );
    return res.json({ success: true, message: 'OTP format template updated successfully.' });
  } catch (err) {
    console.error('updateOtpFormat error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// 7. Resource Consumption Negative-Billing functions
const crypto = require('crypto');

async function addSite(req, res) {
  try {
    const userId = req.user.userId;
    const { site_name, site_url } = req.body;

    if (!site_name || !site_url) {
      return res.status(400).json({ success: false, error: 'Site name and Site URL are required' });
    }

    const users = await query('SELECT is_paid, active_plan_name, expiry_date FROM users WHERE id = ? LIMIT 1', [userId]);
    if (users.length === 0) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }
    const user = users[0];

    if (user.is_paid === 0 || user.active_plan_name === 'FREE_LEVEL') {
      return res.status(400).json({
        success: false,
        error: 'SUBSCRIPTION_REQUIRED',
        message: 'সাইট বা ডিভাইস হোস্ট করতে দয়া করে একটি সাবস্ক্রিপশন প্যাকেজ কিনুন।'
      });
    }

    // Check limits
    const plans = await query('SELECT max_sites FROM subscription_plans WHERE plan_name = ? LIMIT 1', [user.active_plan_name]);
    if (plans.length === 0) {
      return res.status(400).json({ success: false, error: 'Subscription plan not found' });
    }
    const plan = plans[0];

    const currentSitesRows = await query('SELECT COUNT(*) as cnt FROM gateway_layouts WHERE user_id = ?', [userId]);
    const currentSites = currentSitesRows[0].cnt;

    if (currentSites >= plan.max_sites) {
      return res.status(403).json({
        success: false,
        error: 'LIMIT_EXCEEDED',
        message: `👑 লিমিট শেষ! আরও সাইট বা ডিভাইস যুক্ত করতে অনুগ্রহ করে আপনার প্যাকেজটি আপগ্রেড করুন।`
      });
    }

    // Generate keys
    const apiKey = 'pk_' + crypto.randomBytes(16).toString('hex');
    const apiSecret = 'sk_' + crypto.randomBytes(24).toString('hex');

    // Insert into gateway_layouts
    const result = await query(
      `INSERT INTO gateway_layouts 
        (user_id, site_name, site_url, api_key, api_secret, layout_config, is_active) 
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [userId, site_name, site_url, apiKey, apiSecret, JSON.stringify({}), 1]
    );

    console.log(`[Billing] User ${userId} registered site ${site_name}. Current Level: ${user.active_plan_name}. Layout count: ${currentSites + 1}`);

    return res.json({
      success: true,
      message: 'ওয়েবসাইট সফলভাবে যুক্ত হয়েছে।',
      apiKey,
      apiSecret,
      is_paid: !!user.is_paid,
      active_plan_name: user.active_plan_name,
      expiry_date: user.expiry_date,
      site: {
        id: result.insertId,
        user_id: userId,
        site_name,
        site_url,
        is_active: 1
      }
    });

  } catch (error) {
    console.error('[Billing] addSite error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}


async function manualGrace(req, res) {
  try {
    const { id } = req.params;
    let { credits } = req.body;

    let daysValue = parseInt(credits, 10);
    if (isNaN(daysValue) || daysValue < 0) {
      daysValue = 7;
    }

    const rows = await query("SELECT expiry_date, is_paid FROM users WHERE id = ?", [id]);
    if (rows.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }
    const user = rows[0];

    let baseDate = new Date();
    if (user.is_paid === 1 && user.expiry_date && new Date(user.expiry_date) > new Date()) {
      baseDate = new Date(user.expiry_date);
    }

    baseDate.setDate(baseDate.getDate() + daysValue);
    const year = baseDate.getFullYear();
    const month = String(baseDate.getMonth() + 1).padStart(2, '0');
    const day = String(baseDate.getDate()).padStart(2, '0');
    const formattedExpiry = `${year}-${month}-${day}`;

    await query(
      "UPDATE users SET is_paid = 1, active_plan_name = 'Basic', expiry_date = ? WHERE id = ?",
      [formattedExpiry, id]
    );

    return res.json({
      success: true,
      message: `ব্যবহারকারীকে সফলভাবে ${daysValue} দিনের সাবস্ক্রিপশন মেয়াদ প্রদান করা হয়েছে।`,
      is_paid: true,
      active_plan_name: 'Basic',
      expiry_date: formattedExpiry
    });
  } catch (err) {
    console.error('[Admin Billing] manualGrace error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

module.exports = {
  verifyAdmin,
  getConfigs,
  updateConfig,
  getSmsTemplates,
  saveSmsTemplate,
  deleteSmsTemplate,
  getCheckoutTemplates,
  saveCheckoutTemplate,
  getEmailAccounts,
  saveEmailAccount,
  deleteEmailAccount,
  getSmsSettings,
  saveSmsSettings,
  deleteSmsSettings,
  resetEmailCounter,
  resetAllEmailCounters,
  listUsers,
  toggleUserBlock,
  updateDeviceTrial,
  getOtpFormat,
  updateOtpFormat,
  addSite,
  manualGrace
};
