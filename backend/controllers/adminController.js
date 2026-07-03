const prisma = require('../db/prisma');
const dataSyncCache = require('../services/dataSyncCache');
const layoutHelper = require('../services/checkoutLayoutHelper');
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
    const user = await prisma.users.findUnique({
      where: { id: decoded.userId },
      select: { role: true }
    });
    if (!user || user.role !== 'admin') {
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
    const configs = await prisma.global_config.findMany();
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
    const { key, value, configs } = req.body;

    if (configs && typeof configs === 'object') {
      const updates = Object.entries(configs).map(([k, v]) => {
        return prisma.global_config.upsert({
          where: { config_key: k },
          update: { config_value: String(v) },
          create: { config_key: k, config_value: String(v) }
        });
      });
      await Promise.all(updates);
      return res.json({ success: true, message: 'Configurations updated successfully.' });
    }

    if (!key) return res.status(400).json({ error: 'config_key is required' });
    
    await prisma.global_config.upsert({
      where: { config_key: key },
      update: { config_value: String(value) },
      create: { config_key: key, config_value: String(value) }
    });
    return res.json({ success: true, message: 'Configuration updated successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

function generateCustomRegex(smsText) {
  if (!smsText) return '';
  const patterns = smsText.split('|||');
  const compiledPatterns = patterns.map(patternText => {
      const tokens = patternText.split(/(\{[a-zA-Z0-9_]+\})/g);
      const result = tokens.map(token => {
          if (token.startsWith('{') && token.endsWith('}')) {
              const tag = token.slice(1, -1);
              if (tag === 'amount') return '(?<amount>[\\d,\\.]+)';
              if (tag === 'sender') return '(?<sender>[\\d*xX]+)';
              if (tag === 'trxid') return '(?<trxid>[A-Za-z0-9]+)';
              if (tag === 'random') return '(.*)';
              return '(.*?)';
          } else {
              return token.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
          }
      }).join('');
      return `^${result}$`;
  });
  return compiledPatterns.join('|||');
}

function regexToBrackets(regexPattern) {
  if (!regexPattern) return '';
  const patterns = regexPattern.split('|||');
  const bracketPatterns = patterns.map(pattern => {
      let s = pattern;
      if (s.startsWith('^')) s = s.slice(1);
      if (s.endsWith('$')) s = s.slice(0, -1);

      s = s.split('(?<amount>[\\d,\\.]+)').join('{amount}');
      s = s.split('(?<sender>[\\d*xX]+)').join('{sender}');
      s = s.split('(?<trxid>[A-Za-z0-9]+)').join('{trxid}');
      s = s.split('(.*?)').join('{random}');
      s = s.split('(.*)').join('{random}');

      s = s.replace(/\\([-\/\\^$*+?.()|[\]{}])/g, '$1');
      return s;
  });
  return bracketPatterns.join('|||');
}

// 2. Official SMS Templates
async function getSmsTemplates(req, res) {
  try {
    const templates = await dataSyncCache.getOfficialTemplatesForAdmin();
    const mapped = templates.map(t => ({
      ...t,
      sender_number: t.sender_number || '',
      matching_keyword: t.matching_keyword || '',
      category: t.category || 'SEND_MONEY',
      regex_pattern: regexToBrackets(t.regex_pattern || '')
    }));
    return res.json({ success: true, templates: mapped });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function saveSmsTemplate(req, res) {
  try {
    const { id, template_name, sender_id, sender_number, matching_keyword, regex_pattern, is_active, is_parseable, category } = req.body;
    if (!template_name || !sender_id) {
      return res.status(400).json({ error: 'Missing required template fields' });
    }

    const allowedCategories = ['SEND_MONEY', 'CASH_OUT', 'PAYMENT', 'BANK', 'CARD'];
    const resolvedCategory = allowedCategories.includes(category) ? category : 'SEND_MONEY';

    const data = {
      template_name,
      sender_id,
      sender_number: sender_number || null,
      matching_keyword: matching_keyword || '',
      regex_pattern: generateCustomRegex(regex_pattern ? regex_pattern.trim() : ''),
      is_active: is_active === undefined ? 1 : (is_active ? 1 : 0),
      is_parseable: is_parseable === undefined ? 1 : (is_parseable ? 1 : 0),
      category: resolvedCategory,
      is_official: 1,
      updated_at: new Date()
    };

    if (id) {
      await prisma.sms_templates.updateMany({
        where: { id: parseInt(id), is_official: 1 },
        data
      });
    } else {
      await prisma.sms_templates.create({ data });
    }

    const version = await dataSyncCache.bumpGlobalTemplateVersion();

    const io = req.app.get('io');
    if (io) {
      dataSyncCache.scheduleTemplateSyncBroadcast(io, version);
    }

    return res.json({ success: true, message: 'SMS Template saved successfully.', data_version: version });
  } catch (err) {
    console.error(err);
    const msg = err?.message || 'Internal Server Error';
    return res.status(500).json({ success: false, error: msg, message: 'টেমপ্লেট সেভ ব্যর্থ হয়েছে। সার্ভার রিস্টার্ট করে আবার চেষ্টা করুন।' });
  }
}

async function deleteSmsTemplate(req, res) {
  try {
    const { id } = req.params;
    await prisma.sms_templates.deleteMany({
      where: { id: parseInt(id), is_official: 1 }
    });

    const version = await dataSyncCache.bumpGlobalTemplateVersion();

    const io = req.app.get('io');
    if (io) {
      dataSyncCache.scheduleTemplateSyncBroadcast(io, version);
    }

    return res.json({ success: true, message: 'Official SMS Template deleted successfully.', data_version: version });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// 3. Checkout View Templates
async function getCheckoutTemplates(req, res) {
  try {
    const templates = await prisma.checkout_view_templates.findMany({
      include: {
        sms_templates: {
          select: { template_name: true }
        }
      }
    });
    
    const formatted = templates.map(t => ({
      ...t,
      template_name: t.sms_templates?.template_name
    }));

    return res.json({ success: true, templates: formatted });
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

    await prisma.checkout_view_templates.upsert({
      where: { sms_template_id: parseInt(sms_template_id) },
      update: { single_number_instruction, multiple_number_instruction },
      create: { 
        sms_template_id: parseInt(sms_template_id), 
        single_number_instruction, 
        multiple_number_instruction 
      }
    });
    return res.json({ success: true, message: 'Checkout instructions saved successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// 4. SMTP (Email Round-Robin) Accounts
async function getEmailAccounts(req, res) {
  try {
    const accounts = await prisma.smtp_gateways.findMany();
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

    const data = {
      email,
      password,
      host,
      port: parseInt(port),
      secure: secure ? 1 : 0,
      daily_limit: parseInt(daily_limit) || 500,
      is_active: is_active ? 1 : 0
    };

    if (id) {
      await prisma.smtp_gateways.update({
        where: { id: parseInt(id) },
        data
      });
    } else {
      await prisma.smtp_gateways.create({ data });
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
    await prisma.smtp_gateways.delete({
      where: { id: parseInt(id) }
    });
    return res.json({ success: true, message: 'SMTP Profile deleted successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// 5. SMS Gateway Settings
async function getSmsSettings(req, res) {
  try {
    const settings = await prisma.sms_gateways.findMany();
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

    const data = {
      gateway_url,
      http_method: http_method || 'GET',
      post_body_template: post_body_template || null,
      api_key: api_key || null,
      username: username || null,
      sender_id: sender_id || null,
      is_active: activeFlag,
      provider_name: provider_name || null
    };

    let savedId;
    if (id) {
      const updated = await prisma.sms_gateways.update({
        where: { id: parseInt(id) },
        data
      });
      savedId = updated.id;
    } else {
      const created = await prisma.sms_gateways.create({ data });
      savedId = created.id;
    }

    if (activeFlag) {
      await prisma.sms_gateways.updateMany({
        where: { id: { not: savedId } },
        data: { is_active: 0 }
      });
      console.log(`[SMS-POLICY] One-Active enforced. Only provider id=${savedId} is now active.`);
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
    await prisma.sms_gateways.delete({
      where: { id: parseInt(id) }
    });
    return res.json({ success: true, message: 'SMS Gateway provider deleted successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function resetEmailCounter(req, res) {
  try {
    const { id } = req.params;
    if (!id) return res.status(400).json({ error: 'Email account id is required' });
    await prisma.smtp_gateways.update({
      where: { id: parseInt(id) },
      data: { sent_today: 0 }
    });
    return res.json({ success: true, message: 'Daily email counter reset to 0 successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function resetAllEmailCounters(req, res) {
  try {
    await prisma.smtp_gateways.updateMany({
      data: { sent_today: 0 }
    });
    return res.json({ success: true, message: 'All daily email counters reset to 0.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// 6. User and Device Listing
async function listUsers(req, res) {
  try {
    const users = await prisma.users.findMany();
    const devices = await prisma.registered_devices.findMany();

    const devicesByUserId = {};
    devices.forEach(d => {
      const userId = d.user_id;
      const deviceObj = { 
        id: d.id,
        deviceId: d.device_id,
        deviceName: d.device_name,
        customName: d.custom_name,
        deviceModel: d.device_model,
        androidVersion: d.android_version,
        status: d.status,
        isParent: d.is_parent,
        lastSeenAt: d.last_seen_at,
        lastBatteryPercent: d.last_battery_percent,
        trialExpiresAt: d.trial_expires_at,
        isTrialLocked: d.is_trial_locked,
        lockReason: d.lock_reason
      };
      
      if (!devicesByUserId[userId]) {
        devicesByUserId[userId] = [];
      }
      devicesByUserId[userId].push(deviceObj);
    });

    const result = users.map(u => ({
      ...u,
      is_paid: !!u.is_paid,
      blocked: !!u.blocked,
      profile_complete: !!u.profile_complete,
      devices: devicesByUserId[u.id] || []
    }));

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

    await prisma.users.update({
      where: { id: parseInt(id) },
      data: { blocked: blocked ? 1 : 0 }
    });
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

    const data = {};
    if (trial_expires_at !== undefined) data.trial_expires_at = trial_expires_at ? new Date(trial_expires_at) : null;
    if (is_trial_locked !== undefined) data.is_trial_locked = is_trial_locked ? 1 : 0;
    if (lock_reason !== undefined) data.lock_reason = lock_reason || null;

    await prisma.registered_devices.update({
      where: { id: parseInt(id) },
      data
    });

    return res.json({ success: true, message: 'Device trial parameters updated successfully.' });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function getOtpFormat(req, res) {
  try {
    const row = await prisma.otp_sms_templates.findUnique({
      where: { setting_key: 'otp_format_template' }
    });
    const template = row?.setting_value || "আপনার প্রিয় পে-চেক অ্যাপ ভেরিফিকেশন কোড হলো: {otp}। কোডটি গোপন রাখুন।";
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
    await prisma.otp_sms_templates.upsert({
      where: { setting_key: 'otp_format_template' },
      update: { setting_value: template },
      create: { setting_key: 'otp_format_template', setting_value: template }
    });
    return res.json({ success: true, message: 'OTP format template updated successfully.' });
  } catch (err) {
    console.error('updateOtpFormat error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

const crypto = require('crypto');

async function addSite(req, res) {
  try {
    const userId = req.user.userId;
    const { site_name, site_url } = req.body;

    if (!site_name || !site_url) {
      return res.status(400).json({ success: false, error: 'Site name and Site URL are required' });
    }

    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { is_paid: true, active_plan_name: true, expiry_date: true }
    });
    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    if (!user.is_paid || user.active_plan_name === 'FREE_LEVEL') {
      return res.status(400).json({
        success: false,
        error: 'SUBSCRIPTION_REQUIRED',
        message: 'সাইট বা ডিভাইস হোস্ট করতে দয়া করে একটি সাবস্ক্রিপশন প্যাকেজ কিনুন।'
      });
    }

    let maxSites = 1;
    if (user.active_plan_name === 'Trial Package') {
      const configVal = await prisma.global_config.findUnique({
        where: { config_key: 'trial_max_sites' }
      });
      maxSites = configVal ? parseInt(configVal.config_value, 10) : 1;
    } else {
      const plan = await prisma.subscription_plans.findUnique({
        where: { plan_name: user.active_plan_name }
      });
      if (!plan) {
        return res.status(400).json({ success: false, error: 'Subscription plan not found' });
      }
      maxSites = plan.max_sites;
    }

    const currentSites = await prisma.gateway_layouts.count({
      where: { user_id: userId }
    });

    if (currentSites >= maxSites) {
      return res.status(403).json({
        success: false,
        error: 'LIMIT_EXCEEDED',
        message: `👑 লিমিট শেষ! আরও সাইট বা ডিভাইস যুক্ত করতে অনুগ্রহ করে আপনার প্যাকেজটি আপগ্রেড করুন।`
      });
    }

    const apiKey = 'pk_' + crypto.randomBytes(16).toString('hex');
    const apiSecret = 'sk_' + crypto.randomBytes(24).toString('hex');

    const result = await prisma.gateway_layouts.create({
      data: {
        user_id: userId,
        site_name,
        site_url,
        api_key: apiKey,
        api_secret: apiSecret,
        layout_config: JSON.stringify({})
      }
    });

    console.log(`[Billing] User ${userId} registered site ${site_name}. Current Level: ${user.active_plan_name}. Layout count: ${currentSites + 1}`);

    return res.json({
      success: true,
      message: 'ওয়েবসাইট সফলভাবে যুক্ত হয়েছে।',
      apiKey,
      apiSecret,
      is_paid: !!user.is_paid,
      active_plan_name: user.active_plan_name,
      expiry_date: user.expiry_date,
      site: result
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

    const user = await prisma.users.findUnique({
      where: { id: parseInt(id) },
      select: { expiry_date: true, is_paid: true }
    });
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    let baseDate = new Date();
    if (user.is_paid && user.expiry_date && new Date(user.expiry_date) > new Date()) {
      baseDate = new Date(user.expiry_date);
    }

    baseDate.setDate(baseDate.getDate() + daysValue);
    const formattedExpiry = new Date(baseDate);

    await prisma.users.update({
      where: { id: parseInt(id) },
      data: {
        is_paid: 1,
        active_plan_name: 'Basic',
        expiry_date: formattedExpiry
      }
    });

    return res.json({
      success: true,
      message: `ব্যবহারকারীকে সফলভাবে ${daysValue} দিনের সাবস্ক্রিপশন মেয়াদ প্রদান করা হয়েছে।`,
      is_paid: 1,
      active_plan_name: 'Basic',
      expiry_date: formattedExpiry
    });
  } catch (err) {
    console.error('[Admin Billing] manualGrace error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * Admin: control merchant callback/commission permissions for a website.
 * GET  /api/admin/websites            → list all websites (any user) with flags
 * POST /api/admin/websites/:id/permissions → set permission flags
 *
 * Permission flags are ONLY writable here (never by the merchant), satisfying
 * "Admin decides whether merchant can receive Payment Type / Commission".
 */
async function listAllWebsites(req, res) {
  try {
    const rows = await prisma.gateway_layouts.findMany({
      orderBy: { created_at: 'desc' },
      select: {
        id: true, user_id: true, site_name: true, site_url: true, merchant_id: true,
        api_key: true, is_active: true,
        allow_payment_type_callback: true, allow_commission_callback: true,
        commission_enabled: true, created_at: true,
      },
    });
    return res.json({ success: true, websites: rows });
  } catch (err) {
    console.error('listAllWebsites error:', err);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function setWebsitePermissions(req, res) {
  try {
    const id = parseInt(req.params.id, 10);
    if (!Number.isFinite(id)) {
      return res.status(400).json({ success: false, error: 'INVALID_WEBSITE_ID' });
    }
    const existing = await prisma.gateway_layouts.findUnique({ where: { id } });
    if (!existing) {
      return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    }

    const b = req.body || {};
    const data = { updated_at: new Date() };
    if (b.allow_payment_type_callback !== undefined) data.allow_payment_type_callback = b.allow_payment_type_callback ? 1 : 0;
    if (b.allow_commission_callback !== undefined) data.allow_commission_callback = b.allow_commission_callback ? 1 : 0;
    if (b.commission_enabled !== undefined) data.commission_enabled = b.commission_enabled ? 1 : 0;

    if (Object.keys(data).length === 1) {
      return res.status(400).json({ success: false, error: 'NO_PERMISSION_FIELDS' });
    }

    const updated = await prisma.gateway_layouts.update({ where: { id }, data });
    return res.json({
      success: true,
      website: {
        id: updated.id,
        user_id: updated.user_id,
        site_name: updated.site_name,
        site_url: updated.site_url,
        merchant_id: updated.merchant_id,
        api_key: updated.api_key,
        is_active: updated.is_active,
        allow_payment_type_callback: updated.allow_payment_type_callback,
        allow_commission_callback: updated.allow_commission_callback,
        commission_enabled: updated.commission_enabled,
      },
    });
  } catch (err) {
    console.error('setWebsitePermissions error:', err);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** GET /api/admin/checkout-design — global tab icons/labels + provider branding */
async function getCheckoutDesignConfig(req, res) {
  try {
    const { tabs: globalTabs, providerBranding } = await layoutHelper.loadGlobalCheckoutDefaults();
    const tabs = layoutHelper.parseTabs(null, globalTabs);
    const providers = layoutHelper.resolveProviderBranding(providerBranding);
    return res.json({ success: true, tabs, providerBranding: providers, designs: ['design-1', 'design-2', 'design-3'] });
  } catch (err) {
    console.error('getCheckoutDesignConfig error:', err);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** POST /api/admin/checkout-design — save global checkout tab + provider config (all merchants) */
async function saveCheckoutDesignConfig(req, res) {
  try {
    const b = req.body || {};
    const tabsInput = b.tabs || {};
    const tabs = {};
    for (const [key, def] of Object.entries(layoutHelper.DEFAULT_TABS)) {
      const ov = tabsInput[key] || {};
      tabs[key] = {
        enabled: ov.enabled !== undefined ? !!ov.enabled : def.enabled,
        label: String(ov.label || def.label).trim() || def.label,
        icon: String(ov.icon || def.icon).trim() || def.icon,
        iconUrl: String(ov.iconUrl || '').trim(),
        category: String(ov.category || def.category).toUpperCase(),
      };
    }
    const providerBranding = b.providerBranding && typeof b.providerBranding === 'object'
      ? b.providerBranding : layoutHelper.DEFAULT_PROVIDER_BRANDING;
    await layoutHelper.saveGlobalCheckoutDefaults(tabs, providerBranding);
    return res.json({
      success: true,
      message: 'গ্লোবাল চেকআউট ডিজাইন কনফিগ সংরক্ষণ হয়েছে। সব মার্চেন্টে প্রযোজ্য হবে।',
      tabs: layoutHelper.parseTabs(null, tabs),
      providerBranding: layoutHelper.resolveProviderBranding(providerBranding),
    });
  } catch (err) {
    console.error('saveCheckoutDesignConfig error:', err);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

module.exports = {
  verifyAdmin,
  listAllWebsites,
  setWebsitePermissions,
  getCheckoutDesignConfig,
  saveCheckoutDesignConfig,
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
  manualGrace,
  generateCustomRegex
};
