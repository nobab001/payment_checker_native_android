package online.paychek.app.services.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
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
        private const val WORK_NAME_IMMEDIATE = "paychek_sms_inbox_poll_immediate"

        /**
         * Guard-2 WorkManager job schedule করা।
         * Safe to call multiple times — KEEP policy prevents duplicates।
         * Inbox scan-এ নেটওয়ার্ক লাগে না — শুধু Room queue-তে লেখা হয়।
         */
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<SmsPollWorker>(
                AppConfig.SMS_POLL_WORKER_INTERVAL_MIN, TimeUnit.MINUTES
            )
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
         * স্ক্রিন বন্ধ/লক হলে বা broadcast miss হলে তৎক্ষণাৎ inbox স্ক্যান।
         */
        fun scheduleImmediate(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<SmsPollWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "[Guard-2] Immediate inbox poll enqueued")
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

        // ── Cursor high-water mark ─────────────────────────────────────────
        // candidates ASC (_id) ক্রমে। প্রতিটি SMS "durably handled" (queued /
        // duplicate / বৈধ skip) হলে committableId এগোয়। প্রথম hard-failure
        // (exception বা pipeline failure) হলে cursorBlocked=true — এর পর আর
        // cursor এগোয় না, ফলে ওই SMS ও তার পরের সব SMS পরের poll-এ আবার
        // scan হবে (data-loss রোধ)।
        var committableId = -1L
        var cursorBlocked = false

        for (candidate in candidates) {
            // durably handled না হলে (retry দরকার) false হবে
            var handled = true
            try {
                val cleanSender = candidate.sender.trim().lowercase(Locale.US)

                // ── SIM slot resolve করা ─────────────────────────────────
                val simSlot   = resolveSimSlotFromSubId(candidate.subscriptionId)
                val simNumber = resolveSimNumberFromSubId(candidate.subscriptionId)

                // ── SIM slot filter (বৈধ skip → handled) ─────────────────
                val simAllowed = if (simSlot != null) {
                    if (simSlot == 1) sim1Enabled else sim2Enabled
                } else {
                    sim1Enabled || sim2Enabled
                }

                if (!simAllowed) {
                    Log.d(TAG, "[Guard-2] Skip — SIM slot ${simSlot ?: "?"} disabled (handled)")
                } else {
                    // ── 4-Step Verification Chain (Dynamic) ───────────────
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
                        Log.d(TAG, "[Guard-2] Skip — no matching gateway config for '${candidate.sender}' (handled)")
                    } else {
                        val isArchiveMode = (matchingMethod.isParseable ?: 1) == 0

                        // পুরনো SMS (method তৈরির আগের) → বৈধ skip
                        val createdAtStr = matchingMethod.createdAt
                        val tooOld = !createdAtStr.isNullOrBlank() && run {
                            val createdTimeMs = parseIsoDateToMillis(createdAtStr)
                            createdTimeMs > 0L && candidate.timestamp < createdTimeMs
                        }

                        if (tooOld) {
                            Log.d(TAG, "[Guard-2] Skip old SMS for '${candidate.sender}' — before method creation (handled)")
                        } else {
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

                            Log.i(TAG, "[Guard-2] 4 Conditions Met. Forwarding RAW payload to queue. Provider: ${matchingMethod.provider} | SIM: $simSlot")

                            // ── ProcessIncomingSmsUseCase দিয়ে queue এ push ──
                            // rawBodyHash UNIQUE index Guard-1 এর duplicate ignore করবে
                            val result = ProcessIncomingSmsUseCase(context).execute(payment)
                            result.fold(
                                onSuccess = { id ->
                                    if (id > 0L) {
                                        processedCount++
                                        Log.i(TAG, "[Guard-2] ✅ Queued — smsId=${candidate.smsId} | roomId=$id")
                                    } else {
                                        Log.d(TAG, "[Guard-2] Duplicate skip — smsId=${candidate.smsId} (already queued)")
                                    }
                                },
                                onFailure = { e ->
                                    // queue insert ব্যর্থ → retry দরকার, cursor এগোবে না
                                    handled = false
                                    Log.e(TAG, "[Guard-2] Pipeline error — smsId=${candidate.smsId}: ${e.message}")
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // অপ্রত্যাশিত exception → retry দরকার, cursor এগোবে না
                handled = false
                Log.e(TAG, "[Guard-2] Candidate processing error — smsId=${candidate.smsId}: ${e.message}", e)
            }

            // Cursor শুধু ধারাবাহিক durably-handled SMS পর্যন্ত এগোয়।
            if (!handled) cursorBlocked = true
            if (!cursorBlocked) committableId = candidate.smsId
        }

        // ── Cursor commit — শুধুমাত্র নিশ্চিতভাবে handle হওয়া SMS পর্যন্ত ──
        if (committableId > 0L) {
            scanner.commitCursor(committableId)
        }

        Log.i(TAG, "[Guard-2] Poll complete — ${candidates.size} candidates | $processedCount নতুন queued | cursor→$committableId${if (cursorBlocked) " (blocked, বাকিগুলো পরের poll-এ retry হবে)" else ""}")
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
