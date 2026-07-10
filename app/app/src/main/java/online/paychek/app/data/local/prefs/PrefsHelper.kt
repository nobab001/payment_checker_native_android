package online.paychek.app.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import online.paychek.app.config.AppConfig
import online.paychek.app.utils.SecurePreferences

/**
 * PrefsHelper — plain SharedPreferences for non-sensitive flags and caches.
 *
 * Gateway methods + SMS templates are NOT secrets — they must live in plain prefs.
 * Storing large JSON via Android Keystore AES previously failed silently on many devices.
 */
object PrefsHelper {

    private const val TAG = "PrefsHelper"
    private const val KEY_CACHE_MIGRATED_V1 = "pcu_cache_plain_migrated_v1"

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
    // Gateway method + SMS template cache (plain JSON)
    // -------------------------------------------------------------------------

    /**
     * One-time migrate Keystore-encrypted caches → plain prefs, then drop secure copies.
     */
    fun migrateCachesFromSecureStoreIfNeeded(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_CACHE_MIGRATED_V1, false)) return

        try {
            val methods = SecurePreferences.decrypt(context, AppConfig.KEY_GATEWAY_METHODS_CACHE)
            val templates = SecurePreferences.decrypt(context, AppConfig.KEY_SMS_TEMPLATES_CACHE)
            val editor = p.edit()
            if (methods.isNotBlank() && p.getString(AppConfig.KEY_GATEWAY_METHODS_CACHE, null).isNullOrBlank()) {
                editor.putString(AppConfig.KEY_GATEWAY_METHODS_CACHE, methods)
            }
            if (templates.isNotBlank() && p.getString(AppConfig.KEY_SMS_TEMPLATES_CACHE, null).isNullOrBlank()) {
                editor.putString(AppConfig.KEY_SMS_TEMPLATES_CACHE, templates)
            }
            editor.putBoolean(KEY_CACHE_MIGRATED_V1, true).commit()

            SecurePreferences.remove(context, AppConfig.KEY_GATEWAY_METHODS_CACHE)
            SecurePreferences.remove(context, AppConfig.KEY_SMS_TEMPLATES_CACHE)
            Log.i(TAG, "Migrated gateway/template caches to plain prefs")
        } catch (e: Exception) {
            Log.w(TAG, "Cache migration failed: ${e.message}")
            p.edit().putBoolean(KEY_CACHE_MIGRATED_V1, true).apply()
        }
    }

    fun getGatewayMethodsCache(context: Context): String {
        migrateCachesFromSecureStoreIfNeeded(context)
        val raw = prefs(context).getString(AppConfig.KEY_GATEWAY_METHODS_CACHE, null)
        return if (raw.isNullOrBlank()) "[]" else raw
    }

    /** @return true if value was persisted and verified */
    fun setGatewayMethodsCache(context: Context, json: String): Boolean {
        migrateCachesFromSecureStoreIfNeeded(context)
        return writeAndVerify(context, AppConfig.KEY_GATEWAY_METHODS_CACHE, json)
    }

    fun getSmsTemplatesCache(context: Context): String {
        migrateCachesFromSecureStoreIfNeeded(context)
        val raw = prefs(context).getString(AppConfig.KEY_SMS_TEMPLATES_CACHE, null)
        return if (raw.isNullOrBlank()) "[]" else raw
    }

    /** @return true if value was persisted and verified */
    fun setSmsTemplatesCache(context: Context, json: String): Boolean {
        migrateCachesFromSecureStoreIfNeeded(context)
        return writeAndVerify(context, AppConfig.KEY_SMS_TEMPLATES_CACHE, json)
    }

    fun hasGatewayMethodsCache(context: Context): Boolean {
        val raw = getGatewayMethodsCache(context)
        return raw.isNotBlank() && raw != "[]"
    }

    fun hasSmsTemplatesCache(context: Context): Boolean {
        val raw = getSmsTemplatesCache(context)
        return raw.isNotBlank() && raw != "[]"
    }

    private fun writeAndVerify(context: Context, key: String, value: String): Boolean {
        return try {
            val ok = prefs(context).edit().putString(key, value).commit()
            if (!ok) {
                Log.e(TAG, "commit() failed for $key")
                return false
            }
            val readBack = prefs(context).getString(key, null)
            val verified = readBack == value
            if (!verified) {
                Log.e(TAG, "verify failed for $key (len=${value.length})")
            }
            verified
        } catch (e: Exception) {
            Log.e(TAG, "write failed for $key: ${e.message}", e)
            false
        }
    }

    fun getGatewayMethodsLastSync(context: Context): Long {
        return prefs(context).getLong("gateway_methods_last_sync_v2", 0L)
    }

    fun setGatewayMethodsLastSync(context: Context, timestamp: Long) {
        prefs(context).edit().putLong("gateway_methods_last_sync_v2", timestamp).apply()
    }

    fun getHistoryLastSync(context: Context): Long {
        return prefs(context).getLong("sms_history_last_sync_v1", 0L)
    }

    fun setHistoryLastSync(context: Context, timestamp: Long) {
        prefs(context).edit().putLong("sms_history_last_sync_v1", timestamp).apply()
    }

    fun getTransactionHistoryBundle(context: Context): String =
        prefs(context).getString("txn_history_bundle_v1", "").orEmpty()

    fun setTransactionHistoryBundle(context: Context, json: String) {
        prefs(context).edit().putString("txn_history_bundle_v1", json).apply()
    }

    fun getDashboardStatsCache(context: Context): String =
        prefs(context).getString(AppConfig.KEY_DASHBOARD_STATS_CACHE, "").orEmpty()

    fun setDashboardStatsCache(context: Context, json: String) {
        prefs(context).edit().putString(AppConfig.KEY_DASHBOARD_STATS_CACHE, json).apply()
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
    // Device ID
    // -------------------------------------------------------------------------

    fun getDeviceId(context: Context): String? =
        prefs(context).getString(AppConfig.KEY_DEVICE_ID, null)

    fun setDeviceId(context: Context, deviceId: String) {
        prefs(context).edit().putString(AppConfig.KEY_DEVICE_ID, deviceId).apply()
    }

    private const val KEY_LAST_WORKER_SYNC_MS = "pcu_last_worker_sync_ms"

    fun getLastWorkerSyncMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_WORKER_SYNC_MS, 0L)

    fun setLastWorkerSyncMs(context: Context, timestampMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_WORKER_SYNC_MS, timestampMs).apply()
    }

    fun clearSessionData(context: Context) {
        prefs(context).edit()
            .remove(AppConfig.KEY_PIN_VERIFIED)
            .remove(AppConfig.KEY_GATEWAY_METHODS_CACHE)
            .remove(AppConfig.KEY_SMS_TEMPLATES_CACHE)
            .remove(KEY_LAST_WORKER_SYNC_MS)
            .remove("sms_history_last_sync_v1")
            .remove("txn_history_bundle_v1")
            .remove(AppConfig.KEY_DASHBOARD_STATS_CACHE)
            .remove("gateway_methods_last_sync_v2")
            .apply()
        // Legacy Keystore copies (if any)
        SecurePreferences.remove(context, AppConfig.KEY_GATEWAY_METHODS_CACHE)
        SecurePreferences.remove(context, AppConfig.KEY_SMS_TEMPLATES_CACHE)
    }
}
