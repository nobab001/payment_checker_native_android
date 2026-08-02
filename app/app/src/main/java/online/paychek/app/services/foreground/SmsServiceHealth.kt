package online.paychek.app.services.foreground

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Real OS-level health for [SmsMonitorService].
 *
 * Heal/watchdog must use [isServiceRunning] (ActivityManager), not notification
 * presence — notification OFF must not force restart loops. SMS ingest does not
 * depend on the FGS notification.
 */
object SmsServiceHealth {
    private const val TAG = "SmsServiceHealth"

    /** True when [SmsMonitorService] is registered with ActivityManager. */
    fun isServiceRunning(context: Context): Boolean = isRunningInActivityManager(context)

    fun isRunningInActivityManager(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return am.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == SmsMonitorService::class.java.name
        }
    }

    fun hasForegroundNotification(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.activeNotifications.any { it.id == SmsMonitorService.NOTIFICATION_ID }
    }

    /**
     * FGS “healthy for UX” — notification optional. Prefer [isServiceRunning] for heal.
     */
    fun isHealthy(context: Context): Boolean {
        val inAm = isRunningInActivityManager(context)
        val hasNotif = hasForegroundNotification(context)
        val flag = SmsMonitorService.isAlive

        if (!inAm) {
            if (flag) {
                Log.w(TAG, "Correcting stale isAlive — service not in ActivityManager")
                SmsMonitorService.isAlive = false
            }
            return false
        }

        // Running in AM is enough for heal decisions; notification is enhancement only.
        val healthy = true
        if (!hasNotif && !flag) {
            Log.d(TAG, "Service in AM without notification — still treated as running for heal")
        }
        return healthy
    }

    /** Keep in-memory [SmsMonitorService.isAlive] aligned with ActivityManager. */
    fun syncAliveFlag(context: Context) {
        val running = isRunningInActivityManager(context)
        when {
            running && !SmsMonitorService.isAlive -> {
                Log.d(TAG, "Syncing isAlive=true — service running in ActivityManager")
                SmsMonitorService.isAlive = true
            }
            !running && SmsMonitorService.isAlive -> {
                Log.w(TAG, "Correcting stale isAlive — service not in ActivityManager")
                SmsMonitorService.isAlive = false
            }
        }
    }
}
