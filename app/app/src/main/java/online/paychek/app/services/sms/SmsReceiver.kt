package online.paychek.app.services.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.local.AppDatabase
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.GatewayMethod
import online.paychek.app.domain.usecase.sms.ProcessIncomingSmsUseCase
import online.paychek.app.utils.SecurePreferences
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
        private const val EXTRA_SUBSCRIPTION_ID = "subscription"
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
                try {
                    val db  = AppDatabase.getInstance(context)
                    val dao = db.pendingSmsDao()
                    val nowMs = System.currentTimeMillis()
                    val pendingItems = dao.getPendingItemsForRetry(nowMs)
                    if (pendingItems.isEmpty()) return@launch

                    Log.i(TAG, "[Sync] ${pendingItems.size} pending SMS sync starting...")

                    val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
                    if (token.isBlank()) {
                        Log.w(TAG, "[Sync] Token missing — sync skipped")
                        return@launch
                    }

                    for (item in pendingItems) {
                        try {
                            val response = RetrofitClient.paymentApiService.ingestPaymentSms(
                                token = "Bearer $token",
                                request = online.paychek.app.data.remote.dto.PaymentIngestRequest(
                                    amount         = item.amount,
                                    trxId          = item.trxId,
                                    providerTag    = item.providerTag,
                                    senderNumber   = item.senderNumber ?: "",
                                    receiverNumber = null,
                                    smsTimestamp   = item.smsTimestamp,
                                    rawBody        = item.rawBody,
                                    simSlot        = item.simSlot,
                                    simNumber      = item.simNumber,
                                    hmacSignature  = item.hmacSignature,
                                    isOfflineSync  = true
                                )
                            )
                            when {
                                response.isSuccessful -> {
                                    dao.markAsSynced(item.id)
                                    Log.i(TAG, "[Sync] OK TrxID ${item.trxId} synced successfully")
                                }
                                response.code() == 422 -> {
                                    // Server HMAC OK but parse failed — retrying will NEVER succeed.
                                    // Remove from queue permanently.
                                    dao.markPermanentlyFailed(item.id, nowMs)
                                    Log.e(TAG, "[Sync] PERMANENT FAIL TrxID ${item.trxId} (422 SMS_PARSE_FAILED) — removed from queue")
                                }
                                else -> {
                                    val nextRetry = calculateNextRetryMs(item.retryCount, nowMs)
                                    dao.markRetryFailed(item.id, nowMs, nextRetry)
                                    Log.w(TAG, "[Sync] FAIL TrxID ${item.trxId}: HTTP ${response.code()} — retry #${item.retryCount + 1} scheduled at $nextRetry")
                                }
                            }
                        } catch (e: Exception) {
                            // Network-level failure — apply exponential backoff
                            val nextRetry = calculateNextRetryMs(item.retryCount, nowMs)
                            dao.markRetryFailed(item.id, nowMs, nextRetry)
                            Log.e(TAG, "[Sync] EXCEPTION TrxID ${item.trxId}: ${e.message} — retry at $nextRetry")
                        }
                    }

                    // Clean up synced entries older than 7 days
                    val cutoff = nowMs - (7L * 24 * 60 * 60 * 1000)
                    dao.deleteSyncedBefore(cutoff)
                } catch (e: Exception) {
                    Log.e(TAG, "[Sync] Queue sync failed: ${e.message}", e)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            // SIM Slot identification
            val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1)
            val simSlot        = resolveSimSlot(context, subscriptionId) // 1, 2, or null
            val simNumber      = resolveSimNumber(context, subscriptionId)

            // Load config and gateway method cache from SharedPreferences
            val prefs = context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
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

            val methodsJson = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsCache(context)
            val methodsType = object : com.google.gson.reflect.TypeToken<List<GatewayMethod>>() {}.type
            val cachedMethods: List<GatewayMethod> = try {
                com.google.gson.Gson().fromJson(methodsJson, methodsType)
            } catch (e: Exception) {
                emptyList()
            }

            for (msg in messages) {
                val sender    = msg.originatingAddress ?: continue
                val body      = msg.messageBody       ?: continue
                val timestamp = msg.timestampMillis

                Log.d(TAG, "Incoming SMS — From: $sender | SIM Slot: $simSlot | Length: ${body.length}")

                val cleanSender = sender.trim().lowercase(Locale.US)

                // Condition 2: Authorized Sender (bKash/Nagad) check
                val isBkashOrNagad = cleanSender.contains("bkash") || cleanSender.contains("nagad")
                if (!isBkashOrNagad) {
                    Log.d(TAG, "SMS ignored: Sender $sender is not bKash or Nagad.")
                    continue
                }

                // Condition 3: Check SIM slot, Sender ID, and keywords matching (using OR matching logic - any match)
                val matchingMethod = cachedMethods.firstOrNull { method ->
                    method.isEnabled == 1 &&
                    (simSlot == null || method.simSlot == simSlot) &&
                    method.provider.trim().lowercase(Locale.US).let { it.contains("bkash") || it.contains("nagad") } &&
                    (
                        if (method.templateId == null) {
                            cleanSender == method.provider.trim().lowercase(Locale.US)
                        } else {
                            val targetSender = method.senderId?.trim()?.lowercase(Locale.US) ?: method.provider.lowercase(Locale.US)
                            cleanSender.contains(targetSender)
                        }
                    ) &&
                    (
                        method.matchingKeyword.isNullOrBlank() ||
                        method.matchingKeyword.split(",").map { it.trim() }.filter { it.isNotEmpty() }.any { keyword ->
                            body.contains(keyword, ignoreCase = true)
                        }
                    )
                }

                if (matchingMethod == null) {
                    Log.d(TAG, "Payment SMS ignored: No matching gateway config found for $sender or keywords mismatch")
                    continue
                }

                // Forward RAW body payload directly with zeroed amount and empty trxId
                val finalPayment = SmsParser.ParsedPayment(
                    amount         = 0.0,
                    trxId          = "",
                    providerTag    = matchingMethod.provider,
                    senderNumber   = sender,
                    rawBody        = body,
                    smsTimestamp   = timestamp,
                    simSlot        = simSlot,
                    simNumber      = simNumber ?: matchingMethod.number,
                    isCustomSender = matchingMethod.templateId == null,
                    fullSms        = body
                )

                Log.i(TAG, "3 Conditions Met. Forwarding RAW SMS payload to queue. Provider: ${matchingMethod.provider}")
                saveToOfflineQueueAndForward(context, finalPayment)
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException — no SIM read permission: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS: ", e)
        }
    }

    // -------------------------------------------------------------------------
    // Phase 5: Delegate to ProcessIncomingSmsUseCase.
    // This method is now a thin dispatcher:
    //   1. Invoke the UI/service callback (unchanged — no regression)
    //   2. Launch the use case on IO dispatcher
    //
    // Removed from this method:
    //   - rawBody blank guard   (now in ProcessIncomingSmsUseCase)
    //   - HMAC generation       (now in ProcessIncomingSmsUseCase)
    //   - SHA-256 rawBodyHash   (now in ProcessIncomingSmsUseCase)
    //   - PendingSmsEntity build (now in ProcessIncomingSmsUseCase)
    //   - Room insert           (now in ProcessIncomingSmsUseCase)
    //   - Network check         (now in ProcessIncomingSmsUseCase via ConnectivityService)
    // -------------------------------------------------------------------------
    private fun saveToOfflineQueueAndForward(context: Context, payment: SmsParser.ParsedPayment) {
        // Invoke existing callback first — foreground service UI won't break
        onPaymentSmsReceived?.invoke(payment)

        // Delegate the entire queue pipeline to the domain use case
        CoroutineScope(Dispatchers.IO).launch {
            val result = ProcessIncomingSmsUseCase(context).execute(payment)
            result.onFailure { e ->
                Log.e(TAG, "[Queue] Pipeline failed for TrxID ${payment.trxId}: ${e.message}")
            }
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

    // -------------------------------------------------------------------------
    // subscriptionId -> Physical SIM Slot (1 or 2)
    // -------------------------------------------------------------------------
    private fun resolveSimSlot(context: Context, subscriptionId: Int): Int? {
        if (subscriptionId == -1) return null
        return try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val info = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                    ?: return null
                (info.simSlotIndex + 1).takeIf { it in 1..2 }
            } else {
                null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read SIM slot: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve SIM slot: ${e.message}")
            null
        }
    }

    // -------------------------------------------------------------------------
    // subscriptionId -> SIM phone number (null if unavailable)
    // -------------------------------------------------------------------------
    private fun resolveSimNumber(context: Context, subscriptionId: Int): String? {
        if (subscriptionId == -1) return null
        return try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val info = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    subscriptionManager.getPhoneNumber(subscriptionId).takeIf { it.isNotBlank() }
                } else {
                    @Suppress("DEPRECATION")
                    info?.number?.takeIf { it.isNotBlank() }
                }
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}