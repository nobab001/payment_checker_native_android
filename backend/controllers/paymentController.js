const { query } = require('../db/connection');
const crypto = require('crypto');

// =============================================================================
// Helper
// =============================================================================

function sha256(data) {
  return crypto.createHash('sha256').update(data).digest('hex');
}

// =============================================================================
// POST /api/payment-sms-ingest
// Android থেকে পার্স করা SMS ডেটা গ্রহণ করে ডাটাবেজে সংরক্ষণ করে।
//
// ডুপ্লিকেট সুরক্ষা (আপনার নির্দেশ অনুযায়ী):
//  ১. INSERT IGNORE ব্যবহার — একই dedupe_key দ্বিতীয়বার insert হবে না
//  ২. affectedRows === 0 মানে duplicate — পুরনো রেকর্ড স্পর্শ করা হয় না
//  ৩. is_used (SOLDOUT) স্ট্যাটাস কোনোভাবেই রিসেট হয় না — INSERT IGNORE
//     বিদ্যমান রো আপডেট করে না, শুধু নতুন insert বাদ দেয়
// =============================================================================
async function paymentSmsIngest(req, res) {
  try {
    const {
      amount,
      trxId,
      providerTag,
      senderNumber,
      receiverNumber,
      smsTimestamp, // Epoch milliseconds
      rawBody,
      fullSms,
      simSlot,
      simNumber
    } = req.body;

    const userId   = req.user.userId;
    const deviceId = req.user.deviceId || 'unknown_device';

    const originalBody = fullSms || rawBody;

    if (!amount || !trxId || !providerTag || !smsTimestamp || !originalBody) {
      return res.status(400).json({ error: 'প্রয়োজনীয় ফিল্ড অনুপস্থিত' });
    }

    const dateObj          = new Date(Number(smsTimestamp));
    const formattedTimestamp = dateObj.toISOString().slice(0, 19).replace('T', ' ');
    const formattedDate    = dateObj.toISOString().slice(0, 10);

    // Deduplication Key গঠন
    const bodyHash = sha256(originalBody);
    const dedupeKey = `${smsTimestamp}|${senderNumber || ''}|${bodyHash}`;

    // ────────────────────────────────────────────────────────────────────────
    // DUPLICATE SAFETY — INSERT IGNORE
    // এই কমান্ড কখনো বিদ্যমান রেকর্ড আপডেট করে না।
    // is_used = 1 (SOLDOUT) থাকলে সেটি অপরিবর্তিত থাকবে।
    // ────────────────────────────────────────────────────────────────────────
    const result = await query(
      `INSERT IGNORE INTO sms_history (
        user_id, device_id, sim_slot, sim_number, provider_tag, amount,
        trx_id, sender_number, receiver_number, sms_timestamp, sms_date,
        full_sms, dedupe_key, is_synced, is_used
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0)`,
      [
        userId,
        deviceId,
        simSlot  || null,
        simNumber || null,
        providerTag,
        amount,
        trxId.toUpperCase(),
        senderNumber   || null,
        receiverNumber || null,
        formattedTimestamp,
        formattedDate,
        originalBody,
        dedupeKey
      ]
    );

    const isDuplicate = result.affectedRows === 0;

    if (isDuplicate) {
      console.log(`[INGEST] Duplicate blocked — TrxID: ${trxId} | User: ${userId}`);
      return res.json({
        success: true,
        isDuplicate: true,
        message: 'ডুপ্লিকেট ট্রানজেকশন — পূর্বের স্ট্যাটাস অপরিবর্তিত রাখা হয়েছে।'
      });
    }

    console.log(`[INGEST] ✅ Saved — TrxID: ${trxId} | ৳${amount} | SIM: ${simSlot} | User: ${userId}`);
    return res.json({
      success: true,
      isDuplicate: false,
      message: 'ট্রানজেকশন সফলভাবে সংরক্ষিত হয়েছে।'
    });

  } catch (error) {
    console.error('[INGEST] Error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// GET /api/sms-history
// পেজিনেটেড ট্রানজেকশন লিস্ট রিটার্ন করে।
// Query params: page (default 1), limit (default 20), provider (default 'all')
// =============================================================================
async function getSmsHistory(req, res) {
  try {
    const userId   = req.user.userId;
    const page     = Math.max(1, parseInt(req.query.page)  || 1);
    const limit    = Math.min(50, parseInt(req.query.limit) || 20); // সর্বোচ্চ ৫০
    const provider = req.query.provider || 'all';
    const offset   = (page - 1) * limit;

    // ── Provider ফিল্টার সংযুক্ত করা ──────────────────────────────────────
    const allowedProviders = ['bKash', 'Nagad', 'Rocket', 'Upay'];
    const useFilter = allowedProviders.includes(provider);

    // ── মোট সংখ্যা (pagination metadata) ──────────────────────────────────
    const countQuery = useFilter
      ? `SELECT COUNT(*) AS total FROM sms_history WHERE user_id = ? AND provider_tag = ?`
      : `SELECT COUNT(*) AS total FROM sms_history WHERE user_id = ?`;
    const countParams = useFilter ? [userId, provider] : [userId];
    const countResult = await query(countQuery, countParams);
    const totalCount  = countResult[0]?.total || 0;

    // ── মূল ডেটা Query ─────────────────────────────────────────────────────
    const dataQuery = useFilter
      ? `SELECT id, provider_tag, amount, trx_id, sender_number, sim_slot,
                sms_timestamp, is_used, created_at
           FROM sms_history
          WHERE user_id = ? AND provider_tag = ?
          ORDER BY sms_timestamp DESC
          LIMIT ? OFFSET ?`
      : `SELECT id, provider_tag, amount, trx_id, sender_number, sim_slot,
                sms_timestamp, is_used, created_at
           FROM sms_history
          WHERE user_id = ?
          ORDER BY sms_timestamp DESC
          LIMIT ? OFFSET ?`;
    const dataParams = useFilter
      ? [userId, provider, limit, offset]
      : [userId, limit, offset];

    const rows = await query(dataQuery, dataParams);

    console.log(`[HISTORY] User ${userId} | Page ${page} | Provider: ${provider} | Found: ${rows.length}`);

    return res.json({
      success:     true,
      data:        rows,
      page:        page,
      limit:       limit,
      total_count: totalCount,
      has_more:    (offset + rows.length) < totalCount
    });

  } catch (error) {
    console.error('[HISTORY] Error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// GET /api/dashboard/stats
// Dashboard-এর জন্য aggregate statistics রিটার্ন করে।
// আজকের ডেটা BD সময় (UTC+6) হিসেবে গণনা করা হয়।
// =============================================================================
async function getDashboardStats(req, res) {
  try {
    const userId = req.user.userId;

    // আজকের তারিখ BD Timezone (UTC+6) অনুযায়ী
    const now = new Date();
    const bdOffset = 6 * 60; // ৬ ঘণ্টা
    const bdNow = new Date(now.getTime() + bdOffset * 60 * 1000);
    const todayDate = bdNow.toISOString().slice(0, 10); // 'YYYY-MM-DD'

    // ── সব-সময়ের Aggregate Stats ───────────────────────────────────────────
    const [totalRow] = await query(
      `SELECT
         COALESCE(SUM(amount), 0)  AS total_earnings,
         COUNT(*)                   AS total_transactions,
         SUM(is_used = 0)           AS unused_count,
         SUM(is_used = 1)           AS soldout_count
       FROM sms_history
       WHERE user_id = ?`,
      [userId]
    );

    // ── আজকের Stats ────────────────────────────────────────────────────────
    const [todayRow] = await query(
      `SELECT
         COALESCE(SUM(amount), 0) AS today_earnings,
         COUNT(*)                  AS today_transactions
       FROM sms_history
       WHERE user_id = ? AND sms_date = ?`,
      [userId, todayDate]
    );

    // ── সক্রিয় ডিভাইস সংখ্যা ──────────────────────────────────────────────
    const [deviceRow] = await query(
      `SELECT COUNT(*) AS active_devices
         FROM registered_devices
        WHERE user_id = ? AND status = 'active'`,
      [userId]
    );

    // ── সর্বশেষ ৫টি ট্রানজেকশন ─────────────────────────────────────────────
    const recentRows = await query(
      `SELECT id, provider_tag, amount, trx_id, sender_number,
              sim_slot, sms_timestamp, is_used, created_at
         FROM sms_history
        WHERE user_id = ?
        ORDER BY sms_timestamp DESC
        LIMIT 5`,
      [userId]
    );

    console.log(`[STATS] Dashboard loaded for user: ${userId} | Today: ${todayDate}`);

    return res.json({
      success: true,
      data: {
        total_earnings:      parseFloat(totalRow.total_earnings)  || 0,
        today_earnings:      parseFloat(todayRow.today_earnings)  || 0,
        total_transactions:  totalRow.total_transactions  || 0,
        today_transactions:  todayRow.today_transactions  || 0,
        unused_count:        totalRow.unused_count        || 0,
        soldout_count:       totalRow.soldout_count       || 0,
        active_devices:      deviceRow.active_devices     || 0,
        recent_transactions: recentRows
      }
    });

  } catch (error) {
    console.error('[STATS] Error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// Exports
// =============================================================================
module.exports = {
  paymentSmsIngest,
  getSmsHistory,
  getDashboardStats
};
