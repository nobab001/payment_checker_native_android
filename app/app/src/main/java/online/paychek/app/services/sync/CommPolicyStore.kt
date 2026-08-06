package online.paychek.app.services.sync

import android.content.Context
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.AccountEntitlementsDto
import online.paychek.app.data.remote.dto.HeartbeatResponse
import online.paychek.app.utils.SecurePreferences

/**
 * PayCheck Communication Policy v1.2 — package-tiered heartbeat (HTTP only).
 * Feature permissions stay in [AccountEntitlementsStore]; this is contact intensity only.
 * Server is authoritative: heartbeat response overwrites cached interval/profile.
 */
object CommPolicyStore {

    const val PROFILE_WELCOME = "welcome"
    const val PROFILE_PERSONAL = "personal"
    const val PROFILE_PERSONAL_BUSINESS = "personal_business"
    const val PROFILE_GATEWAY = "gateway"

    fun profile(context: Context): String {
        val cached = SecurePreferences.decrypt(context, AppConfig.KEY_COMM_PROFILE)
        if (cached.isNotBlank()) return cached
        return PROFILE_PERSONAL
    }

    /** Base interval (no jitter) — for tests / diagnostics. */
    fun heartbeatBaseIntervalMs(context: Context): Long {
        val sec = SecurePreferences.decrypt(context, AppConfig.KEY_COMM_HEARTBEAT_SEC).toIntOrNull()
        if (sec != null && sec in 30..7200) {
            return sec * 1000L
        }
        return when (profile(context)) {
            PROFILE_WELCOME, PROFILE_GATEWAY -> AppConfig.HEARTBEAT_INTERVAL_MS
            PROFILE_PERSONAL_BUSINESS -> AppConfig.HEARTBEAT_PERSONAL_BUSINESS_MS
            else -> AppConfig.HEARTBEAT_SPARSE_MS
        }
    }

    fun heartbeatIntervalMs(context: Context): Long {
        val baseMs = heartbeatBaseIntervalMs(context)
        val jitter = AppConfig.HEARTBEAT_JITTER_MS
        val delta = ((Math.random() * 2 - 1) * jitter).toLong()
        return (baseMs + delta).coerceAtLeast(30_000L)
    }

    fun applyEntitlements(context: Context, ent: AccountEntitlementsDto) {
        ent.commProfile?.takeIf { it.isNotBlank() }?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_PROFILE, it)
        }
        ent.heartbeatSec?.takeIf { it in 30..7200 }?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_HEARTBEAT_SEC, it.toString())
        }
        if (ent.commProfile.isNullOrBlank()) {
            val inferred = if (ent.hasWebsite) PROFILE_GATEWAY else PROFILE_PERSONAL
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_PROFILE, inferred)
            SecurePreferences.encrypt(
                context,
                AppConfig.KEY_COMM_HEARTBEAT_SEC,
                if (ent.hasWebsite) "900" else "3600"
            )
        }
    }

    fun applyHeartbeatResponse(context: Context, body: HeartbeatResponse) {
        body.profile?.takeIf { it.isNotBlank() }?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_PROFILE, it)
        }
        body.heartbeatSec?.takeIf { it in 30..7200 }?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_HEARTBEAT_SEC, it.toString())
        }
    }
}
