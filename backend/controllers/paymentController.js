const prisma = require('../db/prisma');
const crypto = require('crypto');
const { verifyHmac } = require('../utils/verifyHmac');
const { parseRawSms } = require('../utils/parseRawSms');
const {
    HTTP_PARSE_FAIL,
    HTTP_DUPLICATE,
    assertRawBodyIntegrity,
} = require('../utils/smsSecuritySpec');

const { smsQueue } = require('../services/smsQueue');

// =============================================================================
// POST /api/payment-sms-ingest
// =============================================================================
async function paymentSmsIngest(req, res) {
  try {
    const payload = {
      userId: req.user.userId,
      deviceId: req.user.deviceId || 'unknown_device',
      rawBody: req.body.rawBody,
      hmacSignature: req.body.hmacSignature,
      senderNumber: req.body.senderNumber,
      smsTimestamp: req.body.smsTimestamp,
      simSlot: req.body.simSlot,
      simNumber: req.body.simNumber
    };

    if (!payload.rawBody || !payload.hmacSignature || !payload.smsTimestamp) {
      return res.status(400).json({ error: 'Missing basic validation fields' });
    }

    await smsQueue.add('processSms', payload);

    return res.status(202).json({
      success: true,
      status: 'Accepted',
      message: 'SMS queued for processing'
    });

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

    const jobs = items.map(item => ({
      name: 'processSms',
      data: {
        userId,
        deviceId,
        rawBody: item.rawBody,
        hmacSignature: item.hmacSignature,
        senderNumber: item.senderNumber,
        smsTimestamp: item.smsTimestamp,
        simSlot: item.simSlot,
        simNumber: item.simNumber
      }
    }));

    if (jobs.length > 0) {
      await smsQueue.addBulk(jobs);
    }

    return res.status(202).json({
      success: true,
      processed: jobs.length,
      failed: 0,
      status: 'Accepted',
      message: 'Bulk SMS queued for processing'
    });

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
