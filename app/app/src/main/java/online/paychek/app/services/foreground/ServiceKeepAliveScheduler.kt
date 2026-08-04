package online.paychek.app.services.foreground

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.utils.OemBackgroundHelper

/**
 * AlarmManager backup — WorkManager 15min minimum-এর চেয়ে দ্রুত OEM kill recover করে।
 * Aggressive OEM-এ ~2 মিনিট; অন্যথায় ~5 মিনিট। Task swipe-এ immediate (+2s) alarm।
 */
object ServiceKeepAliveScheduler {
    private const val TAG = "ServiceKeepAlive"
    private const val REQUEST_CODE = 7712
    private const val REQUEST_CODE_IMMEDIATE = 7713
    const val ACTION = "online.paychek.app.ACTION_KEEP_ALIVE_ALARM"
    const val ACTION_IMMEDIATE = "online.paychek.app.ACTION_KEEP_ALIVE_IMMEDIATE"

    fun intervalMs(): Long = if (OemBackgroundHelper.isAggressiveOem()) {
        2L * 60L * 1000L
    } else {
        5L * 60L * 1000L
    }

    fun schedule(context: Context) {
        if (!PrefsHelper.isSmsServiceActive(context)) {
            cancel(context)
            return
        }
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(app, ACTION, REQUEST_CODE)
        val triggerAt = SystemClock.elapsedRealtime() + intervalMs()
        scheduleAt(alarmManager, triggerAt, pending, "periodic")
    }

    /** Fire in ~2s after task swipe / unexpected destroy — faster than WorkManager. */
    fun scheduleImmediate(context: Context, delayMs: Long = 2_000L) {
        if (!PrefsHelper.isSmsServiceActive(context)) return
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(app, ACTION_IMMEDIATE, REQUEST_CODE_IMMEDIATE)
        val triggerAt = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(500L)
        scheduleAt(alarmManager, triggerAt, pending, "immediate")
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(app, ACTION, REQUEST_CODE))
        alarmManager.cancel(pendingIntent(app, ACTION_IMMEDIATE, REQUEST_CODE_IMMEDIATE))
    }

    private fun scheduleAt(
        alarmManager: AlarmManager,
        triggerAt: Long,
        pending: PendingIntent,
        label: String
    ) {
        try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            if (canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending
                )
            }
            Log.d(TAG, "Keep-alive $label scheduled")
        } catch (e: Exception) {
            Log.w(TAG, "Exact alarm failed ($label), fallback: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pending
                    )
                } else {
                    @Suppress("DEPRECATION")
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Keep-alive schedule failed ($label): ${e2.message}")
            }
        }
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, KeepAliveAlarmReceiver::class.java).apply {
            this.action = action
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
