const { Worker } = require('bullmq');
const { getRedisClient } = require('../services/redisClient');
const prisma = require('../db/prisma');
const dataSyncCache = require('../services/dataSyncCache');
const numberHealth = require('../services/numberHealthService');
const crypto = require('crypto');
const { verifyHmac } = require('../utils/verifyHmac');
const { parseRawSms } = require('../utils/parseRawSms');
const checkoutController = require('../controllers/checkoutController');
const presenceV25 = require('../services/presenceV25');

function sha256(data) {
  return crypto.createHash('sha256').update(data, 'utf8').digest('hex');
}

const connection = getRedisClient();

const smsWorker = new Worker('smsIngestQueue', async (job) => {
  const { userId, deviceId, rawBody, hmacSignature, senderNumber, smsTimestamp, simSlot, simNumber } = job.data;

  if (!rawBody || typeof rawBody !== 'string' || !hmacSignature || !smsTimestamp) {
    throw new Error('Missing basic validation fields');
  }

  // Fetch user secret key
  const user = await prisma.users.findUnique({
    where: { id: userId },
    select: { secretKey: true }
  });
  const secretKey = user?.secretKey;

  if (!secretKey) {
    throw new Error('HMAC_KEY_NOT_FOUND');
  }

  // HMAC Verification
  const hmacResult = verifyHmac(rawBody, hmacSignature, secretKey);
  if (!hmacResult.valid) {
    throw new Error('HMAC_INVALID');
  }

  const isParseable = job.data.isParseable !== undefined ? parseInt(job.data.isParseable, 10) : 1;
  if (isParseable === 0) {
    try {
      const slotNum = simSlot != null && simSlot !== '' ? parseInt(simSlot, 10) : null;
      let providerTag = (job.data.providerTag || 'Custom').toString().trim() || 'Custom';
      let archiveSenderId = (job.data.templateSenderId || senderNumber || '').toString().trim() || null;

      const customMatch = /^Custom-(.+)$/i.exec(providerTag);
      if (customMatch && !archiveSenderId) {
        archiveSenderId = customMatch[1].trim();
      }

      // Prefer official ready-made template name for display
      if (archiveSenderId) {
        try {
          const official = await prisma.$queryRaw`
            SELECT template_name, sender_id
            FROM sms_templates
            WHERE is_official = 1 AND is_parseable = 0
              AND LOWER(sender_id) = LOWER(${archiveSenderId})
            LIMIT 1
          `;
          if (official?.[0]?.template_name && (!providerTag || customMatch)) {
            providerTag = official[0].template_name;
            archiveSenderId = official[0].sender_id || archiveSenderId;
          }
        } catch (_) { /* non-fatal */ }
      }

      let resolvedSimNumber = simNumber ? String(simNumber).trim() : '';
      if (!resolvedSimNumber && Number.isInteger(slotNum) && deviceId) {
        try {
          const binding = await prisma.sim_slot_bindings.findFirst({
            where: {
              user_id: String(userId),
              device_id: String(deviceId),
              sim_slot: slotNum,
            },
            select: { phone_number: true },
          });
          resolvedSimNumber = (binding?.phone_number || '').trim();
        } catch (_) { /* optional */ }
      }

      await prisma.custom_sms_archives.create({
        data: {
          user_id: userId,
          device_id: deviceId || 'unknown_device',
          sim_slot: Number.isInteger(slotNum) && slotNum >= 1 && slotNum <= 2 ? slotNum : null,
          sim_number: resolvedSimNumber || null,
          provider_tag: providerTag,
          sender_id: archiveSenderId,
          full_sms: rawBody
        }
      });
      await dataSyncCache.bumpUserHistoryVersion(userId);
      try {
        await presenceV25.markDeviceAlive(userId, deviceId, { source: 'SMS_SUCCESS' });
      } catch (e) {
        console.warn('[PresenceV25] markDeviceAlive SMS_SUCCESS failed:', e.message);
      }
      console.log(`[WORKER] Custom archive SMS saved in 1ms for user ${userId}`);
      return { success: true, isArchive: true };
    } catch (archiveErr) {
      console.error('[WORKER] Custom SMS archive write error:', archiveErr);
      throw archiveErr;
    }
  }

  const rawBodyHash = sha256(rawBody.trim());
  
  // Always parse on server side — app only sends rawBody
  const clientProviderTag = (job.data.providerTag || '').trim();
  const parsed = await parseRawSms(rawBody, senderNumber || '', clientProviderTag);

  if (!parsed.success) {
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
    } catch (e) {}
    throw new Error('SMS_PARSE_FAILED');
  }

  // Prefer full template name from parser; fall back to client-matched method provider.
  const providerTag = (parsed.templateName || parsed.provider || clientProviderTag || 'Unknown').trim();

  const dateObj = new Date(Number(smsTimestamp));
  const formattedDate = dateObj.toISOString().slice(0, 10);
  // If trxId is empty (e.g. all {random} template), use rawBodyHash as unique trx_id
  const finalTrxId = parsed.trxId || `RAW_${rawBodyHash.substring(0, 20)}`;
  const dedupeKey = `${smsTimestamp}|${parsed.senderNumber || ''}|${rawBodyHash}`;

  let savedHistory;
  try {
    savedHistory = await prisma.sms_history.create({
      data: {
        user_id: userId,
        device_id: deviceId || 'unknown_device',
        sim_slot: simSlot ? parseInt(simSlot, 10) : null,
        sim_number: simNumber ? String(simNumber) : null,
        provider_tag: providerTag,
        amount: parsed.amount,
        trx_id: finalTrxId,
        sender_number: parsed.senderNumber || null,
        receiver_number: null,
        sms_timestamp: dateObj,
        sms_date: new Date(formattedDate),
        full_sms: rawBody,
        dedupe_key: dedupeKey,
        raw_sms_sha256: rawBodyHash,
        is_synced: 1,
        is_used: 0
      },
      select: {
        id: true, amount: true, trx_id: true, provider_tag: true, sender_number: true,
        sms_timestamp: true, full_sms: true, device_id: true, sim_slot: true, sim_number: true
      }
    });
  } catch (dbErr) {
    if (dbErr.code === 'P2002') {
      // Duplicate, which is fine, just resolve normally
      return { success: true, isDuplicate: true };
    }
    throw dbErr;
  }

  await dataSyncCache.bumpUserHistoryVersion(userId);
  try {
    await presenceV25.markDeviceAlive(userId, deviceId, { source: 'SMS_SUCCESS' });
  } catch (e) {
    console.warn('[PresenceV25] markDeviceAlive SMS_SUCCESS failed:', e.message);
  }

  if (simNumber) {
    numberHealth.touchNumberLive(userId, deviceId, simNumber).catch((err) => {
      console.warn('[WORKER] touchNumberLive failed:', err.message);
    });
  }

  // Merchant Vibe Mode: try to auto-match a waiting checkout request. Never
  // let a callback failure fail the ingest job.
  try {
    await checkoutController.matchVibeForHistory(userId, savedHistory);
  } catch (vibeErr) {
    console.error('[WORKER] Vibe match error:', vibeErr.message);
  }

  return { success: true, isDuplicate: false };

}, { 
  connection,
  concurrency: 50 
});

smsWorker.on('failed', (job, err) => {
  console.error(`[WORKER] Job ${job?.id} failed: ${err.message}`);
});

module.exports = smsWorker;
