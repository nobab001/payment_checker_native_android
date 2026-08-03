package online.paychek.app.services.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.paychek.app.config.AppConfig
import online.paychek.app.data.local.AppDatabase
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.GatewayMethod
import online.paychek.app.domain.usecase.sms.ProcessIncomingSmsUseCase
import online.paychek.app.utils.SecurePreferences
import online.paychek.app.utils.SimSlotHelper
import online.paychek.app.utils.SmsParser
import java.util.Locale
import java.util.regex.Pattern

/**
 * SmsReceiver — Guard-1: Incoming SMS BroadcastReceiver
 *
 * Refactored (Phase 6 — SmsRoutingEngine):
 *  Receiver-এর কাজ শুধু SMS সংগ্রহ করা এবং SmsRoutingEngine-এ পাঠানো।
 *  কোনো Business Logic, filter chain বা regex match এখানে নেই।
 *  সব routing decision SmsRoutingEngine নেয় (HISTORY / ARCHIVE / DROP)।
 *
 * Guard-1 Pipeline:
 *  SMS_RECEIVED_ACTION → WakeLock → SmsRoutingEngine.resolve() →
 *  SmsRoutingEngine.buildPayload() → ProcessIncomingSmsUseCase → Queue → Sync
 *
 * syncPendingQueue() এবং syncPendingQueueAndAwait() backward-compatible রাখা হয়েছে।
 */
class SmsReceiver(
    private val onPaymentSmsReceived: ((SmsParser.ParsedPayment) -> Unit)? = null
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        const val KEY_HMAC_SECRET = "pcu_hmac_secret_key_v2"

        // -----------------------------------------------------------------------
        // GUARD: SHA-256 computation is ONLY done via HmacHelper.sha256Hex().
        // -----------------------------------------------------------------------

        /**
         * calculateNextRetryMs — exponential backoff delay for failed queue items.
         * Schedule: 30s -> 2min -> 10min -> 1hr -> 6hr (cap)
         */
        private const val TRANSIENT_RETRY_MS = 5_000L

        fun calculateNextRetryMs(retryCount: Int, nowMs: Long): Long {
            val delayMs = when (retryCount) {
                0    -> 30_000L
                1    -> 120_000L
                2    -> 600_000L
                3    -> 3_600_000L
                else -> 21_600_000L
            }
            return nowMs + delayMs
        }

        /**
         * syncPendingQueue — connectivity restore হলে pending SMS push করে।
         */
        fun syncPendingQueue(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                syncPendingQueueInternal(context)
            }
        }

        suspend fun syncPendingQueueAndAwait(context: Context): Boolean {
            return withContext(Dispatchers.IO) {
                syncPendingQueueInternal(context)
            }
        }

        private suspend fun syncPendingQueueInternal(context: Context): Boolean {
            return try {
                val db  = AppDatabase.getInstance(context)
                val dao = db.pendingSmsDao()
                val nowMs = System.currentTimeMillis()

                dao.recoverOutageFailedItems()

                val pendingItems = dao.getPendingItemsForRetry(nowMs)
                if (pendingItems.isEmpty()) return true

                Log.i(TAG, "[Sync] ${pendingItems.size} pending SMS sync starting...")

                val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
                if (token.isBlank()) {
                    Log.w(TAG, "[Sync] Token missing — sync skipped")
                    return false
                }

                var syncHadFailure = false
                val chunks = pendingItems.chunked(50)
                for (chunk in chunks) {
                    try {
                        val requestItems = chunk.map { item ->
                            // Persist-at-queue-time flag (Room v4+). Do not re-derive from cache
                            // — empty/stale cache previously defaulted ARCHIVE → 1 → 422.
                            online.paychek.app.data.remote.dto.PaymentIngestRequest(
                                amount         = 0.0,
                                trxId          = "",
                                providerTag    = item.providerTag,
                                senderNumber   = item.senderNumber ?: "",
                                receiverNumber = null,
                                smsTimestamp   = item.smsTimestamp,
                                rawBody        = item.rawBody,
                                simSlot        = item.simSlot,
                                simNumber      = item.simNumber,
                                isParseable    = item.isParseable,
                                hmacSignature  = item.hmacSignature,
                                isOfflineSync  = true
                            )
                        }

                        val response = RetrofitClient.paymentApiService.ingestPaymentSmsBulk(
                            token = "Bearer $token",
                            request = online.paychek.app.data.remote.dto.BulkPaymentIngestRequest(requestItems)
                        )

                        when {
                            response.isSuccessful -> {
                                chunk.forEach { dao.markAsSynced(it.id) }
                                Log.i(TAG, "[Sync] OK Bulk synced ${chunk.size} items successfully")
                                online.paychek.app.services.sync.NumberHeartbeatEngine
                                    .noteSmsUploadSuccess(context)
                            }
                            response.code() == 422 -> {
                                chunk.forEach { dao.markPermanentlyFailed(it.id, nowMs) }
                                Log.w(TAG, "[Sync] HTTP 422 — ${chunk.size} items marked permanently failed")
                            }
                            response.code() == 503 -> {
                                chunk.forEach { item -> handleSyncFailure(dao, item, nowMs) }
                                Log.w(TAG, "[Sync] HTTP 503 QUEUE_UNAVAILABLE — keeping offline, starting PingEngine")
                                online.paychek.app.services.sync.PingEngine.start(context)
                                online.paychek.app.services.sync.ServerProbeWorker.scheduleSoon(context)
                                syncHadFailure = true
                            }
                            else -> {
                                chunk.forEach { item -> handleSyncFailure(dao, item, nowMs) }
                                Log.w(TAG, "[Sync] FAIL Bulk HTTP ${response.code()} — starting PingEngine")
                                online.paychek.app.services.sync.PingEngine.start(context)
                                online.paychek.app.services.sync.ServerProbeWorker.scheduleSoon(context)
                                syncHadFailure = true
                            }
                        }
                    } catch (e: Exception) {
                        chunk.forEach { item -> handleSyncFailure(dao, item, nowMs) }
                        Log.e(TAG, "[Sync] EXCEPTION Bulk Sync: ${e.message} — starting PingEngine")
                        online.paychek.app.services.sync.PingEngine.start(context)
                        online.paychek.app.services.sync.ServerProbeWorker.scheduleSoon(context)
                        syncHadFailure = true
                    }
                }

                val cutoff = nowMs - (7L * 24 * 60 * 60 * 1000)
                dao.deleteSyncedBefore(cutoff)

                !syncHadFailure && dao.getPendingItemsForRetry(System.currentTimeMillis()).isEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "[Sync] Queue sync failed: ${e.message}", e)
                false
            }
        }

        private suspend fun handleSyncFailure(
            dao: online.paychek.app.data.local.dao.PendingSmsDao,
            item: online.paychek.app.data.local.entity.PendingSmsEntity,
            nowMs: Long
        ) {
            dao.markTransientFailure(item.id, nowMs, nowMs + TRANSIENT_RETRY_MS)
        }
    }

    // =========================================================================
    // Guard-1: BroadcastReceiver entry point
    // =========================================================================

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // স্ক্রিন বন্ধ/লক থাকলে process kill হওয়া রোধ — goAsync + WakeLock
        val pendingResult = goAsync()
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Paychek::SmsReceiveWakeLock")
            .apply {
                setReferenceCounted(false)
                acquire(60_000L)
            }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleIncomingSms(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Async SMS processing error: ${e.message}", e)
            } finally {
                try {
                    if (wakeLock.isHeld) wakeLock.release()
                } catch (_: Exception) { }
                pendingResult.finish()
            }
        }
    }

    /**
     * SMS পড়ে SmsRoutingEngine-এ পাঠায়।
     * Receiver-এ কোনো Business Logic নেই — শুধু data collection।
     */
    private suspend fun handleIncomingSms(context: Context, intent: Intent) {
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            // SIM Slot identification (multi-OEM extras)
            val subscriptionId = SimSlotHelper.resolveSubscriptionId(intent)
            val simSlot        = SimSlotHelper.resolveSimSlotFromIntent(context, intent)
            val simNumber      = SimSlotHelper.resolveSimNumber(context, subscriptionId)

            // SMS monitor active?
            val prefs = context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, false)) {
                Log.d(TAG, "SMS ignored: monitor service disabled by user")
                return
            }

            // SIM slot enabled?
            val sim1Enabled = prefs.getBoolean(AppConfig.KEY_SIM1_ENABLED, false)
            val sim2Enabled = prefs.getBoolean(AppConfig.KEY_SIM2_ENABLED, false)
            if (simSlot != null) {
                val isSimEnabled = if (simSlot == 1) sim1Enabled else sim2Enabled
                if (!isSimEnabled) {
                    Log.d(TAG, "SMS ignored: SIM slot $simSlot is disabled.")
                    return
                }
            } else {
                if (!sim1Enabled && !sim2Enabled) {
                    Log.d(TAG, "SMS ignored: both SIM slots are disabled.")
                    return
                }
            }

            // Combine multi-part SMS into single body
            val sender    = messages[0].originatingAddress ?: return
            val timestamp = messages[0].timestampMillis
            val body      = StringBuilder().apply {
                for (msg in messages) msg.messageBody?.let { append(it) }
            }.toString()

            Log.d(TAG, "[Guard-1] Incoming SMS — From: $sender | SIM: $simSlot | SubId: $subscriptionId | Len: ${body.length}")

            // Load gateway method cache (SharedPrefs — no server call)
            val methodsJson = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsCache(context)
            val methodsType = object : com.google.gson.reflect.TypeToken<List<GatewayMethod>>() {}.type
            val cachedMethods: List<GatewayMethod> = try {
                online.paychek.app.utils.GsonUtils.gson.fromJson(methodsJson, methodsType)
            } catch (e: Exception) { emptyList() }

            // ── SmsRoutingEngine: 3-Stage decision ────────────────────────────
            // Stage-1: Collect Candidates (all matching methods, not firstOrNull)
            // Stage-2: Resolve Route (Template Match → HISTORY | Archive → ARCHIVE | DROP)
            // Stage-3: Build Payload (isParseable=1/0 translate হয় server payload-এ)
            val globalBlocked = online.paychek.app.data.local.prefs.PrefsHelper
                .getGlobalBlockedSenders(context)
            val routeResult = SmsRoutingEngine.resolve(
                sender        = sender,
                body          = body,
                simSlot       = simSlot,
                cachedMethods = cachedMethods,
                globalBlockedSenders = globalBlocked
            ) ?: run {
                Log.d(TAG, "[Guard-1] No route for sender='$sender' → DROP")
                return
            }

            val parsedPayment = SmsRoutingEngine.buildPayload(
                result    = routeResult,
                sender    = sender,
                body      = body,
                timestamp = timestamp,
                simSlot   = simSlot,
                simNumber = simNumber
            )

            Log.i(TAG, "[Guard-1] Route=${routeResult.route} | Provider=${routeResult.matchedMethod.provider} → queuing")
            saveToOfflineQueueAndForward(context, parsedPayment)

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException — no SIM read permission: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS: ", e)
        }
    }

    private suspend fun saveToOfflineQueueAndForward(context: Context, payment: SmsParser.ParsedPayment) {
        onPaymentSmsReceived?.invoke(payment)
        // FGS may listen for notification status text only (ingest does not require FGS).
        try {
            online.paychek.app.services.foreground.SmsMonitorService.paymentStatusListener?.invoke(payment)
        } catch (_: Exception) { }
        val result = ProcessIncomingSmsUseCase(context).execute(payment)
        result.onFailure { e ->
            Log.e(TAG, "[Queue] Pipeline failed for TrxID ${payment.trxId}: ${e.message}")
        }
    }

    @Suppress("unused") // backward-compat: referenced by other legacy call sites if any
    private fun parseWithCustomRegex(body: String, patternStr: String, providerTag: String, timestamp: Long): SmsParser.ParsedPayment? {
        return try {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val groupCount = matcher.groupCount()
                val amountStr = matcher.group(1)?.replace(",", "") ?: "0.0"
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                val trxId: String
                val senderNumber: String
                if (groupCount >= 3) {
                    senderNumber = matcher.group(2) ?: "Unknown"
                    trxId = matcher.group(3) ?: ""
                } else {
                    trxId = if (groupCount >= 2) matcher.group(2) ?: "" else ""
                    senderNumber = "Unknown"
                }
                if (trxId.isNotEmpty()) {
                    SmsParser.ParsedPayment(
                        amount       = amount,
                        trxId        = trxId.uppercase(Locale.US),
                        providerTag  = providerTag,
                        senderNumber = senderNumber,
                        rawBody      = body,
                        smsTimestamp = timestamp
                    )
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing with custom regex: ${e.message}")
            null
        }
    }
}