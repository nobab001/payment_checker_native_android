package online.paychek.app.domain.usecase.sms

import android.content.Context
import android.util.Log
import online.paychek.app.data.local.AppDatabase
import online.paychek.app.data.local.entity.PendingSmsEntity
import online.paychek.app.services.connectivity.ConnectivityService
import online.paychek.app.services.sms.SmsReceiver
import online.paychek.app.utils.HmacHelper
import online.paychek.app.utils.SecurePreferences
import online.paychek.app.utils.SmsParser
import online.paychek.app.config.AppConfig
import online.paychek.app.utils.SmsSecuritySpec

/**
 * ProcessIncomingSmsUseCase — Domain orchestrator for a parsed payment SMS.
 *
 * Responsibility: accept a ParsedPayment from SmsReceiver and execute the
 * complete post-parse pipeline in the correct order.
 *
 * Pipeline:
 *  1. Validate rawBody integrity (SmsSecuritySpec Section 1)
 *  2. Generate HMAC signature
 *  3. Compute SHA-256 rawBodyHash
 *  4. Insert into Room offline queue
 *  5. Trigger sync if online
 *
 * Design contract:
 *  - Does NOT parse SMS — that is SmsReceiver's job.
 *  - Does NOT sync directly — delegates to SmsReceiver.syncPendingQueue().
 *  - Returns Result<Long> — the Room insert ID, or failure with reason.
 *  - All crypto follows SmsSecuritySpec exactly.
 *
 * Caller (Phase 5+):
 *   SmsReceiver.saveToOfflineQueueAndForward() → this use case.
 *   val result = ProcessIncomingSmsUseCase(context).execute(parsedPayment)
 *   result.onFailure { Log.e(TAG, "Queue insert failed: ${it.message}") }
 */
class ProcessIncomingSmsUseCase(private val context: Context) {

    private val TAG = "ProcessSmsUseCase"

    /**
     * Executes the full post-parse pipeline for one SMS.
     *
     * @param payment  ParsedPayment from SmsReceiver — rawBody MUST be immutable.
     * @return Result.success(insertId) if queued, Result.failure if aborted.
     */
    suspend fun execute(payment: SmsParser.ParsedPayment): Result<Long> {
        // 1. rawBody immutability guard (SmsSecuritySpec Section 1)
        if (!SmsSecuritySpec.isRawBodyValid(payment.rawBody)) {
            val msg = "rawBody is blank — aborting to prevent hash mismatch"
            Log.e(TAG, "[Guard] $msg")
            return Result.failure(IllegalArgumentException(msg))
        }

        return try {
            // 2. HMAC signature
            val secretKey = SecurePreferences.decrypt(context, SmsReceiver.KEY_HMAC_SECRET)
            val hmac = if (HmacHelper.isKeyValid(secretKey)) {
                HmacHelper.generate(payment.rawBody, secretKey) ?: "NO_HMAC"
            } else {
                Log.w(TAG, "[HMAC] Secret key missing or invalid")
                "NO_HMAC"
            }

            // 3. SHA-256 rawBodyHash (SmsSecuritySpec Section 3)
            val rawBodyHash = HmacHelper.sha256Hex(payment.rawBody)
            Log.d(TAG, "[Hash] rawBody[:40]='${payment.rawBody.take(40)}' hash=$rawBodyHash")

            // 4. Build entity and insert into Room queue
            val entity = PendingSmsEntity(
                rawBody       = payment.rawBody,
                rawBodyHash   = rawBodyHash,
                hmacSignature = hmac,
                providerTag   = payment.providerTag,
                trxId         = payment.trxId,
                amount        = payment.amount,
                senderNumber  = payment.senderNumber,
                simSlot       = payment.simSlot,
                simNumber     = payment.simNumber,
                smsTimestamp  = payment.smsTimestamp
            )

            val db         = AppDatabase.getInstance(context)
            val insertedId = db.pendingSmsDao().insert(entity)

            if (insertedId <= 0L) {
                // IGNORE conflict — duplicate rawBodyHash already in queue
                Log.d(TAG, "[Queue] Duplicate skipped (rawBodyHash already exists)")
                return Result.success(0L)
            }
            Log.d(TAG, "[Queue] Inserted — TrxID: ${payment.trxId} | id: $insertedId")

            // 5. Trigger sync if online
            val connectivity = ConnectivityService(context)
            if (connectivity.isOnline()) {
                SmsReceiver.syncPendingQueue(context)
            } else {
                Log.i(TAG, "[Queue] Offline — sync deferred to SyncWorker")
            }

            Result.success(insertedId)

        } catch (e: Exception) {
            Log.e(TAG, "[Queue] Pipeline error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
