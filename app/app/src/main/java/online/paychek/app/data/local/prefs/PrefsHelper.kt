package online.paychek.app.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import online.paychek.app.config.AppConfig

/**
 * PrefsHelper — Centralized SharedPreferences Wrapper
 * ====================================================
 * Single access point for all SharedPreferences reads/writes in the app.
 *
 * Rules:
 *  - All key constants live in AppConfig, not here.
 *  - EncryptedSharedPreferences (SecurePreferences) handles the HMAC secret
 *    and auth token — those are NOT accessed through PrefsHelper.
 *  - This class wraps the plain (non-encrypted) preferences only.
 *  - All methods are synchronous — call from IO dispatcher if needed.
 *
 * Encryption boundary:
 *  Sensitive (token, secretKey) -> SecurePreferences (EncryptedSharedPreferences)
 *  Non-sensitive (flags, cache) -> PrefsHelper (plain SharedPreferences)
 */
object PrefsHelper {

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)

    // -------------------------------------------------------------------------
    // Service state
    // -------------------------------------------------------------------------

    fun isSmsServiceActive(context: Context): Boolean =
        prefs(context).getBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, false)

    fun setSmsServiceActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, active).apply()
    }

    // -------------------------------------------------------------------------
    // SIM slot enablement
    // -------------------------------------------------------------------------

    fun isSim1Enabled(context: Context): Boolean =
        prefs(context).getBoolean(AppConfig.KEY_SIM1_ENABLED, true)

    fun isSim2Enabled(context: Context): Boolean =
        prefs(context).getBoolean(AppConfig.KEY_SIM2_ENABLED, true)

    fun setSim1Enabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(AppConfig.KEY_SIM1_ENABLED, enabled).apply()
    }

    fun setSim2Enabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(AppConfig.KEY_SIM2_ENABLED, enabled).apply()
    }

    // -------------------------------------------------------------------------
    // Gateway method cache (JSON string)
    // -------------------------------------------------------------------------

    fun getGatewayMethodsCache(context: Context): String {
        val decrypted = online.paychek.app.utils.SecurePreferences.decrypt(context, AppConfig.KEY_GATEWAY_METHODS_CACHE)
        return decrypted.ifEmpty { "[]" }
    }

    fun setGatewayMethodsCache(context: Context, json: String) {
        online.paychek.app.utils.SecurePreferences.encrypt(context, AppConfig.KEY_GATEWAY_METHODS_CACHE, json)
    }

    fun getSmsTemplatesCache(context: Context): String {
        val decrypted = online.paychek.app.utils.SecurePreferences.decrypt(context, AppConfig.KEY_SMS_TEMPLATES_CACHE)
        return decrypted.ifEmpty { "[]" }
    }

    fun setSmsTemplatesCache(context: Context, json: String) {
        online.paychek.app.utils.SecurePreferences.encrypt(context, AppConfig.KEY_SMS_TEMPLATES_CACHE, json)
    }

    fun getGatewayMethodsLastSync(context: Context): Long {
        return prefs(context).getLong("gateway_methods_last_sync_v2", 0L)
    }

    fun setGatewayMethodsLastSync(context: Context, timestamp: Long) {
        prefs(context).edit().putLong("gateway_methods_last_sync_v2", timestamp).apply()
    }

    // -------------------------------------------------------------------------
    // Session PIN flag
    // -------------------------------------------------------------------------

    fun isPinVerified(context: Context): Boolean =
        prefs(context).getBoolean(AppConfig.KEY_PIN_VERIFIED, false)

    fun setPinVerified(context: Context, verified: Boolean) {
        prefs(context).edit().putBoolean(AppConfig.KEY_PIN_VERIFIED, verified).apply()
    }

    // -------------------------------------------------------------------------
    // Device ID (hardware-derived, non-sensitive)
    // -------------------------------------------------------------------------

    fun getDeviceId(context: Context): String? =
        prefs(context).getString(AppConfig.KEY_DEVICE_ID, null)

    fun setDeviceId(context: Context, deviceId: String) {
        prefs(context).edit().putString(AppConfig.KEY_DEVICE_ID, deviceId).apply()
    }

    // -------------------------------------------------------------------------
    // WorkManager tracking — last successful queue flush timestamp
    // Lets the UI show "last synced: X minutes ago" without querying Room.
    // -------------------------------------------------------------------------

    private const val KEY_LAST_WORKER_SYNC_MS = "pcu_last_worker_sync_ms"

    fun getLastWorkerSyncMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_WORKER_SYNC_MS, 0L)

    fun setLastWorkerSyncMs(context: Context, timestampMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_WORKER_SYNC_MS, timestampMs).apply()
    }

    // -------------------------------------------------------------------------
    // Convenience: clear session-only flags on logout
    // (permanent device settings like SIM config are retained)
    // -------------------------------------------------------------------------

    fun clearSessionData(context: Context) {
        prefs(context).edit()
            .remove(AppConfig.KEY_PIN_VERIFIED)
            .remove(AppConfig.KEY_GATEWAY_METHODS_CACHE)
            .remove(AppConfig.KEY_SMS_TEMPLATES_CACHE)
            .remove(KEY_LAST_WORKER_SYNC_MS)
            .apply()
    }
}
