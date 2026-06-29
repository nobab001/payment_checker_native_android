package online.paychek.app.services.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import online.paychek.app.config.AppConfig
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.data.remote.dto.GatewayMethod
import online.paychek.app.domain.usecase.sms.ProcessIncomingSmsUseCase
import online.paychek.app.services.sms.SmsInboxScanner
import online.paychek.app.utils.SmsParser
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * SmsPollWorker — Guard-2 ContentProvider SMS Inbox Polling Worker
 * ============================================================================
 * উদ্দেশ্য:
 *  Android 14/15-এ OS-level throttle বা OEM battery kill এর কারণে
 *  Guard-1 (BroadcastReceiver) miss করা payment SMS গুলো catch করা।
 *
 * পদ্ধতি:
 *  ১. READ_SMS permission চেক
 *  ২. SmsInboxScanner দিয়ে নতুন SMS candidate collect করা
 *  ③. বিদ্যমান gateway config (sim slot, sender, keyword) দিয়ে filter করা
 *  ④. ProcessIncomingSmsUseCase দিয়ে queue করা
 *     → rawBodyHash UNIQUE index Guard-1 এবং Guard-2 উভয়ের duplicate রোধ করে
 *
 * Schedule:
 *  - প্রতি 15 মিনিটে (Android minimum)
 *  - INTERNET connected হলেই চলবে
 *  - KEEP policy — duplicate instance তৈরি হবে না
 *
 * Integration:
 *  SmsMonitorService.onCreate() → SmsPollWorker.schedule()
 *  SmsMonitorService.onDestroy() → SmsPollWorker.cancel()
 * ============================================================================
 */
class SmsPollWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG       = "SmsPollWorker"
        private const val WORK_NAME = "paychek_sms_inbox_poll_guard2"

        /**
         * Guard-2 WorkManager job schedule করা।
         * Safe to call multiple times — KEEP policy prevents duplicates।
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SmsPollWorker>(
                AppConfig.SMS_POLL_WORKER_INTERVAL_MIN, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.i(TAG, "[Guard-2] SmsPollWorker scheduled — interval: ${AppConfig.SMS_POLL_WORKER_INTERVAL_MIN}min, policy: KEEP")
        }

        /**
         * Guard-2 WorkManager job cancel করা।
         * সাধারণত SmsMonitorService.onDestroy()-এ call করা হয়।
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "[Guard-2] SmsPollWorker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "[Guard-2] Poll cycle শুরু")

        // ── Guard 1: READ_SMS permission ──────────────────────────────────
        val hasReadSms = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadSms) {
            Log.w(TAG, "[Guard-2] READ_SMS permission নেই — poll skip")
            return Result.success() // retry করব না — permission না পেলে worker-ই fail করা উচিত না
        }

        // ── Guard 2: Gateway methods cache ────────────────────────────────
        val methodsJson = PrefsHelper.getGatewayMethodsCache(context)
        val methodsType = object : TypeToken<List<GatewayMethod>>() {}.type
        val cachedMethods: List<GatewayMethod> = try {
            online.paychek.app.utils.GsonUtils.gson.fromJson(methodsJson, methodsType) ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "[Guard-2] Gateway methods cache read ব্যর্থ: ${e.message}")
            emptyList()
        }

        if (cachedMethods.isEmpty()) {
            Log.d(TAG, "[Guard-2] Gateway methods cache ফাঁকা — poll skip")
            return Result.success()
        }

        // ── Inbox scan করা ───────────────────────────────────────────────
        val scanner    = SmsInboxScanner(context)
        val candidates = scanner.scanSinceLastCursor()

        if (candidates == null) {
            Log.w(TAG, "[Guard-2] Inbox scan নাল রিটার্ন করেছে (permission ছিল না?)")
            return Result.success()
        }

        if (candidates.isEmpty()) {
            Log.d(TAG, "[Guard-2] নতুন কোনো SMS নেই — poll complete")
            return Result.success()
        }

        Log.i(TAG, "[Guard-2] ${candidates.size}টি SMS candidate পাওয়া গেছে — filter শুরু")

        // ── SharedPrefs থেকে SIM enabled state পড়া ──────────────────────
        val prefs       = context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        val sim1Enabled = prefs.getBoolean(AppConfig.KEY_SIM1_ENABLED, true)
        val sim2Enabled = prefs.getBoolean(AppConfig.KEY_SIM2_ENABLED, true)

        var processedCount = 0

        for (candidate in candidates) {
            try {
                val cleanSender = candidate.sender.trim().lowercase(Locale.US)

                // ── Sender pre-filter (Dynamic) ─────────────────
                // ── SIM slot resolve করা ─────────────────────────────────
                val simSlot   = resolveSimSlotFromSubId(candidate.subscriptionId)
                val simNumber = resolveSimNumberFromSubId(candidate.subscriptionId)

                // ── SIM slot filter ──────────────────────────────────────
                if (simSlot != null) {
                    val isEnabled = if (simSlot == 1) sim1Enabled else sim2Enabled
                    if (!isEnabled) {
                        Log.d(TAG, "[Guard-2] Skip — SIM slot $simSlot disabled")
                        continue
                    }
                } else {
                    if (!sim1Enabled && !sim2Enabled) {
                        Log.d(TAG, "[Guard-2] Skip — উভয় SIM slot disabled")
                        continue
                    }
                }

                // ── 4-Step Verification Chain (Dynamic) ─────────────────────
                // Step 1: SIM Slot — already filtered above
                // Step 2: Sender ID — alphanumeric/phone sender match
                // Step 3: Sender Number — separate sender_number field match
                // Step 4: SMS Body — matching keywords from template conditions
                val matchingMethod = cachedMethods.firstOrNull { method ->
                    val isArchiveMode = (method.isParseable ?: 1) == 0
                    method.isEnabled == 1 &&
                    (simSlot == null || method.simSlot == simSlot) &&
                    (
                        if (method.templateId == null) {
                            cleanSender == method.provider.trim().lowercase(Locale.US)
                        } else {
                            val targetSender = method.senderId?.trim()?.lowercase(Locale.US)
                                ?: method.provider.lowercase(Locale.US)
                            cleanSender.contains(targetSender)
                        }
                    ) &&
                    (
                        isArchiveMode ||
                        method.senderNumber.isNullOrBlank() ||
                        run {
                            val targetSenderNumber = method.senderNumber.trim().lowercase(Locale.US)
                            cleanSender.contains(targetSenderNumber)
                        }
                    ) &&
                    (
                        isArchiveMode ||
                        method.matchingKeyword.isNullOrBlank() ||
                        method.matchingKeyword.split(",")
                            .map { it.trim() }.filter { it.isNotEmpty() }
                            .any { kw -> candidate.body.contains(kw, ignoreCase = true) }
                    )
                }

                if (matchingMethod == null) {
                    Log.d(TAG, "[Guard-2] Skip — no matching gateway config for '${candidate.sender}'")
                    continue
                }

                val isArchiveMode = (matchingMethod.isParseable ?: 1) == 0

                val createdAtStr = matchingMethod.createdAt
                if (!createdAtStr.isNullOrBlank()) {
                    val createdTimeMs = parseIsoDateToMillis(createdAtStr)
                    if (createdTimeMs > 0L && candidate.timestamp < createdTimeMs) {
                        Log.d(TAG, "[Guard-2] Skip old SMS for '${candidate.sender}' — timestamp (${candidate.timestamp}) is before creation time ($createdTimeMs)")
                        continue
                    }
                }

                // Forward RAW SMS payload — archive senders skip parsing
                val payment = if (isArchiveMode) {
                    SmsParser.ParsedPayment(
                        amount         = 0.0,
                        trxId          = "",
                        providerTag    = matchingMethod.provider,
                        senderNumber   = candidate.sender,
                        rawBody        = candidate.body,
                        smsTimestamp   = candidate.timestamp,
                        simSlot        = simSlot,
                        simNumber      = simNumber ?: matchingMethod.number,
                        isCustomSender = true,
                        fullSms        = candidate.body,
                        isParseable    = 0
                    )
                } else {
                    SmsParser.parseWithDynamicRegex(
                        body = candidate.body,
                        regexPattern = matchingMethod.regexPattern,
                        providerTag = matchingMethod.provider,
                        senderNumber = candidate.sender,
                        timestamp = candidate.timestamp,
                        simSlot = simSlot,
                        simNumber = simNumber ?: matchingMethod.number,
                        isCustomSender = false
                    ) ?: SmsParser.parseSms(candidate.sender, candidate.body, candidate.timestamp)?.copy(
                        simSlot = simSlot,
                        simNumber = simNumber ?: matchingMethod.number,
                        isCustomSender = false,
                        providerTag = matchingMethod.provider
                    ) ?: SmsParser.ParsedPayment(
                        amount         = 0.0,
                        trxId          = "",
                        providerTag    = matchingMethod.provider,
                        senderNumber   = candidate.sender,
                        rawBody        = candidate.body,
                        smsTimestamp   = candidate.timestamp,
                        simSlot        = simSlot,
                        simNumber      = simNumber ?: matchingMethod.number,
                        isCustomSender = false,
                        fullSms        = candidate.body,
                        isParseable    = matchingMethod.isParseable ?: 1
                    )
                }

                Log.i(TAG, "[Guard-2] 4 Conditions Met. Forwarding RAW payload to queue. Provider: ${matchingMethod.provider} | SIM: $simSlot | Processing via use case")

                // ── ProcessIncomingSmsUseCase দিয়ে pipeline এ push করা ──
                // rawBodyHash UNIQUE index Guard-1 এর duplicate silently ignore করবে
                val result = ProcessIncomingSmsUseCase(context).execute(payment)
                result.fold(
                    onSuccess = { id ->
                        if (id > 0L) {
                            processedCount++
                            Log.i(TAG, "[Guard-2] ✅ Queued — smsId=${candidate.smsId} | roomId=$id")
                        } else {
                            Log.d(TAG, "[Guard-2] Duplicate skip — smsId=${candidate.smsId} (rawBodyHash already exists)")
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "[Guard-2] Pipeline error — smsId=${candidate.smsId}: ${e.message}")
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "[Guard-2] Candidate processing error: ${e.message}", e)
            }
        }

        Log.i(TAG, "[Guard-2] Poll complete — ${candidates.size} candidates | $processedCount নতুন queued")
        return Result.success()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SIM slot utilities (ContentProvider subscription ID → physical slot)
    // ─────────────────────────────────────────────────────────────────────────

    private fun resolveSimSlotFromSubId(subscriptionId: Int): Int? {
        if (subscriptionId == -1) return null
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as SubscriptionManager
            val info = sm.getActiveSubscriptionInfo(subscriptionId) ?: return null
            (info.simSlotIndex + 1).takeIf { it in 1..2 }
        } catch (e: Exception) {
            Log.w(TAG, "[Guard-2] SIM slot resolve ব্যর্থ: ${e.message}")
            null
        }
    }

    private fun resolveSimNumberFromSubId(subscriptionId: Int): String? {
        if (subscriptionId == -1) return null
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as SubscriptionManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                sm.getPhoneNumber(subscriptionId).takeIf { it.isNotBlank() }
            } else {
                @Suppress("DEPRECATION")
                sm.getActiveSubscriptionInfo(subscriptionId)?.number?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseIsoDateToMillis(isoString: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.parse(isoString)?.time ?: 0L
        } catch (e: Exception) {
            try {
                val sdfNoMillis = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                sdfNoMillis.parse(isoString)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }
}
