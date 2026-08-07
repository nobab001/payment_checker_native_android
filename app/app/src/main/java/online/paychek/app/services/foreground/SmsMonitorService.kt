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
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import online.paychek.app.config.AppConfig
import online.paychek.app.data.local.AppDatabase
import online.paychek.app.services.connectivity.ConnectivityService
import online.paychek.app.services.sms.SmsReceiver
import online.paychek.app.services.sync.NumberHeartbeatEngine
import online.paychek.app.services.sync.PingEngine
import online.paychek.app.services.sync.ServerProbeWorker
import online.paychek.app.services.sync.SmsPollWorker
import online.paychek.app.services.sync.SyncWorker
import online.paychek.app.utils.OemBackgroundHelper
import online.paychek.app.utils.SmsParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SmsMonitorService — lightweight Foreground Service (UX + soft sync helpers).
 *
 * SMS ingest is NOT owned here — Guard-1 static [SmsReceiver] is primary.
 * Guard-2 [SmsPollWorker] recovers missed inbox rows.
 * HTTP presence is [online.paychek.app.services.sync.HeartbeatWorker] (WorkManager).
 *
 * This FGS only shows "Monitoring Active", flushes offline queue when network returns,
 * and arms recovery workers. No exact-alarm keep-alive / panic restart loops.
 */
class SmsMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var screenReceiver: BroadcastReceiver? = null
    private var connectivityJob: Job? = null
    private var pendingFlushJob: Job? = null

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

        /**
         * Optional FGS notification status callback.
         * Manifest [SmsReceiver] is the sole SMS ingest path — this is display-only.
         */
        @Volatile
        var paymentStatusListener: ((SmsParser.ParsedPayment) -> Unit)? = null

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
            paymentStatusListener = null
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

        // Manifest SmsReceiver is the sole SMS ingest path (no dynamic register).
        paymentStatusListener = { parsedPayment ->
            lastPaymentTime = formatTime(parsedPayment.smsTimestamp)
            updateNotification("✅ সর্বশেষ: ${parsedPayment.providerTag} ${parsedPayment.amount}৳ — $lastPaymentTime")
        }
        registerScreenReceiver()
        // Missed-SMS recovery when FGS starts after process death.
        SmsPollWorker.scheduleImmediate(this)

        serviceScope.launch {
            startOfflineRecovery()
            val presenceTrigger = intent?.getStringExtra(SmsServiceGuard.EXTRA_PRESENCE_TRIGGER)
            online.paychek.app.utils.AccountEntitlementsStore.refresh(this@SmsMonitorService)
            // Arms WorkManager HeartbeatWorker + one immediate pulse (no in-process delay loop).
            NumberHeartbeatEngine.start(this@SmsMonitorService, presenceTrigger)
            SmsServiceGuard.scheduleWatchdog(this@SmsMonitorService)
            ServiceKeepAliveScheduler.cancel(this@SmsMonitorService)
        }

        return START_STICKY
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

    private fun startOfflineRecovery() {
        SyncWorker.schedule(this)
        SmsPollWorker.schedule(this)

        if (connectivityJob?.isActive != true) {
            connectivityJob = serviceScope.launch {
                var previousOnline: Boolean? = null
                ConnectivityService(this@SmsMonitorService).observe().collect { isOnline ->
                    if (isOnline) {
                        Log.i(TAG, "Network available — flushing offline SMS queue")
                        SmsReceiver.syncPendingQueue(this@SmsMonitorService)
                        // Only offline → online restores trigger immediate heartbeat (not WiFi ↔ mobile).
                        if (previousOnline == false) {
                            Log.i(TAG, "Internet restored — scheduling debounced presence heartbeat")
                            NumberHeartbeatEngine.pulseNetworkRestored(this@SmsMonitorService)
                        }
                    }
                    previousOnline = isOnline
                }
            }
        }

        serviceScope.launch {
            flushPendingOnStartup()
        }

        // While FGS is alive: periodically re-check pending queue even when the phone
        // never lost network (API server outage). Heartbeat + ServerProbeWorker cover
        // Doze; this covers the always-awake FGS case.
        if (pendingFlushJob?.isActive != true) {
            pendingFlushJob = serviceScope.launch {
                while (isActive) {
                    delay(5 * 60_000L)
                    try {
                        val dao = AppDatabase.getInstance(this@SmsMonitorService).pendingSmsDao()
                        val pending = dao.countPendingUnsynced()
                        if (pending > 0) {
                            Log.i(TAG, "Periodic pending check — $pending items, attempting flush")
                            dao.clearBackoffForPending()
                            val ok = SmsReceiver.syncPendingQueueAndAwait(this@SmsMonitorService)
                            if (!ok) {
                                PingEngine.start(this@SmsMonitorService)
                                ServerProbeWorker.scheduleSoon(this@SmsMonitorService)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Periodic pending flush error: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun flushPendingOnStartup() {
        val connectivity = ConnectivityService(this)
        val dao = AppDatabase.getInstance(this).pendingSmsDao()
        val nowMs = System.currentTimeMillis()
        val hasPending = dao.countPendingUnsynced() > 0

        if (!hasPending) return

        if (connectivity.isOnline()) {
            Log.i(TAG, "Service start — pending queue found, flushing")
            dao.clearBackoffForPending()
            dao.recoverOutageFailedItems()
            val ok = SmsReceiver.syncPendingQueueAndAwait(this)
            if (!ok) {
                PingEngine.start(this)
                ServerProbeWorker.scheduleSoon(this)
            }
        } else {
            Log.i(TAG, "Service start — pending queue found while offline, starting PingEngine + probe")
            PingEngine.start(this)
            ServerProbeWorker.scheduleSoon(this)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (OemBackgroundHelper.isAggressiveOem()) {
                NotificationManager.IMPORTANCE_DEFAULT
            } else {
                NotificationManager.IMPORTANCE_LOW
            }
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = "ব্যাকগ্রাউন্ডে পেমেন্ট SMS ট্র্যাক করে রাখে"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String = "পেমেন্ট SMS ব্যাকগ্রাউন্ডে ট্র্যাক হচ্ছে..."): Notification {
        val priority = if (OemBackgroundHelper.isAggressiveOem()) {
            NotificationCompat.PRIORITY_DEFAULT
        } else {
            NotificationCompat.PRIORITY_LOW
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("💳 Paycheck — সক্রিয়")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Notification live আপডেট করা (নতুন SMS পেলে) */
    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun stopMonitoring() {
        Log.i(TAG, "SMS Monitoring সার্ভিস বন্ধ হচ্ছে")
        NumberHeartbeatEngine.signalOffline(this)
        isAlive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────
    private fun formatTime(epochMs: Long): String =
        SimpleDateFormat("hh:mm a", Locale.forLanguageTag("bn-BD")).format(Date(epochMs))

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (userInitiatedStop || !online.paychek.app.data.local.prefs.PrefsHelper.isSmsServiceActive(this)) return
        // No panic FGS restart / exact alarm — re-arm WorkManager recovery only.
        Log.w(TAG, "Task removed — re-arming poll + heartbeat workers (no panic restart)")
        SmsServiceGuard.enqueueImmediateRecovery(this)
        SmsServiceGuard.scheduleWatchdog(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Foreground service onDestroy")
        isAlive = false
        paymentStatusListener = null
        NumberHeartbeatEngine.stop()
        unregisterScreenReceiver()
        connectivityJob?.cancel()
        connectivityJob = null
        pendingFlushJob?.cancel()
        pendingFlushJob = null
        serviceScope.cancel()

        if (!userInitiatedStop && online.paychek.app.data.local.prefs.PrefsHelper.isSmsServiceActive(this)) {
            // Soft recovery: workers only — SMS ingest does not need this FGS.
            Log.w(TAG, "FGS destroyed while monitoring ON — re-arming WorkManager workers")
            SmsServiceGuard.enqueueImmediateRecovery(this)
            SmsServiceGuard.scheduleWatchdog(this)
        }
        userInitiatedStop = false
    }
}
