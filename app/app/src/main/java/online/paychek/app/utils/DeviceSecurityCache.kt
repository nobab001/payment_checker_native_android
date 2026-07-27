package online.paychek.app.utils

import android.content.Context
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient

/**
 * Device role + staff PIN cache used by the app lock gate.
 *
 * Owner / main device → biometric + account main PIN.
 * Staff (restricted) → no biometric; extra PIN if set, else main PIN.
 */
object DeviceSecurityCache {

    fun readDeviceRole(context: Context): String =
        SecurePreferences.decrypt(context, "pcu_device_role").ifEmpty { "pending" }

    fun isOwnerDevice(context: Context): Boolean =
        readDeviceRole(context) == "owner"

    fun applyRoleAndPin(context: Context, role: String?, deviceSpecificPin: String?) {
        val normalizedRole = role?.trim().orEmpty().ifEmpty { "pending" }
        SecurePreferences.encrypt(context, "pcu_device_role", normalizedRole)
        SecurePreferences.encrypt(
            context,
            AppConfig.KEY_IS_OWNER_DEVICE,
            if (normalizedRole == "owner") "true" else "false"
        )
        val pin = deviceSpecificPin?.trim().orEmpty()
        if (normalizedRole == "restricted" && pin.isNotEmpty()) {
            SecurePreferences.encrypt(context, AppConfig.KEY_DEVICE_SPECIFIC_PIN, pin)
        } else {
            // Owner (or staff without extra PIN): never keep a stale staff PIN.
            SecurePreferences.remove(context, AppConfig.KEY_DEVICE_SPECIFIC_PIN)
        }
    }

    /**
     * Pull latest role/PIN from server into local secure prefs.
     * Best-effort — on failure callers keep the last cached role.
     */
    suspend fun refreshFromServer(context: Context): Boolean {
        val appContext = context.applicationContext
        return try {
            val token = SecurePreferences.decrypt(appContext, AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) return isOwnerDevice(appContext)

            val authHeader = "Bearer $token"
            val statusRes = RetrofitClient.gatewayApiService.checkApprovalStatus(authHeader)
            if (statusRes.isSuccessful) {
                val body = statusRes.body()
                if (body != null) {
                    applyRoleAndPin(appContext, body.deviceRole, body.deviceSpecificPin)
                }
            }

            val configRes = RetrofitClient.gatewayApiService.getMyDeviceConfig(authHeader)
            if (configRes.isSuccessful && configRes.body()?.success == true) {
                val device = configRes.body()?.data
                if (device != null) {
                    applyRoleAndPin(appContext, device.deviceRole, device.deviceSpecificPin)
                }
            }

            isOwnerDevice(appContext)
        } catch (_: Exception) {
            isOwnerDevice(appContext)
        }
    }
}
