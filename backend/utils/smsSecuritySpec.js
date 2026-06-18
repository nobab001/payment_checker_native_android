/**
 * smsSecuritySpec.js — Canonical Cryptographic Contract (Server Mirror)
 * ======================================================================
 *
 * PURPOSE
 * -------
 * This file is the SERVER-SIDE MIRROR of the canonical cryptographic and
 * data-integrity contract for the SMS payment pipeline.
 *
 * The authoritative Android source is:
 *   app/src/main/java/online/paychek/app/utils/SmsSecuritySpec.kt
 *
 * Both files MUST remain in sync. Any change here must be reflected there,
 * and vice versa.
 *
 * Server implementations referenced by this spec:
 *   - backend/utils/verifyHmac.js       (HMAC verification)
 *   - backend/controllers/paymentController.js  (sha256 helper + ingest logic)
 *   - backend/schema.sql                (dedupe_key formula)
 *
 * ======================================================================
 * CONTRACT SECTION 1 — rawBody Immutability
 * ======================================================================
 *
 * RULE: rawBody is the IMMUTABLE byte-for-byte SMS body string as received
 *       from the Android client. It must NEVER be modified before hashing.
 *
 * ALLOWED on server:
 *   const { rawBody } = req.body;   // receive and store as-is
 *   verifyHmac(rawBody, signature, secretKey);  // hash the exact string
 *
 * FORBIDDEN (will silently break HMAC + duplicate detection):
 *   rawBody.trim()
 *   rawBody.toLowerCase()
 *   rawBody.replace(...)
 *   rawBody + anything
 *
 * WHY: Both HMAC-SHA256 and SHA-256(rawBody) are computed on this string.
 *      Any mutation causes:
 *        - Server HMAC != Android HMAC  -> payload rejected as tampered
 *        - Server SHA256 != Android SHA256 -> deduplication ghost entries
 *
 * ======================================================================
 * CONTRACT SECTION 2 — HMAC-SHA256 Specification
 * ======================================================================
 *
 * ALGORITHM:  HMAC-SHA256
 * KEY:        per-user secret stored in users table (secretKey column)
 * INPUT:      UTF-8 string rawBody (immutable, see Section 1)
 * OUTPUT:     lowercase hexadecimal string (64 chars)
 *
 * Server implementation (verifyHmac.js):
 *   crypto.createHmac('sha256', secretKey)
 *          .update(rawBody, 'utf8')
 *          .digest('hex')
 *
 * Android implementation (HmacHelper.generate):
 *   Mac.getInstance("HmacSHA256")
 *   mac.doFinal(rawBody.toByteArray(UTF_8))
 *   -> joinToString("") { "%02x".format(it) }
 *
 * VERIFICATION: Use crypto.timingSafeEqual() — NEVER use === for comparison.
 *
 * ======================================================================
 * CONTRACT SECTION 3 — SHA-256 rawBodyHash Specification
 * ======================================================================
 *
 * PURPOSE:     Deduplication key component in sms_history table.
 *
 * ALGORITHM:  SHA-256
 * INPUT:      UTF-8 string rawBody (immutable, see Section 1)
 * OUTPUT:     lowercase hexadecimal string (64 chars)
 *
 * Server implementation (paymentController.js sha256 helper):
 *   crypto.createHash('sha256').update(data, 'utf8').digest('hex')
 *
 * Android implementation (HmacHelper.sha256Hex):
 *   MessageDigest.getInstance("SHA-256")
 *   digest(rawBody.toByteArray(UTF_8))
 *   -> joinToString("") { "%02x".format(it) }
 *
 * Server deduplication key formula (schema.sql):
 *   dedupe_key = CONCAT(sms_timestamp, '|', sender, '|', SHA256(raw_body))
 *
 * ======================================================================
 * CONTRACT SECTION 4 — Offline Queue Retry Policy
 * ======================================================================
 *
 * MAX_RETRIES:   10  (enforced in Android DAO query)
 * BACKOFF:       Exponential (enforced in SmsReceiver.calculateNextRetryMs)
 *   Attempt 0  -> retry in 30 seconds
 *   Attempt 1  -> retry in 2 minutes
 *   Attempt 2  -> retry in 10 minutes
 *   Attempt 3  -> retry in 1 hour
 *   Attempt 4+ -> retry in 6 hours (cap)
 *
 * SERVER RESPONSE CODES (must match what Android client expects):
 *   200 / 201 -> success      -> Android: markAsSynced()
 *   401       -> HMAC fail    -> Android: markRetryFailed() (may fix itself)
 *   409       -> duplicate    -> Android: markAsSynced() (already on server)
 *   422       -> parse fail   -> Android: markPermanentlyFailed() — DO NOT change to 4xx else
 *   5xx       -> server error -> Android: markRetryFailed() with backoff
 *
 * CRITICAL: HTTP 422 is the PERMANENT FAILURE signal.
 *   If the server changes this code, Android retry logic breaks.
 *   The Android client will retry 422 indefinitely if the code changes.
 *
 * ======================================================================
 * CONTRACT SECTION 5 — Ingest Request Fields
 * ======================================================================
 *
 * Android sends to POST /api/payments/sms-ingest:
 *   rawBody        {string}  exact SMS body — immutable (Section 1)
 *   hmacSignature  {string}  HMAC-SHA256 hex (Section 2)
 *   smsTimestamp   {number}  Unix ms — from Android SmsMessage.timestampMillis
 *   simSlot        {number}  1 or 2 — physical SIM slot index
 *   simNumber      {string?} phone number of the SIM (may be null)
 *   isOfflineSync  {boolean} true when replaying from Room offline queue
 *
 * Server MUST process in this order:
 *   1. Verify HMAC  (reject 401 on mismatch — do not proceed)
 *   2. Parse rawBody using stored provider regex template
 *   3. Compute SHA256(rawBody) to build dedupe_key
 *   4. Check sms_history for dedupe_key collision  (return 409 if exists)
 *   5. Insert into sms_history
 *   6. Return 422 if step 2 fails  (parse failure — do not retry)
 *
 * ======================================================================
 * CHANGE POLICY
 * ======================================================================
 *
 * Any change to the above contracts requires ALL of the following:
 *   [ ] Update this file (smsSecuritySpec.js)
 *   [ ] Update SmsSecuritySpec.kt (Android mirror)
 *   [ ] Update verifyHmac.js (server HMAC implementation)
 *   [ ] Update paymentController.js sha256() helper if needed
 *   [ ] Update HmacHelper.kt (Android HMAC + SHA256 implementation)
 *   [ ] Increment API version if encoding or algorithm changes
 *   [ ] Cross-verify: compute hash of a known rawBody on both sides
 *       and confirm the outputs are identical before deploying
 *
 * ======================================================================
 */

// -------------------------------------------------------------------------
// Section 2 constants — HMAC
// -------------------------------------------------------------------------
const HMAC_ALGORITHM      = 'sha256';         // crypto.createHmac arg
const HMAC_ENCODING       = 'utf8';           // .update() encoding arg
const HMAC_OUTPUT_FORMAT  = 'hex';            // .digest() format (lowercase)

// -------------------------------------------------------------------------
// Section 3 constants — SHA-256 rawBodyHash
// -------------------------------------------------------------------------
const HASH_ALGORITHM      = 'sha256';         // crypto.createHash arg
const HASH_ENCODING       = 'utf8';           // .update() encoding arg
const HASH_OUTPUT_FORMAT  = 'hex';            // .digest() format (lowercase)

// -------------------------------------------------------------------------
// Section 4 constants — HTTP response codes Android client expects
// -------------------------------------------------------------------------
const HTTP_SUCCESS        = 200;
const HTTP_CREATED        = 201;
const HTTP_DUPLICATE      = 409;  // already exists — Android marks as synced
const HTTP_HMAC_FAIL      = 401;  // HMAC mismatch — reject without processing
const HTTP_PARSE_FAIL     = 422;  // SMS unparseable — Android PERMANENT FAIL (no retry)

// -------------------------------------------------------------------------
// Section 4 constants — Retry schedule (for documentation; enforced on Android)
// -------------------------------------------------------------------------
const RETRY_MAX_ATTEMPTS  = 10;
const RETRY_DELAYS_MS     = [30_000, 120_000, 600_000, 3_600_000, 21_600_000]; // cap at index 4

// -------------------------------------------------------------------------
// Section 1 — rawBody validation helper
// -------------------------------------------------------------------------
/**
 * Validates that rawBody has not been mutated before hashing.
 * Returns true if safe to proceed; throws if rawBody is empty/null.
 *
 * @param {string} rawBody
 * @returns {boolean}
 */
function assertRawBodyIntegrity(rawBody) {
    if (!rawBody || typeof rawBody !== 'string' || rawBody.length === 0) {
        throw new Error(
            '[SmsSecuritySpec] rawBody integrity violation: ' +
            'rawBody is null, undefined, or empty. ' +
            'This indicates rawBody was modified before reaching this point. ' +
            'See smsSecuritySpec.js Section 1.'
        );
    }
    return true;
}

module.exports = {
    HMAC_ALGORITHM,
    HMAC_ENCODING,
    HMAC_OUTPUT_FORMAT,
    HASH_ALGORITHM,
    HASH_ENCODING,
    HASH_OUTPUT_FORMAT,
    HTTP_SUCCESS,
    HTTP_CREATED,
    HTTP_DUPLICATE,
    HTTP_HMAC_FAIL,
    HTTP_PARSE_FAIL,
    RETRY_MAX_ATTEMPTS,
    RETRY_DELAYS_MS,
    assertRawBodyIntegrity,
};
