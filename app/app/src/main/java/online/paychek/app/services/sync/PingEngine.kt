package online.paychek.app.services.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.services.sms.SmsReceiver

/**
 * Demand-Driven Conditional Lightweight Ping Engine.
 *
 * Activated when offline or API request fails (e.g., 5xx).
 * Pings GET /api/ping immediately, then every 15 seconds.
 * On HTTP 200 OK, flushes the offline queue and stops only after sync succeeds.
 */
object PingEngine {
    private const val TAG = "PingEngine"
    private const val PING_INTERVAL_MS = 15_000L
    private var pingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Synchronized
    fun start(context: Context) {
        if (pingJob?.isActive == true) {
            Log.d(TAG, "PingEngine is already running. Ignoring start request.")
            return
        }

        val appContext = context.applicationContext
        Log.i(TAG, "Starting Lightweight Ping Engine...")
        pingJob = scope.launch {
            while (isActive) {
                try {
                    val response = RetrofitClient.paymentApiService.pingServer()
                    if (response.isSuccessful) {
                        Log.i(TAG, "Received HTTP 200 OK from ping. Server is LIVE. Triggering bulk sync...")
                        val syncOk = SmsReceiver.syncPendingQueueAndAwait(appContext)
                        if (syncOk) {
                            Log.i(TAG, "Bulk sync complete — stopping PingEngine")
                            stop()
                            break
                        }
                        Log.w(TAG, "Bulk sync after ping had failures — continuing to ping")
                    } else {
                        Log.w(TAG, "Ping received HTTP ${response.code()}. Server still down.")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Ping failed (Network issue): ${e.message}")
                }
                delay(PING_INTERVAL_MS)
            }
        }
    }

    @Synchronized
    fun stop() {
        if (pingJob?.isActive == true) {
            Log.i(TAG, "Stopping Lightweight Ping Engine...")
            pingJob?.cancel()
            pingJob = null
        }
    }
}
