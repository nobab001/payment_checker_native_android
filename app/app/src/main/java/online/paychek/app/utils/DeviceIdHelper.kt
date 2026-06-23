package online.paychek.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
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
     * Retrieves the unique identifiers for SIM card slots/subscription IDs.
     * Sorted to ensure stable comparisons.
     */
    fun getSimSlotIds(context: Context): String {
        return try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeList = subscriptionManager.activeSubscriptionInfoList
            if (activeList.isNullOrEmpty()) {
                "no_sims"
            } else {
                activeList.map { info ->
                    "slot_${info.simSlotIndex}_sub_${info.subscriptionId}"
                }.sorted().joinToString(",")
            }
        } catch (e: SecurityException) {
            "permission_denied"
        } catch (e: Exception) {
            "unknown"
        }
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

    /**
     * Attempts to auto-detect the phone numbers for SIM 1 and SIM 2.
     * Returns a Pair of (SIM 1 Number, SIM 2 Number).
     */
    fun getSimNumbers(context: Context): Pair<String?, String?> {
        var sim1Num: String? = null
        var sim2Num: String? = null
        try {
            val hasPhoneState = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            val hasPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_NUMBERS
                ) == PackageManager.PERMISSION_GRANTED
            } else true

            val hasReadSms = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPhoneState && !hasPhoneNumbers && !hasReadSms) {
                return Pair(null, null)
            }

            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeList = subscriptionManager.activeSubscriptionInfoList
            if (!activeList.isNullOrEmpty()) {
                for (info in activeList) {
                    val slot = info.simSlotIndex // 0-based
                    val num = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            subscriptionManager.getPhoneNumber(info.subscriptionId)
                        } catch (e: SecurityException) {
                            @Suppress("DEPRECATION")
                            info.number
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        info.number
                    }

                    if (!num.isNullOrBlank()) {
                        val cleanNum = cleanPhoneNumber(num)
                        if (cleanNum.length == 11) {
                            if (slot == 0) {
                                sim1Num = cleanNum
                            } else if (slot == 1) {
                                sim2Num = cleanNum
                            }
                        }
                    }
                }
            }

            // Fallback to TelephonyManager if SubscriptionManager returned nothing
            if (sim1Num.isNullOrBlank() && sim2Num.isNullOrBlank()) {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                @Suppress("DEPRECATION")
                val line1Num = try { telephonyManager.line1Number } catch (e: Exception) { null }
                if (!line1Num.isNullOrBlank()) {
                    val cleanNum = cleanPhoneNumber(line1Num)
                    if (cleanNum.length == 11) {
                        sim1Num = cleanNum
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return Pair(sim1Num, sim2Num)
    }

    private fun cleanPhoneNumber(number: String): String {
        var clean = number.replace(Regex("[^0-9]"), "")
        if (clean.startsWith("880") && clean.length == 13) {
            clean = clean.substring(2)
        }
        if (clean.length == 10 && clean.startsWith("1")) {
            clean = "0$clean"
        }
        return clean
    }
}

