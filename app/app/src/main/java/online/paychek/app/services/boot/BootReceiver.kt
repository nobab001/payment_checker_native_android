package online.paychek.app.services.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import online.paychek.app.config.AppConfig
import online.paychek.app.services.foreground.SmsServiceGuard
import online.paychek.app.services.sync.NumberHeartbeatEngine
import online.paychek.app.services.sync.SmsPollWorker
import online.paychek.app.utils.SessionFlags

/**
 * BootReceiver — starts SMS monitoring after reboot without Keystore decrypt.
 * Uses plain [SessionFlags] so boot is not blocked by Android Keystore unlock.
 *
 * APK Update (MY_PACKAGE_REPLACED) handling:
 *  - Direct startForegroundService() করা যাবে না — race condition তৈরি হয়।
 *  - পুরনো process kill হওয়ার সময় onDestroy() → enqueueImmediateRecovery() এবং
 *    MY_PACKAGE_REPLACED → startForegroundService() একই সাথে চললে
 *    ForegroundServiceDidNotStartInTimeException crash হয়।
 *  - Fix: MY_PACKAGE_REPLACED-এ শুধু WorkManager delayed recovery enqueue করা হয়,
 *    direct startForegroundService() করা হয় না। নতুন process নিজেই start হবে।
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Boot broadcast — Action: $action")

        val isPackageReplaced = action == Intent.ACTION_MY_PACKAGE_REPLACED
        val isBootEvent = action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED"

        if (!isBootEvent && !isPackageReplaced) return

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

        if (isPackageReplaced) {
            // APK update: direct startForegroundService() করলে race condition crash হয়।
            // পুরনো process onDestroy → recovery এবং নতুন process start — দুটো একসাথে
            // চললে ForegroundServiceDidNotStartInTimeException throw হয়।
            // Fix: শুধু WorkManager delayed recovery enqueue করা হয় (5s+ delay)।
            // MainActivity.onResume → healIfNeeded() নতুন process চালু হলে service heal করবে।
            Log.i(TAG, "MY_PACKAGE_REPLACED — scheduling delayed WorkManager recovery (no immediate FGS start)")
            try {
                SmsServiceGuard.enqueueImmediateRecovery(context)
                SmsServiceGuard.scheduleWatchdog(context)
                SmsPollWorker.schedule(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Package replace recovery schedule failed: ${e.message}")
            }
            return
        }

        // Normal BOOT_COMPLETED / LOCKED_BOOT_COMPLETED
        try {
            SmsServiceGuard.startService(context, NumberHeartbeatEngine.TRIGGER_BOOT_COMPLETED)
            SmsServiceGuard.scheduleWatchdog(context)
            Log.i(TAG, "SMS Monitor Service started on boot (BOOT_COMPLETED heartbeat scheduled)")
            SmsPollWorker.schedule(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Boot service start failed: ${e.message}")
        }
    }
}
