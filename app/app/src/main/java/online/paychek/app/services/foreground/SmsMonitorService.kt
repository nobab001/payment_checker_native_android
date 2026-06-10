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
import online.paychek.app.data.remote.dto.PaymentIngestRequest
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
 * নতুন ফিচার (ধাপ ১):
 *  ১. SIM Slot সংখ্যা সহ ingest request পাঠানো হয়
 *  ২. Offline Queue — নেটওয়ার্ক না থাকলে SharedPrefs-এ JSON সেভ, পরে retry
 *  ৩. Notification-এ সর্বশেষ পেমেন্টের সময় দেখানো হয়
 *  ৪. START_STICKY — সিস্টেম kill করলে নিজে থেকে পুনরায় চালু হয়
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
        startForeground(NOTIFICATION_ID, buildNotification())

        // CPU Wakelock নেওয়া
        acquireWakeLock()

        // SMS BroadcastReceiver রেজিস্টার করা
        registerSmsReceiver()

        // ── পুরনো Offline Queue retry করা ──────────────────────────────
        serviceScope.launch {
            delay(3_000L) // সার্ভিস পুরোপুরি চালু হতে ৩ সেকেন্ড দেওয়া
            retryOfflineQueue()
        }

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
            // SMS পাওয়ার সাথে সাথে upload করার চেষ্টা
            uploadOrQueuePayment(parsedPayment)
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
    // পেমেন্ট Upload অথবা Offline Queue-তে সেভ
    // ─────────────────────────────────────────────────────────────────────────
    private fun uploadOrQueuePayment(parsed: SmsParser.ParsedPayment) {
        serviceScope.launch {
            val success = attemptUpload(parsed)
            if (!success) {
                Log.w(TAG, "Upload ব্যর্থ — Offline Queue-তে সেভ করা হচ্ছে: ${parsed.trxId}")
                addToOfflineQueue(parsed)
                updateNotification("⚠️ অফলাইন — ${parsed.trxId} কিউতে সেভ আছে")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Retrofit দিয়ে API-তে upload করার চেষ্টা
    // সফল হলে true, ব্যর্থ হলে false রিটার্ন
    // ─────────────────────────────────────────────────────────────────────────
    private suspend fun attemptUpload(parsed: SmsParser.ParsedPayment): Boolean {
        return try {
            val token = SecurePreferences.decrypt(this, AppConfig.KEY_AUTH_TOKEN)

            if (token.isEmpty()) {
                Log.w(TAG, "Auth Token নেই — upload করা যাচ্ছে না")
                return false
            }

            val request = PaymentIngestRequest(
                amount         = parsed.amount,
                trxId          = parsed.trxId,
                providerTag    = parsed.providerTag,
                senderNumber   = parsed.senderNumber,
                receiverNumber = parsed.simNumber, // SIM নম্বরকে receiver হিসেবে পাঠানো
                smsTimestamp   = parsed.smsTimestamp,
                rawBody        = parsed.rawBody,
                simSlot        = parsed.simSlot,
                simNumber      = parsed.simNumber
            )

            Log.i(TAG, "Upload চেষ্টা — TrxID: ${parsed.trxId} | SIM: ${parsed.simSlot}")

            val response = RetrofitClient.paymentApiService
                .ingestPaymentSms("Bearer $token", request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.isDuplicate) {
                    Log.i(TAG, "Duplicate TrxID — সার্ভার ignore করেছে: ${parsed.trxId}")
                } else {
                    // সফল — notification আপডেট করা
                    lastPaymentTime = formatTime(parsed.smsTimestamp)
                    updateNotification("✅ সর্বশেষ: ${parsed.providerTag} ${parsed.amount}৳ — $lastPaymentTime")
                    Log.i(TAG, "✅ Upload সফল — TrxID: ${parsed.trxId}")
                }
                true
            } else {
                Log.e(TAG, "API Error ${response.code()} — ${response.message()}")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Network Exception: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OFFLINE QUEUE — SharedPreferences-এ JSON Array হিসেবে পেন্ডিং সেভ করা
    // ─────────────────────────────────────────────────────────────────────────
    private fun addToOfflineQueue(parsed: SmsParser.ParsedPayment) {
        try {
            val prefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            val queueJson = prefs.getString(QUEUE_KEY, "[]") ?: "[]"
            val queue = JSONArray(queueJson)

            // Queue সর্বোচ্চ সীমা চেক
            if (queue.length() >= MAX_QUEUE_SIZE) {
                Log.w(TAG, "Offline Queue পূর্ণ ($MAX_QUEUE_SIZE) — সবচেয়ে পুরনো বাদ দেওয়া হচ্ছে")
                // সবচেয়ে পুরনো আইটেম বাদ দেওয়া (FIFO)
                val newQueue = JSONArray()
                for (i in 1 until queue.length()) newQueue.put(queue.get(i))
                newQueue.put(parsedToJson(parsed))
                prefs.edit().putString(QUEUE_KEY, newQueue.toString()).apply()
            } else {
                queue.put(parsedToJson(parsed))
                prefs.edit().putString(QUEUE_KEY, queue.toString()).apply()
            }

            Log.d(TAG, "Offline Queue-এ যোগ — মোট পেন্ডিং: ${queue.length() + 1}")
        } catch (e: Exception) {
            Log.e(TAG, "Offline Queue সেভ ব্যর্থ: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OFFLINE QUEUE RETRY — সার্ভিস চালু হলে পেন্ডিং আইটেম পাঠানোর চেষ্টা
    // ─────────────────────────────────────────────────────────────────────────
    private suspend fun retryOfflineQueue() {
        try {
            val prefs     = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            val queueJson = prefs.getString(QUEUE_KEY, "[]") ?: "[]"
            val queue     = JSONArray(queueJson)

            if (queue.length() == 0) return

            Log.i(TAG, "Offline Queue retry শুরু — মোট পেন্ডিং: ${queue.length()}")
            val successfulIndexes = mutableListOf<Int>()

            for (i in 0 until queue.length()) {
                val item   = queue.getJSONObject(i)
                val parsed = jsonToParsed(item) ?: continue

                val success = attemptUpload(parsed)
                if (success) {
                    successfulIndexes.add(i)
                    delay(500L) // প্রতিটি retry-এর মাঝে ছোট বিরতি
                }
            }

            // সফল আইটেমগুলো Queue থেকে বাদ দেওয়া
            if (successfulIndexes.isNotEmpty()) {
                val newQueue = JSONArray()
                for (i in 0 until queue.length()) {
                    if (i !in successfulIndexes) newQueue.put(queue.get(i))
                }
                prefs.edit().putString(QUEUE_KEY, newQueue.toString()).apply()
                Log.i(TAG, "Retry সফল — ${successfulIndexes.size}টি পাঠানো হয়েছে, " +
                        "${newQueue.length()}টি এখনো পেন্ডিং")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Queue retry-তে ত্রুটি: ${e.message}")
        }
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
                sharedPrefs.edit().putString(AppConfig.KEY_GATEWAY_METHODS_CACHE, json).apply()
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

                Log.i(TAG, "✅ Device configuration synced. SIM1: $sim1Active, SIM2: $sim2Active, AppActive: $isAppActive")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync device configuration: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ParsedPayment ↔ JSON রূপান্তর (Queue সিরিয়ালাইজেশন)
    // ─────────────────────────────────────────────────────────────────────────
    private fun parsedToJson(p: SmsParser.ParsedPayment): JSONObject = JSONObject().apply {
        put("amount",       p.amount)
        put("trxId",        p.trxId)
        put("providerTag",  p.providerTag)
        put("senderNumber", p.senderNumber)
        put("rawBody",      p.rawBody)
        put("smsTimestamp", p.smsTimestamp)
        put("simSlot",      p.simSlot ?: JSONObject.NULL)
        put("simNumber",    p.simNumber ?: JSONObject.NULL)
    }

    private fun jsonToParsed(json: JSONObject): SmsParser.ParsedPayment? = try {
        SmsParser.ParsedPayment(
            amount        = json.getDouble("amount"),
            trxId         = json.getString("trxId"),
            providerTag   = json.getString("providerTag"),
            senderNumber  = json.getString("senderNumber"),
            rawBody       = json.getString("rawBody"),
            smsTimestamp  = json.getLong("smsTimestamp"),
            simSlot       = if (json.isNull("simSlot")) null else json.getInt("simSlot"),
            simNumber     = if (json.isNull("simNumber")) null else json.getString("simNumber")
        )
    } catch (e: Exception) {
        Log.e(TAG, "JSON parse error: ${e.message}")
        null
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
        SimpleDateFormat("hh:mm a", Locale("bn", "BD")).format(Date(epochMs))

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Foreground service onDestroy")
        releaseWakeLock()
        unregisterSmsReceiver()
        serviceScope.cancel()
    }
}
