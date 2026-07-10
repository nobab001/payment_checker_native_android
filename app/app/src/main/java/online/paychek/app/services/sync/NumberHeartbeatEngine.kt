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
 * Sends device-level number health heartbeats every 60s while SMS service runs.
 * Server derives ONLINE / GRACE / OFFLINE / STALE from last_seen timestamps.
 */
object NumberHeartbeatEngine {
    private const val TAG = "NumberHeartbeat"
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Synchronized
    fun start(context: Context) {
        if (heartbeatJob?.isActive == true) return
        val app = context.applicationContext
        Log.i(TAG, "Starting number heartbeat engine")
        heartbeatJob = scope.launch {
            sendHeartbeat(app)
            while (isActive) {
                delay(AppConfig.HEARTBEAT_INTERVAL_MS)
                sendHeartbeat(app)
            }
        }
    }

    @Synchronized
    fun stop() {
        if (heartbeatJob?.isActive == true) {
            Log.i(TAG, "Stopping number heartbeat engine")
            heartbeatJob?.cancel()
            heartbeatJob = null
        }
    }

    /** Immediate one-shot heartbeat (e.g. before refreshing account number list). */
    fun pulse(context: Context) {
        scope.launch {
            sendHeartbeat(context.applicationContext)
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

    private suspend fun sendHeartbeat(context: Context) {
        if (!PrefsHelper.isSmsServiceActive(context)) return

        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
        if (token.isEmpty()) return

        val numbers = collectActiveNumbers(context)
        if (numbers.isEmpty()) return

        val deviceId = DeviceIdHelper.getHashedAndroidId(context)
        val request = HeartbeatRequest(
            numbers = numbers,
            smsServiceActive = true,
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
                Log.d(TAG, "Heartbeat OK — ${numbers.size} number(s)")
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
