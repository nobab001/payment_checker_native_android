package online.paychek.app.services.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.services.foreground.SmsServiceGuard

/**
 * Background watchdog — if user left SMS service ON but OEM killed the foreground service,
 * attempt restart and keep Guard-2 inbox polling scheduled.
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

        if (!SmsServiceGuard.isServiceAlive()) {
            Log.w(TAG, "SMS service dead while prefs ON — attempting restart")
            SmsServiceGuard.startService(app)
        }
        SmsPollWorker.schedule(app)

        // Doze-safe presence backstop: keep the device's numbers ONLINE even if the
        // in-service heartbeat loop was suspended while idle. Runs inside the worker
        // coroutine so the POST completes before the job ends.
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
