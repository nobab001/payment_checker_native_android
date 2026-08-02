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
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.services.sync.SmsPollWorker
import online.paychek.app.services.sync.SmsServiceWatchWorker
import java.util.concurrent.TimeUnit

/**
 * Keeps [SmsMonitorService] alive when the user left it ON but Android/OEM killed it.
 * Check-only: if already running, do not stop/restart.
 */
object SmsServiceGuard {
    private const val TAG = "SmsServiceGuard"
    private const val WATCH_WORK_NAME = "paychek_sms_service_watch"
    private const val RECOVER_WORK_NAME = "paychek_sms_service_recover"

    /** Passed to [SmsMonitorService] for v2.5 presence boot recovery heartbeat. */
    const val EXTRA_PRESENCE_TRIGGER = "extra_presence_trigger"

    private fun watchdogIntervalMinutes(): Long = 15L // WorkManager periodic minimum

    fun isServiceAlive(): Boolean {
        return SmsMonitorService.isAlive
    }

    fun isServiceHealthy(context: Context): Boolean {
        SmsServiceHealth.syncAliveFlag(context.applicationContext)
        return SmsServiceHealth.isHealthy(context.applicationContext)
    }

    /** True when the foreground SMS monitor process is actually running. */
    fun isServiceRunning(context: Context): Boolean {
        SmsServiceHealth.syncAliveFlag(context.applicationContext)
        return SmsServiceHealth.isServiceRunning(context.applicationContext)
    }

    /**
     * When user left SMS ON but the foreground service is not running, start it.
     * If already running, do nothing except schedule watchdog + immediate inbox poll.
     */
    @Volatile private var lastHealAtMs: Long = 0L
    private const val HEAL_COOLDOWN_MS = 10_000L

    fun healIfNeeded(context: Context): Boolean {
        val app = context.applicationContext
        if (!PrefsHelper.isSmsServiceActive(app)) return false

        SmsServiceHealth.syncAliveFlag(app)
        if (isServiceRunning(app)) {
            scheduleWatchdog(app)
            // FGS alive ≠ every SMS was received — always arm Guard-2 recovery.
            SmsPollWorker.scheduleImmediate(app)
            return true
        }

        val now = System.currentTimeMillis()
        if (now - lastHealAtMs < HEAL_COOLDOWN_MS) {
            Log.i(TAG, "healIfNeeded skipped — cooldown active (${now - lastHealAtMs}ms since last start)")
            return false
        }
        lastHealAtMs = now
        Log.w(TAG, "SMS service not running while prefs ON — starting")
        startService(app, online.paychek.app.services.sync.NumberHeartbeatEngine.TRIGGER_BOOT_COMPLETED)
        scheduleWatchdog(app)
        SmsPollWorker.scheduleImmediate(app)
        return true
    }

    fun startIfEnabled(context: Context): Boolean {
        val app = context.applicationContext
        if (!PrefsHelper.isSmsServiceActive(app)) return false
        return healIfNeeded(app)
    }

    fun startService(context: Context, presenceTrigger: String? = null): Boolean {
        return try {
            val intent = Intent(context, SmsMonitorService::class.java).apply {
                action = SmsMonitorService.ACTION_START
                presenceTrigger?.let { putExtra(EXTRA_PRESENCE_TRIGGER, it) }
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

    fun scheduleWatchdog(context: Context) {
        if (!PrefsHelper.isSmsServiceActive(context)) return
        val minutes = watchdogIntervalMinutes()
        val request = PeriodicWorkRequestBuilder<SmsServiceWatchWorker>(minutes, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WATCH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        SmsPollWorker.schedule(context)
        ServiceKeepAliveScheduler.schedule(context)
        Log.d(TAG, "Service watchdog scheduled (${minutes}min) + keep-alive alarm")
    }

    fun cancelWatchdog(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WATCH_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(RECOVER_WORK_NAME)
        ServiceKeepAliveScheduler.cancel(context)
    }

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

    fun ensureRunningAndSync(context: Context): Boolean {
        val app = context.applicationContext
        val prefOn = PrefsHelper.isSmsServiceActive(app)
        if (!prefOn) return false
        healIfNeeded(app)
        return isServiceRunning(app)
    }
}
