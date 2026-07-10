const prisma = require('../db/prisma');
const { syncUserEntitlements, ensureEntitlementSchema } = require('../services/accountEntitlementsService');
const {
  computeSubscriptionQuote,
  applySubscriptionPurchase,
  formatDateYmd,
} = require('../services/subscriptionBillingService');

let addonPlansTableReady = false;

async function ensureAddonPlansTable() {
  if (addonPlansTableReady) return;
  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS addon_plans (
      id INT NOT NULL AUTO_INCREMENT,
      plan_name VARCHAR(100) NOT NULL,
      price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
      duration_days INT NOT NULL DEFAULT 30,
      description TEXT NULL,
      is_active TINYINT NOT NULL DEFAULT 1,
      features_json TEXT NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_addon_plan_name (plan_name)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  const existing = await prisma.$queryRaw`SELECT COUNT(*) AS cnt FROM addon_plans`;
  const count = Number(existing[0]?.cnt || 0);
  if (count === 0) {
    const legacy = await prisma.subscription_plans.findMany({
      where: { plan_name: { contains: 'custom sender' } },
      orderBy: { price: 'asc' },
    });
    if (legacy.length > 0) {
      for (const row of legacy) {
        await prisma.$executeRaw`
          INSERT IGNORE INTO addon_plans (plan_name, price, duration_days, description, is_active)
          VALUES (${row.plan_name}, ${row.price}, ${row.duration_days || 365}, ${'কাস্টম সেন্ডার আইডি অ্যাড-অন'}, 1)
        `;
      }
    } else {
      await prisma.$executeRaw`
        INSERT IGNORE INTO addon_plans (plan_name, price, duration_days, description, is_active)
        VALUES ('Custom Sender ID', 250.00, 365, 'কাস্টম সেন্ডার আইডি অ্যাড-অন', 1)
      `;
    }
  }
  await ensurePlanFeaturesColumns();
  addonPlansTableReady = true;
}

async function ensurePlanFeaturesColumns() {
  await ensureEntitlementSchema();
  const subCols = await prisma.$queryRaw`SHOW COLUMNS FROM subscription_plans LIKE 'features_json'`;
  if (!subCols.length) {
    await prisma.$executeRawUnsafe('ALTER TABLE subscription_plans ADD COLUMN features_json TEXT NULL');
  }
  const addonCols = await prisma.$queryRaw`SHOW COLUMNS FROM addon_plans LIKE 'features_json'`;
  if (!addonCols.length) {
    await prisma.$executeRawUnsafe('ALTER TABLE addon_plans ADD COLUMN features_json TEXT NULL');
  }
}

function parseFeaturesJson(raw, fallbackFn) {
  if (!raw) return fallbackFn ? fallbackFn() : [];
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return fallbackFn ? fallbackFn() : [];
    return parsed
      .filter((f) => f && f.text)
      .map((f) => ({
        text: String(f.text),
        icon: f.icon === 'cross' ? 'cross' : 'check',
      }));
  } catch (e) {
    return fallbackFn ? fallbackFn() : [];
  }
}

function serializeFeatures(features) {
  if (!Array.isArray(features)) return null;
  const cleaned = features
    .filter((f) => f && String(f.text || '').trim())
    .map((f) => ({
      text: String(f.text).trim(),
      icon: f.icon === 'cross' ? 'cross' : 'check',
    }));
  return cleaned.length ? JSON.stringify(cleaned) : JSON.stringify([]);
}

function defaultSubscriptionFeatures(plan) {
  return [
    { text: `সর্বোচ্চ ${plan.max_sites} টি ওয়েবসাইট সংযুক্ত করুন`, icon: 'check' },
    { text: `সর্বোচ্চ ${plan.max_devices} টি চাইল্ড ডিভাইস যুক্ত করুন`, icon: 'check' },
    { text: '২৪/৭ লাইভ এডমিন ও হোয়াটসঅ্যাপ সাপোর্ট', icon: 'check' },
  ];
}

function defaultAddonFeatures(plan) {
  const lines = [
    { text: 'কাস্টম সেন্ডার আইডি যোগ করার পারমিশন', icon: 'check' },
    { text: `মেয়াদ: ${plan.duration_days || 30} দিন`, icon: 'check' },
  ];
  if (plan.description) {
    lines.push({ text: String(plan.description), icon: 'check' });
  }
  return lines;
}

function mapSubscriptionPlanRow(row) {
  const plan = {
    id: Number(row.id),
    plan_name: row.plan_name,
    price: Number(row.price),
    max_sites: Number(row.max_sites),
    max_devices: Number(row.max_devices),
    is_custom_sender_allowed: Number(row.is_custom_sender_allowed || 0),
    duration_days: Number(row.duration_days || 365),
    plan_category: row.plan_category || 'personal',
    perm_template: Number(row.perm_template ?? 1),
    perm_website: Number(row.perm_website ?? 1),
    perm_device: Number(row.perm_device ?? 1),
  };
  return {
    ...plan,
    features: parseFeaturesJson(row.features_json, () => defaultSubscriptionFeatures(plan)),
  };
}

function mapAddonPlanRow(row) {
  const plan = {
    id: Number(row.id),
    plan_name: row.plan_name,
    price: Number(row.price),
    duration_days: Number(row.duration_days || 30),
    description: row.description || null,
    is_active: Number(row.is_active ?? 1),
    max_devices: Number(row.max_devices ?? 2),
    perm_custom_sender: Number(row.perm_custom_sender ?? 1),
    perm_template: Number(row.perm_template ?? 0),
    perm_website: Number(row.perm_website ?? 0),
    perm_device: Number(row.perm_device ?? 1),
  };
  return {
    ...plan,
    features: parseFeaturesJson(row.features_json, () => defaultAddonFeatures(plan)),
  };
}

function isLegacyCustomSenderPlanName(name) {
  return typeof name === 'string' && name.toLowerCase().includes('custom sender');
}

async function findAddonPlanIdByName(planName) {
  const normalized = String(planName || '').trim();
  if (!normalized) return null;
  const rows = await prisma.$queryRaw`
    SELECT id FROM addon_plans
    WHERE LOWER(TRIM(plan_name)) = LOWER(${normalized})
    LIMIT 1
  `;
  return rows[0] ? Number(rows[0].id) : null;
}

function isDuplicateKeyError(error) {
  return error?.code === 'P2010' && (error?.meta?.code === '1062' || String(error?.meta?.message || '').includes('Duplicate entry'));
}

async function stackCustomSenderExpiry(userId, durationDays) {
  const user = await prisma.users.findUnique({
    where: { id: userId },
    select: { custom_sender_ends_at: true },
  });
  let baseDate = new Date();
  if (user?.custom_sender_ends_at) {
    const existingExpiry = new Date(user.custom_sender_ends_at);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (existingExpiry > today) {
      baseDate = existingExpiry;
    }
  }
  baseDate.setDate(baseDate.getDate() + durationDays);
  return new Date(baseDate);
}

async function getSubscriptionQuote(req, res) {
  try {
    const userId = req.user.userId;
    const planName = req.query.planName || req.query.plan_name;
    if (!planName) {
      return res.status(400).json({ success: false, error: 'Missing planName query parameter.' });
    }

    const plan = await prisma.subscription_plans.findFirst({ where: { plan_name: planName } });
    if (!plan) {
      return res.status(404).json({ success: false, error: 'PLAN_NOT_FOUND', message: 'প্ল্যানটি খুঁজে পাওয়া যায়নি।' });
    }
    if (isLegacyCustomSenderPlanName(plan.plan_name)) {
      return res.status(400).json({
        success: false,
        error: 'USE_ADDON_ENDPOINT',
        message: 'কাস্টম সেন্ডার প্যাকেজ কিনতে অ্যাড-অন ট্যাব ব্যবহার করুন।',
      });
    }

    const quote = await computeSubscriptionQuote(userId, planName);
    if (quote.error) {
      return res.status(404).json({ success: false, error: quote.error, message: quote.message });
    }

    return res.json({ success: true, quote });
  } catch (error) {
    console.error('[Billing Controller] getSubscriptionQuote error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * POST /api/v1/subscription/purchase
 * Renew: same plan → stack expiry, full price
 * Upgrade: different active plan → prorated credit, new expiry from today
 */
async function purchaseSubscription(req, res) {
  try {
    const userId = req.user.userId;
    const planName = req.body.planName || req.body.plan_name;

    if (!planName) {
      return res.status(400).json({ success: false, error: 'Missing planName field.' });
    }

    const plan = await prisma.subscription_plans.findFirst({
      where: { plan_name: planName },
    });

    if (!plan) {
      return res.status(404).json({ success: false, error: 'PLAN_NOT_FOUND', message: 'প্ল্যানটি খুঁজে পাওয়া যায়নি।' });
    }

    if (isLegacyCustomSenderPlanName(plan.plan_name)) {
      return res.status(400).json({
        success: false,
        error: 'USE_ADDON_ENDPOINT',
        message: 'কাস্টম সেন্ডার প্যাকেজ কিনতে অ্যাড-অন ট্যাব ব্যবহার করুন।',
      });
    }

    const quote = await applySubscriptionPurchase(userId, planName);
    if (quote.error) {
      return res.status(404).json({ success: false, error: quote.error, message: quote.message });
    }

    const formattedExpiry = quote.new_expiry_date;
    console.log(
      `[Subscription] ✅ User ${userId} ${quote.purchase_type} "${plan.plan_name}". Payable: ৳${quote.payable_amount} (credit ৳${quote.credit_applied}). Expiry: ${formattedExpiry}`
    );

    await syncUserEntitlements(userId);

    return res.json({
      success: true,
      message: `${plan.plan_name} প্যাকেজটি সফলভাবে সক্রিয় করা হয়েছে। মেয়াদ: ${formattedExpiry}`,
      is_paid: 1,
      active_plan_name: plan.plan_name,
      expiry_date: formattedExpiry,
      purchase_type: quote.purchase_type,
      list_price: quote.list_price,
      credit_applied: quote.credit_applied,
      payable_amount: quote.payable_amount,
    });
  } catch (error) {
    console.error('[Billing Controller] purchaseSubscription error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * POST /api/v1/subscription/fcm-token
 * Updates the user's FCM push token in the database.
 */
async function updateFcmToken(req, res) {
  try {
    const userId = req.user.userId;
    const { token } = req.body;

    await prisma.users.update({
      where: { id: userId },
      data: { fcm_token: token || null }
    });

    console.log(`[FCM-Token] Registered token for user ${userId}: ${token ? token.substring(0, 15) + '...' : 'CLEARED'}`);

    return res.json({
      success: true,
      message: 'FCM token updated successfully'
    });
  } catch (error) {
    console.error('[Billing Controller] updateFcmToken error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function listPlans(req, res) {
  try {
    await ensurePlanFeaturesColumns();
    const rows = await prisma.$queryRaw`
      SELECT id, plan_name, price, max_sites, max_devices, is_custom_sender_allowed, duration_days, features_json,
             plan_category, perm_template, perm_website, perm_device
      FROM subscription_plans
      ORDER BY price ASC
    `;
    const mainPlans = rows
      .filter((p) => !isLegacyCustomSenderPlanName(p.plan_name))
      .map(mapSubscriptionPlanRow);
    return res.json({
      success: true,
      plans: mainPlans,
    });
  } catch (error) {
    console.error('[Billing Controller] listPlans error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * GET /api/v1/addon-plans
 * Lists active custom-sender add-on packages.
 */
async function listAddonPlans(req, res) {
  try {
    await ensureAddonPlansTable();
    const rows = await prisma.$queryRaw`
      SELECT id, plan_name, price, duration_days, description, is_active, features_json,
             max_devices, perm_custom_sender, perm_template, perm_website, perm_device
      FROM addon_plans
      WHERE is_active = 1
      ORDER BY price ASC
    `;
    return res.json({ success: true, plans: rows.map(mapAddonPlanRow) });
  } catch (error) {
    console.error('[Billing Controller] listAddonPlans error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * GET /api/admin/addon-plans
 */
async function listAddonPlansAdmin(req, res) {
  try {
    await ensureAddonPlansTable();
    const rows = await prisma.$queryRaw`
      SELECT id, plan_name, price, duration_days, description, is_active, features_json, created_at,
             max_devices, perm_custom_sender, perm_template, perm_website, perm_device
      FROM addon_plans
      ORDER BY price ASC
    `;
    return res.json({ success: true, plans: rows.map(mapAddonPlanRow) });
  } catch (error) {
    console.error('[Billing Controller] listAddonPlansAdmin error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * POST /api/admin/addon-plans
 */
async function saveAddonPlan(req, res) {
  try {
    await ensureAddonPlansTable();
    const {
      id,
      plan_name,
      price,
      duration_days,
      description,
      is_active,
      features,
      max_devices,
      perm_custom_sender,
      perm_template,
      perm_website,
      perm_device,
    } = req.body;

    const normalizedName = String(plan_name || '').trim();
    if (!normalizedName || price === undefined || duration_days === undefined) {
      return res.status(400).json({ success: false, error: 'Missing required addon plan fields.' });
    }

    const activeVal = is_active === false || is_active === 0 ? 0 : 1;
    const featuresJson = serializeFeatures(features);
    const planId = id ? parseInt(id, 10) : null;
    const existingId = await findAddonPlanIdByName(normalizedName);

    const maxDev = parseInt(max_devices, 10) || 2;
    const permCustom = perm_custom_sender === false || perm_custom_sender === 0 ? 0 : 1;
    const permTpl = perm_template === true || perm_template === 1 ? 1 : 0;
    const permWeb = perm_website === true || perm_website === 1 ? 1 : 0;
    const permDev = perm_device === false || perm_device === 0 ? 0 : 1;

    if (planId) {
      if (existingId && existingId !== planId) {
        return res.status(400).json({
          success: false,
          error: 'PLAN_NAME_EXISTS',
          message: 'এই নামের একটি অ্যাড-অন প্যাকেজ ইতিমধ্যেই রয়েছে। তালিকা থেকে বিদ্যমান প্যাকেজটি এডিট করুন।',
        });
      }
      await prisma.$executeRaw`
        UPDATE addon_plans
        SET plan_name = ${normalizedName},
            price = ${Number(price)},
            duration_days = ${parseInt(duration_days, 10) || 30},
            description = ${description || null},
            is_active = ${activeVal},
            features_json = ${featuresJson},
            max_devices = ${maxDev},
            perm_custom_sender = ${permCustom},
            perm_template = ${permTpl},
            perm_website = ${permWeb},
            perm_device = ${permDev}
        WHERE id = ${planId}
      `;
    } else {
      if (existingId) {
        return res.status(400).json({
          success: false,
          error: 'PLAN_NAME_EXISTS',
          message: 'এই নামের একটি অ্যাড-অন প্যাকেজ ইতিমধ্যেই রয়েছে। + দিয়ে নতুন তৈরি না করে তালিকা থেকে এডিট করুন।',
        });
      }
      await prisma.$executeRaw`
        INSERT INTO addon_plans (plan_name, price, duration_days, description, is_active, features_json,
          max_devices, perm_custom_sender, perm_template, perm_website, perm_device)
        VALUES (${normalizedName}, ${Number(price)}, ${parseInt(duration_days, 10) || 30}, ${description || null}, ${activeVal}, ${featuresJson},
          ${maxDev}, ${permCustom}, ${permTpl}, ${permWeb}, ${permDev})
      `;
    }

    return res.json({ success: true, message: 'অ্যাড-অন প্যাকেজ সফলভাবে সংরক্ষণ করা হয়েছে।' });
  } catch (error) {
    console.error('[Billing Controller] saveAddonPlan error:', error);
    if (isDuplicateKeyError(error)) {
      return res.status(400).json({
        success: false,
        error: 'PLAN_NAME_EXISTS',
        message: 'এই নামের একটি অ্যাড-অন প্যাকেজ ইতিমধ্যেই রয়েছে। তালিকা থেকে বিদ্যমান প্যাকেজটি এডিট করুন।',
      });
    }
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * DELETE /api/admin/addon-plans/:id
 */
async function deleteAddonPlan(req, res) {
  try {
    await ensureAddonPlansTable();
    const planId = parseInt(req.params.id, 10);
    if (!planId) {
      return res.status(400).json({ success: false, error: 'Missing plan ID.' });
    }
    await prisma.$executeRaw`DELETE FROM addon_plans WHERE id = ${planId}`;
    return res.json({ success: true, message: 'অ্যাড-অন প্যাকেজ সফলভাবে ডিলিট করা হয়েছে।' });
  } catch (error) {
    console.error('[Billing Controller] deleteAddonPlan error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * POST /api/admin/plans/create
 * Creates or updates a subscription plan (Admin Only).
 */
async function createPlan(req, res) {
  try {
    await ensurePlanFeaturesColumns();
    const {
      id, plan_name, price, max_sites, max_devices, duration_days, is_custom_sender_allowed, features,
      plan_category, perm_template, perm_website, perm_device,
    } = req.body;

    if (!plan_name || price === undefined || max_sites === undefined || max_devices === undefined) {
      return res.status(400).json({ success: false, error: 'Missing required plan fields.' });
    }

    const data = {
      plan_name,
      price,
      max_sites,
      max_devices,
      is_custom_sender_allowed: is_custom_sender_allowed ? 1 : 0,
      duration_days: duration_days || 365,
    };
    const featuresJson = serializeFeatures(features);
    const category = ['personal', 'personal_business', 'payment_gateway'].includes(plan_category)
      ? plan_category
      : 'personal';
    const permTpl = perm_template === false || perm_template === 0 ? 0 : 1;
    const permWeb = perm_website === false || perm_website === 0 ? 0 : 1;
    const permDev = perm_device === false || perm_device === 0 ? 0 : 1;

    let planId = id ? parseInt(id, 10) : null;

    if (planId) {
      await prisma.subscription_plans.update({
        where: { id: planId },
        data,
      });
    } else {
      const existingName = await prisma.subscription_plans.findUnique({
        where: { plan_name },
      });
      if (existingName) {
        return res.status(400).json({ success: false, error: 'PLAN_NAME_EXISTS', message: 'এই নামের একটি প্যাকেজ ইতিমধ্যেই রয়েছে।' });
      }
      const created = await prisma.subscription_plans.create({ data });
      planId = created.id;
    }

    await prisma.$executeRaw`
      UPDATE subscription_plans
      SET features_json = ${featuresJson},
          plan_category = ${category},
          perm_template = ${permTpl},
          perm_website = ${permWeb},
          perm_device = ${permDev}
      WHERE id = ${planId}
    `;

    return res.json({ success: true, message: 'প্ল্যান সফলভাবে তৈরি/আপডেট করা হয়েছে।' });
  } catch (error) {
    console.error('[Billing Controller] createPlan error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * DELETE /api/admin/plans/:id
 * Deletes a subscription plan (Admin Only).
 */
async function deletePlan(req, res) {
  try {
    const { id } = req.params;
    if (!id) {
      return res.status(400).json({ success: false, error: 'Missing plan ID.' });
    }
    await prisma.subscription_plans.delete({
      where: { id: parseInt(id) }
    });
    return res.json({ success: true, message: 'প্ল্যান সফলভাবে ডিলিট করা হয়েছে।' });
  } catch (error) {
    console.error('[Billing Controller] deletePlan error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function purchaseCustomSenderAddon(req, res) {
  try {
    const userId = req.user.userId;
    const planId = parseInt(req.body.planId || req.body.plan_id || req.body.addon_plan_id, 10);

    await ensureAddonPlansTable();

    if (!planId) {
      return res.status(400).json({
        success: false,
        error: 'MISSING_PLAN_ID',
        message: 'অ্যাড-অন প্যাকেজ নির্বাচন করুন।',
      });
    }

    const rows = await prisma.$queryRaw`
      SELECT id, plan_name, price, duration_days, is_active
      FROM addon_plans
      WHERE id = ${planId}
      LIMIT 1
    `;
    const plan = rows[0];
    if (!plan || Number(plan.is_active) !== 1) {
      return res.status(404).json({
        success: false,
        error: 'PLAN_NOT_FOUND',
        message: 'অ্যাড-অন প্যাকেজটি খুঁজে পাওয়া যায়নি।',
      });
    }

    const durationDays = Number(plan.duration_days) || 30;
    const newCustomSenderExpiry = await stackCustomSenderExpiry(userId, durationDays);
    const formattedExpiry = formatDateYmd(newCustomSenderExpiry);

    await prisma.users.update({
      where: { id: userId },
      data: {
        has_custom_sender_addon: 1,
        custom_sender_ends_at: newCustomSenderExpiry,
        active_addon_plan_id: planId,
      },
    });

    await syncUserEntitlements(userId);

    console.log(`[Subscription] ✅ User ${userId} purchased addon "${plan.plan_name}" (id=${planId}). Expiry: ${formattedExpiry}`);

    return res.json({
      success: true,
      message: `${plan.plan_name} সফলভাবে সক্রিয় করা হয়েছে। মেয়াদ: ${formattedExpiry}`,
      has_custom_sender_addon: 1,
      custom_sender_ends_at: formattedExpiry,
    });
  } catch (error) {
    console.error('[Billing Controller] purchaseCustomSenderAddon error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function getAccountEntitlements(req, res) {
  try {
    const userId = req.user.userId;
    const ent = await syncUserEntitlements(userId);
    return res.json({ success: true, entitlements: ent });
  } catch (error) {
    console.error('[Billing Controller] getAccountEntitlements error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

module.exports = {
  updateFcmToken,
  getSubscriptionQuote,
  purchaseSubscription,
  listPlans,
  listAddonPlans,
  listAddonPlansAdmin,
  saveAddonPlan,
  deleteAddonPlan,
  createPlan,
  deletePlan,
  purchaseCustomSenderAddon,
  getAccountEntitlements,
};
