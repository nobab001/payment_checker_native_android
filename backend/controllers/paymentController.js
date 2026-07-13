const prisma = require('../db/prisma');
const crypto = require('crypto');
const { verifyHmac } = require('../utils/verifyHmac');
const { parseRawSms } = require('../utils/parseRawSms');
const {
    HTTP_PARSE_FAIL,
    HTTP_DUPLICATE,
    assertRawBodyIntegrity,
} = require('../utils/smsSecuritySpec');
const { fetchGatewayMethodsForUser } = require('./gatewayController');
const dataSyncCache = require('../services/dataSyncCache');
const numberHealth = require('../services/numberHealthService');

const { smsQueue } = require('../services/smsQueue');
const { getRedisClient } = require('../services/redisClient');

/** Fail fast if Redis is down — client must keep SMS in offline queue and retry. */
async function assertSmsQueueReady() {
  try {
    const redis = getRedisClient();
    const pong = await Promise.race([
      redis.ping(),
      new Promise((_, reject) => setTimeout(() => reject(new Error('REDIS_TIMEOUT')), 2000)),
    ]);
    if (pong !== 'PONG') {
      const err = new Error('REDIS_UNAVAILABLE');
      err.code = 'REDIS_UNAVAILABLE';
      throw err;
    }
  } catch (e) {
    if (e.code === 'REDIS_UNAVAILABLE') throw e;
    const err = new Error('SMS queue unavailable — Redis is down');
    err.code = 'REDIS_UNAVAILABLE';
    err.cause = e;
    throw err;
  }
}

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
      simNumber: req.body.simNumber,
      providerTag: req.body.providerTag,
      amount: req.body.amount,
      trxId: req.body.trxId,
      isParseable: req.body.is_parseable !== undefined ? parseInt(req.body.is_parseable, 10) : (req.body.isParseable !== undefined ? parseInt(req.body.isParseable, 10) : 1)
    };

    if (!payload.rawBody || !payload.hmacSignature || !payload.smsTimestamp) {
      return res.status(400).json({ error: 'Missing basic validation fields' });
    }

    await assertSmsQueueReady();
    await smsQueue.add('processSms', payload);

    if (payload.simNumber) {
      numberHealth.touchNumberLive(payload.userId, payload.deviceId, payload.simNumber).catch(() => {});
    }

    return res.status(202).json({
      success: true,
      status: 'Accepted',
      message: 'SMS queued for processing'
    });

  } catch (error) {
    if (error.code === 'REDIS_UNAVAILABLE') {
      console.error('[INGEST] Redis unavailable — client should retry:', error.message);
      return res.status(503).json({
        success: false,
        error: 'QUEUE_UNAVAILABLE',
        message: 'SMS queue temporarily unavailable. Keep offline and retry.',
      });
    }
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

    const MAX_BULK_ITEMS = parseInt(process.env.BULK_SMS_MAX_ITEMS || '100', 10);
    if (items.length > MAX_BULK_ITEMS) {
      return res.status(413).json({
        error: 'TOO_MANY_ITEMS',
        message: `Bulk ingest accepts at most ${MAX_BULK_ITEMS} items per request.`,
        limit: MAX_BULK_ITEMS,
        received: items.length,
      });
    }

    await assertSmsQueueReady();

    const jobs = [];
    const rejected = [];
    items.forEach((item, index) => {
      if (!item || typeof item !== 'object') {
        rejected.push({ index, reason: 'item must be an object' });
        return;
      }
      if (typeof item.rawBody !== 'string' || item.rawBody.trim() === '') {
        rejected.push({ index, reason: 'rawBody is required' });
        return;
      }
      jobs.push({
        name: 'processSms',
        data: {
          userId,
          deviceId,
          rawBody: item.rawBody,
          hmacSignature: item.hmacSignature,
          senderNumber: item.senderNumber,
          smsTimestamp: item.smsTimestamp,
          simSlot: item.simSlot,
          simNumber: item.simNumber,
          providerTag: item.providerTag,
          amount: item.amount,
          trxId: item.trxId,
          isParseable: item.is_parseable !== undefined ? parseInt(item.is_parseable, 10) : (item.isParseable !== undefined ? parseInt(item.isParseable, 10) : 1)
        }
      });
    });

    if (jobs.length > 0) {
      await smsQueue.addBulk(jobs);
    }

    return res.status(202).json({
      success: true,
      processed: jobs.length,
      failed: rejected.length,
      rejected,
      status: 'Accepted',
      message: 'Bulk SMS queued for processing'
    });

  } catch (error) {
    if (error.code === 'REDIS_UNAVAILABLE') {
      console.error('[INGEST BULK] Redis unavailable — client should retry:', error.message);
      return res.status(503).json({
        success: false,
        error: 'QUEUE_UNAVAILABLE',
        message: 'SMS queue temporarily unavailable. Keep offline and retry.',
        processed: 0,
        failed: Array.isArray(req.body?.items) ? req.body.items.length : 0,
      });
    }
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
    const lastSync = req.headers['x-history-last-sync'] ? parseInt(req.headers['x-history-last-sync'], 10) : 0;
    const historyVersion = await dataSyncCache.getUserHistoryVersion(userId);
    const cacheHit = page === 1 && lastSync > 0 && dataSyncCache.isClientSyncCurrent(lastSync, historyVersion);

    if (page > 1 && lastSync > 0 && dataSyncCache.isClientSyncCurrent(lastSync, historyVersion)) {
      return res.json({
        success: true,
        cache_hit: true,
        history_version: historyVersion,
        data: [],
        page,
        limit,
        total_count: 0,
        has_more: false,
      });
    }

    if (cacheHit) {
      console.log(`[HISTORY] User ${userId} | cache HIT | client=${lastSync} server=${historyVersion} | provider=${provider}`);
      return res.json({
        success: true,
        cache_hit: true,
        history_version: historyVersion,
        data: [],
        page,
        limit,
        total_count: 0,
        has_more: false,
      });
    }

    const allowedProviders = ['bKash', 'Nagad', 'Rocket', 'Upay'];
    const useFilter = allowedProviders.includes(provider);

    const whereClause = { user_id: userId };
    if (useFilter) {
      whereClause.provider_tag = provider;
    }

    const hasDateFilter = req.query.startDate && req.query.endDate;
    if (hasDateFilter) {
      whereClause.sms_date = {
        gte: new Date(req.query.startDate),
        lte: new Date(req.query.endDate)
      };
    }

    const totalCount = await prisma.sms_history.count({
      where: whereClause
    });

    const queryOptions = {
      where: whereClause,
      select: {
        id: true, provider_tag: true, amount: true, trx_id: true, sender_number: true,
        sim_slot: true, sms_timestamp: true, is_used: true, created_at: true, full_sms: true
      },
      orderBy: { sms_timestamp: 'desc' }
    };

    if (!hasDateFilter) {
      queryOptions.skip = offset;
      queryOptions.take = limit;
    }

    const rows = await prisma.sms_history.findMany(queryOptions);

    const userDevices = await prisma.registered_devices.findMany({
      where: { user_id: userId },
      select: { device_id: true, device_model: true, custom_device_name: true }
    });
    const deviceMap = {};
    userDevices.forEach(d => {
      deviceMap[d.device_id] = d.custom_device_name || d.device_model || 'Unknown Device';
    });

    const mappedRows = rows.map(row => ({
      ...row,
      status: row.is_used ? 'SOLD_OUT' : 'READY',
      amount: Number(row.amount),
      device_name: deviceMap[row.device_id] || 'Unknown Device'
    }));

    console.log(`[HISTORY] User ${userId} | cache MISS | client=${lastSync} server=${historyVersion} | Page ${page} | Provider: ${provider} | Found: ${rows.length}`);

    return res.json({
      success:     true,
      cache_hit:   false,
      history_version: historyVersion,
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

    const userDevices = await prisma.registered_devices.findMany({
      where: { user_id: userId },
      select: { device_id: true, device_model: true, custom_device_name: true }
    });
    const deviceMap = {};
    userDevices.forEach(d => {
      deviceMap[d.device_id] = d.custom_device_name || d.device_model || 'Unknown Device';
    });

    const mappedRecentRows = recentRows.map(row => ({
      ...row,
      status: row.is_used ? 'SOLD_OUT' : 'READY',
      amount: Number(row.amount),
      device_name: deviceMap[row.device_id] || 'Unknown Device'
    }));

    console.log(`[STATS] Dashboard loaded for user: ${userId} | Today: ${todayDate} | Paid: ${isPaid} | Plan: ${activePlanName}`);

    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const lastSync = req.headers['x-gateway-last-sync'] ? parseInt(req.headers['x-gateway-last-sync'], 10) : 0;
    const latestServerUpdateTime = await dataSyncCache.getDeviceSyncVersion(userId, deviceId);
    const cacheHit = dataSyncCache.isClientSyncCurrent(lastSync, latestServerUpdateTime);

    let gatewayMethods = null;
    let globalTemplates = null;
    if (!cacheHit) {
      gatewayMethods = await fetchGatewayMethodsForUser(userId, deviceId);
      globalTemplates = await dataSyncCache.getActiveTemplatesForDashboard();
      console.log(`[STATS] Client cache outdated (${lastSync} < ${latestServerUpdateTime}). Syncing ${gatewayMethods.length} methods.`);
    } else {
      console.log(`[STATS] Client cache current (${lastSync} >= ${latestServerUpdateTime}). Skipping template/method payload.`);
    }

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
        recent_transactions: mappedRecentRows,
        global_templates:    globalTemplates,
        gateway_methods:     gatewayMethods,
        gateway_methods_last_sync: latestServerUpdateTime,
        data_version:        latestServerUpdateTime,
        cache_hit:           cacheHit
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

    await dataSyncCache.bumpUserHistoryVersion(userId);

    return res.json({ success: true, message: 'Transaction marked as sold out successfully.' });
  } catch (error) {
    console.error('[SOLDOUT] Error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function getCustomArchives(req, res) {
  try {
    const userId = req.user.userId;
    const page = Math.max(1, parseInt(req.query.page, 10) || 1);
    const limit = Math.min(50, parseInt(req.query.limit, 10) || 20);
    const offset = (page - 1) * limit;

    const rows = await prisma.custom_sms_archives.findMany({
      where: { user_id: userId },
      orderBy: { created_at: 'desc' },
      skip: offset,
      take: limit
    });

    const userDevices = await prisma.registered_devices.findMany({
      where: { user_id: userId },
      select: { device_id: true, device_model: true, custom_device_name: true }
    });
    const deviceMap = {};
    userDevices.forEach(d => {
      deviceMap[d.device_id] = d.custom_device_name || d.device_model || 'Unknown Device';
    });

    const mappedRows = rows.map(r => ({
      id: r.id,
      device_id: r.device_id,
      device_name: deviceMap[r.device_id] || 'Unknown Device',
      provider_tag: r.provider_tag,
      full_sms: r.full_sms,
      created_at: r.created_at
    }));

    return res.json({ success: true, data: mappedRows });
  } catch (error) {
    console.error('[ARCHIVE] getCustomArchives error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
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
  markTransactionSoldOut,
  getCustomArchives
};
