package online.paychek.app.services.foreground

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * DEPRECATED — aggressive exact-alarm keep-alive removed (Phase 1 event-driven refactor).
 *
 * SMS ingest is Guard-1 [SmsReceiver]; presence is [online.paychek.app.services.sync.HeartbeatWorker].
 * [schedule] / [scheduleImmediate] are no-ops that cancel any leftover alarms.
 */
object ServiceKeepAliveScheduler {
    private const val TAG = "ServiceKeepAlive"
    private const val REQUEST_CODE = 7712
    private const val REQUEST_CODE_IMMEDIATE = 7713
    const val ACTION = "online.paychek.app.ACTION_KEEP_ALIVE_ALARM"
    const val ACTION_IMMEDIATE = "online.paychek.app.ACTION_KEEP_ALIVE_IMMEDIATE"

    /** No-op: cancels any previously scheduled keep-alive alarms. */
    fun schedule(context: Context) {
        cancel(context)
    }

    /** No-op: panic immediate restart disabled. */
    @Suppress("UNUSED_PARAMETER")
    fun scheduleImmediate(context: Context, delayMs: Long = 2_000L) {
        cancel(context)
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.cancel(pendingIntent(app, ACTION, REQUEST_CODE))
            alarmManager.cancel(pendingIntent(app, ACTION_IMMEDIATE, REQUEST_CODE_IMMEDIATE))
            Log.d(TAG, "Keep-alive alarms cancelled (disabled)")
        } catch (e: Exception) {
            Log.w(TAG, "Keep-alive cancel failed: ${e.message}")
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
