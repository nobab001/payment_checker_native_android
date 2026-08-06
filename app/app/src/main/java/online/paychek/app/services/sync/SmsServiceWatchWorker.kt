package online.paychek.app.services.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.services.foreground.SmsServiceGuard

/**
 * Lightweight recovery tick — ensures Guard-2 poll + HeartbeatWorker stay scheduled.
 * Does NOT heal/restart the Foreground Service (SMS ingest is event-driven via SmsReceiver).
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

        SmsPollWorker.schedule(app)
        HeartbeatWorker.schedule(app)
        Log.d(TAG, "Recovery tick — poll + heartbeat workers ensured (no FGS heal)")
        return Result.success()
    }

    companion object {
        private const val TAG = "SmsServiceWatch"
    }
}
