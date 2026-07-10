package online.paychek.app.services.foreground

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import online.paychek.app.config.AppConfig
import online.paychek.app.data.local.AppDatabase
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.services.connectivity.ConnectivityService
import online.paychek.app.services.sms.SmsReceiver
import online.paychek.app.services.sync.NumberHeartbeatEngine
import online.paychek.app.services.sync.PingEngine
import online.paychek.app.services.sync.SmsPollWorker
import online.paychek.app.services.sync.SyncWorker
import online.paychek.app.utils.SecurePreferences
import online.paychek.app.utils.SmsParser
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.socket.client.IO
import io.socket.client.Socket

/**
 * SmsMonitorService — প্রোডাকশন-রেডি Foreground Service
 *
 * ডুয়েল-গার্ড আর্কিটেকচার:
 *  বার্ড-১ (প্রাইমারি): SmsReceiver — OS BroadcastReceiver (real-time, immediate)
 *  বার্ড-২ (ফলব্যাক): SmsPollWorker — ContentProvider inbox polling (15-min WorkManager)
 *
 *  উভয় গার্ড সর্বদা সাথে সক্রিয় থাকে। Android 14/15-এ OEM battery kill
 *  বা broadcast throttle হলেও Guard-2 পেমেন্ট SMS miss হতে দেয় না।
 *
 *  Dedup: rawBodyHash UNIQUE index দুটি guard থেকে একই SMS duplicate
 *  process হতে দেয় না।
 */
class SmsMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockRenewJob: Job? = null
    private var smsReceiver: SmsReceiver? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var socket: Socket? = null
    private var connectivityJob: Job? = null

    // সর্বশেষ সফল পেমেন্টের সময় (notification-এ দেখানোর জন্য)
    private var lastPaymentTime: String = "এখনো কোনো পেমেন্ট আসেনি"

    companion object {
        private const val TAG = "SmsMonitorService"

        /** True while service process is alive (more reliable than ActivityManager). */
        @Volatile
        var isAlive: Boolean = false

        /** Set when user explicitly toggles OFF — blocks auto-restart in onDestroy. */
        @Volatile
        var userInitiatedStop: Boolean = false

        const val NOTIFICATION_ID   = 991
        const val CHANNEL_ID        = "sms_monitor_channel"
        private const val CHANNEL_NAME = "SMS ট্র্যাকিং সার্ভিস"

        const val ACTION_START = "ACTION_START_SERVICE"
        const val ACTION_STOP  = "ACTION_STOP_SERVICE"

        // Offline Queue SharedPrefs key (AppConfig থেকে)
        private val QUEUE_KEY = AppConfig.KEY_OFFLINE_INGEST_QUEUE
        private const val MAX_QUEUE_SIZE = 50 // সর্বোচ্চ ৫০টি পেন্ডিং রাখা হবে
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Foreground service onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "onStartCommand — Action: $action")

        if (action == ACTION_STOP) {
            userInitiatedStop = true
            isAlive = false
            stopMonitoring()
            return START_NOT_STICKY
        }

        userInitiatedStop = false
        isAlive = true

        // Foreground notification FIRST — Android requires this within ~5s of startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        // Lightweight sync work on main; heavy recovery off the service thread
        registerSmsReceiver()
        registerScreenReceiver()
        acquireWakeLock()
        startWakeLockRenewal()

        serviceScope.launch {
            startOfflineRecovery()
            startSocketConnection()
            NumberHeartbeatEngine.start(this@SmsMonitorService)
            online.paychek.app.services.foreground.SmsServiceGuard.scheduleWatchdog(this@SmsMonitorService)
        }

        return START_STICKY // সিস্টেম kill করলে নিজে পুনরায় চালু হবে
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SMS BroadcastReceiver রেজিস্ট্রেশন
    // ─────────────────────────────────────────────────────────────────────────
    private fun registerSmsReceiver() {
        if (smsReceiver != null) return // ইতিমধ্যে রেজিস্টার্ড

        smsReceiver = SmsReceiver { parsedPayment ->
            // SMS রিসিভ হলে শুধু নোটিফিকেশন আপডেট হবে, সিঙ্ক লজিক ProcessIncomingSmsUseCase হ্যান্ডেল করবে
            lastPaymentTime = formatTime(parsedPayment.smsTimestamp)
            updateNotification("✅ সর্বশেষ: ${parsedPayment.providerTag} ${parsedPayment.amount}৳ — $lastPaymentTime")
        }

        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            priority = 999
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(smsReceiver, filter)
        }
        Log.d(TAG, "SmsReceiver dynamically registered ✅")
    }

    /**
     * স্ক্রিন বন্ধ/লক হলে Guard-2 inbox poll তৎক্ষণাৎ চালায় —
     * OEM/Doze broadcast throttle-এ miss হওয়া SMS catch করে।
     */
    private fun registerScreenReceiver() {
        if (screenReceiver != null) return

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT -> {
                        Log.i(TAG, "Screen event (${intent.action}) — triggering immediate inbox poll")
                        SmsPollWorker.scheduleImmediate(ctx)
                        acquireWakeLock()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }
        Log.d(TAG, "Screen receiver registered ✅")
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Screen receiver unregister failed: ${e.message}")
            }
        }
        screenReceiver = null
    }

    private fun startWakeLockRenewal() {
        wakeLockRenewJob?.cancel()
        wakeLockRenewJob = serviceScope.launch {
            while (isActive) {
                delay(8 * 60 * 1000L)
                acquireWakeLock()
            }
        }
    }

    private fun startOfflineRecovery() {
        SyncWorker.schedule(this)
        SmsPollWorker.schedule(this)

        if (connectivityJob?.isActive != true) {
            connectivityJob = serviceScope.launch {
                ConnectivityService(this@SmsMonitorService).observe().collect { isOnline ->
                    if (isOnline) {
                        Log.i(TAG, "Network available — flushing offline SMS queue")
                        SmsReceiver.syncPendingQueue(this@SmsMonitorService)
                    }
                }
            }
        }

        serviceScope.launch {
            flushPendingOnStartup()
        }
    }

    private suspend fun flushPendingOnStartup() {
        val connectivity = ConnectivityService(this)
        val dao = AppDatabase.getInstance(this).pendingSmsDao()
        val nowMs = System.currentTimeMillis()
        val hasPending = dao.getPendingItemsForRetry(nowMs).isNotEmpty()

        if (!hasPending) return

        if (connectivity.isOnline()) {
            Log.i(TAG, "Service start — pending queue found, flushing")
            SmsReceiver.syncPendingQueueAndAwait(this)
        } else {
            Log.i(TAG, "Service start — pending queue found while offline, starting PingEngine")
            PingEngine.start(this)
        }
    }

    private fun unregisterSmsReceiver() {
        smsReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "SmsReceiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Unregister failed (already removed?): ${e.message}")
            }
        }
        smsReceiver = null
    }

    private fun getUserIdFromToken(token: String): String? {
        try {
            val parts = token.split(".")
            if (parts.size == 3) {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                val json = JSONObject(payload)
                return json.optString("userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JWT: ${e.message}")
        }
        return null
    }

    private fun startSocketConnection() {
        try {
            val token = SecurePreferences.decrypt(this, AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) return
            
            val userId = getUserIdFromToken(token) ?: return
            val deviceId = online.paychek.app.utils.DeviceIdHelper.getHashedAndroidId(this)
            
            val options = IO.Options.builder()
                .setQuery("userId=$userId&deviceId=$deviceId")
                .build()
                
            socket = IO.socket(AppConfig.SOCKET_URL, options)
            
            socket?.on(Socket.EVENT_CONNECT) {
                Log.i(TAG, "Socket.IO Connected to Room: $userId:$deviceId")
                SmsReceiver.syncPendingQueue(this@SmsMonitorService)
                NumberHeartbeatEngine.start(this@SmsMonitorService)
                NumberHeartbeatEngine.emitSocketDeviceNumbers(socket, this@SmsMonitorService)
            }
            
            socket?.on("sync_gateway_methods") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val dataArray = args[0]
                        val jsonStr = dataArray.toString()
                        online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsCache(this@SmsMonitorService, jsonStr)
                        Log.i(TAG, "✅ Push-Driven Cache Sync: Gateway methods updated via Socket.IO")
                        NumberHeartbeatEngine.emitSocketDeviceNumbers(socket, this@SmsMonitorService)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing socket push data: ${e.message}")
                    }
                }
            }
            
            socket?.on("sync_device_config") { args ->
                if (args.isEmpty()) return@on
                try {
                    val raw = args[0]
                    val json = when (raw) {
                        is org.json.JSONObject -> raw
                        else -> org.json.JSONObject(raw.toString())
                    }
                    val pin = json.optString("device_specific_pin", "")
                    if (pin.isNotEmpty() && pin != "null") {
                        SecurePreferences.encrypt(
                            this@SmsMonitorService,
                            AppConfig.KEY_DEVICE_SPECIFIC_PIN,
                            pin
                        )
                    } else {
                        SecurePreferences.remove(
                            this@SmsMonitorService,
                            AppConfig.KEY_DEVICE_SPECIFIC_PIN
                        )
                    }
                    val role = json.optString("device_role", "")
                    if (role.isNotEmpty()) {
                        SecurePreferences.encrypt(this@SmsMonitorService, "pcu_device_role", role)
                        SecurePreferences.encrypt(
                            this@SmsMonitorService,
                            AppConfig.KEY_IS_OWNER_DEVICE,
                            if (role == "owner") "true" else "false"
                        )
                    }
                    Log.i(TAG, "✅ Push-Driven Cache Sync: Device config updated via Socket.IO")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing sync_device_config: ${e.message}")
                }
            }

            socket?.on("force_template_sync") { args ->
                var serverVersion = 0L
                if (args.isNotEmpty()) {
                    try {
                        val raw = args[0]
                        val json = when (raw) {
                            is org.json.JSONObject -> raw
                            else -> org.json.JSONObject(raw.toString())
                        }
                        serverVersion = json.optLong("version", json.optLong("timestamp", 0L))
                    } catch (_: Exception) {
                        // ignore malformed payload
                    }
                }

                val localSync = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsLastSync(this@SmsMonitorService)
                if (serverVersion > 0 && localSync > 0 && serverVersion <= localSync) {
                    Log.i(TAG, "Template sync skipped: local cache current (local=$localSync, server=$serverVersion)")
                    return@on
                }

                Log.i(TAG, "✅ Push-Driven Cache Sync: Global template updated. Scheduling random delayed fetch to prevent server overload...")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val randomDelayMs = (500..2000).random().toLong()
                        delay(randomDelayMs)

                        val token = SecurePreferences.decrypt(this@SmsMonitorService, AppConfig.KEY_AUTH_TOKEN)
                        if (token.isNotEmpty()) {
                            val syncToken = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsLastSync(this@SmsMonitorService)
                            val res = RetrofitClient.gatewayApiService.getGatewayMethods("Bearer $token", syncToken)
                            if (res.isSuccessful) {
                                val body = res.body()
                                body?.dataVersion?.takeIf { it > 0 }?.let {
                                    online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsLastSync(this@SmsMonitorService, it)
                                }
                                body?.data?.let { methods ->
                                    val jsonStr = online.paychek.app.utils.GsonUtils.gson.toJson(methods)
                                    online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsCache(this@SmsMonitorService, jsonStr)
                                }
                            }

                            val resTemplates = RetrofitClient.gatewayApiService.getTemplates("Bearer $token", syncToken)
                            if (resTemplates.isSuccessful) {
                                val templateBody = resTemplates.body()
                                templateBody?.dataVersion?.takeIf { it > 0 }?.let {
                                    online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsLastSync(this@SmsMonitorService, it)
                                }
                                templateBody?.templates?.let { templates ->
                                    val jsonTemplates = online.paychek.app.utils.GsonUtils.gson.toJson(templates)
                                    online.paychek.app.data.local.prefs.PrefsHelper.setSmsTemplatesCache(this@SmsMonitorService, jsonTemplates)
                                }
                            }

                            Log.i(TAG, "✅ Background Sync Complete: Gateway methods and templates cache updated after ${randomDelayMs}ms.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Background Sync Failed: ${e.message}")
                    }
                }
            }
            
            socket?.connect()
        } catch (e: Exception) {
            Log.w(TAG, "Socket connection failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ব্যাকগ্রাউন্ডে পেমেন্ট SMS ট্র্যাক করে রাখে"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String = "পেমেন্ট SMS ব্যাকগ্রাউন্ডে ট্র্যাক হচ্ছে..."): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("💳 Paychek — সক্রিয়")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)  // সোয়াইপ করে বন্ধ করা যাবে না
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /** Notification live আপডেট করা (নতুন SMS পেলে) */
    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WakeLock helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Paychek::SmsMonitorWakeLock"
                ).apply { setReferenceCounted(false) }
            }
            if (wakeLock?.isHeld == true) {
                @Suppress("DEPRECATION")
                wakeLock?.release()
            }
            wakeLock?.acquire(10 * 60 * 1000L)
            Log.d(TAG, "WakeLock acquired/renewed ✅")
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock acquire failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            Log.d(TAG, "WakeLock released")
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release error: ${e.message}")
        }
    }

    private fun stopMonitoring() {
        Log.i(TAG, "SMS Monitoring সার্ভিস বন্ধ হচ্ছে")
        isAlive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────
    private fun formatTime(epochMs: Long): String =
        SimpleDateFormat("hh:mm a", Locale.forLanguageTag("bn-BD")).format(Date(epochMs))

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Foreground service onDestroy")
        isAlive = false
        NumberHeartbeatEngine.stop()
        wakeLockRenewJob?.cancel()
        wakeLockRenewJob = null
        releaseWakeLock()
        unregisterSmsReceiver()
        unregisterScreenReceiver()
        connectivityJob?.cancel()
        connectivityJob = null
        socket?.disconnect()
        socket?.off()
        serviceScope.cancel()

        if (!userInitiatedStop && online.paychek.app.data.local.prefs.PrefsHelper.isSmsServiceActive(this)) {
            Log.w(TAG, "Unexpected service death — scheduling recovery")
            online.paychek.app.services.foreground.SmsServiceGuard.enqueueImmediateRecovery(this)
        }
        userInitiatedStop = false
    }
}
