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
const prisma = require('../db/prisma');
const merchantCache = require('../services/merchantCache');
const layoutHelper = require('../services/checkoutLayoutHelper');

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
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

/** Resolve the max number of sites permitted for a user based on plan. */
async function resolveMaxSites(user) {
  if (user.active_plan_name === 'Trial Package') {
    const configVal = await prisma.global_config.findUnique({
      where: { config_key: 'trial_max_sites' },
    });
    return configVal ? parseInt(configVal.config_value, 10) : 1;
  }
  const plan = await prisma.subscription_plans.findUnique({
    where: { plan_name: user.active_plan_name || '' },
  });
  return plan ? plan.max_sites : 1;
}

/** Fetch a website row owned by the given user, or null. */
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
 * Website Add Wizard — only Domain (required) and Website Name (optional).
 * Returns the generated Merchant ID, API Key and (once only) the API Secret.
 */
async function createWebsite(req, res) {
  try {
    const userId = req.user.userId;
    const domainRaw = req.body.domain || req.body.site_url || '';
    const websiteName = (req.body.website_name || req.body.site_name || '').trim();

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
    if (!isAdmin && (!user.is_paid || user.active_plan_name === 'FREE_LEVEL')) {
      return res.status(402).json({
        success: false,
        error: 'SUBSCRIPTION_REQUIRED',
        message: 'ওয়েবসাইট যুক্ত করতে একটি সাবস্ক্রিপশন প্যাকেজ কিনুন।',
      });
    }

    if (!isAdmin) {
      const maxSites = await resolveMaxSites(user);
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
    let created = null;
    let plaintextSecret = null;
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
            checkout_theme: 'design-1',
            checkout_mode: 'transaction',
            layout_config: JSON.stringify({ tabs: layoutHelper.DEFAULT_TABS }),
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
      message: 'ওয়েবসাইট সফলভাবে তৈরি হয়েছে।',
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

    // Auto-synced active SIM numbers for this account (across all devices).
    // Source of truth = gateway_methods (same data the SMS reader uses). We only
    // READ it here; checkout enable/disable is stored separately in numberOrder.
    const methods = await prisma.gateway_methods.findMany({
      where: { user_id: String(row.user_id), is_enabled: 1, NOT: { number: '' } },
      orderBy: [{ priority: 'asc' }, { sim_slot: 'asc' }],
      select: { id: true, provider: true, number: true, sim_slot: true, device_id: true, display_name: true },
    });
    const orderLookup = new Map();
    numberOrder.forEach((o, idx) => {
      const key = o.methodId != null ? `id:${o.methodId}` : `num:${o.provider}|${o.number}`;
      orderLookup.set(key, { position: o.position != null ? o.position : idx, enabled: o.enabled !== false });
    });
    const activeNumbers = methods
      .filter((m) => m.number && m.number.trim() !== '')
      .map((m) => {
        const ov = orderLookup.get(`id:${m.id}`) || orderLookup.get(`num:${m.provider}|${m.number}`);
        return {
          methodId: m.id,
          provider: m.provider,
          number: m.number,
          simSlot: m.sim_slot,
          deviceId: m.device_id,
          displayName: m.display_name || m.provider,
          enabled: ov ? ov.enabled : true,
          position: ov ? ov.position : Number.MAX_SAFE_INTEGER,
        };
      })
      .sort((a, b) => a.position - b.position);

    return res.json({
      success: true,
      website: toWebsiteDto(row),
      activeNumbers,
      checkoutTabs: layoutHelper.parseTabs(row.layout_config),
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
      logo_url: 'logo_url',
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
      data.layout_config = JSON.stringify(layoutHelper.mergeTabsIntoLayout(existing, b.checkout_tabs));
    }

    if (b.checkout_mode !== undefined) {
      const mode = String(b.checkout_mode);
      if (!['transaction', 'merchant_vibe'].includes(mode)) {
        return res.status(400).json({ success: false, error: 'INVALID_CHECKOUT_MODE' });
      }
      data.checkout_mode = mode;
    }

    // Merchant preferences. Effective inclusion is still gated by admin
    // permission at callback dispatch time.
    if (b.receive_payment_type !== undefined) data.receive_payment_type = b.receive_payment_type ? 1 : 0;
    if (b.receive_commission !== undefined) data.receive_commission = b.receive_commission ? 1 : 0;

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

/** DELETE /api/v1/websites/:id */
async function deleteWebsite(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    await prisma.gateway_layouts.delete({ where: { id: row.id } });
    return res.json({ success: true, message: 'ওয়েবসাইট মুছে ফেলা হয়েছে।' });
  } catch (error) {
    console.error('[Website] deleteWebsite error:', error);
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
    return res.json({
      success: true,
      commissionEnabled: !!row.commission_enabled,
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

// ── Official payment gateway configuration (Phase 6) ─────────────────────────
// Per-website redirect config for official channels (bKash/Nagad/Rocket Merchant,
// SSLCommerz, Card, Bank). PayCheck never processes these payments itself — it
// only redirects the customer to the original gateway page and receives a callback.

const OFFICIAL_PROVIDER_SET = new Set([
  'bkash_merchant', 'nagad_merchant', 'rocket_merchant', 'sslcommerz', 'card', 'bank',
]);

function toOfficialGatewayDto(row) {
  return {
    id: row.id,
    provider: row.provider,
    displayName: row.display_name || row.provider,
    redirectUrlTemplate: row.redirect_url_template,
    isActive: !!row.is_active,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

/** GET /api/v1/websites/:id/official-gateways */
async function listOfficialGateways(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const gateways = await prisma.website_official_gateways.findMany({
      where: { website_id: row.id },
      orderBy: { provider: 'asc' },
    });
    return res.json({ success: true, officialGateways: gateways.map(toOfficialGatewayDto) });
  } catch (error) {
    console.error('[Website] listOfficialGateways error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** POST /api/v1/websites/:id/official-gateways — upsert one provider config. */
async function upsertOfficialGateway(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const b = req.body || {};
    const provider = (b.provider || '').trim().toLowerCase();
    if (!OFFICIAL_PROVIDER_SET.has(provider)) {
      return res.status(400).json({ success: false, error: 'INVALID_PROVIDER' });
    }
    const redirectUrlTemplate = (b.redirect_url_template || b.redirectUrlTemplate || '').trim();
    if (!redirectUrlTemplate || !/^https?:\/\//i.test(redirectUrlTemplate)) {
      return res.status(400).json({ success: false, error: 'INVALID_REDIRECT_URL' });
    }

    const saved = await prisma.website_official_gateways.upsert({
      where: { website_id_provider: { website_id: row.id, provider } },
      update: {
        display_name: b.display_name || b.displayName || null,
        redirect_url_template: redirectUrlTemplate,
        is_active: b.is_active === undefined ? 1 : (b.is_active ? 1 : 0),
        config_json: b.config ? JSON.stringify(b.config) : null,
        updated_at: new Date(),
      },
      create: {
        website_id: row.id,
        provider,
        display_name: b.display_name || b.displayName || null,
        redirect_url_template: redirectUrlTemplate,
        is_active: b.is_active === undefined ? 1 : (b.is_active ? 1 : 0),
        config_json: b.config ? JSON.stringify(b.config) : null,
      },
    });

    return res.json({ success: true, officialGateway: toOfficialGatewayDto(saved) });
  } catch (error) {
    console.error('[Website] upsertOfficialGateway error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** DELETE /api/v1/websites/:id/official-gateways/:gatewayId */
async function deleteOfficialGateway(req, res) {
  try {
    const userId = req.user.userId;
    const row = await findOwnedWebsite(req.params.id, userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });

    const gatewayId = parseInt(req.params.gatewayId, 10);
    if (!Number.isFinite(gatewayId)) {
      return res.status(400).json({ success: false, error: 'INVALID_GATEWAY_ID' });
    }
    const gw = await prisma.website_official_gateways.findUnique({ where: { id: gatewayId } });
    if (!gw || gw.website_id !== row.id) {
      return res.status(404).json({ success: false, error: 'GATEWAY_NOT_FOUND' });
    }
    await prisma.website_official_gateways.delete({ where: { id: gatewayId } });
    return res.json({ success: true, message: 'Official gateway মুছে ফেলা হয়েছে।' });
  } catch (error) {
    console.error('[Website] deleteOfficialGateway error:', error);
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
  listCommissions,
  upsertCommission,
  deleteCommission,
  listOfficialGateways,
  upsertOfficialGateway,
  deleteOfficialGateway,
  // exported for reuse in admin permission controller / callback dispatcher
  _helpers: { sha256, toWebsiteDto },
};
