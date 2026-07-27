package online.paychek.app.services.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import online.paychek.app.data.local.AppDatabase
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.services.sms.SmsReceiver
import java.util.concurrent.TimeUnit

/**
 * Durable server-reachability probe for the offline SMS queue.
 *
 * Why this exists:
 *  - Phone network can stay "online" while the API server is down for hours/days.
 *  - In-memory [PingEngine] dies when the process is killed or its stages exhaust.
 *  - WorkManager survives process death and Doze better than a coroutine loop.
 *
 * Behavior:
 *  - If SMS monitoring is off or queue empty → success (no reschedule).
 *  - Ping GET /api/ping; on 200 → force-clear backoff + flush queue.
 *  - If queue still has pending after flush (or ping failed) → reschedule self
 *    with staged delay (1m → 5m → 15m → 30m).
 */
class ServerProbeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!PrefsHelper.isSmsServiceActive(app)) {
            Log.d(TAG, "SMS service off — probe cancelled")
            return Result.success()
        }

        val dao = AppDatabase.getInstance(app).pendingSmsDao()
        val pending = dao.countPendingUnsynced()
        if (pending <= 0) {
            Log.d(TAG, "No pending SMS — probe done")
            return Result.success()
        }

        Log.i(TAG, "Probe start — pending=$pending")
        val stage = inputData.getInt(KEY_STAGE, 0)

        val pingOk = runCatching {
            RetrofitClient.paymentApiService.pingServer().isSuccessful
        }.getOrDefault(false)

        if (pingOk) {
            Log.i(TAG, "Server LIVE — forcing queue flush")
            dao.clearBackoffForPending()
            dao.recoverOutageFailedItems()
            val ok = SmsReceiver.syncPendingQueueAndAwait(app)
            val remaining = dao.countPendingUnsynced()
            if (ok || remaining == 0) {
                Log.i(TAG, "Queue flushed — stopping probe chain")
                return Result.success()
            }
            Log.w(TAG, "Flush incomplete — remaining=$remaining, rescheduling")
        } else {
            Log.w(TAG, "Ping failed — server still unreachable (stage=$stage)")
        }

        schedule(app, stage + 1)
        return Result.success()
    }

    companion object {
        private const val TAG = "ServerProbeWorker"
        private const val WORK_NAME = "paychek_server_probe"
        private const val KEY_STAGE = "stage"

        private val STAGE_DELAYS_MIN = longArrayOf(1L, 5L, 15L, 30L, 30L)

        fun schedule(context: Context, stage: Int = 0) {
            val app = context.applicationContext
            val delayMin = STAGE_DELAYS_MIN[stage.coerceIn(0, STAGE_DELAYS_MIN.lastIndex)]
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<ServerProbeWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delayMin, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .setInputData(
                    androidx.work.Data.Builder().putInt(KEY_STAGE, stage.coerceAtMost(STAGE_DELAYS_MIN.lastIndex)).build()
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(app).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.i(TAG, "Scheduled probe in ${delayMin}min (stage=$stage)")
        }

        /** Kick a near-immediate probe when pending SMS exist and server may be back. */
        fun scheduleSoon(context: Context) {
            schedule(context, stage = 0)
        }
    }
}
