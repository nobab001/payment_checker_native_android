package online.paychek.app.services.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * ConnectivityService — Reactive Network State Observer
 * ======================================================
 * Wraps Android's ConnectivityManager into a clean Kotlin Flow
 * so the foreground service and WorkManager can react to connectivity
 * changes without polling.
 *
 * Design rules:
 *  - This class DOES NOT sync the queue itself.
 *  - It only reports network state changes.
 *  - The caller (SmsMonitorService, SyncWorker) decides what to do.
 *
 * Usage (in a coroutine scope):
 *   ConnectivityService(context).observe().collect { isOnline ->
 *       if (isOnline) SmsReceiver.syncPendingQueue(context)
 *   }
 */
class ConnectivityService(private val context: Context) {

    private val TAG = "ConnectivityService"

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // -------------------------------------------------------------------------
    // Synchronous check — use for one-shot decisions (e.g. before insert)
    // -------------------------------------------------------------------------

    /**
     * Returns true if there is an active network with INTERNET capability right now.
     * Equivalent to NetworkConnectivityObserver.isNetworkAvailable() but centralized here.
     */
    fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // -------------------------------------------------------------------------
    // Reactive Flow — use for continuous observation in a foreground service
    // -------------------------------------------------------------------------

    /**
     * Emits true when network becomes available, false when lost.
     * Uses distinctUntilChanged() to prevent duplicate emissions.
     *
     * The Flow is cold — observation starts when collected and stops on cancellation.
     * Always call this inside a structured coroutine scope (e.g. lifecycleScope).
     */
    fun observe(): Flow<Boolean> = callbackFlow {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                trySend(true)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost: $network")
                trySend(false)
            }

            override fun onUnavailable() {
                Log.d(TAG, "Network unavailable")
                trySend(false)
            }
        }

        // Emit current state immediately so collector doesn't wait for a change event
        trySend(isOnline())

        connectivityManager.registerNetworkCallback(networkRequest, callback)
        Log.d(TAG, "Network callback registered")

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
            Log.d(TAG, "Network callback unregistered")
        }
    }.distinctUntilChanged()
}
