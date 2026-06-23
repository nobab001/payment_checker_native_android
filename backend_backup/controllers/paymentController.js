const { query } = require('../db/connection');
const crypto = require('crypto');
const { verifyHmac } = require('../utils/verifyHmac');
const { parseRawSms } = require('../utils/parseRawSms');
const {
    HTTP_PARSE_FAIL,
    HTTP_DUPLICATE,
    assertRawBodyIntegrity,
} = require('../utils/smsSecuritySpec');

// =============================================================================
// Helper — SHA-256(rawBody, 'utf8') -> lowercase hex
// Canonical spec: smsSecuritySpec.js Section 3
// =============================================================================

function sha256(data) {
  return crypto.createHash('sha256').update(data, 'utf8').digest('hex');
}

// =============================================================================
// POST /api/payment-sms-ingest
// Android থেকে RAW SMS + HMAC Signature গ্রহণ করে, cryptographically যাচাই
// করে, server-side parse করে, ডাটাবেজে সংরক্ষণ করে।
//
// Execution Order (বাধ্যতামূলক):
//  1. Input validation
//  2. DB থেকে user secretKey লোড
//  3. HMAC verify — fail হলে সরাসরি 403, কোনো parse নেই
//  4. rawBodyHash = SHA256(rawBody) — integrity tracking
//  5. parseRawSms() — server-side — client data trust করা হয় না
//  6. Parse fail হলে sms_parse_failures audit table এ insert, 422 return
//  7. INSERT IGNORE — dedupe_key দিয়ে duplicate prevention
//
// Deduplication:
//  Formula: smsTimestamp + '|' + senderNumber + '|' + SHA256(rawBody)
//  UNIQUE constraint prevents same SMS being inserted twice.
//  is_used (SOLDOUT) কোনোভাবেই রিসেট হয় না।
// =============================================================================
async function paymentSmsIngest(req, res) {
  try {
    const userId   = req.user.userId;
    const deviceId = req.user.deviceId || 'unknown_device';

    const { rawBody, hmacSignature, senderNumber, smsTimestamp, simSlot, simNumber } = req.body;

    // ────────────────────────────────────────────────────────────────────────
    // STEP 1: Input Validation
    // rawBody ও hmacSignature ছাড়া কিছু accept করা হবে না
    // ────────────────────────────────────────────────────────────────────────
    if (!rawBody || typeof rawBody !== 'string' || rawBody.trim().length === 0) {
      return res.status(400).json({ error: 'rawBody অনুপস্থিত বা খালি' });
    }
    if (!hmacSignature || typeof hmacSignature !== 'string') {
      return res.status(400).json({ error: 'hmacSignature অনুপস্থিত' });
    }
    if (!smsTimestamp) {
      return res.status(400).json({ error: 'smsTimestamp অনুপস্থিত' });
    }

    // ────────────────────────────────────────────────────────────────────────
    // STEP 2: User-এর secretKey ডাটাবেজ থেকে লোড করো
    // Client-এর পাঠানো key কখনো trust করা হয় না — সবসময় DB থেকে নাও
    // ────────────────────────────────────────────────────────────────────────
    const userRows = await query('SELECT secretKey FROM users WHERE id = ? LIMIT 1', [userId]);
    const secretKey = userRows[0]?.secretKey;

    if (!secretKey) {
      console.warn(`[INGEST] ⛔ No secretKey — userId: ${userId}. Device re-bind required.`);
      return res.status(403).json({
        error: 'HMAC_KEY_NOT_FOUND',
        message: 'আপনার ডিভাইসের HMAC Key পাওয়া যায়নি। অনুগ্রহ করে আবার লগইন করুন।'
      });
    }

    // ────────────────────────────────────────────────────────────────────────
    // STEP 3: HMAC Verification — সবার আগে, parse করার আগে
    // এটা fail হলে সরাসরি 403 — কোনো parsing বা business logic নয়
    //
    // Canonical Formula (Android ও Node.js উভয়েই একই):
    //   HMAC-SHA256(key=UTF-8(secretKey), input=UTF-8(rawBody)) → lowercase hex
    // ────────────────────────────────────────────────────────────────────────
    const hmacResult = verifyHmac(rawBody, hmacSignature, secretKey);

    if (!hmacResult.valid) {
      console.warn(`[INGEST] ⛔ HMAC INVALID — userId: ${userId} | deviceId: ${deviceId} | error: ${hmacResult.error}`);
      return res.status(403).json({
        error: 'HMAC_INVALID',
        message: 'অনুরোধটি cryptographically যাচাই করা যায়নি। সম্ভাব্য কারণ: SMS body পরিবর্তিত হয়েছে।'
      });
    }

    // ────────────────────────────────────────────────────────────────────────
    // STEP 4: rawBodyHash — fast duplicate detection ও integrity tracking
    // Server-side compute — client-এর পাঠানো hash কখনো trust করা হয় অ্যাক্সেস
    // ────────────────────────────────────────────────────────────────────────
    const rawBodyHash = sha256(rawBody.trim());

    // ────────────────────────────────────────────────────────────────────────
    // STEP 5: Server-side SMS Parsing
    // HMAC verify সফল হওয়ার পরেই parseRawSms চালানো হয়।
    // Client-এর parsed fields (amount, trxId) সরাসরি ব্যবহার করা হয় না।
    // ────────────────────────────────────────────────────────────────────────
    const parsed = await parseRawSms(rawBody, senderNumber || '');

    if (!parsed.success) {
      // HMAC verify সফল কিন্তু parse fail — audit table এ রাখো, reject করো
      // "Never silently discard verified SMS" — audit policy
      console.warn(`[INGEST] ⚠️ PARSE_FAILED (HMAC OK) — userId: ${userId} | body[:80]: ${rawBody.substring(0, 80)}`);

      try {
        await query(
          `INSERT INTO sms_parse_failures
             (user_id, device_id, raw_body, raw_body_hash, hmac_signature, parse_error, sms_timestamp_ms, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, NOW())`,
          [
            userId,
            deviceId,
            rawBody,
            rawBodyHash,
            hmacSignature,
            parsed.error || 'No matching template',
            Number(smsTimestamp)
          ]
        );
      } catch (auditErr) {
        // Audit insert fail করলে main flow block হবে না — শুধু log
        console.error('[INGEST] Audit insert failed (non-critical):', auditErr.message);
      }

      return res.status(422).json({
        error: 'SMS_PARSE_FAILED',
        message: 'SMS format চেনা যায়নি। এই বার্তাটি audit log এ সংরক্ষিত হয়েছে।',
        hint: 'Admin-কে এই SMS format সম্পর্কে জানান — নতুন template যোগ করা হবে।'
      });
    }

    // ────────────────────────────────────────────────────────────────────────
    // STEP 6: Timestamp ও Deduplication Key তৈরি
    // ────────────────────────────────────────────────────────────────────────
    const dateObj            = new Date(Number(smsTimestamp));
    const formattedTimestamp = dateObj.toISOString().slice(0, 19).replace('T', ' ');
    const formattedDate      = dateObj.toISOString().slice(0, 10);

    // Deduplication Key Formula: smsTimestamp + '|' + senderNumber + '|' + SHA256(rawBody)
    const dedupeKey = `${smsTimestamp}|${parsed.senderNumber || ''}|${rawBodyHash}`;

    // ────────────────────────────────────────────────────────────────────────
    // STEP 7: DUPLICATE SAFETY — INSERT IGNORE
    // একই dedupe_key দ্বিতীয়বার insert হবে না।
    // is_used = 1 (SOLDOUT) থাকলে সেটি অপরিবর্তিত থাকবে।
    // raw_sms_sha256: fast lookup ও debugging এর জন্য সংরক্ষণ করা হচ্ছে।
    // ────────────────────────────────────────────────────────────────────────
    const result = await query(
      `INSERT IGNORE INTO sms_history (
        user_id, device_id, sim_slot, sim_number, provider_tag, amount,
        trx_id, sender_number, receiver_number, sms_timestamp, sms_date,
        full_sms, dedupe_key, raw_sms_sha256, is_synced, is_used
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0)`,
      [
        userId,
        deviceId,
        simSlot   || null,
        simNumber || null,
        parsed.provider,
        parsed.amount,
        parsed.trxId || '',
        parsed.senderNumber || null,
        null, // receiverNumber — server-side parse এ available নেই এই version এ
        formattedTimestamp,
        formattedDate,
        rawBody,
        dedupeKey,
        rawBodyHash
      ]
    );

    const isDuplicate = result.affectedRows === 0;

    if (isDuplicate) {
      console.log(`[INGEST] Duplicate blocked — hash: ${rawBodyHash.substring(0, 16)}… | User: ${userId}`);
      return res.json({
        success: true,
        isDuplicate: true,
        message: 'ডুপ্লিকেট ট্রানজেকশন — পূর্বের স্ট্যাটাস অপরিবর্তিত রাখা হয়েছে।'
      });
    }

    console.log(`[INGEST] ✅ Saved — Provider: ${parsed.provider} | ৳${parsed.amount} | TrxID: ${parsed.trxId} | SIM: ${simSlot} | User: ${userId}`);
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
                sms_timestamp, is_used, created_at, full_sms
           FROM sms_history
          WHERE user_id = ? AND provider_tag = ?
          ORDER BY sms_timestamp DESC
          LIMIT ? OFFSET ?`
      : `SELECT id, provider_tag, amount, trx_id, sender_number, sim_slot,
                sms_timestamp, is_used, created_at, full_sms
           FROM sms_history
          WHERE user_id = ?
          ORDER BY sms_timestamp DESC
          LIMIT ? OFFSET ?`;
    const dataParams = useFilter
      ? [userId, provider, limit, offset]
      : [userId, limit, offset];

    const rows = await query(dataQuery, dataParams);

    const mappedRows = rows.map(row => ({
      ...row,
      status: row.is_used === 1 ? 'SOLD_OUT' : 'READY'
    }));

    console.log(`[HISTORY] User ${userId} | Page ${page} | Provider: ${provider} | Found: ${rows.length}`);

    return res.json({
      success:     true,
      data:        mappedRows,
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

    // ── সাবস্ক্রিপশন স্ট্যাটাস (Subscription Status) ───────────────────────
    const [userRow] = await query(
      `SELECT is_paid, active_plan_name, expiry_date, secretKey, secretKeyVersion FROM users WHERE id = ? LIMIT 1`,
      [userId]
    );
    const isPaid = userRow ? userRow.is_paid : 0;
    const activePlanName = userRow ? userRow.active_plan_name : 'FREE_LEVEL';
    const expiryDate = userRow ? userRow.expiry_date : null;

    // ── সর্বশেষ ৫টি ট্রানজেকশন ─────────────────────────────────────────────
    const recentRows = await query(
      `SELECT id, provider_tag, amount, trx_id, sender_number,
              sim_slot, sms_timestamp, is_used, created_at, full_sms
         FROM sms_history
        WHERE user_id = ?
        ORDER BY sms_timestamp DESC
        LIMIT 5`,
      [userId]
    );

    const mappedRecentRows = recentRows.map(row => ({
      ...row,
      status: row.is_used === 1 ? 'SOLD_OUT' : 'READY'
    }));

    console.log(`[STATS] Dashboard loaded for user: ${userId} | Today: ${todayDate} | Paid: ${isPaid} | Plan: ${activePlanName}`);

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
        is_paid:             !!isPaid,
        active_plan_name:    activePlanName,
        expiry_date:         expiryDate,
        secretKey:           userRow ? userRow.secretKey : null,
        secretKeyVersion:    userRow ? userRow.secretKeyVersion : 1,
        recent_transactions: mappedRecentRows
      }
    });

  } catch (error) {
    console.error('[STATS] Error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// POST /api/sms-history/:id/soldout
// =============================================================================
async function markTransactionSoldOut(req, res) {
  try {
    const { id } = req.params;
    const userId = req.user.userId;

    if (!id) {
      return res.status(400).json({ success: false, error: 'Transaction ID is required' });
    }

    const result = await query(
      'UPDATE sms_history SET is_used = 1, used_at = NOW() WHERE id = ? AND user_id = ?',
      [id, userId]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ success: false, error: 'Transaction not found or not owned by user' });
    }

    return res.json({ success: true, message: 'Transaction marked as sold out successfully.' });
  } catch (error) {
    console.error('[SOLDOUT] Error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

// =============================================================================
// Exports
// =============================================================================
module.exports = {
  paymentSmsIngest,
  getSmsHistory,
  getDashboardStats,
  markTransactionSoldOut
};

