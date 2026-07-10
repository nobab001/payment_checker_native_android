package online.paychek.app.services.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import online.paychek.app.config.AppConfig
import online.paychek.app.services.foreground.SmsMonitorService
import online.paychek.app.services.sync.SmsPollWorker
import online.paychek.app.utils.SessionFlags

/**
 * BootReceiver — starts SMS monitoring after reboot without Keystore decrypt.
 * Uses plain [SessionFlags] so boot is not blocked by Android Keystore unlock.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Boot broadcast — Action: $action")

        val isBootEvent = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED"

        if (!isBootEvent) return

        val prefs = context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, false)
        val hasAuth = SessionFlags.hasAuth(context)

        if (!isEnabled) {
            Log.i(TAG, "SMS Service was off — skip boot start")
            return
        }

        if (!hasAuth) {
            Log.w(TAG, "No auth session flag — skip boot start")
            return
        }

        try {
            val serviceIntent = Intent(context, SmsMonitorService::class.java).apply {
                this.action = SmsMonitorService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.i(TAG, "SMS Monitor Service started on boot")
            SmsPollWorker.schedule(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Boot service start failed: ${e.message}")
        }
    }
}
