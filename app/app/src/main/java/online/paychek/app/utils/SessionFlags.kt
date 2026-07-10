package online.paychek.app.utils

import android.content.Context
import online.paychek.app.config.AppConfig

/**
 * Plain SharedPreferences session mirrors — fast after reboot.
 * Android Keystore decrypt can take seconds on cold boot; these flags avoid
 * blocking MainActivity / BootReceiver / Navigation on the main thread.
 */
object SessionFlags {
    private const val KEY_HAS_AUTH = "pcu_fast_has_auth"
    private const val KEY_PROFILE_COMPLETE = "pcu_fast_profile_complete"
    private const val KEY_USER_ROLE = "pcu_fast_user_role"
    private const val KEY_CONTACT = "pcu_fast_contact"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)

    fun hasAuth(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_AUTH, false)

    fun isProfileComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROFILE_COMPLETE, true)

    fun userRole(context: Context): String =
        prefs(context).getString(KEY_USER_ROLE, "") ?: ""

    fun contact(context: Context): String =
        prefs(context).getString(KEY_CONTACT, "") ?: ""

    fun setHasAuth(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAS_AUTH, value).apply()
    }

    fun setProfileComplete(context: Context, complete: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROFILE_COMPLETE, complete).apply()
    }

    fun setUserRole(context: Context, role: String) {
        prefs(context).edit().putString(KEY_USER_ROLE, role).apply()
    }

    fun setContact(context: Context, contact: String) {
        prefs(context).edit().putString(KEY_CONTACT, contact).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_HAS_AUTH, false)
            .putBoolean(KEY_PROFILE_COMPLETE, true)
            .putString(KEY_USER_ROLE, "")
            .putString(KEY_CONTACT, "")
            .apply()
    }

    /**
     * One-time migrate from Keystore-backed values into fast flags (background only).
     */
    fun migrateFromSecureStore(context: Context) {
        try {
            val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
            val profile = SecurePreferences.decrypt(context, "pcu_profile_complete")
            val role = SecurePreferences.decrypt(context, "pcu_user_role")
            val contact = SecurePreferences.decrypt(context, "pcu_contact")
            prefs(context).edit()
                .putBoolean(KEY_HAS_AUTH, token.isNotEmpty())
                .putBoolean(KEY_PROFILE_COMPLETE, profile != "false")
                .putString(KEY_USER_ROLE, role)
                .putString(KEY_CONTACT, contact)
                .apply()
        } catch (_: Exception) {
            // Ignore — flags stay as-is
        }
    }
}
