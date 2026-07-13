package online.paychek.app.services.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.services.sms.SmsReceiver

/**
 * Demand-Driven Conditional Lightweight Ping Engine.
 *
 * Activated when offline or an API request fails (e.g., 5xx / network).
 * Pings GET /api/ping on a STAGED schedule. যত সময় যায় তত interval বড় হয় যাতে
 * ব্যাটারি/ডেটা বাঁচে, কিন্তু কভারেজ অনেক লম্বা (~৩৫ ঘণ্টা) থাকে।
 *
 * Schedule (প্রতি stage-এ maxAttempts বার চেষ্টা করে পরের stage-এ যায়):
 *   1) 5s   × 3   — প্রাথমিক দ্রুত রিট্রাই (SMS পাঠানো fail হওয়ার সাথে সাথে)
 *   2) 15s  × 20
 *   3) 1min × 20
 *   4) 5min × 20
 *   5) 15min× 20
 *   6) 30min× 20
 *   7) 1hr  × 20
 * যেকোনো মুহূর্তে ping HTTP 200 পেলে জমা SMS bulk flush করে, সফল হলে PingEngine নিজেকে kill করে।
 * সব stage শেষেও সার্ভার না ফিরলে ping বন্ধ হয় (SMS queue-তে নিরাপদ থাকে; পরের SMS বা
 * periodic SyncWorker আবার PingEngine চালু করবে)।
 */
object PingEngine {
    private const val TAG = "PingEngine"

    private data class PingStage(val intervalMs: Long, val maxAttempts: Int)

    private val PING_SCHEDULE = listOf(
        PingStage(5_000L, 3),            // ৫ সেকেন্ড পর পর ৩ বার (প্রাথমিক দ্রুত রিট্রাই)
        PingStage(15_000L, 20),          // ১৫ সেকেন্ড পর পর ২০ বার
        PingStage(60_000L, 20),          // ১ মিনিট পর পর ২০ বার
        PingStage(5 * 60_000L, 20),      // ৫ মিনিট পর পর ২০ বার
        PingStage(15 * 60_000L, 20),     // ১৫ মিনিট পর পর ২০ বার
        PingStage(30 * 60_000L, 20),     // ৩০ মিনিট পর পর ২০ বার
        PingStage(60 * 60_000L, 20)      // ১ ঘণ্টা পর পর ২০ বার
    )

    private var pingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Synchronized
    fun start(context: Context) {
        if (pingJob?.isActive == true) {
            Log.d(TAG, "PingEngine is already running. Ignoring start request.")
            return
        }

        val appContext = context.applicationContext
        Log.i(TAG, "Starting Staged Ping Engine...")
        pingJob = scope.launch {
            for ((stageIndex, stage) in PING_SCHEDULE.withIndex()) {
                var attempt = 0
                while (attempt < stage.maxAttempts) {
                    if (!isActive) return@launch
                    attempt++
                    try {
                        val response = RetrofitClient.paymentApiService.pingServer()
                        if (response.isSuccessful) {
                            Log.i(TAG, "Ping HTTP 200 (stage ${stageIndex + 1}, attempt $attempt). Server is LIVE. Flushing queue...")
                            val syncOk = SmsReceiver.syncPendingQueueAndAwait(appContext)
                            if (syncOk) {
                                Log.i(TAG, "Bulk sync complete — stopping PingEngine")
                                stop()
                                return@launch
                            }
                            Log.w(TAG, "Bulk sync after ping had failures — continuing to ping")
                        } else {
                            Log.w(TAG, "Ping HTTP ${response.code()} (stage ${stageIndex + 1}, attempt $attempt/${stage.maxAttempts}). Server still down.")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Ping failed (stage ${stageIndex + 1}, attempt $attempt/${stage.maxAttempts}): ${e.message}")
                    }
                    delay(stage.intervalMs)
                }
            }
            // সব stage শেষ — সার্ভার দীর্ঘ সময়েও ফেরেনি। ping বন্ধ; SMS queue-তে জমা থাকবে,
            // পরের নতুন SMS বা periodic SyncWorker আবার চেষ্টা করবে।
            Log.w(TAG, "All ping stages exhausted — stopping PingEngine (SMS remain safely queued).")
            stop()
        }
    }

    @Synchronized
    fun stop() {
        if (pingJob?.isActive == true) {
            Log.i(TAG, "Stopping Ping Engine...")
            pingJob?.cancel()
            pingJob = null
        }
    }
}
