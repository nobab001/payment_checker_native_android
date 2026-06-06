package online.paychek.app.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.Locale

object DeviceIdHelper {

    /**
     * Retrieves the Android ID (Settings.Secure.ANDROID_ID).
     * Returns a default fallback if null or empty.
     */
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_android_id"
    }

    /**
     * Retrieves the hardware build fingerprint.
     */
    fun getBuildFingerprint(): String {
        return Build.FINGERPRINT ?: "unknown_fingerprint"
    }

    /**
     * Generates a unique, privacy-preserving SHA-256 hash of the Android ID.
     */
    fun getHashedAndroidId(context: Context): String {
        val rawId = getAndroidId(context)
        return sha256(rawId)
    }

    /**
     * Generates a unique, privacy-preserving SHA-256 hash of the build fingerprint.
     */
    fun getHashedFingerprint(): String {
        val rawFingerprint = getBuildFingerprint()
        return sha256(rawFingerprint)
    }

    /**
     * Generates a secure SHA-256 hash for any input string.
     */
    fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString().lowercase(Locale.US)
        } catch (e: Exception) {
            // Fallback in case of hashing failures (highly unlikely)
            input.hashCode().toString()
        }
    }
}
