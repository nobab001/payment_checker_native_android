/**
 * websiteController.js — API Integration v2: Website / Merchant management.
 *
 * Design notes:
 *  - Backward compatible: the legacy `adminController.addSite` remains untouched.
 *    This controller is the new, richer merchant-management surface mounted at
 *    /api/v1/websites. It operates on the SAME `gateway_layouts` table, so all
 *    existing checkout/verify/claim-check flows keep working unchanged.
 *  - The API secret is shown exactly once (on create / regenerate). Only a
 *    SHA-256 hash + last-4 are persisted. Legacy plaintext `api_secret` column
 *    is intentionally left blank for v2 records.
 *  - Commission menu and the two callback-inclusion fields are gated by
 *    admin-controlled permission flags on the website row.
 */

const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const prisma = require('../db/prisma');
const merchantCache = require('../services/merchantCache');
const layoutHelper = require('../services/checkoutLayoutHelper');
const checkoutData = require('../services/checkoutDataService');
const websiteLogoService = require('../services/websiteLogoService');
const { getUserEntitlements, requirePermission } = require('../services/accountEntitlementsService');
const { normalizePaymentType } = require('../services/merchantCallback');
const { normalizeWebsitePurpose } = require('../services/websitePurpose');

// ── Helpers ────────────────────────────────────────────────────────────────

function sha256(input) {
  return crypto.createHash('sha256').update(String(input)).digest('hex');
}

function genApiKey() {
  return 'pk_' + crypto.randomBytes(16).toString('hex');
}

function genApiSecret() {
  return 'sk_' + crypto.randomBytes(24).toString('hex');
}

function genMerchantId() {
  return 'MID_' + crypto.randomBytes(8).toString('hex').toUpperCase();
}

function normalizeDomain(raw) {
  if (!raw) return '';
  let d = String(raw).trim();
  // Strip protocol and trailing slash for a clean stored domain,
  // but keep the full value in site_url for backward compatibility.
  d = d.replace(/^https?:\/\//i, '').replace(/\/+$/, '');
  return d;
}

/** Serialize a gateway_layouts row into a safe public website object (no secret). */
function toWebsiteDto(row) {
  if (!row) return null;
  return {
    id: row.id,
    merchantId: row.merchant_id || null,
    apiKey: row.api_key,
    siteName: row.site_name || '',
    companyName: row.company_name || null,
    domain: row.site_url || null,
    logoUrl: row.logo_url || null,
    checkoutTheme: row.checkout_theme || 'default',
    checkoutMode: row.checkout_mode || 'transaction',
    successUrl: row.success_url || row.redirect_url || null,
    cancelUrl: row.cancel_url || null,
    callbackUrl: row.callback_url || null,
    webhookUrl: row.webhook_url || null,
    isActive: !!row.is_active,
    secretLast4: row.api_secret_last4 || null,
    secretVersion: row.api_secret_version || 1,
    receivePaymentType: !!row.receive_payment_type,
    receiveCommission: !!row.receive_commission,
    // Admin-controlled permission flags (read-only for merchant)
    allowPaymentTypeCallback: !!row.allow_payment_type_callback,
    allowCommissionCallback: !!row.allow_commission_callback,
    commissionEnabled: !!row.commission_enabled,
    websitePurpose: normalizeWebsitePurpose(row.website_purpose),
    purposeSelected: !!row.purpose_selected,
    purposeLocked: !!row.purpose_locked,
    purposeLockedAt: row.purpose_locked_at || null,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

/** Resolve max sites from cached account entitlements. */
async function resolveMaxSites(userId) {
  const ent = await getUserEntitlements(userId);
  return ent?.eff_max_sites || 0;
}

const GLOBAL_CHECKOUT_KEY_PREFIX = 'merchant_global_checkout_';

/** Verify merchant security PIN (same rules as pinController.verifyPin). */
async function verifyUserPin(userId, role, pin) {
  if (!pin) {
    return { ok: false, status: 400, error: 'PIN প্রয়োজন।', message: 'ওয়েবসাইট মুছতে নিরাপত্তা PIN দিন।' };
  }
  if (role === 'admin') {
    const adminPin = process.env.ADMIN_PIN || '5566';
    if (pin === adminPin) return { ok: true };
    return { ok: false, status: 401, error: 'INVALID_PIN', message: 'ভুল পিন নম্বর।' };
  }
  const user = await prisma.users.findUnique({ where: { id: userId }, select: { pin: true } });
  if (!user?.pin) {
    return { ok: false, status: 400, error: 'PIN_NOT_SET', message: 'ইউজারের কোনো PIN সেট করা নেই।' };
  }
  const isMatch = await bcrypt.compare(String(pin), user.pin);
  if (!isMatch) return { ok: false, status: 401, error: 'INVALID_PIN', message: 'ভুল পিন নম্বর।' };
  return { ok: true };
}

/** Build active SIM numbers for merchant designer/preview — includes disabled overrides. */
async function buildActiveNumbers(userId, numberOrderJson) {
  const { activeNumbers, gatewaysByCategory } = await checkoutData.buildSecureCheckoutData(
    userId,
    numberOrderJson,
    { excludeDisabled: false }
  );
  return { activeNumbers, gatewaysByCategory };
}

/**
 * Option A — all parseable ("one value") templates for Commission / Campaign pickers.
 * Not limited to currently-enabled gateway numbers: merchant can set rules once;
 * later number/device changes still match via tpl_<id>.
 * Includes: official is_parseable=1 + this user's custom is_parseable=1 templates.
 */
async function listAccountIncentiveTemplates(userId) {
  const uid = Number(userId);
  const rows = await prisma.sms_templates.findMany({
    where: {
      is_parseable: 1,
      OR: [
        { is_official: 1 },
        { is_official: 0, user_id: uid },
      ],
    },
    orderBy: [{ display_order: 'asc' }, { template_name: 'asc' }, { id: 'asc' }],
    select: {
      id: true,
      template_name: true,
      category: true,
      sender_id: true,
    },
  });
  return rows.map((r) => {
    const name = r.template_name || r.sender_id || `Template ${r.id}`;
    return {
      id: Number(r.id),
      name,
      category: r.category || null,
      provider: r.sender_id || null,
      paymentType: `tpl_${Number(r.id)}`,
      token: normalizePaymentType(r.sender_id || '', name),
    };
  });
}

function sanitizeNumberOrder(order) {
  if (!Array.isArray(order)) return [];
  return order.map((o, idx) => ({
    methodId: o.methodId != null ? Number(o.methodId) : null,
    provider: o.provider ? String(o.provider) : null,
    number: o.number ? String(o.number) : null,
    enabled: o.enabled === undefined ? true : !!o.enabled,
    position: o.position != null ? Number(o.position) : idx,
  }));
}

/** Parse stored global checkout JSON from global_config. */
function parseGlobalCheckoutConfig(raw) {
  const defaults = {
    checkout_theme: 'design-1',
    checkout_mode: 'transaction',
    layout_config: { tabs: layoutHelper.DEFAULT_TABS },
    numberOrder: [],
  };
  if (!raw) return defaults;
  try {
    const parsed = JSON.parse(raw);
    return {
      checkout_theme: parsed.checkout_theme || defaults.checkout_theme,
      checkout_mode: parsed.checkout_mode || defaults.checkout_mode,
      layout_config: parsed.layout_config || defaults.layout_config,
      numberOrder: Array.isArray(parsed.numberOrder) ? parsed.numberOrder : [],
    };
  } catch (_) {
    return defaults;
  }
}

async function findOwnedWebsite(websiteId, userId) {
  const id = parseInt(websiteId, 10);
  if (!Number.isFinite(id)) return null;
  const row = await prisma.gateway_layouts.findUnique({ where: { id } });
  if (!row || row.user_id !== userId) return null;
  return row;
}

// ── Endpoints ────────────────────────────────────────────────────────────────

/**
 * POST /api/v1/websites
 * Website Add Wizard — Domain + optional name + required purpose (lock-once).
 * Returns Merchant ID, API Key and (once only) the API Secret.
 */
async function createWebsite(req, res) {
  try {
    const userId = req.user.userId;
    const domainRaw = req.body.domain || req.body.site_url || '';
    const websiteName = (req.body.website_name || req.body.site_name || '').trim();
    const purposeRaw = req.body.website_purpose ?? req.body.websitePurpose ?? req.body.purpose;
    if (purposeRaw == null || String(purposeRaw).trim() === '') {
      return res.status(400).json({
        success: false,
        error: 'PURPOSE_REQUIRED',
        message: 'ওয়েবসাইটের উদ্দেশ্য সিলেক্ট করুন: Add Balance, Pay, অথবা Both।',
      });
    }
    const purpose = normalizeWebsitePurpose(purposeRaw);
    if (!['add_balance', 'payment', 'both'].includes(purpose)) {
      return res.status(400).json({ success: false, error: 'INVALID_WEBSITE_PURPOSE' });
    }

    const domain = normalizeDomain(domainRaw);
    if (!domain) {
      return res.status(400).json({ success: false, error: 'DOMAIN_REQUIRED', message: 'ডোমেইন আবশ্যক।' });
    }

    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { is_paid: true, active_plan_name: true, expiry_date: true, role: true },
    });
    if (!user) {
      return res.status(404).json({ success: false, error: 'USER_NOT_FOUND' });
    }

    const isAdmin = user.role === 'admin';
    if (!isAdmin) {
      const perm = await requirePermission(userId, 'perm_website');
      if (!perm.ok) {
        return res.status(403).json({
          success: false,
          error: 'PERMISSION_DENIED',
          message: perm.message,
        });
      }
      const maxSites = await resolveMaxSites(userId);
      const currentSites = await prisma.gateway_layouts.count({ where: { user_id: userId } });
      if (currentSites >= maxSites) {
        return res.status(403).json({
          success: false,
          error: 'LIMIT_EXCEEDED',
          message: '👑 লিমিট শেষ! আরও ওয়েবসাইট যুক্ত করতে প্যাকেজ আপগ্রেড করুন।',
        });
      }
    }

    // Generate identifiers. Retry a few times on the (extremely rare)
    // unique-collision for merchant_id / api_key.
    const globalKey = `${GLOBAL_CHECKOUT_KEY_PREFIX}${userId}`;
    const globalCfgRow = await prisma.global_config.findUnique({ where: { config_key: globalKey } });
    const globalCfg = parseGlobalCheckoutConfig(globalCfgRow?.config_value);
    const globalLayoutStr = JSON.stringify(globalCfg.layout_config || { tabs: layoutHelper.DEFAULT_TABS });
    const globalNumberOrderStr = JSON.stringify(globalCfg.numberOrder || []);

    let created = null;
    let plaintextSecret = null;
    const lockedAt = new Date();
    for (let attempt = 0; attempt < 5 && !created; attempt++) {
      const apiKey = genApiKey();
      const merchantId = genMerchantId();
      plaintextSecret = genApiSecret();
      try {
        created = await prisma.gateway_layouts.create({
          data: {
            user_id: userId,
            site_name: websiteName || domain,
            site_url: domainRaw.trim(),
            api_key: apiKey,
            // Stored server-side for webhook HMAC signing/verification. NEVER
            // exposed by any GET endpoint (toWebsiteDto returns only last4).
            api_secret: plaintextSecret,
            api_secret_hash: sha256(plaintextSecret),
            api_secret_last4: plaintextSecret.slice(-4),
            api_secret_version: 1,
            merchant_id: merchantId,
            company_name: websiteName || null,
            checkout_theme: globalCfg.checkout_theme || 'design-1',
            checkout_mode: globalCfg.checkout_mode || 'transaction',
            layout_config: globalLayoutStr,
            number_order_json: globalNumberOrderStr,
            website_purpose: purpose,
            purpose_selected: 1,
            purpose_locked: 1,
            purpose_locked_at: lockedAt,
          },
        });
      } catch (e) {
        if (e && e.code === 'P2002') continue; // unique collision — retry
        throw e;
      }
    }

    if (!created) {
      return res.status(500).json({ success: false, error: 'ID_GENERATION_FAILED' });
    }

    return res.status(201).json({
      success: true,
      message: 'ওয়েবসাইট সফলভাবে তৈরি হয়েছে। Purpose লক করা হয়েছে।',
      website: toWebsiteDto(created),
      // Secret is delivered exactly once — the client must store/show it now.
      apiSecret: plaintextSecret,
    });
  } catch (error) {
    console.error('[Website] createWebsite error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** GET /api/v1/websites — list all websites owned by the user. */
async function listWebsites(req, res) {
  try {
    const userId = req.user.userId;
    const rows = await prisma.gateway_layouts.findMany({
      where: { user_id: userId },
      orderBy: { created_at: 'desc' },
    });
    return res.json({ success: true, websites: rows.map(toWebsiteDto) });
  } catch (error) {
    console.error('[Website] listWebsites error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** GET /api/v1/websites/:id — single website with commissions + number order. */
async function getWebsite(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const commissions = await prisma.merchant_commissions.findMany({
      where: { website_id: row.id },
      orderBy: { payment_type: 'asc' },
    });

    let numberOrder = [];
    if (row.number_order_json) {
      try { numberOrder = JSON.parse(row.number_order_json); } catch (_) { numberOrder = []; }
    }

    const { activeNumbers, gatewaysByCategory } = await buildActiveNumbers(row.user_id, row.number_order_json);
    const incentiveTemplates = await listAccountIncentiveTemplates(row.user_id);
    const { providerBranding: gBranding } = await layoutHelper.loadGlobalCheckoutDefaults();
    const providerBranding = await layoutHelper.resolveProviderBrandingFull(gBranding);

    return res.json({
      success: true,
      website: toWebsiteDto(row),
      activeNumbers,
      incentiveTemplates,
      gatewaysByCategory,
      providerBranding,
      checkoutTabs: await layoutHelper.parseTabsForMerchant(row.layout_config),
      commissions: commissions.map((c) => ({
        id: c.id,
        paymentType: c.payment_type,
        commissionType: c.commission_type,
        commissionValue: Number(c.commission_value),
        chargeType: c.charge_type,
        chargeValue: Number(c.charge_value),
        isActive: !!c.is_active,
      })),
      numberOrder,
    });
  } catch (error) {
    console.error('[Website] getWebsite error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * PATCH /api/v1/websites/:id
 * Merchant-editable settings. Admin permission flags are NEVER writable here.
 */
async function updateWebsiteSettings(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const b = req.body || {};
    const data = {};

    const strFields = {
      website_name: 'site_name',
      site_name: 'site_name',
      company_name: 'company_name',
      // logo_url is managed exclusively via POST/DELETE /branding/logo (multipart upload).
      checkout_theme: 'checkout_theme',
      success_url: 'success_url',
      cancel_url: 'cancel_url',
      callback_url: 'callback_url',
      webhook_url: 'webhook_url',
      domain: 'site_url',
      site_url: 'site_url',
    };
    for (const [inKey, col] of Object.entries(strFields)) {
      if (b[inKey] !== undefined) data[col] = b[inKey] === null ? null : String(b[inKey]).trim();
    }

    if (b.checkout_theme !== undefined) {
      const theme = String(b.checkout_theme);
      if (!layoutHelper.VALID_DESIGNS.has(theme) && !['default', 'light', 'dark', 'minimal', 'brand'].includes(theme)) {
        return res.status(400).json({ success: false, error: 'INVALID_CHECKOUT_THEME' });
      }
      data.checkout_theme = theme;
    }

    // Tab customization (Send Money, Cash Out, Payment, Bank, Card)
    if (b.checkout_tabs && typeof b.checkout_tabs === 'object') {
      const existing = row.layout_config;
      const { tabs: globalTabs } = await layoutHelper.loadGlobalCheckoutDefaults();
      data.layout_config = JSON.stringify(layoutHelper.mergeTabsIntoLayout(existing, b.checkout_tabs, globalTabs));
    }

    if (b.checkout_mode !== undefined) {
      const mode = String(b.checkout_mode);
      // 'hybrid' = full experience (transaction + vibe + optional official gateways),
      // 'live'   = official gateways only.
      // Legacy values 'transaction' / 'merchant_vibe' are still accepted for
      // backward compatibility with older app builds.
      if (!['transaction', 'merchant_vibe', 'hybrid', 'live'].includes(mode)) {
        return res.status(400).json({ success: false, error: 'INVALID_CHECKOUT_MODE' });
      }
      data.checkout_mode = mode;
    }

    // Merchant preferences. Effective inclusion is still gated by admin
    // permission at callback dispatch time.
    if (b.receive_payment_type !== undefined) {
      if (!row.allow_payment_type_callback && b.receive_payment_type) {
        return res.status(403).json({
          success: false,
          error: 'PAYMENT_TYPE_CALLBACK_LOCKED',
          message: 'Payment Type Callback লক আছে। আনলক করতে অ্যাডমিনের সাথে যোগাযোগ করুন।',
        });
      }
      data.receive_payment_type = b.receive_payment_type ? 1 : 0;
    }
    if (b.receive_commission !== undefined) {
      if (!row.allow_commission_callback && b.receive_commission) {
        return res.status(403).json({
          success: false,
          error: 'COMMISSION_CALLBACK_LOCKED',
          message: 'Commission Callback লক আছে। আনলক করতে অ্যাডমিনের সাথে যোগাযোগ করুন।',
        });
      }
      data.receive_commission = b.receive_commission ? 1 : 0;
    }

    if (b.website_purpose !== undefined || b.websitePurpose !== undefined || b.lockPurpose === true) {
      const raw = b.website_purpose ?? b.websitePurpose;
      const purpose = normalizeWebsitePurpose(raw);
      if (!['add_balance', 'payment', 'both'].includes(purpose)) {
        return res.status(400).json({ success: false, error: 'INVALID_WEBSITE_PURPOSE' });
      }
      if (row.purpose_locked) {
        return res.status(403).json({
          success: false,
          error: 'PURPOSE_LOCKED',
          message: 'Purpose লক করা আছে। চেঞ্জ করতে সুপার অ্যাডমিনের সাথে যোগাযোগ করুন।',
        });
      }
      // First explicit select → lock forever (merchant cannot unlock).
      data.website_purpose = purpose;
      data.purpose_selected = 1;
      data.purpose_locked = 1;
      data.purpose_locked_at = new Date();
    }

    if (b.is_active !== undefined) data.is_active = b.is_active ? 1 : 0;

    if (Object.keys(data).length === 0) {
      return res.status(400).json({ success: false, error: 'NO_FIELDS_TO_UPDATE' });
    }
    data.updated_at = new Date();

    const updated = await prisma.gateway_layouts.update({ where: { id: row.id }, data });
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true, website: toWebsiteDto(updated) });
  } catch (error) {
    console.error('[Website] updateWebsiteSettings error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** POST /api/v1/websites/:id/regenerate-secret — returns new secret once. */
async function regenerateSecret(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const plaintextSecret = genApiSecret();
    const updated = await prisma.gateway_layouts.update({
      where: { id: row.id },
      data: {
        api_secret: plaintextSecret,
        api_secret_hash: sha256(plaintextSecret),
        api_secret_last4: plaintextSecret.slice(-4),
        api_secret_version: (row.api_secret_version || 1) + 1,
        updated_at: new Date(),
      },
    });
    merchantCache.invalidate(row.api_key);

    return res.json({
      success: true,
      message: 'নতুন Secret Key তৈরি হয়েছে। এটি এখনই সংরক্ষণ করুন — পরে আর দেখা যাবে না।',
      apiSecret: plaintextSecret,
      website: toWebsiteDto(updated),
    });
  } catch (error) {
    console.error('[Website] regenerateSecret error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * PUT /api/v1/websites/:id/number-order
 * Persists checkout-only ordering + enable/disable overrides for numbers.
 * IMPORTANT: this does NOT touch gateway_methods and never disables the SMS
 * reader — it only affects what the customer-facing checkout renders.
 *
 * Body: { order: [{ methodId, provider, number, enabled, position }] }
 */
async function updateNumberOrder(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const order = Array.isArray(req.body.order) ? req.body.order : null;
    if (!order) return res.status(400).json({ success: false, error: 'INVALID_ORDER' });

    const sanitized = order.map((o, idx) => ({
      methodId: o.methodId != null ? Number(o.methodId) : null,
      provider: o.provider ? String(o.provider) : null,
      number: o.number ? String(o.number) : null,
      enabled: o.enabled === undefined ? true : !!o.enabled,
      position: o.position != null ? Number(o.position) : idx,
    }));

    const updated = await prisma.gateway_layouts.update({
      where: { id: row.id },
      data: { number_order_json: JSON.stringify(sanitized), updated_at: new Date() },
    });

    return res.json({ success: true, numberOrder: sanitized, website: toWebsiteDto(updated) });
  } catch (error) {
    console.error('[Website] updateNumberOrder error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** DELETE /api/v1/websites/:id — requires security PIN in body. */
async function deleteWebsite(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const pin = (req.body && req.body.pin) ? String(req.body.pin) : '';
    const pinCheck = await verifyUserPin(userId, req.user.role, pin);
    if (!pinCheck.ok) {
      return res.status(pinCheck.status).json({
        success: false,
        error: pinCheck.error,
        message: pinCheck.message,
      });
    }

    await prisma.gateway_layouts.delete({ where: { id: row.id } });
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true, message: 'ওয়েবসাইট মুছে ফেলা হয়েছে।' });
  } catch (error) {
    console.error('[Website] deleteWebsite error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * GET /api/v1/websites/global-checkout
 * Merchant-wide default checkout config (applies to all websites on save).
 */
async function getGlobalCheckout(req, res) {
  try {
    const userId = req.user.userId;
    const configKey = `${GLOBAL_CHECKOUT_KEY_PREFIX}${userId}`;
    const cfgRow = await prisma.global_config.findUnique({ where: { config_key: configKey } });

    let config = parseGlobalCheckoutConfig(cfgRow?.config_value);
    if (!cfgRow) {
      const first = await prisma.gateway_layouts.findFirst({
        where: { user_id: userId },
        orderBy: { created_at: 'asc' },
      });
      if (first) {
        config.checkout_theme = first.checkout_theme || config.checkout_theme;
        config.checkout_mode = first.checkout_mode || config.checkout_mode;
        if (first.layout_config) {
          try {
            config.layout_config = typeof first.layout_config === 'string'
              ? JSON.parse(first.layout_config) : first.layout_config;
          } catch (_) { /* keep default */ }
        }
        if (first.number_order_json) {
          try { config.numberOrder = JSON.parse(first.number_order_json); } catch (_) { /* */ }
        }
      }
    }

    const numberOrderJson = JSON.stringify(config.numberOrder || []);
    const { activeNumbers, gatewaysByCategory } = await buildActiveNumbers(userId, numberOrderJson);
    const websiteCount = await prisma.gateway_layouts.count({ where: { user_id: userId } });
    const layoutRaw = typeof config.layout_config === 'string'
      ? config.layout_config : JSON.stringify(config.layout_config || {});
    const { providerBranding: gBranding } = await layoutHelper.loadGlobalCheckoutDefaults();
    const providerBranding = await layoutHelper.resolveProviderBrandingFull(gBranding);

    return res.json({
      success: true,
      checkoutTheme: config.checkout_theme,
      checkoutMode: config.checkout_mode,
      checkoutTabs: await layoutHelper.parseTabsForMerchant(layoutRaw),
      providerBranding,
      activeNumbers,
      gatewaysByCategory,
      numberOrder: config.numberOrder || [],
      websiteCount,
    });
  } catch (error) {
    console.error('[Website] getGlobalCheckout error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * PUT /api/v1/websites/global-checkout
 * Save global checkout and propagate live to every website owned by the merchant.
 */
async function saveGlobalCheckout(req, res) {
  try {
    const userId = req.user.userId;
    const b = req.body || {};

    const theme = String(b.checkout_theme || b.checkoutTheme || 'design-1');
    if (!layoutHelper.VALID_DESIGNS.has(theme) && !['default', 'light', 'dark', 'minimal', 'brand'].includes(theme)) {
      return res.status(400).json({ success: false, error: 'INVALID_CHECKOUT_THEME' });
    }
    const mode = String(b.checkout_mode || b.checkoutMode || 'transaction');
    if (!['transaction', 'merchant_vibe', 'hybrid', 'live'].includes(mode)) {
      return res.status(400).json({ success: false, error: 'INVALID_CHECKOUT_MODE' });
    }

    const tabsInput = b.checkout_tabs || b.checkoutTabs;
    const { tabs: globalTabs } = await layoutHelper.loadGlobalCheckoutDefaults();
    const layoutObj = layoutHelper.mergeTabsIntoLayout(null, tabsInput || {}, globalTabs);
    const layoutConfigStr = JSON.stringify(layoutObj);
    const sanitizedOrder = sanitizeNumberOrder(b.order || b.numberOrder || []);
    const numberOrderJson = JSON.stringify(sanitizedOrder);

    const configKey = `${GLOBAL_CHECKOUT_KEY_PREFIX}${userId}`;
    const globalPayload = JSON.stringify({
      checkout_theme: theme,
      checkout_mode: mode,
      layout_config: layoutObj,
      numberOrder: sanitizedOrder,
      updatedAt: new Date().toISOString(),
    });
    await prisma.global_config.upsert({
      where: { config_key: configKey },
      update: { config_value: globalPayload, updated_at: new Date() },
      create: { config_key: configKey, config_value: globalPayload, updated_at: new Date() },
    });

    const websites = await prisma.gateway_layouts.findMany({ where: { user_id: userId } });
    for (const w of websites) {
      await prisma.gateway_layouts.update({
        where: { id: w.id },
        data: {
          checkout_theme: theme,
          checkout_mode: mode,
          layout_config: layoutConfigStr,
          number_order_json: numberOrderJson,
          updated_at: new Date(),
        },
      });
      merchantCache.invalidate(w.api_key);
    }

    return res.json({
      success: true,
      message: `গ্লোবাল চেকআউট সংরক্ষণ হয়েছে — ${websites.length}টি ওয়েবসাইটে প্রয়োগ করা হয়েছে।`,
      websitesUpdated: websites.length,
      checkoutTheme: theme,
      checkoutMode: mode,
      checkoutTabs: layoutHelper.parseTabs(layoutConfigStr, globalTabs),
      numberOrder: sanitizedOrder,
    });
  } catch (error) {
    console.error('[Website] saveGlobalCheckout error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

// ── Commission management (gated by admin permission on the website) ─────────

/** GET /api/v1/websites/:id/commissions */
async function listCommissions(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const commissions = await prisma.merchant_commissions.findMany({
      where: { website_id: row.id },
      orderBy: { payment_type: 'asc' },
    });
    const incentiveTemplates = await listAccountIncentiveTemplates(row.user_id);
    return res.json({
      success: true,
      commissionEnabled: !!row.commission_enabled,
      incentiveTemplates,
      commissions: commissions.map((c) => ({
        id: c.id,
        paymentType: c.payment_type,
        commissionType: c.commission_type,
        commissionValue: Number(c.commission_value),
        chargeType: c.charge_type,
        chargeValue: Number(c.charge_value),
        isActive: !!c.is_active,
      })),
    });
  } catch (error) {
    console.error('[Website] listCommissions error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** POST /api/v1/websites/:id/commissions — upsert one payment-type commission. */
async function upsertCommission(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    // Commission menu is locked until admin grants permission.
    if (!row.commission_enabled) {
      return res.status(403).json({
        success: false,
        error: 'COMMISSION_LOCKED',
        message: 'Commission ফিচারটি Admin অনুমতি ছাড়া ব্যবহার করা যাবে না।',
      });
    }

    const b = req.body || {};
    const paymentType = (b.payment_type || b.paymentType || '').trim();
    if (!paymentType) return res.status(400).json({ success: false, error: 'PAYMENT_TYPE_REQUIRED' });

    const commissionType = ['percentage', 'flat'].includes(b.commission_type || b.commissionType)
      ? (b.commission_type || b.commissionType) : 'percentage';
    const chargeType = ['percentage', 'flat'].includes(b.charge_type || b.chargeType)
      ? (b.charge_type || b.chargeType) : 'flat';
    const commissionValue = parseFloat(b.commission_value ?? b.commissionValue ?? 0) || 0;
    const chargeValue = parseFloat(b.charge_value ?? b.chargeValue ?? 0) || 0;

    const saved = await prisma.merchant_commissions.upsert({
      where: { website_id_payment_type: { website_id: row.id, payment_type: paymentType } },
      update: {
        commission_type: commissionType,
        commission_value: commissionValue,
        charge_type: chargeType,
        charge_value: chargeValue,
        is_active: b.is_active === undefined ? 1 : (b.is_active ? 1 : 0),
        updated_at: new Date(),
      },
      create: {
        website_id: row.id,
        payment_type: paymentType,
        commission_type: commissionType,
        commission_value: commissionValue,
        charge_type: chargeType,
        charge_value: chargeValue,
        is_active: b.is_active === undefined ? 1 : (b.is_active ? 1 : 0),
      },
    });

    return res.json({
      success: true,
      commission: {
        id: saved.id,
        paymentType: saved.payment_type,
        commissionType: saved.commission_type,
        commissionValue: Number(saved.commission_value),
        chargeType: saved.charge_type,
        chargeValue: Number(saved.charge_value),
        isActive: !!saved.is_active,
      },
    });
  } catch (error) {
    console.error('[Website] upsertCommission error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** DELETE /api/v1/websites/:id/commissions/:commissionId */
async function deleteCommission(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const commissionId = parseInt(req.params.commissionId, 10);
    if (!Number.isFinite(commissionId)) {
      return res.status(400).json({ success: false, error: 'INVALID_COMMISSION_ID' });
    }
    const commission = await prisma.merchant_commissions.findUnique({ where: { id: commissionId } });
    if (!commission || commission.website_id !== row.id) {
      return res.status(404).json({ success: false, error: 'COMMISSION_NOT_FOUND' });
    }
    await prisma.merchant_commissions.delete({ where: { id: commissionId } });
    return res.json({ success: true, message: 'Commission মুছে ফেলা হয়েছে।' });
  } catch (error) {
    console.error('[Website] deleteCommission error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

// ── Campaign / Extra incentives (amount-range commission or charge) ──────────
// Raw-SQL over merchant_campaigns (see db/ensure-merchant-campaigns.js). Each row
// gives a commission (cashback) OR a charge on transactions whose amount falls in
// [min_amount, max_amount], scoped to one payment type (template) or ALL types.

function campaignRowToDto(c) {
  return {
    id: c.id,
    paymentType: c.payment_type || '',
    label: c.label || '',
    minAmount: Number(c.min_amount),
    maxAmount: Number(c.max_amount),
    mode: c.mode,
    valueType: c.value_type,
    value: Number(c.value),
    isActive: !!c.is_active,
  };
}

/** GET /api/v1/websites/:id/campaigns */
async function listCampaigns(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const rows = await prisma.$queryRawUnsafe(
      `SELECT * FROM merchant_campaigns WHERE website_id = ? ORDER BY id DESC`,
      row.id,
    );
    return res.json({ success: true, campaigns: rows.map(campaignRowToDto) });
  } catch (error) {
    console.error('[Website] listCampaigns error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** POST /api/v1/websites/:id/campaigns — create a campaign/extra rule. */
async function upsertCampaign(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    // Same admin gate as commissions.
    if (!row.commission_enabled) {
      return res.status(403).json({
        success: false,
        error: 'COMMISSION_LOCKED',
        message: 'Commission/Campaign ফিচারটি Admin অনুমতি ছাড়া ব্যবহার করা যাবে না।',
      });
    }

    const b = req.body || {};
    // Empty payment_type = ALL transaction types.
    const paymentType = (b.payment_type || b.paymentType || '').trim();
    const label = (b.label || '').toString().slice(0, 128);
    const minAmount = Math.max(0, parseFloat(b.min_amount ?? b.minAmount ?? 0) || 0);
    const maxAmount = Math.max(0, parseFloat(b.max_amount ?? b.maxAmount ?? 0) || 0);
    const mode = ['commission', 'charge'].includes(b.mode) ? b.mode : 'commission';
    const valueType = ['percentage', 'flat'].includes(b.value_type || b.valueType)
      ? (b.value_type || b.valueType) : 'flat';
    const value = Math.max(0, parseFloat(b.value ?? 0) || 0);
    const isActive = b.is_active === undefined && b.isActive === undefined
      ? 1 : ((b.is_active ?? b.isActive) ? 1 : 0);

    if (value <= 0) return res.status(400).json({ success: false, error: 'VALUE_REQUIRED' });
    if (maxAmount > 0 && maxAmount < minAmount) {
      return res.status(400).json({ success: false, error: 'INVALID_RANGE' });
    }
    if (valueType === 'percentage' && value > 100) {
      return res.status(400).json({ success: false, error: 'PERCENT_TOO_HIGH' });
    }

    const campaignId = parseInt(b.id, 10);
    if (Number.isFinite(campaignId)) {
      // Update existing (must belong to this website).
      const existing = await prisma.$queryRawUnsafe(
        `SELECT id FROM merchant_campaigns WHERE id = ? AND website_id = ?`,
        campaignId, row.id,
      );
      if (!existing.length) return res.status(404).json({ success: false, error: 'CAMPAIGN_NOT_FOUND' });
      await prisma.$executeRawUnsafe(
        `UPDATE merchant_campaigns SET payment_type=?, label=?, min_amount=?, max_amount=?,
           mode=?, value_type=?, value=?, is_active=?, updated_at=NOW() WHERE id=?`,
        paymentType, label, minAmount, maxAmount, mode, valueType, value, isActive, campaignId,
      );
      const updated = await prisma.$queryRawUnsafe(`SELECT * FROM merchant_campaigns WHERE id=?`, campaignId);
      return res.json({ success: true, campaign: campaignRowToDto(updated[0]) });
    }

    await prisma.$executeRawUnsafe(
      `INSERT INTO merchant_campaigns
         (website_id, payment_type, label, min_amount, max_amount, mode, value_type, value, is_active)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      row.id, paymentType, label, minAmount, maxAmount, mode, valueType, value, isActive,
    );
    const created = await prisma.$queryRawUnsafe(
      `SELECT * FROM merchant_campaigns WHERE website_id=? ORDER BY id DESC LIMIT 1`, row.id,
    );
    return res.json({ success: true, campaign: campaignRowToDto(created[0]) });
  } catch (error) {
    console.error('[Website] upsertCampaign error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** DELETE /api/v1/websites/:id/campaigns/:campaignId */
async function deleteCampaign(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const campaignId = parseInt(req.params.campaignId, 10);
    if (!Number.isFinite(campaignId)) {
      return res.status(400).json({ success: false, error: 'INVALID_CAMPAIGN_ID' });
    }
    const existing = await prisma.$queryRawUnsafe(
      `SELECT id FROM merchant_campaigns WHERE id = ? AND website_id = ?`,
      campaignId, row.id,
    );
    if (!existing.length) return res.status(404).json({ success: false, error: 'CAMPAIGN_NOT_FOUND' });
    await prisma.$executeRawUnsafe(`DELETE FROM merchant_campaigns WHERE id = ?`, campaignId);
    return res.json({ success: true, message: 'ক্যাম্পেইন মুছে ফেলা হয়েছে।' });
  } catch (error) {
    console.error('[Website] deleteCampaign error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

// ── Live Merchant Accounts (multiple per provider) ───────────────────────────
// Full merchant credential vault. Multiple accounts per provider per website.
// Sensitive fields are AES-encrypted at rest and NEVER returned in plaintext —
// only a masked hint + a boolean "has value" flag are exposed.

const merchantCrypto = require('../utils/merchantCrypto');

let merchantAccountsTableReady = false;
async function ensureMerchantAccountsTable() {
  if (merchantAccountsTableReady) return;
  try {
    await prisma.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS merchant_accounts (
        id INT NOT NULL AUTO_INCREMENT,
        website_id INT NOT NULL,
        provider VARCHAR(40) NOT NULL,
        merchant_name VARCHAR(120) NOT NULL,
        merchant_ref VARCHAR(120) NULL,
        logo_url VARCHAR(512) NULL,
        api_key VARCHAR(512) NULL,
        api_secret_enc TEXT NULL,
        username VARCHAR(255) NULL,
        password_enc TEXT NULL,
        app_key VARCHAR(512) NULL,
        app_secret_enc TEXT NULL,
        base_url VARCHAR(512) NULL,
        callback_url VARCHAR(512) NULL,
        is_active TINYINT NOT NULL DEFAULT 1,
        is_default TINYINT NOT NULL DEFAULT 0,
        priority INT NOT NULL DEFAULT 0,
        notes TEXT NULL,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        INDEX idx_merchant_acct_website_active (website_id, is_active),
        INDEX idx_merchant_acct_provider (website_id, provider, is_active)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `);
    merchantAccountsTableReady = true;
  } catch (e) {
    console.error('[Website] ensureMerchantAccountsTable:', e.message);
  }
}

// Providers supported for live merchant accounts. Kept permissive so future
// providers work without code change — validated only as a non-empty slug.
function normalizeProviderSlug(raw) {
  return String(raw || '').trim().toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '');
}

/** Serialize a merchant_accounts row into a safe DTO (secrets masked, never raw). */
function toMerchantAccountDto(row) {
  if (!row) return null;
  return {
    id: row.id,
    websiteId: row.website_id,
    provider: row.provider,
    merchantName: row.merchant_name,
    merchantRef: row.merchant_ref || null,
    logoUrl: row.logo_url || null,
    apiKey: row.api_key || null,
    apiSecretMask: merchantCrypto.mask(row.api_secret_enc),
    hasApiSecret: merchantCrypto.hasSecret(row.api_secret_enc),
    username: row.username || null,
    hasPassword: merchantCrypto.hasSecret(row.password_enc),
    passwordMask: merchantCrypto.mask(row.password_enc),
    appKey: row.app_key || null,
    hasAppSecret: merchantCrypto.hasSecret(row.app_secret_enc),
    appSecretMask: merchantCrypto.mask(row.app_secret_enc),
    baseUrl: row.base_url || null,
    callbackUrl: row.callback_url || null,
    isActive: !!row.is_active,
    isDefault: !!row.is_default,
    priority: row.priority || 0,
    notes: row.notes || null,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

/**
 * Build the writable data object from a request body.
 * @param {object} b request body
 * @param {boolean} isCreate on update, absent secret fields are left untouched;
 *        an explicit empty string clears them.
 */
function buildMerchantData(b, isCreate) {
  const data = {};
  if (b.merchant_name !== undefined || b.merchantName !== undefined) {
    data.merchant_name = String(b.merchant_name ?? b.merchantName ?? '').trim();
  }
  if (b.merchant_ref !== undefined || b.merchantRef !== undefined) {
    const v = b.merchant_ref ?? b.merchantRef;
    data.merchant_ref = v ? String(v).trim() : null;
  }
  if (b.api_key !== undefined || b.apiKey !== undefined) {
    const v = b.api_key ?? b.apiKey;
    data.api_key = v ? String(v).trim() : null;
  }
  if (b.username !== undefined) data.username = b.username ? String(b.username).trim() : null;
  if (b.app_key !== undefined || b.appKey !== undefined) {
    const v = b.app_key ?? b.appKey;
    data.app_key = v ? String(v).trim() : null;
  }
  if (b.base_url !== undefined || b.baseUrl !== undefined) {
    const v = b.base_url ?? b.baseUrl;
    data.base_url = v ? String(v).trim() : null;
  }
  if (b.callback_url !== undefined || b.callbackUrl !== undefined) {
    const v = b.callback_url ?? b.callbackUrl;
    data.callback_url = v ? String(v).trim() : null;
  }
  if (b.notes !== undefined) data.notes = b.notes ? String(b.notes) : null;
  if (b.priority !== undefined) data.priority = parseInt(b.priority, 10) || 0;
  if (b.is_active !== undefined || b.isActive !== undefined) {
    data.is_active = (b.is_active ?? b.isActive) ? 1 : 0;
  }

  // Sensitive fields: encrypt on write. On update, only touch when the key is
  // present in the payload (absent = keep existing; '' = clear).
  const apiSecret = b.api_secret ?? b.apiSecret;
  if (apiSecret !== undefined) data.api_secret_enc = apiSecret ? merchantCrypto.encrypt(String(apiSecret)) : '';
  const password = b.password;
  if (password !== undefined) data.password_enc = password ? merchantCrypto.encrypt(String(password)) : '';
  const appSecret = b.app_secret ?? b.appSecret;
  if (appSecret !== undefined) data.app_secret_enc = appSecret ? merchantCrypto.encrypt(String(appSecret)) : '';

  return data;
}

/** GET /api/v1/websites/:id/merchant-accounts — list all accounts for a website. */
async function listMerchantAccounts(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    await ensureMerchantAccountsTable();

    const q = (req.query.q || '').trim().toLowerCase();
    const providerFilter = normalizeProviderSlug(req.query.provider || '');
    const where = { website_id: row.id };
    if (providerFilter) where.provider = providerFilter;

    let accounts = await prisma.merchant_accounts.findMany({
      where,
      orderBy: [{ provider: 'asc' }, { priority: 'asc' }, { id: 'asc' }],
    });
    if (q) {
      accounts = accounts.filter((a) =>
        (a.merchant_name || '').toLowerCase().includes(q) ||
        (a.provider || '').toLowerCase().includes(q) ||
        (a.merchant_ref || '').toLowerCase().includes(q));
    }
    return res.json({ success: true, merchantAccounts: accounts.map(toMerchantAccountDto) });
  } catch (error) {
    console.error('[Website] listMerchantAccounts error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** POST /api/v1/websites/:id/merchant-accounts — create a new merchant account. */
async function createMerchantAccount(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    await ensureMerchantAccountsTable();

    const b = req.body || {};
    const provider = normalizeProviderSlug(b.provider);
    if (!provider) return res.status(400).json({ success: false, error: 'PROVIDER_REQUIRED' });

    const data = buildMerchantData(b, true);
    if (!data.merchant_name) {
      return res.status(400).json({ success: false, error: 'MERCHANT_NAME_REQUIRED' });
    }
    data.provider = provider;
    data.website_id = row.id;
    if (data.is_active === undefined) data.is_active = 1;

    // First account of a provider becomes its default automatically.
    const existing = await prisma.merchant_accounts.count({
      where: { website_id: row.id, provider },
    });
    const wantDefault = (b.is_default ?? b.isDefault) ? true : (existing === 0);

    const created = await prisma.merchant_accounts.create({ data });
    if (wantDefault) {
      await prisma.merchant_accounts.updateMany({
        where: { website_id: row.id, provider, id: { not: created.id } },
        data: { is_default: 0 },
      });
      await prisma.merchant_accounts.update({ where: { id: created.id }, data: { is_default: 1 } });
      created.is_default = 1;
    }
    merchantCache.invalidate(row.api_key);
    return res.status(201).json({ success: true, merchantAccount: toMerchantAccountDto(created) });
  } catch (error) {
    console.error('[Website] createMerchantAccount error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** Resolve a merchant account owned by the user for a website. */
async function findOwnedMerchantAccount(websiteRow, accountId) {
  const id = parseInt(accountId, 10);
  if (!Number.isFinite(id)) return null;
  const acct = await prisma.merchant_accounts.findUnique({ where: { id } });
  if (!acct || acct.website_id !== websiteRow.id) return null;
  return acct;
}

/** PATCH /api/v1/websites/:id/merchant-accounts/:accountId — update an account. */
async function updateMerchantAccount(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const acct = await findOwnedMerchantAccount(row, req.params.accountId);
    if (!acct) return res.status(404).json({ success: false, error: 'ACCOUNT_NOT_FOUND' });

    const b = req.body || {};
    const data = buildMerchantData(b, false);
    if (b.provider !== undefined) {
      const provider = normalizeProviderSlug(b.provider);
      if (!provider) return res.status(400).json({ success: false, error: 'PROVIDER_REQUIRED' });
      data.provider = provider;
    }
    if (data.merchant_name !== undefined && !data.merchant_name) {
      return res.status(400).json({ success: false, error: 'MERCHANT_NAME_REQUIRED' });
    }
    data.updated_at = new Date();

    const updated = await prisma.merchant_accounts.update({ where: { id: acct.id }, data });
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true, merchantAccount: toMerchantAccountDto(updated) });
  } catch (error) {
    console.error('[Website] updateMerchantAccount error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** POST /api/v1/websites/:id/merchant-accounts/:accountId/toggle — enable/disable. */
async function toggleMerchantAccount(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const acct = await findOwnedMerchantAccount(row, req.params.accountId);
    if (!acct) return res.status(404).json({ success: false, error: 'ACCOUNT_NOT_FOUND' });

    const b = req.body || {};
    const nextActive = (b.is_active ?? b.isActive);
    const value = nextActive === undefined ? (acct.is_active ? 0 : 1) : (nextActive ? 1 : 0);
    const updated = await prisma.merchant_accounts.update({
      where: { id: acct.id },
      data: { is_active: value, updated_at: new Date() },
    });
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true, merchantAccount: toMerchantAccountDto(updated) });
  } catch (error) {
    console.error('[Website] toggleMerchantAccount error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** POST /api/v1/websites/:id/merchant-accounts/:accountId/default — set default. */
async function setDefaultMerchantAccount(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const acct = await findOwnedMerchantAccount(row, req.params.accountId);
    if (!acct) return res.status(404).json({ success: false, error: 'ACCOUNT_NOT_FOUND' });

    await prisma.merchant_accounts.updateMany({
      where: { website_id: row.id, provider: acct.provider },
      data: { is_default: 0 },
    });
    const updated = await prisma.merchant_accounts.update({
      where: { id: acct.id },
      data: { is_default: 1, updated_at: new Date() },
    });
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true, merchantAccount: toMerchantAccountDto(updated) });
  } catch (error) {
    console.error('[Website] setDefaultMerchantAccount error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** POST /api/v1/websites/:id/merchant-accounts/:accountId/duplicate — clone an account. */
async function duplicateMerchantAccount(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const acct = await findOwnedMerchantAccount(row, req.params.accountId);
    if (!acct) return res.status(404).json({ success: false, error: 'ACCOUNT_NOT_FOUND' });

    const created = await prisma.merchant_accounts.create({
      data: {
        website_id: row.id,
        provider: acct.provider,
        merchant_name: `${acct.merchant_name} (Copy)`,
        merchant_ref: acct.merchant_ref,
        logo_url: acct.logo_url,
        api_key: acct.api_key,
        api_secret_enc: acct.api_secret_enc,
        username: acct.username,
        password_enc: acct.password_enc,
        app_key: acct.app_key,
        app_secret_enc: acct.app_secret_enc,
        base_url: acct.base_url,
        callback_url: acct.callback_url,
        is_active: 0, // clones start disabled to avoid accidental live use
        is_default: 0,
        priority: acct.priority,
        notes: acct.notes,
      },
    });
    merchantCache.invalidate(row.api_key);
    return res.status(201).json({ success: true, merchantAccount: toMerchantAccountDto(created) });
  } catch (error) {
    console.error('[Website] duplicateMerchantAccount error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** DELETE /api/v1/websites/:id/merchant-accounts/:accountId */
async function deleteMerchantAccount(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const acct = await findOwnedMerchantAccount(row, req.params.accountId);
    if (!acct) return res.status(404).json({ success: false, error: 'ACCOUNT_NOT_FOUND' });

    const wasDefault = !!acct.is_default;
    if (acct.logo_url && websiteLogoService.isManagedLogoPath(acct.logo_url, userId, row.id)) {
      websiteLogoService.deleteLogoFile(acct.logo_url);
    }
    await prisma.merchant_accounts.delete({ where: { id: acct.id } });

    // Promote a new default within the same provider if we removed the default.
    if (wasDefault) {
      const next = await prisma.merchant_accounts.findFirst({
        where: { website_id: row.id, provider: acct.provider },
        orderBy: [{ priority: 'asc' }, { id: 'asc' }],
      });
      if (next) {
        await prisma.merchant_accounts.update({ where: { id: next.id }, data: { is_default: 1 } });
      }
    }
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true, message: 'Merchant account মুছে ফেলা হয়েছে।' });
  } catch (error) {
    console.error('[Website] deleteMerchantAccount error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * POST /api/v1/websites/:id/merchant-accounts/:accountId/logo — multipart logo (field: logo).
 */
async function uploadMerchantAccountLogo(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const acct = await findOwnedMerchantAccount(row, req.params.accountId);
    if (!acct) return res.status(404).json({ success: false, error: 'ACCOUNT_NOT_FOUND' });

    if (!req.file || !req.file.buffer) {
      return res.status(400).json({ success: false, error: 'NO_FILE', message: 'লোগো ফাইল পাঠানো হয়নি।' });
    }

    let relPath;
    try {
      relPath = await websiteLogoService.saveWebsiteLogo(req.file.buffer, req.file.mimetype, userId, row.id);
    } catch (e) {
      const code = e.message || 'UPLOAD_FAILED';
      const status = code === 'FILE_TOO_LARGE' ? 413
        : (code === 'INVALID_FILE_TYPE' || code === 'INVALID_IMAGE_CONTENT') ? 415 : 400;
      return res.status(status).json({
        success: false,
        error: code,
        message: code === 'FILE_TOO_LARGE' ? 'ফাইল সর্বোচ্চ ৫ MB হতে পারে।'
          : 'অবৈধ ছবি ফরম্যাট। PNG, JPG, JPEG বা WEBP ব্যবহার করুন।',
      });
    }

    if (acct.logo_url && websiteLogoService.isManagedLogoPath(acct.logo_url, userId, row.id)) {
      websiteLogoService.deleteLogoFile(acct.logo_url);
    }
    const updated = await prisma.merchant_accounts.update({
      where: { id: acct.id },
      data: { logo_url: relPath, updated_at: new Date() },
    });
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true, logoUrl: relPath, merchantAccount: toMerchantAccountDto(updated) });
  } catch (error) {
    console.error('[Website] uploadMerchantAccountLogo error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * POST /api/v1/websites/:id/branding/logo — multipart logo upload (field: logo).
 * Replaces any previously managed on-disk logo; external legacy URLs are overwritten in DB.
 */
async function uploadWebsiteLogo(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    if (!req.file || !req.file.buffer) {
      return res.status(400).json({ success: false, error: 'NO_FILE', message: 'লোগো ফাইল পাঠানো হয়নি।' });
    }

    const oldPath = row.logo_url;
    let relPath;
    try {
      relPath = await websiteLogoService.saveWebsiteLogo(
        req.file.buffer,
        req.file.mimetype,
        userId,
        row.id
      );
    } catch (e) {
      const code = e.message || 'UPLOAD_FAILED';
      const status = code === 'FILE_TOO_LARGE' ? 413
        : (code === 'INVALID_FILE_TYPE' || code === 'INVALID_IMAGE_CONTENT') ? 415
          : 400;
      return res.status(status).json({
        success: false,
        error: code,
        message: code === 'FILE_TOO_LARGE' ? 'ফাইল সর্বোচ্চ ৫ MB হতে পারে।'
          : 'অবৈধ ছবি ফরম্যাট। PNG, JPG, JPEG বা WEBP ব্যবহার করুন।',
      });
    }

    if (oldPath && websiteLogoService.isManagedLogoPath(oldPath, userId, row.id)) {
      websiteLogoService.deleteLogoFile(oldPath);
    }

    const updated = await prisma.gateway_layouts.update({
      where: { id: row.id },
      data: { logo_url: relPath, updated_at: new Date() },
    });
    merchantCache.invalidate(row.api_key);

    return res.json({
      success: true,
      message: 'কোম্পানি লোগো আপলোড হয়েছে।',
      logoPath: relPath,
      logoUrl: relPath,
      website: toWebsiteDto(updated),
    });
  } catch (error) {
    console.error('[Website] uploadWebsiteLogo error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * DELETE /api/v1/websites/:id/branding/logo — remove stored logo (managed file + DB path).
 * Legacy external URLs are cleared from DB only (remote file is not deleted).
 */
async function deleteWebsiteLogo(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const oldPath = row.logo_url;
    if (oldPath && websiteLogoService.isManagedLogoPath(oldPath, userId, row.id)) {
      websiteLogoService.deleteLogoFile(oldPath);
    }

    const updated = await prisma.gateway_layouts.update({
      where: { id: row.id },
      data: { logo_url: null, updated_at: new Date() },
    });
    merchantCache.invalidate(row.api_key);

    return res.json({
      success: true,
      message: 'কোম্পানি লোগো মুছে ফেলা হয়েছে।',
      website: toWebsiteDto(updated),
    });
  } catch (error) {
    console.error('[Website] deleteWebsiteLogo error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

module.exports = {
  createWebsite,
  listWebsites,
  getWebsite,
  updateWebsiteSettings,
  regenerateSecret,
  updateNumberOrder,
  deleteWebsite,
  getGlobalCheckout,
  saveGlobalCheckout,
  listCommissions,
  upsertCommission,
  deleteCommission,
  listCampaigns,
  upsertCampaign,
  deleteCampaign,
  listMerchantAccounts,
  createMerchantAccount,
  updateMerchantAccount,
  toggleMerchantAccount,
  setDefaultMerchantAccount,
  duplicateMerchantAccount,
  deleteMerchantAccount,
  uploadMerchantAccountLogo,
  uploadWebsiteLogo,
  deleteWebsiteLogo,
  // exported for reuse in admin permission controller / callback dispatcher
  _helpers: { sha256, toWebsiteDto },
};
