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
    const { id, gateway_url, http_method, post_body_template, api_key, username, sender_id, is_active } = req.body;
    if (!gateway_url) {
      return res.status(400).json({ error: 'gateway_url is required' });
    }

    if (id) {
      await query(
        `UPDATE sms_settings 
         SET gateway_url = ?, http_method = ?, post_body_template = ?, api_key = ?, username = ?, sender_id = ?, is_active = ? 
         WHERE id = ?`,
        [gateway_url, http_method || 'GET', post_body_template || null, api_key || null, username || null, sender_id || null, is_active ? 1 : 0, id]
      );
    } else {
      await query(
        `INSERT INTO sms_settings (gateway_url, http_method, post_body_template, api_key, username, sender_id, is_active) 
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
        [gateway_url, http_method || 'GET', post_body_template || null, api_key || null, username || null, sender_id || null, is_active ? 1 : 0]
      );
    }
    return res.json({ success: true, message: 'SMS Gateway configurations saved successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// 6. User and Device Listing
async function listUsers(req, res) {
  try {
    const users = await query(
      `SELECT u.id, u.name, u.phone, u.email, u.role, u.balance, u.blocked, u.profile_complete, u.created_at,
              JSON_ARRAYAGG(
                JSON_OBJECT(
                  'id', rd.id,
                  'deviceId', rd.device_id,
                  'deviceName', rd.device_name,
                  'customName', rd.custom_name,
                  'deviceModel', rd.device_model,
                  'androidVersion', rd.android_version,
                  'status', rd.status,
                  'isParent', rd.is_parent,
                  'lastSeenAt', rd.last_seen_at,
                  'lastBatteryPercent', rd.last_battery_percent,
                  'trialExpiresAt', rd.trial_expires_at,
                  'isTrialLocked', rd.is_trial_locked,
                  'lockReason', rd.lock_reason
                )
              ) AS devices
       FROM users u
       LEFT JOIN registered_devices rd ON u.id = rd.user_id
       GROUP BY u.id`
    );

    const result = users.map(u => {
      let parsedDevices = [];
      try {
        parsedDevices = typeof u.devices === 'string' ? JSON.parse(u.devices) : u.devices;
        if (parsedDevices.length === 1 && parsedDevices[0].id === null) {
          parsedDevices = [];
        }
      } catch (e) {
        parsedDevices = [];
      }
      return { ...u, devices: parsedDevices };
    });

    return res.json({ success: true, users: result });
  } catch (err) {
    console.error(err);
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
  listUsers,
  toggleUserBlock,
  updateDeviceTrial
};
