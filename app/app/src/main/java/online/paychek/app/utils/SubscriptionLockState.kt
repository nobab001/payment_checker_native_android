package online.paychek.app.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.DashboardStats
import online.paychek.app.data.repository.PaymentRepository

/**
 * Shared subscription lock state for HomeScreen (all tabs).
 */
object SubscriptionLockState {
    const val STATUS_SUSPENDED = "suspended"
    const val STATUS_ACTIVE = "active"
    const val STATUS_GRACE = "grace"
    /** SharedPreferences key — bump after purchase so Home refreshes even if compose missed the flag. */
    const val KEY_BILLING_REFRESH_PING = "pcu_billing_refresh_ping"

    /**
     * Explicit status wins. Suspended → locked.
     * Active/grace → unlocked.
     * Unknown/null → fall back to isPaid.
     */
    fun isLocked(isPaid: Boolean, subscriptionStatus: String?): Boolean {
        return when (subscriptionStatus?.lowercase()?.trim()) {
            STATUS_SUSPENDED -> true
            STATUS_ACTIVE, STATUS_GRACE -> false
            else -> !isPaid
        }
    }

    fun readCachedIsPaid(context: Context): Boolean? {
        val json = online.paychek.app.data.local.prefs.PrefsHelper.getDashboardStatsCache(context)
        if (json.isBlank() || json == "[]") return null
        return try {
            GsonUtils.gson.fromJson(json, DashboardStats::class.java).isPaid
        } catch (_: Exception) {
            null
        }
    }

    fun readCachedSubscriptionStatus(context: Context): String? {
        val raw = SecurePreferences.decrypt(context, AppConfig.KEY_SUBSCRIPTION_STATUS)
        return raw.ifBlank { null }
    }

    fun writeSubscriptionStatus(context: Context, status: String?) {
        val normalized = status?.lowercase()?.trim().orEmpty()
        if (normalized.isBlank()) {
            SecurePreferences.remove(context, AppConfig.KEY_SUBSCRIPTION_STATUS)
        } else {
            SecurePreferences.encrypt(context, AppConfig.KEY_SUBSCRIPTION_STATUS, normalized)
        }
    }

    /** Notify HomeScreen (and others) to re-read lock state after billing. */
    fun notifyBillingRefresh(context: Context) {
        context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_BILLING_REFRESH_PING, System.currentTimeMillis())
            .apply()
    }

    suspend fun refresh(context: Context): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        var isPaid = readCachedIsPaid(context) ?: true
        var status = readCachedSubscriptionStatus(context)

        val ent = AccountEntitlementsStore.refresh(context)
        if (ent != null) {
            if (!ent.subscriptionStatus.isNullOrBlank()) {
                status = ent.subscriptionStatus
                writeSubscriptionStatus(context, status)
            }
            // Legacy: suspended flag on DTO
            if (ent.suspended == true) {
                status = STATUS_SUSPENDED
                writeSubscriptionStatus(context, status)
            } else if (ent.suspended == false && status == STATUS_SUSPENDED) {
                // Entitlements say not suspended but cache still suspended — clear
                status = STATUS_ACTIVE
                writeSubscriptionStatus(context, status)
            }
        }

        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
        if (token.isNotEmpty()) {
            runCatching {
                PaymentRepository().fetchDashboardStats(token, 0L).onSuccess { stats ->
                    isPaid = stats.isPaid
                    online.paychek.app.data.local.prefs.PrefsHelper.setDashboardStatsCache(
                        context,
                        GsonUtils.gson.toJson(stats)
                    )
                    // Paid account must not stay locked on a stale "suspended" cache
                    if (stats.isPaid && status == STATUS_SUSPENDED) {
                        status = STATUS_ACTIVE
                        writeSubscriptionStatus(context, status)
                    }
                    if (!stats.isPaid && status.isNullOrBlank()) {
                        // keep unknown; isLocked falls back to !isPaid
                    }
                }
            }
        }

        isPaid to status
    }
}
