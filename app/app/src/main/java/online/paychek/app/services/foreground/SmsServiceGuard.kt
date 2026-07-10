package online.paychek.app.services.foreground

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import online.paychek.app.config.AppConfig
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.services.sync.SmsPollWorker
import online.paychek.app.services.sync.SmsServiceWatchWorker
import java.util.concurrent.TimeUnit

/**
 * Keeps [SmsMonitorService] alive when the user left it ON but Android/OEM killed it.
 */
object SmsServiceGuard {
    private const val TAG = "SmsServiceGuard"
    private const val WATCH_WORK_NAME = "paychek_sms_service_watch"
    private const val RECOVER_WORK_NAME = "paychek_sms_service_recover"

    fun isServiceAlive(): Boolean = SmsMonitorService.isAlive

    fun startIfEnabled(context: Context): Boolean {
        val app = context.applicationContext
        if (!PrefsHelper.isSmsServiceActive(app)) return false
        if (isServiceAlive()) return true
        return startService(app)
    }

    fun startService(context: Context): Boolean {
        return try {
            val intent = Intent(context, SmsMonitorService::class.java).apply {
                action = SmsMonitorService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "SMS monitor service (re)started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SMS service: ${e.message}")
            false
        }
    }

    fun stopService(context: Context) {
        try {
            val intent = Intent(context, SmsMonitorService::class.java).apply {
                action = SmsMonitorService.ACTION_STOP
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send STOP to SMS service: ${e.message}")
        }
    }

    /** Periodic watchdog — restarts service if prefs say ON but process was killed. */
    fun scheduleWatchdog(context: Context) {
        if (!PrefsHelper.isSmsServiceActive(context)) return
        val request = PeriodicWorkRequestBuilder<SmsServiceWatchWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WATCH_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        SmsPollWorker.schedule(context)
        Log.d(TAG, "Service watchdog scheduled")
    }

    fun cancelWatchdog(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WATCH_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(RECOVER_WORK_NAME)
    }

    /** One-shot recovery after unexpected service death. */
    fun enqueueImmediateRecovery(context: Context) {
        if (!PrefsHelper.isSmsServiceActive(context)) return
        val request = OneTimeWorkRequestBuilder<SmsServiceWatchWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RECOVER_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        SmsPollWorker.scheduleImmediate(context)
        Log.i(TAG, "Immediate service recovery enqueued")
    }

    /**
     * Sync dashboard toggle with actual service state; restart if user left it ON.
     * @return true when the foreground service is running
     */
    fun ensureRunningAndSync(context: Context): Boolean {
        val app = context.applicationContext
        val prefOn = PrefsHelper.isSmsServiceActive(app)
        if (!prefOn) return false
        if (!isServiceAlive()) {
            startService(app)
            scheduleWatchdog(app)
        }
        return isServiceAlive()
    }
}
