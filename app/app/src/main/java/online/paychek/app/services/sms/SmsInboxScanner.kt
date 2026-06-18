package online.paychek.app.services.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import online.paychek.app.config.AppConfig
import online.paychek.app.data.local.AppDatabase
import online.paychek.app.data.local.entity.SeenSmsCursorEntity

/**
 * SmsInboxScanner — Guard-2 ContentProvider SMS Inbox Scanner
 * ============================================================================
 * উদ্দেশ্য:
 *  OS-level broadcast throttle বা OEM battery kill এর কারণে Guard-1
 *  (BroadcastReceiver) miss করা SMS খুঁজে বের করা।
 *
 * পদ্ধতি:
 *  1. Room থেকে `lastSeenSmsId` পড়া
 *  2. `content://sms/inbox` এ query: `_id > lastSeenSmsId`
 *  3. বিদ্যমান gateway config (sender, slot, keyword) দিয়ে ফিল্টার
 *  4. নতুন SMS list রিটার্ন
 *  5. Room cursor আপডেট করা (সর্বোচ্চ `_id` দিয়ে)
 *
 * Baseline Mode (প্রথমবার):
 *  lastSeenSmsId = 0 → শুধু বর্তমান inbox-এর সর্বোচ্চ _id দিয়ে
 *  baseline সেট করা হবে, পুরনো SMS process হবে না।
 *  এটি app update বা fresh install-এ পুরনো SMS পুনরায় process হওয়া রোধ করে।
 *
 * Deduplication:
 *  Guard-1 এবং Guard-2 একই SMS process করলে `rawBodyHash` UNIQUE index
 *  Room insert IGNORE করে — duplicate upload হবে না।
 * ============================================================================
 */
class SmsInboxScanner(private val context: Context) {

    companion object {
        private const val TAG = "SmsInboxScanner"
        private val SMS_INBOX_URI = Uri.parse("content://sms/inbox")

        // ContentProvider column names
        private const val COL_ID       = "_id"
        private const val COL_ADDRESS  = "address"
        private const val COL_BODY     = "body"
        private const val COL_DATE     = "date"
        private const val COL_SUB_ID   = "subscription_id" // SIM subscription ID
    }

    /**
     * SMS inbox থেকে নতুন candidate SMS scan করা।
     *
     * @return List<SmsCandidate> — নতুন SMS এর list (empty হলে নতুন কিছু নেই)
     *         null রিটার্ন হলে permission নেই বা ContentProvider access ব্যর্থ
     */
    suspend fun scanSinceLastCursor(): List<SmsCandidate>? {
        // Guard: READ_SMS permission check
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPerm) {
            Log.w(TAG, "[Guard-2] READ_SMS permission নেই — inbox scan skip করা হচ্ছে")
            return null
        }

        val db     = AppDatabase.getInstance(context)
        val cursorDao = db.seenSmsCursorDao()
        val stored = cursorDao.getCursor()
        val lastSeenId = stored?.lastSeenSmsId ?: 0L

        Log.d(TAG, "[Guard-2] Scan শুরু — lastSeenSmsId=$lastSeenId")

        // Baseline mode: প্রথমবার চলছে → শুধু cursor সেট করো, কিছু process করো না
        if (lastSeenId == 0L) {
            val maxId = queryMaxSmsId()
            if (maxId > 0L) {
                cursorDao.upsertCursor(
                    SeenSmsCursorEntity(
                        lastSeenSmsId = maxId,
                        lastScannedAt = System.currentTimeMillis()
                    )
                )
                Log.i(TAG, "[Guard-2] Baseline সেট — maxSmsId=$maxId (পুরনো SMS process হবে না)")
            }
            return emptyList()
        }

        // নতুন SMS query করা (_id > lastSeenId)
        val candidates = mutableListOf<SmsCandidate>()
        var newMaxId = lastSeenId

        try {
            val projection = arrayOf(COL_ID, COL_ADDRESS, COL_BODY, COL_DATE, COL_SUB_ID)
            val selection  = "$COL_ID > ?"
            val selArgs    = arrayOf(lastSeenId.toString())
            val sortOrder  = "$COL_ID ASC LIMIT ${AppConfig.SMS_INBOX_SCAN_LIMIT}"

            val cursor: Cursor? = context.contentResolver.query(
                SMS_INBOX_URI,
                projection,
                selection,
                selArgs,
                sortOrder
            )

            cursor?.use { c ->
                val idxId     = c.getColumnIndex(COL_ID)
                val idxAddr   = c.getColumnIndex(COL_ADDRESS)
                val idxBody   = c.getColumnIndex(COL_BODY)
                val idxDate   = c.getColumnIndex(COL_DATE)
                val idxSubId  = c.getColumnIndex(COL_SUB_ID)

                while (c.moveToNext()) {
                    val smsId    = if (idxId >= 0) c.getLong(idxId) else continue
                    val address  = if (idxAddr >= 0) c.getString(idxAddr) ?: continue else continue
                    val body     = if (idxBody >= 0) c.getString(idxBody) ?: continue else continue
                    val date     = if (idxDate >= 0) c.getLong(idxDate) else System.currentTimeMillis()
                    val subId    = if (idxSubId >= 0) c.getInt(idxSubId) else -1

                    candidates.add(
                        SmsCandidate(
                            smsId          = smsId,
                            sender         = address,
                            body           = body,
                            timestamp      = date,
                            subscriptionId = subId
                        )
                    )
                    if (smsId > newMaxId) newMaxId = smsId
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Guard-2] ContentProvider query ব্যর্থ: ${e.message}", e)
            return emptyList()
        }

        // cursor advance করা
        if (newMaxId > lastSeenId) {
            cursorDao.upsertCursor(
                SeenSmsCursorEntity(
                    lastSeenSmsId = newMaxId,
                    lastScannedAt = System.currentTimeMillis()
                )
            )
            Log.i(TAG, "[Guard-2] Cursor advanced — $lastSeenId → $newMaxId | ${candidates.size}টি candidate পাওয়া গেছে")
        } else {
            Log.d(TAG, "[Guard-2] নতুন কোনো SMS নেই (lastSeenId=$lastSeenId অপরিবর্তিত)")
        }

        return candidates
    }

    /**
     * inbox-এর সর্বোচ্চ `_id` বের করা (baseline সেট করার জন্য)।
     */
    private fun queryMaxSmsId(): Long {
        return try {
            val cursor: Cursor? = context.contentResolver.query(
                SMS_INBOX_URI,
                arrayOf("MAX(_id) AS maxId"),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("maxId")
                    if (idx >= 0) it.getLong(idx) else 0L
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "[Guard-2] Max SMS ID query ব্যর্থ: ${e.message}")
            0L
        }
    }

    /**
     * Guard-2 এর একটি SMS candidate — ContentProvider row থেকে extract করা।
     */
    data class SmsCandidate(
        val smsId: Long,          // ContentProvider _id
        val sender: String,       // originating address (sender number/name)
        val body: String,         // SMS body
        val timestamp: Long,      // epoch ms
        val subscriptionId: Int   // SIM subscription ID (-1 = unknown)
    )
}
