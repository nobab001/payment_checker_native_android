const { query } = require('../db/connection');
const crypto = require('crypto');

/**
 * Computes SHA-256 hash of a string.
 */
function sha256(data) {
  return crypto.createHash('sha256').update(data).digest('hex');
}

/**
 * POST /api/payment-sms-ingest
 * Receives parsed payment SMS details from Android device.
 * Enforces UNIQUE dedupe key checks using INSERT IGNORE.
 */
async function paymentSmsIngest(req, res) {
  try {
    const {
      amount,
      trxId,
      providerTag,
      senderNumber,
      receiverNumber,
      smsTimestamp, // Epoch milliseconds (Long)
      rawBody,
      simSlot,
      simNumber
    } = req.body;

    const userId = req.user.userId;
    const deviceId = req.user.deviceId || 'unknown_device';

    if (!amount || !trxId || !providerTag || !smsTimestamp || !rawBody) {
      return res.status(400).json({ error: 'Missing required payment telemetry fields' });
    }

    // 1. Format date/time for MySQL from epoch timestamp
    const dateObj = new Date(Number(smsTimestamp));
    // Convert to local BD timezone offset if necessary, otherwise use ISO format (YYYY-MM-DD HH:mm:ss)
    // mysql2 will adjust according to pool timezone.
    const formattedTimestamp = dateObj.toISOString().slice(0, 19).replace('T', ' ');
    const formattedDate = dateObj.toISOString().slice(0, 10);

    // 2. Compute the stable deduplication key
    // Formula: smsTimestamp + "|" + senderNumber + "|" + sha256(rawBody)
    const bodyHash = sha256(rawBody);
    const dedupeKey = `${smsTimestamp}|${senderNumber || ''}|${bodyHash}`;

    // 3. Database Ingestion using INSERT IGNORE to prevent duplicates
    // If a duplicate (user_id + dedupe_key) is hit, MySQL ignores the insert, returning affectedRows = 0.
    // This ensures that the existing record and its 'is_used' / 'used_at' states are NOT altered or reset.
    const result = await query(
      `INSERT IGNORE INTO sms_history (
        user_id, device_id, sim_slot, sim_number, provider_tag, amount, 
        trx_id, sender_number, receiver_number, sms_timestamp, sms_date, 
        raw_body, dedupe_key, is_synced, is_used
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0)`,
      [
        userId,
        deviceId,
        simSlot || null,
        simNumber || null,
        providerTag,
        amount,
        trxId.toUpperCase(),
        senderNumber || null,
        receiverNumber || null,
        formattedTimestamp,
        formattedDate,
        rawBody,
        dedupeKey
      ]
    );

    const isDuplicate = result.affectedRows === 0;

    if (isDuplicate) {
      console.log(`[INGEST] Ignored duplicate transaction TrxID: ${trxId} from user: ${userId}`);
      return res.json({
        success: true,
        isDuplicate: true,
        message: 'Transaction already exists. Deduplicated successfully.'
      });
    }

    console.log(`[INGEST] Successfully ingested transaction TrxID: ${trxId}, Amount: ৳${amount} for user: ${userId}`);
    return res.json({
      success: true,
      isDuplicate: false,
      message: 'Transaction ingested successfully.'
    });

  } catch (error) {
    console.error('Error ingesting parsed payment SMS:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

module.exports = {
  paymentSmsIngest
};
