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

        val bySlot = methods
            .filter { !it.number.isNullOrBlank() }
            .groupBy { it.simSlot }

        val out = mutableListOf<HeartbeatNumberItem>()
        if (sim1Enabled) {
            bySlot[1]?.firstOrNull()?.number?.takeIf { it.length >= 11 }?.let {
                out.add(HeartbeatNumberItem(simSlot = 1, phoneNumber = it.takeLast(11)))
            }
        }
        if (sim2Enabled) {
            bySlot[2]?.firstOrNull()?.number?.takeIf { it.length >= 11 }?.let {
                out.add(HeartbeatNumberItem(simSlot = 2, phoneNumber = it.takeLast(11)))
            }
        }
        return out
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
