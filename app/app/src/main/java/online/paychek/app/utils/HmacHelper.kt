package online.paychek.app.utils

import android.util.Log
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HmacHelper — Android-side HMAC-SHA256 Generator
 * ============================================================================
 * উদ্দেশ্য : RAW SMS body এবং per-user secretKey থেকে HMAC-SHA256 তৈরি করা।
 *           এই HMAC সার্ভারে পাঠানো হয়, যেখানে server-side এ timingSafeEqual
 *           দিয়ে verify করা হয়।
 *
 * Algorithm : HMAC-SHA256(rawSmsBody, secretKey) → hex string
 * Library   : javax.crypto.Mac (Android built-in, কোনো external dependency নেই)
 * ============================================================================
 */
object HmacHelper {

    private const val TAG = "HmacHelper"
    private const val ALGORITHM = "HmacSHA256"

    /**
     * একটি RAW SMS body থেকে HMAC-SHA256 signature তৈরি করো।
     *
     * @param rawBody    অপরিবর্তিত মূল SMS text
     * @param secretKey  per-user 64-char hex secret (EncryptedSharedPreferences থেকে)
     * @return           lowercase hex HMAC string, অথবা null যদি কোনো error হয়
     */
    fun generate(rawBody: String, secretKey: String): String? {
        return try {
            val keyBytes    = secretKey.toByteArray(Charsets.UTF_8)
            val messageBytes = rawBody.toByteArray(Charsets.UTF_8)

            val mac = Mac.getInstance(ALGORITHM)
            val secretKeySpec = SecretKeySpec(keyBytes, ALGORITHM)
            mac.init(secretKeySpec)

            val hmacBytes = mac.doFinal(messageBytes)

            // Bytes → lowercase hex string (সার্ভারের সাথে মিলবে)
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "HMAC generation failed: ${e.message}", e)
            null
        }
    }

    /**
     * secretKey valid এবং non-empty কিনা চেক করো।
     */
    fun isKeyValid(secretKey: String?): Boolean {
        return !secretKey.isNullOrBlank() && secretKey.length >= 32
    }

    // =========================================================================
    // CANONICAL SHA-256 SPEC — DO NOT CHANGE WITHOUT UPDATING SERVER SIDE
    // =========================================================================
    // ┌──────────────────────────────────────────────────────────────────────┐
    // │  Formula (identical on Android and Node.js):                         │
    // │    SHA-256(                                                           │
    // │        input   = UTF-8(rawSmsBody),   ← raw, NEVER trimmed/modified  │
    // │    ) → lowercase hexadecimal string                                  │
    // │                                                                       │
    // │  Server-side equivalent (paymentController.js):                      │
    // │    crypto.createHash('sha256').update(data, 'utf8').digest('hex')    │
    // │                                                                       │
    // │  ⛔ DO NOT change:                                                     │
    // │     • UTF-8 encoding                                                  │
    // │     • lowercase hex output (not base64, not uppercase)               │
    // │  Any change MUST be mirrored in paymentController.js sha256()        │
    // └──────────────────────────────────────────────────────────────────────┘
    /**
     * sha256Hex — rawSmsBody থেকে SHA-256 integrity hash তৈরি করো।
     *
     * ⚠️ IMMUTABILITY CONTRACT: input must be the exact, unmodified SMS body
     * as received from [SmsMessage.messageBody]. Any trim/normalize BEFORE
     * calling this function will break duplicate detection and server hashing.
     *
     * @param input  Raw, unmodified string (UTF-8)
     * @return       64-char lowercase hex SHA-256 digest
     */
    fun sha256Hex(input: String): String {
        val digest    = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
