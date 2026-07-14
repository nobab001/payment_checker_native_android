package online.paychek.app.services.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import online.paychek.app.data.remote.api.RetrofitClient
import java.util.concurrent.TimeUnit

/**
 * 4-layer connection engine:
 *  1) Network transport (WiFi / Mobile / Ethernet)
 *  2) Actual internet reachability (generate_204)
 *  3) PayCheck server health (/api/ping)
 *  4) API response — reported separately via [reportApiSyncFailure]
 */
class ConnectionEngine private constructor(context: Context) {

    companion object {
        private const val TAG = "ConnectionEngine"
        private const val DEBOUNCE_MS = 2_000L
        private const val PROBE_CACHE_MS = 15_000L
        private const val INTERNET_PROBE_URL = "https://clients3.google.com/generate_204"

        @Volatile
        private var instance: ConnectionEngine? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = ConnectionEngine(context.applicationContext)
                    }
                }
            }
        }

        fun getInstance(context: Context): ConnectionEngine {
            init(context)
            return instance!!
        }
    }

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val connectivityService = ConnectivityService(appContext)

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    private val probeMutex = Mutex()
    private var monitoringJob: Job? = null

    private var cachedStatus: ConnectionStatus? = null
    private var cachedStatusAtMs = 0L

    private val _status = MutableStateFlow(ConnectionStatus())
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _banner = MutableStateFlow<ConnectionBanner?>(null)
    val banner: StateFlow<ConnectionBanner?> = _banner.asStateFlow()

    private var lastBanner: ConnectionBanner? = null
    private var lastBannerAtMs = 0L

    fun startMonitoring(scope: CoroutineScope) {
        if (monitoringJob?.isActive == true) return

        monitoringJob = scope.launch(Dispatchers.IO) {
            launch { probe(force = true) }

            connectivityService.observe()
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect {
                    Log.d(TAG, "Network signal changed — probing after debounce")
                    probe(force = true)
                }
        }
    }

    suspend fun probe(force: Boolean = false): ConnectionStatus = probeMutex.withLock {
        if (!force) {
            val cached = cachedStatus
            if (cached != null && System.currentTimeMillis() - cachedStatusAtMs < PROBE_CACHE_MS) {
                return cached
            }
        }

        val wasConnected = _status.value.hasServer
        _status.value = _status.value.copy(isChecking = true)
        // 🔄 Reconnecting — only when we were not already Connected
        if (!wasConnected) {
            emitBannerDebounced(ConnectionBanner.Reconnecting())
        }

        val layer1 = hasActiveTransport()
        val layer2 = if (layer1) checkInternetReachable() else false
        val layer3 = if (layer2) checkServerAlive() else false

        val result = ConnectionStatus(
            layer1NetworkConnected = layer1,
            layer2InternetAvailable = layer2,
            layer3ServerAlive = layer3,
            isChecking = false,
            lastCheckedAtMs = System.currentTimeMillis()
        )

        cachedStatus = result
        cachedStatusAtMs = result.lastCheckedAtMs
        _status.value = result

        val connectivityBanner = result.toConnectivityBanner()
        if (connectivityBanner != null) {
            emitBannerDebounced(connectivityBanner)
        } else if (
            _banner.value is ConnectionBanner.NoInternet ||
            _banner.value is ConnectionBanner.Disconnected ||
            _banner.value is ConnectionBanner.Reconnecting
        ) {
            // ✅ Connected — clear transport/reconnect banners (keep ServerError until sync OK)
            _banner.value = null
            lastBanner = null
        }

        Log.d(
            TAG,
            "Probe L1=$layer1 L2=$layer2 L3=$layer3"
        )
        result
    }

    fun reportApiSyncFailure(message: String = ConnectionBanner.MSG_SERVER_ERROR, isRetrying: Boolean = false) {
        val current = _status.value
        if (!current.hasServer) return
        // 🔄 retrying sync → Reconnecting; final failure → Server Error
        if (isRetrying) {
            emitBannerDebounced(ConnectionBanner.Reconnecting())
        } else {
            emitBannerDebounced(ConnectionBanner.ServerError())
        }
    }

    fun clearApiSyncBanner() {
        if (
            _banner.value is ConnectionBanner.ServerError ||
            _banner.value is ConnectionBanner.Reconnecting
        ) {
            _banner.value = null
            lastBanner = null
        }
    }

    private fun emitBannerDebounced(banner: ConnectionBanner) {
        val now = System.currentTimeMillis()
        val sameType = lastBanner?.javaClass == banner.javaClass
        if (sameType && now - lastBannerAtMs < DEBOUNCE_MS) {
            return
        }
        lastBanner = banner
        lastBannerAtMs = now
        _banner.value = banner
    }

    private fun hasActiveTransport(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun checkInternetReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(INTERNET_PROBE_URL)
                .head()
                .build()
            probeClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "Internet probe failed: ${e.message}")
            false
        }
    }

    private suspend fun checkServerAlive(): Boolean = withContext(Dispatchers.IO) {
        try {
            RetrofitClient.paymentApiService.pingServer().isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "Server ping failed: ${e.message}")
            false
        }
    }
}
