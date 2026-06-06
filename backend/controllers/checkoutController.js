const { query } = require('../db/connection');

/**
 * GET /api/checkout/:apiKey
 * Retrieves active merchant gateway layout options.
 */
async function getCheckoutLayout(req, res) {
  try {
    const { apiKey } = req.params;
    if (!apiKey) {
      return res.status(400).json({ error: 'Merchant API Key is required' });
    }

    const layouts = await query(
      'SELECT id, user_id, site_name, site_url, layout_config, redirect_url, callback_url FROM gateway_layouts WHERE api_key = ? AND is_active = 1 LIMIT 1',
      [apiKey]
    );

    if (layouts.length === 0) {
      return res.status(404).json({ error: 'সক্রিয় মার্চেন্ট গেটওয়ে লেআউট পাওয়া যায়নি।' });
    }

    const layout = layouts[0];
    
    // Fallback default block structure if layout_config is empty
    let layoutConfig = layout.layout_config;
    if (!layoutConfig) {
      layoutConfig = [
        { id: 'bkash', name: 'bKash', providerTag: 'bKash', isEnabled: true },
        { id: 'nagad', name: 'Nagad', providerTag: 'Nagad', isEnabled: true },
        { id: 'rocket', name: 'Rocket', providerTag: 'Rocket', isEnabled: true }
      ];
    } else if (typeof layoutConfig === 'string') {
      layoutConfig = JSON.parse(layoutConfig);
    }

    // Retrieve merchant SIM slot details to get phone numbers
    // We get them from the registered_devices profile for this user_id where is_parent = 1
    const devices = await query(
      'SELECT sim1_number, sim1_operator, sim2_number, sim2_operator, sim_settings FROM registered_devices WHERE user_id = ? AND is_parent = 1 LIMIT 1',
      [layout.user_id]
    );

    const simConfig = devices.length > 0 ? devices[0] : null;

    return res.json({
      siteName: layout.site_name,
      siteUrl: layout.site_url,
      layoutConfig,
      simConfig: simConfig ? {
        sim1Number: simConfig.sim1_number,
        sim1Operator: simConfig.sim1_operator,
        sim2Number: simConfig.sim2_number,
        sim2Operator: simConfig.sim2_operator
      } : null
    });

  } catch (error) {
    console.error('Error fetching checkout layout:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

/**
 * POST /api/checkout/verify
 * Public customer verification endpoint for Transaction IDs.
 * Locks the transaction to prevent dual usage.
 */
async function verifyCheckoutPayment(req, res) {
  try {
    const { apiKey, trxId, amount } = req.body;

    if (!apiKey || !trxId || !amount) {
      return res.status(400).json({ error: 'Missing required parameters: apiKey, trxId, amount' });
    }

    const cleanTrx = trxId.trim().toUpperCase();
    const cleanAmount = parseFloat(amount);

    // 1. Fetch merchant details
    const layouts = await query(
      'SELECT id, user_id, redirect_url, callback_url FROM gateway_layouts WHERE api_key = ? AND is_active = 1 LIMIT 1',
      [apiKey]
    );

    if (layouts.length === 0) {
      return res.status(404).json({ error: 'Invalid API Key or Merchant inactive' });
    }

    const merchant = layouts[0];

    // 2. Query sms_history for this user/merchant matching trxId and amount
    const payments = await query(
      'SELECT id, is_used, used_by_merchant_id FROM sms_history WHERE user_id = ? AND trx_id = ? AND amount = ? LIMIT 1',
      [merchant.user_id, cleanTrx, cleanAmount]
    );

    if (payments.length === 0) {
      return res.json({
        success: false,
        message: 'পেমেন্ট এখনও আমাদের সিস্টেমে যুক্ত হয়নি। ১-২ মিনিট অপেক্ষা করে আবার ভেরিফাই করুন।'
      });
    }

    const payment = payments[0];

    // 3. Prevent transaction hijack/double-spend
    if (payment.is_used && payment.used_by_merchant_id !== merchant.id) {
      return res.json({
        success: false,
        message: 'দুঃখিত, এই ট্রানজেকশন আইডিটি অন্য মার্চেন্ট অর্ডারে ইতিমধ্যে ব্যবহার করা হয়েছে।'
      });
    }

    // 4. Mark transaction as used/sold out for this merchant
    if (!payment.is_used) {
      await query(
        'UPDATE sms_history SET is_used = 1, used_at = NOW(), used_by_merchant_id = ? WHERE id = ?',
        [merchant.id, payment.id]
      );
      console.log(`[VERIFICATION] Trx ${cleanTrx} (৳${cleanAmount}) marked SOLDOUT for merchant ID ${merchant.id}`);
      
      // OPTIONAL: Trigger background Webhook callback
      if (merchant.callback_url) {
        triggerWebhook(merchant.callback_url, {
          trxId: cleanTrx,
          amount: cleanAmount,
          merchantId: merchant.id,
          status: 'verified',
          timestamp: new Date()
        });
      }
    } else {
      console.log(`[VERIFICATION] Trx ${cleanTrx} was already locked for this merchant. Skipping update.`);
    }

    // 5. Calculate success redirect parameters
    const redirectUrl = merchant.redirect_url
      ? `${merchant.redirect_url}${merchant.redirect_url.includes('?') ? '&' : '?'}trxId=${cleanTrx}&amount=${cleanAmount}&status=success`
      : null;

    return res.json({
      success: true,
      message: 'পেমেন্ট সফলভাবে যাচাই করা হয়েছে!',
      redirectUrl
    });

  } catch (error) {
    console.error('Error verifying payment:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// Background utility to trigger webhooks
function triggerWebhook(url, payload) {
  // Safe mock fetch trigger, prevents crashing main threads on network errors
  import('http').then((http) => {
    // Standard Node.js fetch callback or axios
    console.log(`[WEBHOOK] Posting verified payment telemetry to ${url}`);
  }).catch(e => {
    console.error('Webhook imports failed:', e);
  });
}

module.exports = {
  getCheckoutLayout,
  verifyCheckoutPayment
};
