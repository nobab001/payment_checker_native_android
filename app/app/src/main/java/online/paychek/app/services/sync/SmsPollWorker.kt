package online.paychek.app.services.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import online.paychek.app.config.AppConfig
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.data.remote.dto.GatewayMethod
import online.paychek.app.services.sms.SmsInboxScanner
import online.paychek.app.services.sms.SmsRoutingEngine
import online.paychek.app.utils.SimSlotHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

/**
 * SmsPollWorker — Guard-2 ContentProvider SMS Inbox Polling Worker
 * ============================================================================
 * উদ্দেশ্য:
 *  Android 14/15-এ OS-level throttle বা OEM battery kill এর কারণে
 *  Guard-1 (BroadcastReceiver) miss করা payment SMS গুলো catch করা।
 *
 * Refactored (Phase 6 — SmsRoutingEngine):
 *  Worker-এর কাজ শুধু inbox scan করা এবং SmsRoutingEngine-এ পাঠানো।
 *  কোনো Business Logic, filter chain বা regex match এখানে নেই।
 *  সব routing decision SmsRoutingEngine নেয় (HISTORY / ARCHIVE / DROP)।
 *  Duplicate protection: rawBodyHash UNIQUE index — Guard-1 ও Guard-2
 *  একই SMS process করলে duplicate insert হয় না।
 *
 * Schedule:
 *  - প্রতি 15 মিনিটে (Android minimum)
 *  - KEEP policy — duplicate instance তৈরি হবে না
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
         */
        fun schedule(context: Context) {
            if (!PrefsHelper.isSmsServiceActive(context)) {
                cancel(context)
                return
            }
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
            if (!PrefsHelper.isSmsServiceActive(context)) {
                cancel(context)
                return
            }
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
         * Guard-2 WorkManager job cancel করা (periodic + immediate).
         */
        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_NAME)
            wm.cancelUniqueWork(WORK_NAME_IMMEDIATE)
            Log.i(TAG, "[Guard-2] SmsPollWorker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "[Guard-2] Poll cycle শুরু")

        // SMS monitor OFF → never ingest via inbox recovery (fail closed on toggle).
        if (!PrefsHelper.isSmsServiceActive(context)) {
            Log.i(TAG, "[Guard-2] SMS service inactive — cancelling poll work")
            cancel(context)
            return Result.success()
        }

        // ── READ_SMS permission check ──────────────────────────────────────────
        val hasReadSms = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadSms) {
            Log.w(TAG, "[Guard-2] READ_SMS permission নেই — poll skip")
            return Result.success()
        }

        // ── Gateway methods cache ──────────────────────────────────────────────
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

        // ── Inbox scan ─────────────────────────────────────────────────────────
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

        Log.i(TAG, "[Guard-2] ${candidates.size}টি SMS candidate পাওয়া গেছে — routing শুরু")

        // ── SharedPrefs থেকে SIM enabled state ────────────────────────────────
        val prefs       = context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        val sim1Enabled = prefs.getBoolean(AppConfig.KEY_SIM1_ENABLED, false)
        val sim2Enabled = prefs.getBoolean(AppConfig.KEY_SIM2_ENABLED, false)

        var processedCount = 0
        val cursorTracker = Guard2CursorTracker()

        for (candidate in candidates) {
            // User disabled monitoring mid-batch — stop processing/uploading; leave cursor.
            if (!PrefsHelper.isSmsServiceActive(context)) {
                Log.i(TAG, "[Guard-2] SMS turned OFF mid-poll — abort remaining candidates")
                cancel(context)
                break
            }

            var outcome = Guard2CursorTracker.Outcome.HANDLED
            try {
                // SIM slot resolve
                val simSlot   = SimSlotHelper.resolveSimSlot(context, candidate.subscriptionId)
                val simNumber = SimSlotHelper.resolveSimNumber(context, candidate.subscriptionId)

                // SIM slot filter (বৈধ skip → handled)
                val simAllowed = if (simSlot != null) {
                    if (simSlot == 1) sim1Enabled else sim2Enabled
                } else {
                    sim1Enabled || sim2Enabled
                }

                if (!simAllowed) {
                    Log.d(TAG, "[Guard-2] Skip — SIM slot ${simSlot ?: "?"} disabled (handled)")
                } else {
                    // পুরনো SMS filter (method তৈরির আগের)
                    // createdAt check এখানে রাখা হয়েছে কারণ এটা SmsInboxScanner-specific
                    val skipOld = shouldSkipOldSms(candidate, cachedMethods, simSlot)

                    if (skipOld) {
                        Log.d(TAG, "[Guard-2] Skip old SMS for '${candidate.sender}' — before method creation (handled)")
                    } else {
                        // ── SmsRoutingEngine: 3-Stage decision ────────────────────
                        // Worker শুধু SMS সংগ্রহ করে। সব routing decision engine নেয়।
                        val globalBlocked = PrefsHelper.getGlobalBlockedSenders(context)
                        val routeResult = SmsRoutingEngine.resolve(
                            sender        = candidate.sender,
                            body          = candidate.body,
                            simSlot       = simSlot,
                            cachedMethods = cachedMethods,
                            globalBlockedSenders = globalBlocked
                        )

                        if (routeResult == null) {
                            Log.d(TAG, "[Guard-2] No route for '${candidate.sender}' → DROP (handled)")
                        } else {
                            val payment = SmsRoutingEngine.buildPayload(
                                result    = routeResult,
                                sender    = candidate.sender,
                                body      = candidate.body,
                                timestamp = candidate.timestamp,
                                simSlot   = simSlot,
                                simNumber = simNumber
                            )

                            Log.i(TAG, "[Guard-2] Route=${routeResult.route} | Provider=${routeResult.matchedMethod.provider} → queuing")

                            // ProcessIncomingSmsUseCase দিয়ে queue-এ push
                            // rawBodyHash UNIQUE index Guard-1 duplicate ignore করবে
                            val result = online.paychek.app.domain.usecase.sms.ProcessIncomingSmsUseCase(context).execute(payment)
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
                                    if (e is PermanentSmsQueueException) {
                                        // Poison input — advance past so later SMS are not stalled forever.
                                        outcome = Guard2CursorTracker.Outcome.HANDLED
                                        Log.w(TAG, "[Guard-2] Permanent skip — smsId=${candidate.smsId}: ${e.message}")
                                    } else {
                                        // HMAC / unknown — do not advance cursor; remains recoverable.
                                        outcome = Guard2CursorTracker.Outcome.RETRYABLE_FAILURE
                                        Log.e(TAG, "[Guard-2] Retryable pipeline error — smsId=${candidate.smsId}: ${e.message}")
                                    }
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                outcome = if (e is PermanentSmsQueueException) {
                    Guard2CursorTracker.Outcome.HANDLED
                } else {
                    Guard2CursorTracker.Outcome.RETRYABLE_FAILURE
                }
                Log.e(TAG, "[Guard-2] Candidate processing error — smsId=${candidate.smsId}: ${e.message}", e)
            }

            cursorTracker.onCandidate(candidate.smsId, outcome)
        }

        // ── Cursor commit — only contiguous durably-handled prefix ─────────────
        if (cursorTracker.shouldCommit()) {
            scanner.commitCursor(cursorTracker.committableId)
        }

        Log.i(
            TAG,
            "[Guard-2] Poll complete — ${candidates.size} candidates | $processedCount নতুন queued | " +
                "cursor→${cursorTracker.committableId}" +
                if (cursorTracker.blocked) " (blocked — retryable failure, later poll will retry)" else ""
        )
        return Result.success()
    }

    /**
     * SMS টি method তৈরির আগের কিনা তা পরীক্ষা করে।
     * Guard-2 inbox scan-এ পুরনো SMS process না হওয়ার জন্য।
     */
    private fun shouldSkipOldSms(
        candidate: SmsInboxScanner.SmsCandidate,
        cachedMethods: List<GatewayMethod>,
        simSlot: Int?
    ): Boolean {
        val cleanSender = candidate.sender.trim().lowercase(java.util.Locale.US)
        // sender-এর যেকোনো matching method-এর createdAt দেখো
        val matchingForCreatedAt = cachedMethods.firstOrNull { method ->
            method.isEnabled == 1 &&
            (simSlot == null || method.simSlot == simSlot) &&
            (
                if (method.templateId == null) {
                    cleanSender == method.provider.trim().lowercase(java.util.Locale.US)
                } else {
                    val targetSender = method.senderId?.trim()?.lowercase(java.util.Locale.US)
                        ?: method.provider.lowercase(java.util.Locale.US)
                    cleanSender == targetSender
                }
            )
        } ?: return false

        val createdAtStr = matchingForCreatedAt.createdAt
        if (createdAtStr.isNullOrBlank()) return false
        val createdTimeMs = parseIsoDateToMillis(createdAtStr)
        return createdTimeMs > 0L && candidate.timestamp < createdTimeMs
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
