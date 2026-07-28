package online.paychek.app.services.foreground

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Real OS-level health for [SmsMonitorService].
 *
 * [SmsMonitorService.isAlive] alone is not enough — OEM kills can leave prefs ON
 * with no foreground notification while the in-memory flag is stale.
 */
object SmsServiceHealth {
    private const val TAG = "SmsServiceHealth"

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
     * @return true when the foreground SMS monitor is actually running with a visible notification.
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

        // Service process exists — require notification (user-visible proof) or fresh start flag.
        val healthy = hasNotif || flag
        if (!healthy) {
            Log.w(TAG, "Zombie service in AM without notification — treating as unhealthy")
        }
        return healthy
    }

    /** Clear stale in-memory flag when OS shows the service is gone. */
    fun syncAliveFlag(context: Context) {
        if (!isRunningInActivityManager(context) && SmsMonitorService.isAlive) {
            SmsMonitorService.isAlive = false
        }
    }
}
