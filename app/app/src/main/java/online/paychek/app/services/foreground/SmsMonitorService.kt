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
import online.paychek.app.utils.SmsParser

class SmsMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var smsReceiver: SmsReceiver? = null

    companion object {
        private const val NOTIFICATION_ID = 991
        private const val CHANNEL_ID = "sms_monitor_channel"
        private const val CHANNEL_NAME = "SMS Tracking Service"

        const val ACTION_START = "ACTION_START_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_SERVICE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i("SmsMonitorService", "Foreground service onCreate called")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i("SmsMonitorService", "Foreground service onStartCommand: Action=$action")

        if (action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        // Start Foreground Notification
        startForeground(NOTIFICATION_ID, buildNotification())

        // Acquire WakeLock to prevent CPU sleep
        acquireWakeLock()

        // Register SMS BroadcastReceiver dynamically
        registerSmsReceiver()

        return START_STICKY
    }

    private fun startMonitoring() {
        // Handled in onStartCommand default flow
    }

    private fun stopMonitoring() {
        Log.i("SmsMonitorService", "Stopping SMS monitoring service")
        stopForeground(true)
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Paychek::SmsMonitorWakeLock"
                ).apply {
                    acquire(10 * 60 * 1000L) // Bounded acquisition fallback (10 mins)
                }
                Log.d("SmsMonitorService", "WakeLock acquired successfully")
            }
        } catch (e: Exception) {
            Log.e("SmsMonitorService", "Failed to acquire WakeLock: ", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d("SmsMonitorService", "WakeLock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e("SmsMonitorService", "Failed to release WakeLock: ", e)
        }
    }

    private fun registerSmsReceiver() {
        if (smsReceiver == null) {
            smsReceiver = SmsReceiver { parsedPayment ->
                uploadParsedPayment(parsedPayment)
            }

            val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
                priority = 999
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Must specify RECEIVER_EXPORTED since SMS is broadcast by the Android OS
                registerReceiver(smsReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(smsReceiver, filter)
            }
            Log.d("SmsMonitorService", "SmsReceiver dynamically registered")
        }
    }

    private fun unregisterSmsReceiver() {
        smsReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d("SmsMonitorService", "SmsReceiver unregistered")
            } catch (e: Exception) {
                Log.e("SmsMonitorService", "Failed to unregister receiver: ", e)
            }
        }
        smsReceiver = null
    }

    private fun uploadParsedPayment(parsed: SmsParser.ParsedPayment) {
        serviceScope.launch {
            try {
                // 1. Retrieve session auth token from SharedPreferences
                val sharedPrefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
                val token = sharedPrefs.getString(AppConfig.KEY_AUTH_TOKEN, "") ?: ""

                if (token.isEmpty()) {
                    Log.w("SmsMonitorService", "Cannot ingest payment. Auth Token is empty.")
                    return@launch
                }

                // Format Bearer Token
                val authHeader = "Bearer $token"

                // 2. Build Ingest DTO
                val request = PaymentIngestRequest(
                    amount = parsed.amount,
                    trxId = parsed.trxId,
                    providerTag = parsed.providerTag,
                    senderNumber = parsed.senderNumber,
                    receiverNumber = null, // Injected by SIM/slot configs if known
                    smsTimestamp = parsed.smsTimestamp,
                    rawBody = parsed.rawBody,
                    simSlot = null,
                    simNumber = null
                )

                Log.i("SmsMonitorService", "Uploading parsed payment telemetry for Trx: ${parsed.trxId}")

                // 3. Make Retrofit Network Call
                val response = RetrofitClient.paymentApiService.ingestPaymentSms(authHeader, request)

                if (response.isSuccessful && response.body() != null) {
                    val res = response.body()!!
                    if (res.isDuplicate) {
                        Log.i("SmsMonitorService", "Server reported duplicate for Trx: ${parsed.trxId}. Ignored.")
                    } else {
                        Log.i("SmsMonitorService", "Successfully ingested Trx: ${parsed.trxId} to API.")
                    }
                } else {
                    Log.e("SmsMonitorService", "Ingest API failed. Code: ${response.code()} Msg: ${response.message()}")
                }

            } catch (e: Exception) {
                Log.e("SmsMonitorService", "Network exception uploading transaction: ", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SMS capturing active in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("পেমেন্ট নোটিফিকেশন ট্র্যাকার")
            .setContentText("ইনকামিং পেমেন্ট SMS ব্যাকগ্রাউন্ডে ট্র্যাক করা হচ্ছে...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("SmsMonitorService", "Foreground service onDestroy called")
        releaseWakeLock()
        unregisterSmsReceiver()
        serviceScope.cancel()
    }
}
