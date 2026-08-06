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
 * Comm Policy v1.0 — package-tiered heartbeat while SMS monitoring is ON.
 *
 * Primary clock: [HeartbeatWorker] (WorkManager, 15/30/60 min).
 * This engine performs the HTTP POST; in-process delay loops are not used
 * (they die with the process / suspend under Doze).
 *
 * SMS upload is independent (never waits for this timer).
 * Heartbeat response can update next interval / forceSync / templateVersion.
 */
object NumberHeartbeatEngine {
    private const val TAG = "NumberHeartbeat"
    const val TRIGGER_BOOT_COMPLETED = "boot_completed"
    const val TRIGGER_NETWORK_RESTORED = "network_restored"
    private const val NETWORK_RESTORE_DEBOUNCE_MS = 7_000L

    private var networkRestorePulseJob: Job? = null
    /** After SMS upload success, skip the next scheduled heartbeat (server already marked alive). */
    @Volatile private var skipNextScheduled = false
    @Volatile private var cacheSyncInFlight = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Call after successful SMS upload to server — resets timer / skips duplicate HB. */
    fun noteSmsUploadSuccess(context: Context) {
        skipNextScheduled = true
        Log.i(TAG, "SMS upload success — next scheduled heartbeat will skip")
    }

    /**
     * Arm WorkManager heartbeat + send one immediate presence pulse.
     * No in-process delay loop (process death / Doze would kill it).
     */
    @Synchronized
    fun start(context: Context, initialPresenceTrigger: String? = null) {
        ensureRunning(context.applicationContext, initialPresenceTrigger)
    }

    @Synchronized
    fun ensureRunning(context: Context, initialPresenceTrigger: String? = null) {
        if (!PrefsHelper.isSmsServiceActive(context)) {
            stop()
            HeartbeatWorker.cancel(context)
            return
        }
        val app = context.applicationContext
        HeartbeatWorker.schedule(app)
        scope.launch {
            sendHeartbeat(app, smsActive = true, presenceTrigger = initialPresenceTrigger)
        }
        Log.i(
            TAG,
            "Heartbeat armed — WorkManager interval=${CommPolicyStore.heartbeatBaseIntervalMs(app)}ms " +
                "profile=${CommPolicyStore.profile(app)}"
        )
    }

    @Synchronized
    fun stop() {
        networkRestorePulseJob?.cancel()
        networkRestorePulseJob = null
    }

    /** Immediate one-shot heartbeat (e.g. before refreshing account number list). */
    fun pulse(context: Context) {
        scope.launch { sendHeartbeat(context.applicationContext, smsActive = PrefsHelper.isSmsServiceActive(context)) }
    }

    /**
     * Suspending one-shot heartbeat — for WorkManager / brief wakelock holders.
     */
    suspend fun sendHeartbeatBlocking(context: Context) {
        if (skipNextScheduled && PrefsHelper.isSmsServiceActive(context)) {
            skipNextScheduled = false
            Log.i(TAG, "Skipped scheduled heartbeat (SMS already counted as alive)")
            return
        }
        sendHeartbeat(context.applicationContext, smsActive = PrefsHelper.isSmsServiceActive(context))
    }

    /**
     * Internet restored (offline → online only). Debounced to avoid heartbeat storms.
     */
    @Synchronized
    fun pulseNetworkRestored(context: Context) {
        networkRestorePulseJob?.cancel()
        networkRestorePulseJob = scope.launch {
            delay(NETWORK_RESTORE_DEBOUNCE_MS)
            if (!PrefsHelper.isSmsServiceActive(context.applicationContext)) return@launch
            Log.i(TAG, "Network restored — immediate heartbeat after debounce")
            sendHeartbeat(
                context.applicationContext,
                smsActive = true,
                presenceTrigger = TRIGGER_NETWORK_RESTORED
            )
        }
    }

    /** Monitoring Stop → Offline Signal + cancel WorkManager heartbeat. */
    fun signalOffline(context: Context) {
        val app = context.applicationContext
        HeartbeatWorker.cancel(app)
        scope.launch {
            sendHeartbeat(app, smsActive = false)
            stop()
        }
    }

    /** @deprecated Prefer [start] / [stop]. */
    fun startFallback(context: Context) = start(context)

    fun stopFallback() = stop()

    private suspend fun sendHeartbeat(
        context: Context,
        smsActive: Boolean,
        presenceTrigger: String? = null
    ) {
        if (smsActive && !PrefsHelper.isSmsServiceActive(context)) return

        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
        if (token.isEmpty()) return

        // Always POST while SMS is active — even with empty numbers — so server
        // refreshes device last_seen and DeviceWatch does not deactivate SIMs.
        val numbers = collectActiveNumbers(context)
        if (smsActive && numbers.isEmpty()) {
            Log.i(TAG, "Heartbeat with 0 numbers — device liveness only")
        }

        val deviceId = DeviceIdHelper.getHashedAndroidId(context)
        val request = HeartbeatRequest(
            numbers = numbers,
            smsServiceActive = smsActive,
            batteryPercent = readBatteryPercent(context),
            presenceTrigger = presenceTrigger
        )

        runCatching {
            RetrofitClient.gatewayApiService.postHeartbeat(
                token = "Bearer $token",
                request = request,
                deviceId = deviceId,
                lastSync = PrefsHelper.getGatewayMethodsLastSync(context)
            )
        }.onSuccess { res ->
            if (res.isSuccessful) {
                val body = res.body()
                if (body != null) {
                    if (body.action == "STOP_MONITORING") {
                        Log.w(TAG, "Server requested STOP_MONITORING (subscription suspended)")
                        SubscriptionLifecycleHelper.stopMonitoringRemote(
                            context,
                            body.message ?: body.error
                        )
                        return
                    }
                    CommPolicyStore.applyHeartbeatResponse(context, body)
                    // Admin announcements piggyback on the heartbeat (no push channel).
                    online.paychek.app.services.notify.AdminNoticeManager
                        .handleIncoming(context, body.notifications)
                    if (smsActive) {
                        HeartbeatWorker.schedule(context)
                    }
                    val serverTpl = parseTemplateVersion(body.templateVersion)
                    val localSync = PrefsHelper.getGatewayMethodsLastSync(context)
                    val needsSync = body.forceSync == true ||
                        (serverTpl > 0L && localSync < serverTpl)
                    if (needsSync) {
                        Log.i(
                            TAG,
                            "Server requested cache sync (forceSync=${body.forceSync}, " +
                                "templateVersion=$serverTpl, local=$localSync)"
                        )
                        refreshGatewayCaches(context, reason = "heartbeat_forceSync")
                    }
                }
                Log.i(
                    TAG,
                    "Heartbeat OK — numbers=${numbers.size} trigger=${presenceTrigger ?: "scheduled"} "
                        + "next=${CommPolicyStore.heartbeatIntervalMs(context)}ms"
                )
                // Server reachable again (even after long outage) — flush queued SMS without
                // waiting for the user to open the app. Connectivity alone is not enough:
                // the phone stays "online" while the API is down.
                flushPendingSmsIfAny(context)
            } else {
                Log.w(TAG, "Heartbeat HTTP ${res.code()}")
                if (smsActive) ServerProbeWorker.scheduleSoon(context)
            }
        }.onFailure { e ->
            Log.w(TAG, "Heartbeat failed: ${e.message}")
            if (smsActive) ServerProbeWorker.scheduleSoon(context)
        }
    }

    private fun parseTemplateVersion(raw: Any?): Long {
        return when (raw) {
            null -> 0L
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 0L
            else -> raw.toString().toLongOrNull() ?: 0L
        }
    }

    /**
     * Template/methods sync — separate from presence heartbeat.
     * Advances local templateVersion ONLY after successful cache persistence
     * (or explicit unchanged confirmation that local content is already current).
     */
    fun refreshGatewayCaches(context: Context, reason: String = "manual") {
        if (cacheSyncInFlight) {
            Log.d(TAG, "Cache sync already in flight — skip ($reason)")
            return
        }
        cacheSyncInFlight = true
        scope.launch {
            try {
                val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
                if (token.isEmpty()) return@launch

                if (reason == "heartbeat_forceSync") {
                    delay((300L..1800L).random())
                }

                // Stale/force path: full fetch so we always get authoritative payload.
                val forceFull = reason == "heartbeat_forceSync" || reason == "stale_template"
                val syncToken = if (forceFull) 0L else PrefsHelper.getGatewayMethodsLastSync(context)

                var methodsOk = false
                var templatesOk = false
                var appliedVersion = 0L

                val res = RetrofitClient.gatewayApiService.getGatewayMethods("Bearer $token", syncToken)
                if (res.isSuccessful) {
                    val body = res.body()
                    body?.globalBlockedSenders?.let { blocked ->
                        PrefsHelper.setGlobalBlockedSenders(context, blocked)
                    }
                    when {
                        body?.data != null -> {
                            val jsonStr = GsonUtils.gson.toJson(body.data)
                            if (PrefsHelper.setGatewayMethodsCache(context, jsonStr)) {
                                methodsOk = true
                                appliedVersion = maxOf(appliedVersion, body.dataVersion ?: 0L)
                                if (PrefsHelper.isSmsServiceActive(context)) {
                                    pulse(context)
                                }
                            } else {
                                Log.e(TAG, "Methods cache write/verify failed — version not advanced")
                            }
                        }
                        body?.unchanged == true -> {
                            methodsOk = true
                            appliedVersion = maxOf(appliedVersion, body.dataVersion ?: 0L)
                        }
                        else -> {
                            Log.w(TAG, "Methods sync: empty payload and not unchanged")
                        }
                    }
                } else {
                    Log.w(TAG, "Methods sync HTTP ${res.code()}")
                }

                val resTemplates = RetrofitClient.gatewayApiService.getTemplates("Bearer $token", syncToken)
                if (resTemplates.isSuccessful) {
                    val templateBody = resTemplates.body()
                    when {
                        templateBody?.templates != null -> {
                            val jsonTemplates = GsonUtils.gson.toJson(templateBody.templates)
                            if (PrefsHelper.setSmsTemplatesCache(context, jsonTemplates)) {
                                templatesOk = true
                                appliedVersion = maxOf(appliedVersion, templateBody.dataVersion ?: 0L)
                            } else {
                                Log.e(TAG, "Templates cache write/verify failed — version not advanced")
                            }
                        }
                        templateBody?.unchanged == true -> {
                            templatesOk = true
                            appliedVersion = maxOf(appliedVersion, templateBody.dataVersion ?: 0L)
                        }
                        else -> {
                            Log.w(TAG, "Templates sync: empty payload and not unchanged")
                        }
                    }
                } else {
                    Log.w(TAG, "Templates sync HTTP ${resTemplates.code()}")
                }

                if (methodsOk && templatesOk && appliedVersion > 0L) {
                    PrefsHelper.setGatewayMethodsLastSync(context, appliedVersion)
                    Log.i(TAG, "Gateway cache refresh OK ($reason) version=$appliedVersion")
                } else {
                    Log.w(
                        TAG,
                        "Gateway cache refresh incomplete ($reason) " +
                            "methodsOk=$methodsOk templatesOk=$templatesOk — local version unchanged"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gateway cache refresh failed ($reason): ${e.message}")
            } finally {
                cacheSyncInFlight = false
            }
        }
    }

    private fun flushPendingSmsIfAny(context: Context) {
        scope.launch {
            try {
                val dao = online.paychek.app.data.local.AppDatabase.getInstance(context).pendingSmsDao()
                val pending = dao.countPendingUnsynced()
                if (pending <= 0) return@launch
                Log.i(TAG, "Heartbeat OK with $pending pending SMS — flushing queue")
                dao.clearBackoffForPending()
                dao.recoverOutageFailedItems()
                val ok = online.paychek.app.services.sms.SmsReceiver.syncPendingQueueAndAwait(context)
                if (!ok) {
                    Log.w(TAG, "Post-heartbeat flush incomplete — scheduling ServerProbeWorker")
                    ServerProbeWorker.scheduleSoon(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Post-heartbeat flush failed: ${e.message}")
                ServerProbeWorker.scheduleSoon(context)
            }
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
