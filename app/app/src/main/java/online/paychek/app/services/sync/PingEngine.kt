package online.paychek.app.services.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.services.sms.SmsReceiver

/**
 * Demand-Driven Conditional Lightweight HEAD Ping Engine.
 * 
 * Activated only when an API request fails (e.g., offline or 5xx).
 * Pings the zero-overhead /api/ping endpoint every 15 seconds.
 * Upon receiving HTTP 200 OK, it flushes the offline queue using bulk sync and stops itself.
 */
object PingEngine {
    private const val TAG = "PingEngine"
    private var pingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Synchronized
    fun start(context: Context) {
        if (pingJob?.isActive == true) {
            Log.d(TAG, "PingEngine is already running. Ignoring start request.")
            return
        }

        Log.i(TAG, "Starting Lightweight Ping Engine...")
        pingJob = scope.launch {
            while (isActive) {
                delay(15_000L) // Ping every 15 seconds
                
                try {
                    val response = RetrofitClient.paymentApiService.pingServer()
                    if (response.isSuccessful) {
                        Log.i(TAG, "Received HTTP 200 OK from ping. Server is LIVE. Triggering bulk sync...")
                        
                        // Fire the bulk sync asynchronously to not block the engine shutdown
                        SmsReceiver.syncPendingQueue(context)
                        
                        // Self-shutdown
                        stop()
                    } else {
                        Log.w(TAG, "Ping received HTTP ${response.code()}. Server still down.")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Ping failed (Network issue): ${e.message}")
                }
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
