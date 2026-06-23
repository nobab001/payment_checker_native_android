const { query } = require('../db/connection');
const axios = require('axios');

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

    // Retrieve active gateways linked to sms_templates and checkout_view_templates (Security Lock)
    const activeGateways = await query(
      `SELECT gm.id, gm.sim_slot, gm.provider, gm.number, gm.display_name,
              cvt.single_number_instruction, cvt.multiple_number_instruction
         FROM gateway_methods gm
         JOIN sms_templates t ON gm.template_id = t.id
         JOIN checkout_view_templates cvt ON cvt.sms_template_id = t.id
        WHERE gm.user_id = ? AND gm.is_enabled = 1 AND gm.number IS NOT NULL AND gm.number != ''
     ORDER BY gm.priority ASC, gm.sim_slot ASC`,
      [layout.user_id]
    );

    // Count active numbers per provider to decide single/multiple instructions
    const providerCounts = {};
    activeGateways.forEach(g => {
      const p = g.provider;
      providerCounts[p] = (providerCounts[p] || 0) + 1;
    });

    // Format active gateways with dynamic instruction texts
    const formattedGateways = activeGateways.map(g => {
      const count = providerCounts[g.provider] || 1;
      const instruction = count > 1 ? g.multiple_number_instruction : g.single_number_instruction;
      return {
        id: g.id,
        simSlot: g.sim_slot,
        provider: g.provider,
        number: g.number,
        displayName: g.display_name || g.provider,
        instruction: instruction
      };
    });

    return res.json({
      siteName: layout.site_name,
      siteUrl: layout.site_url,
      layoutConfig,
      activeGateways: formattedGateways,
      redirectUrl: layout.redirect_url
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

/**
 * POST /api/transactions/claim-check
 * B2B claim check endpoint for merchant systems.
 * Verifies apiKey, searches for READY transaction matching trxId (and optional amount),
 * marks it as SOLD_OUT and posts the Webhook callback.
 */
async function claimCheckTransaction(req, res) {
  try {
    const { apiKey, trxId, amount } = req.body;

    if (!apiKey || !trxId) {
      return res.status(400).json({ success: false, error: 'Missing required parameters: apiKey, trxId' });
    }

    // 1. Fetch merchant details
    const layouts = await query(
      'SELECT id, user_id, callback_url FROM gateway_layouts WHERE api_key = ? AND is_active = 1 LIMIT 1',
      [apiKey]
    );

    if (layouts.length === 0) {
      return res.status(403).json({ success: false, error: 'Invalid API Key or Merchant inactive' });
    }

    const merchant = layouts[0];
    const cleanTrx = trxId.trim().toUpperCase();

    // 2. Query sms_history for this user/merchant matching trxId where is_used = 0 (READY)
    let payments;
    if (amount !== undefined && amount !== null && amount !== '') {
      const cleanAmount = parseFloat(amount);
      payments = await query(
        'SELECT id, amount, trx_id, provider_tag, sender_number, sms_timestamp FROM sms_history WHERE user_id = ? AND trx_id = ? AND amount = ? AND is_used = 0 LIMIT 1',
        [merchant.user_id, cleanTrx, cleanAmount]
      );
    } else {
      payments = await query(
        'SELECT id, amount, trx_id, provider_tag, sender_number, sms_timestamp FROM sms_history WHERE user_id = ? AND trx_id = ? AND is_used = 0 LIMIT 1',
        [merchant.user_id, cleanTrx]
      );
    }

    if (payments.length === 0) {
      return res.status(404).json({
        success: false,
        error: 'TRANSACTION_NOT_FOUND',
        message: 'No matching READY transaction found for the provided TrxID.'
      });
    }

    const payment = payments[0];

    // 3. Mark transaction as used/sold out for this merchant
    await query(
      'UPDATE sms_history SET is_used = 1, used_at = NOW(), used_by_merchant_id = ? WHERE id = ?',
      [merchant.id, payment.id]
    );
    console.log(`[B2B CLAIM] Trx ${payment.trx_id} (৳${payment.amount}) marked SOLDOUT for merchant ID ${merchant.id}`);

    // 4. Trigger Webhook callback to merchant's callback_url
    const webhookPayload = {
      trxId: payment.trx_id,
      amount: payment.amount,
      provider: payment.provider_tag,
      sender: payment.sender_number,
      smsTimestamp: payment.sms_timestamp,
      merchantId: merchant.id,
      status: 'verified',
      timestamp: new Date()
    };

    if (merchant.callback_url) {
      axios.post(merchant.callback_url, webhookPayload)
        .then(response => {
          console.log(`[B2B WEBHOOK] Webhook sent successfully to ${merchant.callback_url}, response status: ${response.status}`);
        })
        .catch(err => {
          console.error(`[B2B WEBHOOK] Webhook failed for ${merchant.callback_url}:`, err.message);
        });
    }

    // 5. Return success telemetry response
    return res.json({
      success: true,
      message: 'Transaction claimed and locked successfully.',
      data: {
        trxId: payment.trx_id,
        amount: payment.amount,
        provider: payment.provider_tag,
        sender: payment.sender_number,
        smsTimestamp: payment.sms_timestamp,
        claimedAt: new Date()
      }
    });

  } catch (error) {
    console.error('Error claiming transaction:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

// Background utility to trigger webhooks using axios
function triggerWebhook(url, payload) {
  console.log(`[WEBHOOK] Posting verified payment telemetry to ${url}`);
  axios.post(url, payload)
    .then(response => {
      console.log(`[WEBHOOK] Callback success: status ${response.status}`);
    })
    .catch(err => {
      console.error(`[WEBHOOK] Callback failed: ${err.message}`);
    });
}

module.exports = {
  getCheckoutLayout,
  verifyCheckoutPayment,
  claimCheckTransaction
};
