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
            api_secret: '', // v2 does not store plaintext
            api_secret_hash: sha256(plaintextSecret),
            api_secret_last4: plaintextSecret.slice(-4),
            api_secret_version: 1,
            merchant_id: merchantId,
            company_name: websiteName || null,
            checkout_theme: 'default',
            checkout_mode: 'transaction',
            layout_config: JSON.stringify({}),
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

    return res.json({
      success: true,
      website: toWebsiteDto(row),
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
        api_secret: '',
        api_secret_hash: sha256(plaintextSecret),
        api_secret_last4: plaintextSecret.slice(-4),
        api_secret_version: (row.api_secret_version || 1) + 1,
        updated_at: new Date(),
      },
    });

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
  // exported for reuse in admin permission controller / callback dispatcher
  _helpers: { sha256, toWebsiteDto },
};
