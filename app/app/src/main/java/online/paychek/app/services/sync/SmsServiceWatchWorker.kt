package online.paychek.app.services.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.services.foreground.SmsServiceGuard

/**
 * Background watchdog — if user left SMS service ON but OEM killed the foreground service,
 * start it (check-only) and keep Guard-2 inbox polling scheduled.
 */
class SmsServiceWatchWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!PrefsHelper.isSmsServiceActive(app)) {
            SmsServiceGuard.cancelWatchdog(app)
            return Result.success()
        }

        if (SmsServiceGuard.isServiceRunning(app)) {
            SmsServiceGuard.scheduleWatchdog(app)
            SmsPollWorker.scheduleImmediate(app)
        } else {
            Log.w(TAG, "SMS service not running while prefs ON — starting")
            SmsServiceGuard.healIfNeeded(app)
        }
        SmsPollWorker.schedule(app)

        try {
            NumberHeartbeatEngine.sendHeartbeatBlocking(app)
        } catch (e: Exception) {
            Log.w(TAG, "Watchdog heartbeat failed: ${e.message}")
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "SmsServiceWatch"
    }
}
