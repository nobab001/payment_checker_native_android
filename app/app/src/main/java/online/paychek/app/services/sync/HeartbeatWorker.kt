package online.paychek.app.services.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import online.paychek.app.data.local.prefs.PrefsHelper
import java.util.concurrent.TimeUnit

/**
 * Primary HTTP heartbeat clock (Comm Policy).
 *
 * SMS ingest does NOT depend on this worker — only device "online / monitoring ON"
 * presence toward the server. Interval comes from [CommPolicyStore] (15 / 30 / 60 min).
 */
class HeartbeatWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!PrefsHelper.isSmsServiceActive(app)) {
            cancel(app)
            return Result.success()
        }
        return try {
            NumberHeartbeatEngine.sendHeartbeatBlocking(app)
            // Interval may have changed via server response — keep periodic work aligned.
            schedule(app)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "HeartbeatWorker failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "HeartbeatWorker"
        const val WORK_NAME = "paychek_http_heartbeat"

        /** WorkManager periodic minimum is 15 minutes. */
        fun intervalMinutes(context: Context): Long {
            val ms = CommPolicyStore.heartbeatBaseIntervalMs(context)
            return (ms / 60_000L).coerceAtLeast(15L)
        }

        fun schedule(context: Context) {
            if (!PrefsHelper.isSmsServiceActive(context)) {
                cancel(context)
                return
            }
            val minutes = intervalMinutes(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.i(TAG, "HeartbeatWorker scheduled — every ${minutes}min (UPDATE)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "HeartbeatWorker cancelled")
        }
    }
}
