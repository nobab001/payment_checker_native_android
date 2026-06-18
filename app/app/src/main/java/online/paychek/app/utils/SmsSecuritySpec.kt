package online.paychek.app.utils

/**
 * SmsSecuritySpec — Canonical Cryptographic Contract for the Payment Checker System
 * ==================================================================================
 *
 * PURPOSE
 * -------
 * This file is the SINGLE SOURCE OF TRUTH for all cryptographic and
 * data-integrity rules in the SMS payment pipeline.
 *
 * Every developer, code reviewer, or AI assistant touching crypto-related
 * code MUST consult this file first. Any change to these rules MUST be
 * mirrored in:
 *   - Android: HmacHelper.kt (implementation)
 *   - Server:  backend/utils/smsSecuritySpec.js (mirror contract)
 *   - Server:  backend/utils/verifyHmac.js (implementation)
 *
 * ==================================================================================
 * CONTRACT SECTION 1 — rawBody Immutability
 * ==================================================================================
 *
 * RULE: rawBody is the IMMUTABLE byte-for-byte SMS body string as received from
 *       android.telephony.SmsMessage.getMessageBody()
 *
 * ALLOWED:
 *   val body = msg.messageBody ?: continue   <- assign once, from OS
 *
 * FORBIDDEN (will silently break HMAC + duplicate detection):
 *   body.trim()
 *   body.lowercase()
 *   body.replace(...)
 *   body + anything
 *   anything + body
 *   body.normalize()
 *
 * WHY: HMAC-SHA256 and SHA-256(rawBody) are both computed from this exact string.
 *      Any byte-level change causes:
 *        - Android HMAC != Server HMAC  -> server rejects as tampered
 *        - Android SHA256 != Server SHA256 -> deduplication breaks silently
 *
 * ==================================================================================
 * CONTRACT SECTION 2 — HMAC-SHA256 Specification
 * ==================================================================================
 *
 * ALGORITHM:  HMAC-SHA256
 * KEY:        per-user 64-char hex secret, stored in EncryptedSharedPreferences
 *             key name: SmsReceiver.KEY_HMAC_SECRET ("pcu_hmac_secret_key_v2")
 * INPUT:      UTF-8 bytes of rawBody (immutable, see Section 1)
 * KEY INPUT:  UTF-8 bytes of secretKey
 * OUTPUT:     lowercase hexadecimal string (64 chars)
 *
 * Android (canonical implementation -> HmacHelper.generate):
 *   Mac.getInstance("HmacSHA256")
 *   SecretKeySpec(secretKey.toByteArray(UTF_8), "HmacSHA256")
 *   mac.doFinal(rawBody.toByteArray(UTF_8))
 *   -> joinToString("") { "%02x".format(it) }
 *
 * Server (canonical implementation -> verifyHmac.js):
 *   crypto.createHmac('sha256', secretKey)
 *        .update(rawBody, 'utf8')
 *        .digest('hex')
 *
 * VERIFICATION: Server uses crypto.timingSafeEqual() to prevent timing attacks.
 *
 * ==================================================================================
 * CONTRACT SECTION 3 — SHA-256 rawBodyHash Specification
 * ==================================================================================
 *
 * PURPOSE:     Deduplication key component in sms_history table on the server.
 *              Stored in PendingSmsEntity.rawBodyHash (Room DB).
 *
 * ALGORITHM:  SHA-256
 * INPUT:      UTF-8 bytes of rawBody (immutable, see Section 1)
 * OUTPUT:     lowercase hexadecimal string (64 chars)
 *
 * Android (canonical implementation -> HmacHelper.sha256Hex):
 *   MessageDigest.getInstance("SHA-256")
 *   digest(rawBody.toByteArray(UTF_8))
 *   -> joinToString("") { "%02x".format(it) }
 *
 * Server (inline in paymentController.js sha256 helper):
 *   crypto.createHash('sha256').update(data, 'utf8').digest('hex')
 *
 * Server deduplication key formula (schema.sql):
 *   dedupe_key = CONCAT(sms_timestamp, '|', sender, '|', SHA256(raw_body))
 *
 * ==================================================================================
 * CONTRACT SECTION 4 — Offline Queue Retry Policy
 * ==================================================================================
 *
 * MAX_RETRIES:   10  (enforced in PendingSmsDao query: retryCount < 10)
 * BACKOFF:       Exponential (see SmsReceiver.calculateNextRetryMs)
 *   Attempt 0  -> retry in 30 seconds
 *   Attempt 1  -> retry in 2 minutes
 *   Attempt 2  -> retry in 10 minutes
 *   Attempt 3  -> retry in 1 hour
 *   Attempt 4+ -> retry in 6 hours (cap)
 *
 * PERMANENT FAILURE: HTTP 422 (SMS_PARSE_FAILED)
 *   -> markPermanentlyFailed() called immediately
 *   -> Item removed from active retry queue
 *   -> Reason: server HMAC passed but SMS body unparseable; retrying is futile
 *
 * CLEANUP: Synced entries older than 7 days are deleted from Room DB.
 *
 * ==================================================================================
 * CONTRACT SECTION 5 — Server-Side Ingest Rules
 * ==================================================================================
 *
 * The Android client sends the following fields to POST /api/payments/sms-ingest:
 *   rawBody        <- exact immutable SMS body (Section 1)
 *   hmacSignature  <- HMAC-SHA256(rawBody, secretKey) (Section 2)
 *   smsTimestamp   <- Unix ms timestamp from SmsMessage.timestampMillis
 *   simSlot        <- 1 or 2 (physical SIM slot)
 *   simNumber      <- phone number of the SIM (may be null)
 *   isOfflineSync  <- true when replaying from Room queue
 *
 * The server MUST:
 *   1. Verify HMAC first (reject 401 if mismatch)
 *   2. Parse rawBody using stored regex templates
 *   3. Compute SHA256(rawBody) for dedupe_key
 *   4. Return 422 if parsing fails (client must NOT retry)
 *   5. Return 409 if dedupe_key already exists (client marks as synced)
 *   6. Return 200/201 on success
 *
 * ==================================================================================
 * CHANGE POLICY
 * ==================================================================================
 *
 * Any change to the above contracts requires:
 *   [ ] Update this file (SmsSecuritySpec.kt)
 *   [ ] Update smsSecuritySpec.js (backend mirror)
 *   [ ] Update HmacHelper.kt (Android implementation)
 *   [ ] Update verifyHmac.js (server implementation)
 *   [ ] Update paymentController.js sha256() helper if needed
 *   [ ] Increment API version if breaking (e.g. encoding change)
 *   [ ] Test with a known rawBody to verify Android hash == Server hash
 *
 * ==================================================================================
 */
object SmsSecuritySpec {

    // -------------------------------------------------------------------------
    // Section 2 constants — HMAC
    // -------------------------------------------------------------------------
    const val HMAC_ALGORITHM          = "HmacSHA256"
    const val HMAC_KEY_PREFS_KEY      = "pcu_hmac_secret_key_v2"
    const val HMAC_OUTPUT_FORMAT      = "lowercase_hex"
    const val HMAC_ENCODING           = "UTF-8"
    const val HMAC_MIN_KEY_LENGTH     = 32

    // -------------------------------------------------------------------------
    // Section 3 constants — SHA-256 rawBodyHash
    // -------------------------------------------------------------------------
    const val HASH_ALGORITHM          = "SHA-256"
    const val HASH_OUTPUT_FORMAT      = "lowercase_hex"
    const val HASH_ENCODING           = "UTF-8"

    // -------------------------------------------------------------------------
    // Section 4 constants — Retry policy
    // -------------------------------------------------------------------------
    const val RETRY_MAX_ATTEMPTS      = 10
    const val RETRY_DELAY_0_MS        = 30_000L       // 30 seconds
    const val RETRY_DELAY_1_MS        = 120_000L      // 2 minutes
    const val RETRY_DELAY_2_MS        = 600_000L      // 10 minutes
    const val RETRY_DELAY_3_MS        = 3_600_000L    // 1 hour
    const val RETRY_DELAY_CAP_MS      = 21_600_000L   // 6 hours (cap)
    const val SYNC_CLEANUP_AGE_MS     = 7L * 24 * 60 * 60 * 1_000  // 7 days

    // HTTP response codes from server
    const val HTTP_HMAC_FAIL          = 401
    const val HTTP_PARSE_FAIL         = 422  // permanent — do not retry
    const val HTTP_DUPLICATE          = 409  // already exists — mark as synced

    // -------------------------------------------------------------------------
    // Section 1 — rawBody immutability check (runtime assertion)
    // Use this in any code path that receives a rawBody before hashing.
    // -------------------------------------------------------------------------
    /**
     * Asserts that rawBody is not blank.
     * Returns true if safe to proceed; false if the item should be aborted.
     *
     * DOES NOT modify rawBody. The caller must abort if this returns false.
     */
    fun isRawBodyValid(rawBody: String): Boolean = rawBody.isNotBlank()
}
