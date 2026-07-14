package online.paychek.app.services.sync

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import online.paychek.app.config.AppConfig
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.GatewayMethod
import online.paychek.app.data.remote.dto.HeartbeatNumberItem
import online.paychek.app.data.remote.dto.HeartbeatRequest
import online.paychek.app.utils.DeviceIdHelper
import online.paychek.app.utils.GsonUtils
import online.paychek.app.utils.SecurePreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Comm Policy v1.0 — package-tiered heartbeat while SMS monitoring is ON.
 *
 * SMS upload is independent (never waits for this timer).
 * Heartbeat response can update next interval / forceSync / templateVersion.
 */
object NumberHeartbeatEngine {
    private const val TAG = "NumberHeartbeat"
    private var loopJob: Job? = null
    @Volatile private var socketConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun onSocketConnected(context: Context, socket: io.socket.client.Socket?) {
        socketConnected = true
        emitSocketDeviceNumbers(socket, context)
        // Still keep HTTP heartbeat for Policy Sync (forceSync / interval).
        ensureRunning(context.applicationContext)
        Log.i(TAG, "Socket up — policy heartbeat continues")
    }

    fun onSocketDisconnected(context: Context) {
        socketConnected = false
        ensureRunning(context.applicationContext)
        Log.i(TAG, "Socket down — HTTP heartbeat active")
    }

    /** Start / restart periodic heartbeat for monitoring session. */
    @Synchronized
    fun start(context: Context) {
        ensureRunning(context.applicationContext)
    }

    @Synchronized
    fun ensureRunning(context: Context) {
        if (!PrefsHelper.isSmsServiceActive(context)) {
            stop()
            return
        }
        if (loopJob?.isActive == true) return
        val app = context.applicationContext
        loopJob = scope.launch {
            sendHeartbeat(app, smsActive = true)
            while (isActive && PrefsHelper.isSmsServiceActive(app)) {
                val delayMs = CommPolicyStore.heartbeatIntervalMs(app)
                Log.d(TAG, "Next heartbeat in ${delayMs / 1000}s (profile=${CommPolicyStore.profile(app)})")
                delay(delayMs)
                if (PrefsHelper.isSmsServiceActive(app)) {
                    sendHeartbeat(app, smsActive = true)
                }
            }
        }
        Log.i(
            TAG,
            "Heartbeat loop started — interval=${CommPolicyStore.heartbeatIntervalMs(app)}ms profile=${CommPolicyStore.profile(app)}"
        )
    }

    @Synchronized
    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    /** Immediate one-shot heartbeat (e.g. before refreshing account number list). */
    fun pulse(context: Context) {
        scope.launch { sendHeartbeat(context.applicationContext, smsActive = PrefsHelper.isSmsServiceActive(context)) }
    }

    /** Monitoring Stop → Offline Signal */
    fun signalOffline(context: Context) {
        scope.launch {
            sendHeartbeat(context.applicationContext, smsActive = false)
            stop()
        }
    }

    /** Push active SIM numbers over Socket.IO so server can mark ONLINE instantly. */
    fun emitSocketDeviceNumbers(socket: io.socket.client.Socket?, context: Context) {
        if (socket == null || !socket.connected()) return
        val numbers = collectActiveNumbers(context)
        if (numbers.isEmpty()) return
        try {
            val arr = JSONArray()
            numbers.forEach { n ->
                arr.put(
                    JSONObject()
                        .put("sim_slot", n.simSlot)
                        .put("phone_number", n.phoneNumber)
                )
            }
            socket.emit("device_numbers", JSONObject().put("numbers", arr))
            Log.d(TAG, "Emitted device_numbers over socket (${numbers.size})")
        } catch (e: Exception) {
            Log.w(TAG, "emitSocketDeviceNumbers failed: ${e.message}")
        }
    }

    /** @deprecated Prefer [start] / [stop]. */
    fun startFallback(context: Context) = start(context)

    fun stopFallback() = stop()

    private suspend fun sendHeartbeat(context: Context, smsActive: Boolean) {
        if (smsActive && !PrefsHelper.isSmsServiceActive(context)) return

        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
        if (token.isEmpty()) return

        val numbers = collectActiveNumbers(context)
        if (smsActive && numbers.isEmpty()) {
            Log.d(TAG, "No active numbers — skip heartbeat")
            return
        }

        val deviceId = DeviceIdHelper.getHashedAndroidId(context)
        val request = HeartbeatRequest(
            numbers = numbers,
            smsServiceActive = smsActive,
            batteryPercent = readBatteryPercent(context)
        )

        runCatching {
            RetrofitClient.gatewayApiService.postHeartbeat(
                token = "Bearer $token",
                request = request,
                deviceId = deviceId
            )
        }.onSuccess { res ->
            if (res.isSuccessful) {
                val body = res.body()
                if (body != null) {
                    CommPolicyStore.applyHeartbeatResponse(context, body)
                    if (body.forceSync == true) {
                        Log.i(TAG, "Server requested forceSync (templateVersion=${body.templateVersion})")
                        // Soft signal — existing dashboards poll templates; optional future hook.
                    }
                }
                Log.d(
                    TAG,
                    "Heartbeat OK — numbers=${numbers.size} next=${CommPolicyStore.heartbeatIntervalMs(context)}ms socketConnected=$socketConnected"
                )
            } else {
                Log.w(TAG, "Heartbeat HTTP ${res.code()}")
            }
        }.onFailure { e ->
            Log.w(TAG, "Heartbeat failed: ${e.message}")
        }
    }

    private fun collectActiveNumbers(context: Context): List<HeartbeatNumberItem> {
        val sim1Enabled = PrefsHelper.isSim1Enabled(context)
        val sim2Enabled = PrefsHelper.isSim2Enabled(context)
        val methods = loadMethodsFromCache(context)

        val out = mutableListOf<HeartbeatNumberItem>()
        if (sim1Enabled) {
            phoneForSlot(methods, 1)?.let { out.add(HeartbeatNumberItem(simSlot = 1, phoneNumber = it)) }
        }
        if (sim2Enabled) {
            phoneForSlot(methods, 2)?.let { out.add(HeartbeatNumberItem(simSlot = 2, phoneNumber = it)) }
        }
        return out
    }

    private fun phoneForSlot(methods: List<GatewayMethod>, slot: Int): String? {
        return methods
            .filter { it.simSlot == slot && !it.number.isNullOrBlank() }
            .sortedBy { it.priority }
            .firstNotNullOfOrNull { method ->
                val digits = method.number!!.filter { it.isDigit() }
                digits.takeLast(11).takeIf { it.length == 11 }
            }
    }

    private fun loadMethodsFromCache(context: Context): List<GatewayMethod> {
        val json = PrefsHelper.getGatewayMethodsCache(context)
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val type = object : TypeToken<List<GatewayMethod>>() {}.type
            GsonUtils.gson.fromJson<List<GatewayMethod>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readBatteryPercent(context: Context): Int? {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level in 0..100) level else null
        } catch (_: Exception) {
            null
        }
    }
}
