package online.paychek.app.services.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import online.paychek.app.config.AppConfig
import online.paychek.app.data.local.AppDatabase
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.domain.usecase.sync.FlushOfflineQueueUseCase
import online.paychek.app.services.connectivity.ConnectivityService
import online.paychek.app.services.sms.SmsReceiver
import java.util.concurrent.TimeUnit

/**
 * SyncWorker — WorkManager Fallback Offline Queue Flush
 * ======================================================
 *
 * ROLE: Recovery-only fallback.
 *
 * The PRIMARY sync mechanism is the foreground service (SmsMonitorService)
 * which syncs in real-time when the network becomes available.
 *
 * SyncWorker handles the case where:
 *  - The device was offline for an extended period AND
 *  - The foreground service was killed by the OS AND
 *  - No manual trigger happened
 *
 * In that scenario, WorkManager wakes up every 15 minutes and flushes
 * any items that are still pending in the Room queue.
 *
 * Design constraints:
 *  - Does NOT duplicate the foreground service sync logic.
 *  - Delegates entirely to SmsReceiver.syncPendingQueue().
 *  - Only runs when INTERNET is available (Constraints.requiresNetwork).
 *  - Uses KEEP policy — only one periodic worker runs at a time.
 *  - Never retries indefinitely — WorkManager handles its own backoff.
 *
 * Lifecycle:
 *  - Scheduled once in SmsMonitorService.onCreate()
 *  - Cancelled in SmsMonitorService.onDestroy() [optional, WorkManager survives service death]
 *  - Re-enqueued on app launch if not already running
 */
class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "[SyncWorker] Periodic flush triggered")

        return try {
            // Delegate all precondition checks and sync logic to the use case.
            // SyncWorker owns zero business logic — it is a pure scheduler.
            val result = FlushOfflineQueueUseCase(context).execute(force = false)

            result.fold(
                onSuccess = { pendingCount ->
                    if (pendingCount > 0) {
                        PrefsHelper.setLastWorkerSyncMs(context, System.currentTimeMillis())
                        Log.i(TAG, "[SyncWorker] Flush complete — $pendingCount items processed")
                    } else {
                        Log.d(TAG, "[SyncWorker] Nothing to flush")
                    }
                    Result.success()
                },
                onFailure = { e ->
                    Log.e(TAG, "[SyncWorker] Flush failed: ${e.message}")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "[SyncWorker] Unexpected error: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG              = "SyncWorker"
        private const val WORK_NAME        = "paychek_offline_queue_sync"

        /**
         * Schedules the periodic SyncWorker.
         * Safe to call multiple times — KEEP policy prevents duplicates.
         *
         * @param context  Application context
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                AppConfig.SYNC_WORKER_INTERVAL_MIN, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Do not replace if already scheduled
                workRequest
            )

            Log.i(TAG, "[SyncWorker] Scheduled — interval: ${AppConfig.SYNC_WORKER_INTERVAL_MIN}min, policy: KEEP")
        }

        /**
         * Cancels the periodic SyncWorker.
         * Call when the user logs out or disables the SMS service.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "[SyncWorker] Cancelled")
        }
    }
}
