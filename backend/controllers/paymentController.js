const prisma = require('../db/prisma');
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
// =============================================================================
async function paymentSmsIngest(req, res) {
  try {
    const userId   = req.user.userId;
    const deviceId = req.user.deviceId || 'unknown_device';

    const { rawBody, hmacSignature, senderNumber, smsTimestamp, simSlot, simNumber } = req.body;

    // STEP 1: Input Validation
    if (!rawBody || typeof rawBody !== 'string' || rawBody.trim().length === 0) {
      return res.status(400).json({ error: 'rawBody অনুপস্থিত বা খালি' });
    }
    if (!hmacSignature || typeof hmacSignature !== 'string') {
      return res.status(400).json({ error: 'hmacSignature অনুপস্থিত' });
    }
    if (!smsTimestamp) {
      return res.status(400).json({ error: 'smsTimestamp অনুপস্থিত' });
    }

    // STEP 2: User-এর secretKey ডাটাবেজ থেকে লোড করো
    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { secretKey: true }
    });
    const secretKey = user?.secretKey;

    if (!secretKey) {
      console.warn(`[INGEST] ⛔ No secretKey — userId: ${userId}. Device re-bind required.`);
      return res.status(403).json({
        error: 'HMAC_KEY_NOT_FOUND',
        message: 'আপনার ডিভাইসের HMAC Key পাওয়া যায়নি। অনুগ্রহ করে আবার লগইন করুন।'
      });
    }

    // STEP 3: HMAC Verification
    const hmacResult = verifyHmac(rawBody, hmacSignature, secretKey);

    if (!hmacResult.valid) {
      console.warn(`[INGEST] ⛔ HMAC INVALID — userId: ${userId} | deviceId: ${deviceId} | error: ${hmacResult.error}`);
      return res.status(403).json({
        error: 'HMAC_INVALID',
        message: 'অনুরোধটি cryptographically যাচাই করা যায়নি। সম্ভাব্য কারণ: SMS body পরিবর্তিত হয়েছে।'
      });
    }

    // STEP 4: rawBodyHash
    const rawBodyHash = sha256(rawBody.trim());

    // STEP 5: Server-side SMS Parsing
    console.log(`[INGEST DEBUG] Raw Body: ${rawBody} | Sender Number: ${senderNumber}`);
    const parsed = await parseRawSms(rawBody, senderNumber || '');

    if (!parsed.success) {
      console.warn(`[INGEST] ⚠️ PARSE_FAILED (HMAC OK) — userId: ${userId} | body[:80]: ${rawBody.substring(0, 80)}`);

      try {
        await prisma.sms_parse_failures.create({
          data: {
            user_id: userId,
            device_id: deviceId,
            raw_body: rawBody,
            raw_body_hash: rawBodyHash,
            hmac_signature: hmacSignature,
            parse_error: parsed.error || 'No matching template',
            sms_timestamp_ms: Number(smsTimestamp)
          }
        });
      } catch (auditErr) {
        console.error('[INGEST] Audit insert failed (non-critical):', auditErr.message);
      }

      return res.status(422).json({
        error: 'SMS_PARSE_FAILED',
        message: 'SMS format চেনা যায়নি। এই বার্তাটি audit log এ সংরক্ষিত হয়েছে।',
        hint: 'Admin-কে এই SMS format সম্পর্কে জানান — নতুন template যোগ করা হবে।'
      });
    }

    // STEP 6: Timestamp ও Deduplication Key তৈরি
    const dateObj            = new Date(Number(smsTimestamp));
    const formattedDate      = dateObj.toISOString().slice(0, 10);
    const dedupeKey = `${smsTimestamp}|${parsed.senderNumber || ''}|${rawBodyHash}`;

    // STEP 7: DUPLICATE SAFETY — INSERT
    try {
      await prisma.sms_history.create({
        data: {
          user_id: userId,
          device_id: deviceId,
          sim_slot: simSlot ? parseInt(simSlot, 10) : null,
          sim_number: simNumber ? String(simNumber) : null,
          provider_tag: parsed.provider,
          amount: parsed.amount,
          trx_id: parsed.trxId || '',
          sender_number: parsed.senderNumber || null,
          receiver_number: null,
          sms_timestamp: dateObj,
          sms_date: new Date(formattedDate),
          full_sms: rawBody,
          dedupe_key: dedupeKey,
          raw_sms_sha256: rawBodyHash,
          is_synced: 1,
          is_used: 0
        }
      });
      
      console.log(`[INGEST] ✅ Saved — Provider: ${parsed.provider} | ৳${parsed.amount} | TrxID: ${parsed.trxId} | SIM: ${simSlot} | User: ${userId}`);
      return res.json({
        success: true,
        isDuplicate: false,
        message: 'ট্রানজেকশন সফলভাবে সংরক্ষিত হয়েছে।'
      });

    } catch (dbErr) {
      // Prisma P2002 is Unique Constraint Violation (equivalent to INSERT IGNORE triggering)
      if (dbErr.code === 'P2002') {
        console.log(`[INGEST] Duplicate blocked — hash: ${rawBodyHash.substring(0, 16)}… | User: ${userId}`);
        return res.json({
          success: true,
          isDuplicate: true,
          message: 'ডুপ্লিকেট ট্রানজেকশন — পূর্বের স্ট্যাটাস অপরিবর্তিত রাখা হয়েছে।'
        });
      }
      throw dbErr; // Rethrow if it's not a duplicate error
    }

  } catch (error) {
    console.error('[INGEST] Error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// POST /api/payment-sms-ingest/bulk
// =============================================================================
async function paymentSmsIngestBulk(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.user.deviceId || 'unknown_device';
    const { items } = req.body;

    if (!items || !Array.isArray(items)) {
      return res.status(400).json({ error: 'items array is required' });
    }

    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { secretKey: true }
    });
    const secretKey = user?.secretKey;

    if (!secretKey) {
      return res.status(403).json({ error: 'HMAC_KEY_NOT_FOUND' });
    }

    let successCount = 0;
    let failCount = 0;

    for (const item of items) {
      const { rawBody, hmacSignature, senderNumber, smsTimestamp, simSlot, simNumber } = item;
      
      if (!rawBody || !hmacSignature || !smsTimestamp) {
        failCount++;
        continue;
      }

      const hmacResult = verifyHmac(rawBody, hmacSignature, secretKey);
      if (!hmacResult.valid) {
        failCount++;
        continue;
      }

      const rawBodyHash = sha256(rawBody.trim());
      const parsed = await parseRawSms(rawBody, senderNumber || '');

      if (!parsed.success) {
        try {
          await prisma.sms_parse_failures.create({
            data: {
              user_id: userId, device_id: deviceId, raw_body: rawBody, raw_body_hash: rawBodyHash,
              hmac_signature: hmacSignature, parse_error: parsed.error || 'No match', sms_timestamp_ms: Number(smsTimestamp)
            }
          });
        } catch (e) {}
        failCount++;
        continue;
      }

      const dateObj = new Date(Number(smsTimestamp));
      const formattedDate = dateObj.toISOString().slice(0, 10);
      const dedupeKey = `${smsTimestamp}|${parsed.senderNumber || ''}|${rawBodyHash}`;

      try {
        await prisma.sms_history.create({
          data: {
            user_id: userId, device_id: deviceId, sim_slot: simSlot ? parseInt(simSlot, 10) : null,
            sim_number: simNumber ? String(simNumber) : null, provider_tag: parsed.provider,
            amount: parsed.amount, trx_id: parsed.trxId || '', sender_number: parsed.senderNumber || null,
            sms_timestamp: dateObj, sms_date: new Date(formattedDate), full_sms: rawBody,
            dedupe_key: dedupeKey, raw_sms_sha256: rawBodyHash, is_synced: 1, is_used: 0
          }
        });
        successCount++;
      } catch (dbErr) {
        if (dbErr.code === 'P2002') {
          successCount++; // Count duplicates as success for the queue clearer
        } else {
          failCount++;
        }
      }
    }

    return res.json({ success: true, processed: successCount, failed: failCount });
  } catch (error) {
    console.error('[INGEST BULK] Error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}


// =============================================================================
// GET /api/sms-history
// পেজিনেটেড ট্রানজেকশন লিস্ট রিটার্ন করে।
// =============================================================================
async function getSmsHistory(req, res) {
  try {
    const userId   = req.user.userId;
    const page     = Math.max(1, parseInt(req.query.page)  || 1);
    const limit    = Math.min(50, parseInt(req.query.limit) || 20);
    const provider = req.query.provider || 'all';
    const offset   = (page - 1) * limit;

    const allowedProviders = ['bKash', 'Nagad', 'Rocket', 'Upay'];
    const useFilter = allowedProviders.includes(provider);

    const whereClause = { user_id: userId };
    if (useFilter) {
      whereClause.provider_tag = provider;
    }

    if (req.query.startDate && req.query.endDate) {
      whereClause.sms_date = {
        gte: new Date(req.query.startDate),
        lte: new Date(req.query.endDate)
      };
    }

    const totalCount = await prisma.sms_history.count({
      where: whereClause
    });

    const rows = await prisma.sms_history.findMany({
      where: whereClause,
      select: {
        id: true, provider_tag: true, amount: true, trx_id: true, sender_number: true,
        sim_slot: true, sms_timestamp: true, is_used: true, created_at: true, full_sms: true
      },
      orderBy: { sms_timestamp: 'desc' },
      skip: offset,
      take: limit
    });

    const mappedRows = rows.map(row => ({
      ...row,
      status: row.is_used ? 'SOLD_OUT' : 'READY',
      amount: Number(row.amount)
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
// =============================================================================
async function getDashboardStats(req, res) {
  try {
    const userId = req.user.userId;

    const now = new Date();
    const bdOffset = 6 * 60;
    const bdNow = new Date(now.getTime() + bdOffset * 60 * 1000);
    const todayDate = bdNow.toISOString().slice(0, 10);

    const totalStats = await prisma.$queryRaw`
      SELECT
         COALESCE(SUM(amount), 0)  AS total_earnings,
         COUNT(*)                   AS total_transactions,
         SUM(is_used = 0)           AS unused_count,
         SUM(is_used = 1)           AS soldout_count
       FROM sms_history
       WHERE user_id = ${userId}
    `;
    const totalRow = totalStats[0] || {};

    const todayStats = await prisma.$queryRaw`
      SELECT
         COALESCE(SUM(amount), 0) AS today_earnings,
         COUNT(*)                  AS today_transactions
       FROM sms_history
       WHERE user_id = ${userId} AND sms_date = ${todayDate}
    `;
    const todayRow = todayStats[0] || {};

    const activeDevices = await prisma.registered_devices.count({
      where: { user_id: userId, status: 'active' }
    });

    const userRow = await prisma.users.findUnique({
      where: { id: userId },
      select: { is_paid: true, active_plan_name: true, expiry_date: true, secretKey: true, secretKeyVersion: true }
    });
    const isPaid = userRow ? userRow.is_paid : 0;
    const activePlanName = userRow ? userRow.active_plan_name : 'FREE_LEVEL';
    const expiryDate = userRow ? userRow.expiry_date : null;

    const recentRows = await prisma.sms_history.findMany({
      where: { user_id: userId },
      select: {
        id: true, provider_tag: true, amount: true, trx_id: true, sender_number: true,
        sim_slot: true, sms_timestamp: true, is_used: true, created_at: true, full_sms: true
      },
      orderBy: { sms_timestamp: 'desc' },
      take: 20
    });

    const mappedRecentRows = recentRows.map(row => ({
      ...row,
      status: row.is_used ? 'SOLD_OUT' : 'READY',
      amount: Number(row.amount)
    }));

    console.log(`[STATS] Dashboard loaded for user: ${userId} | Today: ${todayDate} | Paid: ${isPaid} | Plan: ${activePlanName}`);

    return res.json({
      success: true,
      data: {
        total_earnings:      Number(totalRow.total_earnings)  || 0,
        today_earnings:      Number(todayRow.today_earnings)  || 0,
        total_transactions:  Number(totalRow.total_transactions)  || 0,
        today_transactions:  Number(todayRow.today_transactions)  || 0,
        unused_count:        Number(totalRow.unused_count)        || 0,
        soldout_count:       Number(totalRow.soldout_count)       || 0,
        active_devices:      activeDevices || 0,
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

    const result = await prisma.sms_history.updateMany({
      where: { id: parseInt(id), user_id: userId },
      data: { is_used: 1, used_at: new Date() }
    });

    if (result.count === 0) {
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
  paymentSmsIngestBulk,
  getSmsHistory,
  getDashboardStats,
  markTransactionSoldOut
};
