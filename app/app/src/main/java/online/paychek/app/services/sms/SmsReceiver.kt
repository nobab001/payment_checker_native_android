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
 * SmsReceiver — Incoming SMS filter with 3-layer security and dynamic twin-mode logic.
 *
 * Security filtering logic:
 *  1. Condition 1 & 2 (mandatory): SIM Slot and Sender ID must match the active config.
 *  2. Condition 3 (twin-mode):
 *     - Official provider (Strict Mode): matchingKeyword verification + regex parse.
 *     - Custom provider (Backup Mode): dummy TrxID (BKUP-timestamp-hash), 0 amount, direct backup.
 *
 * Phase 5 refactor:
 *  saveToOfflineQueueAndForward() now delegates entirely to ProcessIncomingSmsUseCase.execute().
 *  All crypto, hashing, and Room insert logic has been removed from this class.
 *  SmsReceiver is now ONLY responsible for:
 *   - Receiving and filtering incoming broadcasts
 *   - Parsing SMS into ParsedPayment
 *   - Handing ParsedPayment to the use case
 *   - Hosting syncPendingQueue() for backward compatibility
 */
class SmsReceiver(
    private val onPaymentSmsReceived: ((SmsParser.ParsedPayment) -> Unit)? = null
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        // EncryptedSharedPreferences key for per-user secretKey
        const val KEY_HMAC_SECRET = "pcu_hmac_secret_key_v2"

        // -----------------------------------------------------------------------
        // GUARD: SHA-256 computation is ONLY done via HmacHelper.sha256Hex().
        // Never compute SHA-256 of rawBody here — there is ONE canonical location.
        // -----------------------------------------------------------------------

        /**
         * calculateNextRetryMs — exponential backoff delay for failed queue items.
         * retryCount 0 = first failure, caps at 6 hours after the 4th attempt.
         *
         * Schedule: 30s -> 2min -> 10min -> 1hr -> 6hr (cap)
         */
        // Transient (server-down) failure-এর ছোট fixed backoff — server ফিরলে দ্রুত flush নিশ্চিত করে।
        // PingEngine-ও fail হলে ৫s পর পর প্রাথমিক দ্রুত রিট্রাই করে; দুটো সামঞ্জস্যপূর্ণ রাখতে ৫s।
        private const val TRANSIENT_RETRY_MS = 5_000L

        fun calculateNextRetryMs(retryCount: Int, nowMs: Long): Long {
            val delayMs = when (retryCount) {
                0    -> 30_000L       // 30 seconds
                1    -> 120_000L      // 2 minutes
                2    -> 600_000L      // 10 minutes
                3    -> 3_600_000L    // 1 hour
                else -> 21_600_000L   // 6 hours (cap)
            }
            return nowMs + delayMs
        }

        /**
         * syncPendingQueue — pushes pending SMS items as soon as connectivity is available.
         * Call this when ConnectivityService.observe() emits true.
         *
         * Retry policy:
         *  - HTTP 2xx          -> markAsSynced()
         *  - HTTP 422          -> markPermanentlyFailed() — server parse failed, retrying is pointless
         *  - Other HTTP error  -> markRetryFailed() with exponential backoff
         *  - Network exception -> markRetryFailed() with exponential backoff
         */
        fun syncPendingQueue(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                syncPendingQueueInternal(context)
            }
        }

        /**
         * @return true if queue empty or all eligible items synced; false if failures remain
         */
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

                // দ্রষ্টব্য: retryCount-ভিত্তিক permanent-fail আর করা হয় না। server outage transient,
                // তাই আগের build-এ retryCount≥10 হয়ে ভুলভাবে আটকে থাকা আইটেমগুলোও এখানে পুনরুদ্ধার
                // করা হয় (permanent-fail কেবল server 422 unparseable-এর জন্য)।
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
                        val methodsJson = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsCache(context)
                        val methodsType = object : com.google.gson.reflect.TypeToken<List<online.paychek.app.data.remote.dto.GatewayMethod>>() {}.type
                        val cachedMethods: List<online.paychek.app.data.remote.dto.GatewayMethod> = try {
                            online.paychek.app.utils.GsonUtils.gson.fromJson(methodsJson, methodsType)
                        } catch (e: Exception) {
                            emptyList()
                        }

                        val requestItems = chunk.map { item ->
                            val matchedMethod = cachedMethods.find { it.provider == item.providerTag }
                            val isParseableVal = matchedMethod?.isParseable ?: 1
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
                                isParseable    = isParseableVal,
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
                                syncHadFailure = true
                            }
                            else -> {
                                chunk.forEach { item -> handleSyncFailure(dao, item, nowMs) }
                                Log.w(TAG, "[Sync] FAIL Bulk HTTP ${response.code()} — starting PingEngine")
                                online.paychek.app.services.sync.PingEngine.start(context)
                                syncHadFailure = true
                            }
                        }
                    } catch (e: Exception) {
                        chunk.forEach { item -> handleSyncFailure(dao, item, nowMs) }
                        Log.e(TAG, "[Sync] EXCEPTION Bulk Sync: ${e.message} — starting PingEngine")
                        online.paychek.app.services.sync.PingEngine.start(context)
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
            // Server outage / network / 5xx হলো transient global failure — per-item দোষ নয়।
            // তাই retryCount বাড়ানো হয় না (কখনো drop হবে না); শুধু ছোট fixed backoff (30s)
            // দেওয়া হয় যাতে PingEngine server-return টের পেলে দ্রুত সব একসাথে flush করতে পারে।
            dao.markTransientFailure(item.id, nowMs, nowMs + TRANSIENT_RETRY_MS)
        }
    }

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

    private suspend fun handleIncomingSms(context: Context, intent: Intent) {
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            // SIM Slot identification (multi-OEM extras)
            val subscriptionId = SimSlotHelper.resolveSubscriptionId(intent)
            val simSlot        = SimSlotHelper.resolveSimSlotFromIntent(context, intent)
            val simNumber      = SimSlotHelper.resolveSimNumber(context, subscriptionId)

            // Load config and gateway method cache from SharedPreferences
            val prefs = context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, false)) {
                Log.d(TAG, "SMS ignored: monitor service disabled by user")
                return
            }
            val sim1Enabled = prefs.getBoolean(AppConfig.KEY_SIM1_ENABLED, true)
            val sim2Enabled = prefs.getBoolean(AppConfig.KEY_SIM2_ENABLED, true)

            // Condition 1: SIM Slot filter (SIM Slot is active/enabled)
            if (simSlot != null) {
                val isSimEnabled = if (simSlot == 1) sim1Enabled else sim2Enabled
                if (!isSimEnabled) {
                    Log.d(TAG, "SMS ignored: SIM slot $simSlot is disabled in master settings.")
                    return
                }
            } else {
                if (!sim1Enabled && !sim2Enabled) {
                    Log.d(TAG, "SMS ignored: both SIM slots are disabled.")
                    return
                }
            }

            // Combine multi-part SMS parts into a single message body
            val sender = messages[0].originatingAddress ?: return
            val timestamp = messages[0].timestampMillis
            val body = StringBuilder().apply {
                for (msg in messages) {
                    msg.messageBody?.let { append(it) }
                }
            }.toString()

            // Read live cache dynamically
            val methodsJson = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsCache(context)
            val methodsType = object : com.google.gson.reflect.TypeToken<List<GatewayMethod>>() {}.type
            val cachedMethods: List<GatewayMethod> = try {
                online.paychek.app.utils.GsonUtils.gson.fromJson(methodsJson, methodsType)
            } catch (e: Exception) {
                emptyList()
            }

            Log.d(TAG, "Incoming SMS — From: $sender | SIM Slot: $simSlot | SubId: $subscriptionId | Length: ${body.length}")

            val cleanSender = sender.trim().lowercase(Locale.US)

            // ── 4-Step Verification Chain (Dynamic) ──────────────────────
            // Step 1: SIM Slot   — already filtered above
            // Step 2: Sender ID  — alphanumeric/phone sender match
            // Step 3: Sender Number — separate sender_number field match
            // Step 4: SMS Body   — matching keywords from template conditions
            val matchingMethod = cachedMethods.firstOrNull { method ->
                val isArchiveMode = (method.isParseable ?: 1) == 0
                // Step 1: Method must be enabled and SIM slot must match
                method.isEnabled == 1 &&
                (simSlot == null || method.simSlot == simSlot) &&
                // Step 2: Sender ID match (exact — no prefix/substring)
                (
                    if (method.templateId == null) {
                        cleanSender == method.provider.trim().lowercase(Locale.US)
                    } else {
                        val targetSender = method.senderId?.trim()?.lowercase(Locale.US) ?: method.provider.lowercase(Locale.US)
                        cleanSender == targetSender
                    }
                ) &&
                // Step 3: Sender Number match (if configured; exact)
                (
                    isArchiveMode ||
                    method.senderNumber.isNullOrBlank() ||
                    run {
                        val targetSenderNumber = method.senderNumber.trim().lowercase(Locale.US)
                        cleanSender == targetSenderNumber
                    }
                ) &&
                // Step 4: SMS Body keyword conditions (skip for custom/archive senders)
                (
                    isArchiveMode ||
                    method.matchingKeyword.isNullOrBlank() ||
                    method.matchingKeyword.split(",").map { it.trim() }.filter { it.isNotEmpty() }.any { keyword ->
                        body.contains(keyword, ignoreCase = true)
                    }
                )
            }

            if (matchingMethod == null) {
                Log.d(TAG, "Payment SMS ignored: No matching gateway config found for $sender or keywords mismatch")
                return
            }

            val isArchiveMode = (matchingMethod.isParseable ?: 1) == 0

                // ── Body Pattern Match Verification ───────────────────────────
                if (!isArchiveMode) {
                val patternsToTry = mutableListOf<String>()
                matchingMethod.customPatterns?.let { patternsToTry.addAll(it) }
                if (!matchingMethod.regexPattern.isNullOrBlank()) {
                    patternsToTry.add(matchingMethod.regexPattern)
                }

                var bodyMatched = false
                for (pattern in patternsToTry) {
                    if (pattern.isBlank()) continue
                    try {
                        val subPatterns = pattern.split("|||")
                        for (sub in subPatterns) {
                            if (sub.isBlank()) continue
                            val compiled = java.util.regex.Pattern.compile(sub, java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.DOTALL)
                            if (compiled.matcher(body.trim()).matches()) {
                                bodyMatched = true
                                break
                            }
                        }
                    } catch (_: Exception) { }
                    if (bodyMatched) break
                }

                if (!bodyMatched) {
                    Log.d(TAG, "Payment SMS ignored: Regex patterns did not match the full body structure.")
                    return
                }
                }

                // Build minimal payload — archive SMS goes to custom_sms_archives on server
                val parsedPayment = online.paychek.app.utils.SmsParser.ParsedPayment(
                    amount       = 0.0,
                    trxId        = "",
                    providerTag  = matchingMethod.provider,
                    senderNumber = sender,
                    rawBody      = body,
                    smsTimestamp  = timestamp,
                    simSlot      = simSlot,
                    simNumber    = simNumber ?: matchingMethod.number,
                    isCustomSender = isArchiveMode,
                    fullSms      = body,
                    isParseable  = if (isArchiveMode) 0 else (matchingMethod.isParseable ?: 1)
                )

                Log.i(TAG, "4 Conditions Met. Forwarding RAW SMS payload to queue. Provider: ${matchingMethod.provider}")
                saveToOfflineQueueAndForward(context, parsedPayment)

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException — no SIM read permission: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS: ", e)
        }
    }

    private suspend fun saveToOfflineQueueAndForward(context: Context, payment: SmsParser.ParsedPayment) {
        onPaymentSmsReceived?.invoke(payment)
        val result = ProcessIncomingSmsUseCase(context).execute(payment)
        result.onFailure { e ->
            Log.e(TAG, "[Queue] Pipeline failed for TrxID ${payment.trxId}: ${e.message}")
        }
    }

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