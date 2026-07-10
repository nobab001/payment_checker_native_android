package online.paychek.app.domain.usecase.sync

import android.content.Context
import android.util.Log
import online.paychek.app.data.local.AppDatabase
import online.paychek.app.services.connectivity.ConnectivityService
import online.paychek.app.services.sms.SmsReceiver

/**
 * FlushOfflineQueueUseCase — Domain use case for triggering a queue flush.
 *
 * Always awaits the canonical sync path ([SmsReceiver.syncPendingQueueAndAwait])
 * so callers know whether upload actually finished.
 *
 * @param force when true (manual "Sync Now"), clears backoff so waiting items
 *              are eligible immediately. Periodic SyncWorker should pass false.
 */
class FlushOfflineQueueUseCase(private val context: Context) {

    private val TAG = "FlushQueueUseCase"

    /**
     * @return Result.success(attemptedCount) — items that were eligible / flushed.
     *         Result.success(0) — nothing to do (empty, offline, or only in backoff).
     *         Result.failure — network/sync failure after attempt.
     */
    suspend fun execute(force: Boolean = false): Result<Int> {
        return try {
            val connectivity = ConnectivityService(context)
            if (!connectivity.isOnline()) {
                Log.d(TAG, "No network — flush skipped")
                return Result.success(0)
            }

            val db = AppDatabase.getInstance(context)
            val dao = db.pendingSmsDao()

            if (force) {
                dao.clearBackoffForPending()
            }

            val pendingCount = dao.getPendingItemsForRetry(System.currentTimeMillis()).size
            if (pendingCount == 0) {
                val waiting = dao.countWaitingBackoff(System.currentTimeMillis())
                val total = dao.countPendingUnsynced()
                Log.d(TAG, "Nothing eligible — totalPending=$total waitingBackoff=$waiting")
                return Result.success(0)
            }

            Log.i(TAG, "$pendingCount pending items — awaiting syncPendingQueueAndAwait(force=$force)")

            val ok = SmsReceiver.syncPendingQueueAndAwait(context)
            if (!ok) {
                Log.w(TAG, "Flush finished with remaining failures")
                return Result.failure(IllegalStateException("Sync incomplete — some items failed or server unavailable"))
            }

            Log.i(TAG, "Flush complete for $pendingCount items")
            Result.success(pendingCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error during queue flush: ${e.message}", e)
            Result.failure(e)
        }
    }
}
