package online.paychek.app.services.foreground

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.services.sms.SmsReceiver
import online.paychek.app.utils.SecurePreferences
import online.paychek.app.utils.SmsParser
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var smsReceiver: SmsReceiver? = null

    // সর্বশেষ সফল পেমেন্টের সময় (notification-এ দেখানোর জন্য)
    private var lastPaymentTime: String = "এখনো কোনো পেমেন্ট আসেনি"

    companion object {
        private const val TAG = "SmsMonitorService"

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
            stopMonitoring()
            return START_NOT_STICKY
        }

        // Foreground notification চালু করা
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

        // CPU Wakelock নেওয়া
        acquireWakeLock()

        // SMS BroadcastReceiver রেজিস্টার করা
        registerSmsReceiver()

        // ── গেটওয়ে মেথড কনফিগ ও ডিভাইস অ্যাক্টিভেশন সিঙ্ক করা ─────────────────────────────
        serviceScope.launch {
            while (isActive) {
                syncDeviceConfig()
                syncGatewayMethods()
                delay(30_000L) // every 30 seconds
            }
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

    // ─────────────────────────────────────────────────────────────────────────
    // গেটওয়ে মেথড কনফিগ সিঙ্ক করা ও ক্যাশ আপডেট
    // ─────────────────────────────────────────────────────────────────────────
    private suspend fun syncGatewayMethods() {
        try {
            val sharedPrefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            val token = SecurePreferences.decrypt(this, AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) return

            val response = RetrofitClient.gatewayApiService.getGatewayMethods("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                val methods = response.body()!!.data
                val json = com.google.gson.Gson().toJson(methods)
                online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsCache(this, json)
                Log.i(TAG, "✅ গেটওয়ে মেথড কনফিগ সফলভাবে সিঙ্ক করা হয়েছে (মোট: ${methods.size})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "গেটওয়ে মেথড সিঙ্ক করতে ব্যর্থ: ${e.message}")
        }
    }

    private suspend fun syncDeviceConfig() {
        try {
            val sharedPrefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            val token = SecurePreferences.decrypt(this, AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) return

            val response = RetrofitClient.gatewayApiService.getMyDeviceConfig("Bearer $token")
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val devConfig = response.body()!!.data
                val sim1Active = devConfig.simOneActive == 1
                val sim2Active = devConfig.simTwoActive == 1
                val isAppActive = devConfig.isAppActive == 1

                sharedPrefs.edit().apply {
                    putBoolean(AppConfig.KEY_SIM1_ENABLED, sim1Active)
                    putBoolean(AppConfig.KEY_SIM2_ENABLED, sim2Active)
                    putBoolean("pcu_is_app_active", isAppActive)
                    putBoolean("pcu_is_parent", devConfig.isParent == 1)
                    putString("pcu_custom_device_name", devConfig.customDeviceName)
                    apply()
                }

                SecurePreferences.encrypt(this@SmsMonitorService, "pcu_is_approved", if (devConfig.isApproved == 1) "true" else "false")
                SecurePreferences.encrypt(this@SmsMonitorService, "pcu_device_role", devConfig.deviceRole)

                Log.i(TAG, "✅ Device configuration synced. SIM1: $sim1Active, SIM2: $sim2Active, AppActive: $isAppActive")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync device configuration: ${e.message}")
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
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Paychek::SmsMonitorWakeLock"
            ).also {
                it.acquire(10 * 60 * 1000L) // ১০ মিনিট bounded, সার্ভিস রি-একোয়্যার করে
            }
            Log.d(TAG, "WakeLock acquired ✅")
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
        releaseWakeLock()
        unregisterSmsReceiver()
        serviceScope.cancel()
    }
}
