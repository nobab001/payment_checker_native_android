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
        val raw = SecurePreferences.decrypt(context, AppConfig.KEY_COMM_USE_SOCKET)
        if (raw == "1" || raw.equals("true", ignoreCase = true)) return true
        if (raw == "0" || raw.equals("false", ignoreCase = true)) return false
        return when (profile(context)) {
            PROFILE_WELCOME, PROFILE_GATEWAY -> true
            else -> false
        }
    }

    fun heartbeatIntervalMs(context: Context): Long {
        val sec = SecurePreferences.decrypt(context, AppConfig.KEY_COMM_HEARTBEAT_SEC).toIntOrNull()
        if (sec != null && sec in 30..3600) return sec * 1000L
        return when (profile(context)) {
            PROFILE_WELCOME, PROFILE_GATEWAY -> AppConfig.HEARTBEAT_INTERVAL_MS
            else -> AppConfig.HEARTBEAT_SPARSE_MS
        }
    }

    fun applyEntitlements(context: Context, ent: AccountEntitlementsDto) {
        ent.commProfile?.takeIf { it.isNotBlank() }?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_PROFILE, it)
        }
        ent.heartbeatSec?.takeIf { it in 30..3600 }?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_HEARTBEAT_SEC, it.toString())
        }
        ent.useSocket?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_USE_SOCKET, if (it) "1" else "0")
        }
        if (ent.commProfile.isNullOrBlank() && ent.useSocket == null) {
            // Infer from website permission until server sends explicit profile
            val inferred = if (ent.hasWebsite) PROFILE_GATEWAY else PROFILE_PERSONAL
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_PROFILE, inferred)
            SecurePreferences.encrypt(
                context,
                AppConfig.KEY_COMM_USE_SOCKET,
                if (ent.hasWebsite) "1" else "0"
            )
            SecurePreferences.encrypt(
                context,
                AppConfig.KEY_COMM_HEARTBEAT_SEC,
                if (ent.hasWebsite) "120" else "600"
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
        body.useSocket?.let {
            SecurePreferences.encrypt(context, AppConfig.KEY_COMM_USE_SOCKET, if (it) "1" else "0")
        }
    }
}
