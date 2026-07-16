package online.paychek.app.services.sync

import android.content.Context
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.AccountEntitlementsDto
import online.paychek.app.data.remote.dto.HeartbeatResponse
import online.paychek.app.utils.SecurePreferences

/**
 * PayCheck Communication Policy v1.0 — package-tiered heartbeat / socket usage.
 * Feature permissions stay in [AccountEntitlementsStore]; this is contact intensity only.
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

    fun useSocket(context: Context): Boolean {
        // Comm Policy v1.1: all packages are HTTP-heartbeat only (no Socket.IO presence).
        return false
    }

    fun heartbeatIntervalMs(context: Context): Long {
        val sec = SecurePreferences.decrypt(context, AppConfig.KEY_COMM_HEARTBEAT_SEC).toIntOrNull()
        val baseMs = if (sec != null && sec in 30..3600) {
            sec * 1000L
        } else {
            when (profile(context)) {
                PROFILE_WELCOME, PROFILE_GATEWAY -> AppConfig.HEARTBEAT_INTERVAL_MS
                PROFILE_PERSONAL_BUSINESS -> 900_000L // 15 min
                else -> AppConfig.HEARTBEAT_SPARSE_MS
            }
        }
        // ±jitter to avoid thundering herd (v2.5)
        val jitter = AppConfig.HEARTBEAT_JITTER_MS
        val delta = ((Math.random() * 2 - 1) * jitter).toLong()
        return (baseMs + delta).coerceAtLeast(30_000L)
    }

    fun applyEntitlements(context: Context, ent: AccountEntitlementsDto) {
        ent.commProfile?.takeIf { it.isNotBlank() }?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_PROFILE, it)
        }
        ent.heartbeatSec?.takeIf { it in 30..3600 }?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_HEARTBEAT_SEC, it.toString())
        }
        // Force HTTP-only — ignore legacy use_socket=true from cache/server
        SecurePreferences.encrypt(context, AppConfig.KEY_COMM_USE_SOCKET, "0")
        if (ent.commProfile.isNullOrBlank()) {
            val inferred = if (ent.hasWebsite) PROFILE_GATEWAY else PROFILE_PERSONAL
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_PROFILE, inferred)
            SecurePreferences.encrypt(
                context,
                AppConfig.KEY_COMM_HEARTBEAT_SEC,
                if (ent.hasWebsite) "600" else "1800"
            )
        }
    }

    fun applyHeartbeatResponse(context: Context, body: HeartbeatResponse) {
        body.profile?.takeIf { it.isNotBlank() }?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_PROFILE, it)
        }
        body.heartbeatSec?.takeIf { it in 30..3600 }?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_HEARTBEAT_SEC, it.toString())
        }
        SecurePreferences.encrypt(context, AppConfig.KEY_COMM_USE_SOCKET, "0")
    }
}
