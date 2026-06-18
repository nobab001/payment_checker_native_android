package online.paychek.app.domain.usecase.sync

import android.content.Context
import android.util.Log
import online.paychek.app.data.local.AppDatabase
import online.paychek.app.services.connectivity.ConnectivityService
import online.paychek.app.services.sms.SmsReceiver

/**
 * FlushOfflineQueueUseCase — Domain use case for triggering a manual queue flush.
 *
 * Responsibility: check preconditions and delegate queue flush to the
 * canonical sync function.
 *
 * Preconditions checked before sync:
 *  1. Network is available (no point trying without connectivity)
 *  2. Queue is non-empty (avoid unnecessary API calls)
 *
 * Design contract:
 *  - Does NOT implement sync logic — delegates entirely to SmsReceiver.syncPendingQueue().
 *  - One canonical sync path; this use case is a controlled entry point.
 *  - Returns Result<Int> — the number of pending items found, or failure with reason.
 *
 * Callers:
 *  - SyncWorker (periodic WorkManager fallback)
 *  - Future: manual "Retry Now" button in the UI
 *  - Future: deep link trigger on connectivity restore
 */
class FlushOfflineQueueUseCase(private val context: Context) {

    private val TAG = "FlushQueueUseCase"

    /**
     * Checks preconditions and triggers a queue flush if eligible.
     *
     * @return Result.success(pendingCount) — how many items were attempted.
     *         Result.success(0)            — nothing to do (empty queue or offline).
     *         Result.failure(e)            — unexpected error during check.
     */
    suspend fun execute(): Result<Int> {
        return try {
            // Precondition 1: network check
            val connectivity = ConnectivityService(context)
            if (!connectivity.isOnline()) {
                Log.d(TAG, "No network — flush skipped")
                return Result.success(0)
            }

            // Precondition 2: queue check
            val db          = AppDatabase.getInstance(context)
            val dao         = db.pendingSmsDao()
            val nowMs       = System.currentTimeMillis()
            val pendingCount = dao.getPendingItemsForRetry(nowMs).size

            if (pendingCount == 0) {
                Log.d(TAG, "Queue empty — nothing to flush")
                return Result.success(0)
            }

            Log.i(TAG, "$pendingCount pending items — delegating to syncPendingQueue()")

            // Delegate to the one canonical sync path
            SmsReceiver.syncPendingQueue(context)

            Log.i(TAG, "Flush triggered for $pendingCount items")
            Result.success(pendingCount)

        } catch (e: Exception) {
            Log.e(TAG, "Error during queue flush: ${e.message}", e)
            Result.failure(e)
        }
    }
}
