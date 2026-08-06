const v3 = require('../services/subscriptionV3');
const { isV3Enabled, getBillingSettings, setConfig } = require('../services/subscriptionV3/configService');
const { getEntitlements, ensureSubscriptionFresh } = require('../services/permissionEngineService');
const { createSubscriptionCheckout } = require('../services/subscriptionCheckoutService');
const { CATEGORY_TAB_ORDER, DURATION_KEYS, CATEGORY_ADDONS } = require('../services/subscriptionV3/constants');
const { discountPercent, websiteDisplayLabel } = require('../services/subscriptionV3/catalogService');

async function getCatalog(req, res) {
  try {
    if (!(await isV3Enabled())) {
      return res.status(404).json({ success: false, error: 'V3_DISABLED' });
    }
    const userId = req.user.userId;
    await ensureSubscriptionFresh(userId);
    const settings = await getBillingSettings();
    const packages = await v3.listActiveCatalog();
    const addons = await v3.listAddonCatalog();
    const subs = await v3.getUserSubscriptions(userId);
    const sharedExpiry = await v3.getSharedExpiry(userId);
    const refundStatus = await v3.getUserRefundStatus(userId);
    const history = await v3.getPurchaseHistory(userId);
    const extensionHistory = await v3.listExtensionHistory(userId);

    const byCategory = {};
    for (const cat of CATEGORY_TAB_ORDER) {
      byCategory[cat] = packages
        .filter((p) => p.category === cat)
        .map((p) => ({
          ...p,
          website_display: p.category === 'gateway' ? websiteDisplayLabel(p.website_limit_internal) : null,
          device_display: 'Unlimited Devices',
          discounts: {
            '6m': discountPercent(p, '6m'),
            '12m': discountPercent(p, '12m'),
          },
          allowed_addons: CATEGORY_ADDONS[cat] || [],
        }));
    }

    return res.json({
      success: true,
      v3: true,
      settings,
      tab_order: CATEGORY_TAB_ORDER,
      duration_segments: [
        { key: 'monthly', duration_key: DURATION_KEYS.monthly, label: 'Monthly' },
        { key: 'annually', duration_key: DURATION_KEYS.annually, label: 'Annually' },
        { key: 'yearly', duration_key: DURATION_KEYS.yearly, label: 'Yearly' },
      ],
      categories: byCategory,
      addons,
      active_subscriptions: subs,
      shared_expiry: sharedExpiry ? v3.formatYmd(sharedExpiry) : null,
      refund_status: refundStatus,
      purchase_history: history,
      extension_history: extensionHistory,
    });
  } catch (err) {
    console.error('[V3] getCatalog', err);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function postQuote(req, res) {
  try {
    if (!(await isV3Enabled())) {
      return res.status(404).json({ success: false, error: 'V3_DISABLED' });
    }
    const userId = req.user.userId;
    const { category, sku_key: skuKey, duration_key: durationKey, addons } = req.body || {};
    if (!category || !skuKey || !durationKey) {
      return res.status(400).json({ success: false, message: 'category, sku_key, duration_key প্রয়োজন।' });
    }
    const quote = await v3.computeQuote(userId, {
      category,
      skuKey,
      durationKey,
      addons: Array.isArray(addons) ? addons : [],
    });
    if (quote.error) {
      return res.status(400).json({ success: false, error: quote.error, message: quote.message });
    }
    const frozen = await v3.freezeQuote(userId, quote);
    return res.json({ success: true, quote, quote_token: frozen.quote_token, quote_expires_at: frozen.expires_at });
  } catch (err) {
    console.error('[V3] postQuote', err);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function postCheckoutInit(req, res) {
  try {
    if (!(await isV3Enabled())) {
      return res.status(404).json({ success: false, error: 'V3_DISABLED' });
    }
    const userId = req.user.userId;
    const { quote_token: quoteToken } = req.body || {};
    if (!quoteToken) {
      return res.status(400).json({ success: false, message: 'quote_token প্রয়োজন।' });
    }
    const loaded = await v3.loadFrozenQuote(quoteToken, userId);
    if (loaded.error) {
      return res.status(400).json({ success: false, error: loaded.error, message: loaded.message });
    }
    const quote = loaded.quote;
    const payable = loaded.payable_amount;

    if (payable <= 0) {
      const result = await v3.fulfillSubscription({
        userId,
        quote,
        transactionId: `free_${quoteToken}`,
        quoteToken,
        ipAddress: req.ip,
      });
      return res.json({
        success: true,
        activated: true,
        invoice_no: result.invoice_no,
        message: 'প্যাকেজ সক্রিয় হয়েছে।',
      });
    }

    const result = await createSubscriptionCheckout(req, {
      userId,
      planName: quote.package_full_name,
      quoteToken,
      v3Quote: quote,
      payableOverride: payable,
    });
    if (result.error) {
      return res.status(result.status || 400).json({ success: false, error: result.error, message: result.message });
    }
    return res.json({ success: true, ...result });
  } catch (err) {
    console.error('[V3] postCheckoutInit', err);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function getHistory(req, res) {
  try {
    const userId = req.user.userId;
    const history = await v3.getPurchaseHistory(userId);
    return res.json({ success: true, history });
  } catch (err) {
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function postRefundRequest(req, res) {
  try {
    const userId = req.user.userId;
    const { purchase_id: purchaseId, reason } = req.body || {};
    const result = await v3.createRefundRequest(userId, purchaseId, reason, req.ip);
    if (!result.ok) {
      return res.status(400).json({ success: false, message: result.message, status: result.status });
    }
    return res.json({ success: true, status: result.status });
  } catch (err) {
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function adminListPendingRefunds(req, res) {
  try {
    const rows = await v3.listPendingRefunds();
    return res.json({ success: true, refunds: rows });
  } catch (err) {
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function adminResolveRefund(req, res) {
  try {
    const adminId = req.user.userId;
    const { id } = req.params;
    const { approve, admin_note: adminNote } = req.body || {};
    const result = await v3.resolveRefund(id, adminId, !!approve, adminNote, req.ip);
    if (result.error) return res.status(404).json({ success: false, error: result.error });
    return res.json({ success: true, status: result.status });
  } catch (err) {
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function adminArchivePackage(req, res) {
  try {
    const { id } = req.params;
    await v3.archivePackage(id, req.user.userId);
    await v3.logAudit({
      adminId: req.user.userId,
      action: 'package_archive',
      oldPackage: String(id),
      ipAddress: req.ip,
    });
    return res.json({ success: true, message: 'প্যাকেজ আর্কাইভ করা হয়েছে।' });
  } catch (err) {
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function adminReorderPackages(req, res) {
  try {
    const items = Array.isArray(req.body?.items) ? req.body.items : [];
    await v3.reorderPackages(items);
    return res.json({ success: true });
  } catch (err) {
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function adminUpdateSettings(req, res) {
  try {
    const body = req.body || {};
    const keys = ['trial_days', 'subscription_v3_enabled'];
    for (const k of keys) {
      if (body[k] !== undefined) await setConfig(k, body[k]);
    }
    return res.json({ success: true, settings: await getBillingSettings() });
  } catch (err) {
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function adminGetSettings(req, res) {
  try {
    return res.json({ success: true, settings: await getBillingSettings() });
  } catch (err) {
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function adminListAddonCatalog(req, res) {
  try {
    const { listAllAddonCatalogAdmin } = require('../services/subscriptionV3/addonCatalogAdminService');
    const addons = await listAllAddonCatalogAdmin();
    return res.json({ success: true, addons });
  } catch (err) {
    console.error('[V3] adminListAddonCatalog', err);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function adminUpdateAddonCatalog(req, res) {
  try {
    const { updateAddonCatalog } = require('../services/subscriptionV3/addonCatalogAdminService');
    const { addonKey } = req.params;
    const result = await updateAddonCatalog(addonKey, req.body || {});
    if (result.error === 'NOT_FOUND') {
      return res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'অ্যাড-অন পাওয়া যায়নি।' });
    }
    if (result.error === 'DISPLAY_NAME_REQUIRED') {
      return res.status(400).json({ success: false, error: result.error, message: 'ডিসপ্লে নাম প্রয়োজন।' });
    }
    if (result.error) {
      return res.status(400).json({ success: false, error: result.error });
    }
    return res.json({ success: true, addon: result.addon });
  } catch (err) {
    console.error('[V3] adminUpdateAddonCatalog', err);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

module.exports = {
  getCatalog,
  postQuote,
  postCheckoutInit,
  getHistory,
  postRefundRequest,
  adminListPendingRefunds,
  adminResolveRefund,
  adminArchivePackage,
  adminReorderPackages,
  adminUpdateSettings,
  adminGetSettings,
  adminListAddonCatalog,
  adminUpdateAddonCatalog,
};
