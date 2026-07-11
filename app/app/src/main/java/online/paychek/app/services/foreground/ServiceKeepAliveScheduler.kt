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
 * Samsung/Vivo-তে foreground service মরলে 3–5 মিনিটে পুনরায় চালু করার চেষ্টা।
 */
object ServiceKeepAliveScheduler {
    private const val TAG = "ServiceKeepAlive"
    private const val REQUEST_CODE = 7712
    private const val ACTION = "online.paychek.app.ACTION_KEEP_ALIVE_ALARM"

    fun intervalMs(): Long = if (OemBackgroundHelper.isAggressiveOem()) {
        3L * 60L * 1000L
    } else {
        8L * 60L * 1000L
    }

    fun schedule(context: Context) {
        if (!PrefsHelper.isSmsServiceActive(context)) {
            cancel(context)
            return
        }
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(app)
        val triggerAt = SystemClock.elapsedRealtime() + intervalMs()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
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
            Log.d(TAG, "Keep-alive alarm scheduled in ${intervalMs() / 1000}s")
        } catch (e: Exception) {
            Log.w(TAG, "Exact alarm failed, fallback: ${e.message}")
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Keep-alive schedule failed: ${e2.message}")
            }
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(app))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, KeepAliveAlarmReceiver::class.java).apply {
            action = ACTION
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
