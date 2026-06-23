const { Worker } = require('bullmq');
const Redis = require('ioredis');
const prisma = require('../db/prisma');
const crypto = require('crypto');
const { verifyHmac } = require('../utils/verifyHmac');
const { parseRawSms } = require('../utils/parseRawSms');

function sha256(data) {
  return crypto.createHash('sha256').update(data, 'utf8').digest('hex');
}

const connection = new Redis({
  host: process.env.REDIS_HOST || '127.0.0.1',
  port: process.env.REDIS_PORT || 6379,
  maxRetriesPerRequest: null,
});

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

  const rawBodyHash = sha256(rawBody.trim());
  
  let parsed = {
    success: true,
    provider: job.data.providerTag,
    amount: parseFloat(job.data.amount),
    trxId: job.data.trxId,
    senderNumber: senderNumber || ''
  };

  // If client didn't provide parsed data, fallback to server-side regex parse
  if (!parsed.provider || isNaN(parsed.amount) || !parsed.trxId) {
    parsed = await parseRawSms(rawBody, senderNumber || '');
  }

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

  const dateObj = new Date(Number(smsTimestamp));
  const formattedDate = dateObj.toISOString().slice(0, 10);
  const dedupeKey = `${smsTimestamp}|${parsed.senderNumber || ''}|${rawBodyHash}`;

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
  } catch (dbErr) {
    if (dbErr.code === 'P2002') {
      // Duplicate, which is fine, just resolve normally
      return { success: true, isDuplicate: true };
    }
    throw dbErr;
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
