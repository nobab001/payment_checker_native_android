package online.paychek.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import online.paychek.app.data.local.entity.PendingSmsEntity

/**
 * PendingSmsDao — Offline SMS Queue DAO
 * ============================================================================
 * CRUD operations for the local pending_sms_queue table.
 *
 * Retry Strategy:
 *   Backoff কে getPendingItemsForRetry()-এ enforce করা হয়:
 *   শুধুমাত্র nextRetryAt <= now AND retryCount < 10 এমন rows fetch হবে।
 *   markRetryFailed() — backoff timestamp সহ retryCount বাড়ায়।
 *   markPermanentlyFailed() — SMS_PARSE_FAILED বা 10+ retry-এর পরে।
 * ============================================================================
 */
@Dao
interface PendingSmsDao {

    /**
     * নতুন pending SMS সেভ করো।
     * rawBodyHash UNIQUE index থাকায় একই SMS দুইবার insert করলে silently ignore হবে।
     * trxId empty হলেও কোনো conflict নেই — deduplication rawBodyHash-এ।
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sms: PendingSmsEntity): Long

    /**
     * Sync-এর জন্য eligible pending items নিয়ে আসো।
     * Conditions:
     *  • isSynced = false (এখনো push হয়নি)
     *  • isPermanentlyFailed = false (server 422 parse-fail নয়)
     *  • nextRetryAt <= now (backoff window শেষ হয়েছে)
     * পুরনো SMS আগে পাঠানো হবে (FIFO)।
     * দ্রষ্টব্য: retryCount দিয়ে আর drop করা হয় না — server outage (network/5xx) transient,
     * তাই কেবল server 422 (unparseable) permanently failed হয়; বাকি সব শেষ পর্যন্ত রিট্রাই হয়।
     */
    @Query("""
        SELECT * FROM pending_sms_queue
        WHERE isSynced = 0
          AND isPermanentlyFailed = 0
          AND nextRetryAt <= :nowMs
        ORDER BY smsTimestamp ASC
    """)
    suspend fun getPendingItemsForRetry(nowMs: Long): List<PendingSmsEntity>

    /**
     * একটি সফলভাবে sync হওয়া SMS কে synced হিসেবে চিহ্নিত করো।
     */
    @Query("UPDATE pending_sms_queue SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)

    /**
     * Sync fail হলে retry metadata আপডেট করো।
     * Exponential Backoff:
     *   Retry 1 → 30s
     *   Retry 2 → 120s (2m)
     *   Retry 3 → 600s (10m)
     *   Retry 4 → 3600s (1h)
     *   Retry 5+ → 21600s (6h)
     *
     * @param id        — entity id
     * @param nowMs     — current timestamp (epoch ms)
     * @param nextRetryMs — পরবর্তী retry কখন করা যাবে (epoch ms)
     */
    @Query("""
        UPDATE pending_sms_queue
        SET retryCount = retryCount + 1,
            lastAttemptAt = :nowMs,
            nextRetryAt = :nextRetryMs
        WHERE id = :id
    """)
    suspend fun markRetryFailed(id: Int, nowMs: Long, nextRetryMs: Long)

    /**
     * Transient failure (server down / network / 5xx) — global outage, per-item দোষ নয়।
     * retryCount বাড়ানো হয় না (যাতে কখনো permanent-fail হয়ে drop না হয়);
     * শুধু একটি ছোট fixed backoff দেওয়া হয় যাতে server ফিরলেই দ্রুত আবার eligible হয়।
     */
    @Query("""
        UPDATE pending_sms_queue
        SET lastAttemptAt = :nowMs,
            nextRetryAt = :nextRetryMs
        WHERE id = :id
    """)
    suspend fun markTransientFailure(id: Int, nowMs: Long, nextRetryMs: Long)

    /**
     * Permanently failed হিসেবে চিহ্নিত করো।
     * ব্যবহার:
     *  • Server 422 (SMS_PARSE_FAILED) দিলে — template নেই, retry অর্থহীন
     *  • retryCount >= 10 হলে — অনেকবার চেষ্টা হয়েছে, manual resync দরকার
     */
    @Query("""
        UPDATE pending_sms_queue
        SET isPermanentlyFailed = 1,
            lastAttemptAt = :nowMs
        WHERE id = :id
    """)
    suspend fun markPermanentlyFailed(id: Int, nowMs: Long)

    /**
     * একটি নির্দিষ্ট rawBodyHash queue তে আছে কিনা চেক করো।
     * trxId-এর বদলে rawBodyHash — reliable duplicate check।
     */
    @Query("SELECT COUNT(*) FROM pending_sms_queue WHERE rawBodyHash = :rawBodyHash")
    suspend fun countByRawBodyHash(rawBodyHash: String): Int

    /**
     * ইতিমধ্যে sync হয়ে যাওয়া পুরনো entries পরিষ্কার করো (৭ দিনের পুরনো)।
     */
    @Query("DELETE FROM pending_sms_queue WHERE isSynced = 1 AND createdAt < :cutoffMs")
    suspend fun deleteSyncedBefore(cutoffMs: Long)

    /**
     * Permanently failed entries-এর সংখ্যা — diagnostic/debug এর জন্য।
     */
    @Query("SELECT COUNT(*) FROM pending_sms_queue WHERE isPermanentlyFailed = 1")
    suspend fun countPermanentlyFailed(): Int

    /** সব unsynced (backoff সহ) — Sync UI মোট pending দেখাতে। */
    @Query("""
        SELECT COUNT(*) FROM pending_sms_queue
        WHERE isSynced = 0 AND isPermanentlyFailed = 0
    """)
    suspend fun countPendingUnsynced(): Int

    /** Backoff window-এ আটকে থাকা আইটেম। */
    @Query("""
        SELECT COUNT(*) FROM pending_sms_queue
        WHERE isSynced = 0
          AND isPermanentlyFailed = 0
          AND nextRetryAt > :nowMs
    """)
    suspend fun countWaitingBackoff(nowMs: Long): Int

    /**
     * Manual "Sync Now" — backoff মুছে সব pending এখনই eligible করে।
     */
    @Query("""
        UPDATE pending_sms_queue
        SET nextRetryAt = 0
        WHERE isSynced = 0 AND isPermanentlyFailed = 0
    """)
    suspend fun clearBackoffForPending()

    /**
     * Manual retry — permanently failed আইটেম আবার চেষ্টা করার জন্য রিসেট।
     */
    @Query("""
        UPDATE pending_sms_queue
        SET isPermanentlyFailed = 0,
            retryCount = 0,
            nextRetryAt = 0,
            lastAttemptAt = 0
        WHERE isPermanentlyFailed = 1 AND isSynced = 0
    """)
    suspend fun resetPermanentlyFailed(): Int

    /**
     * Outage-এ ভুলভাবে আটকে থাকা আইটেম পুনরুদ্ধার।
     * পুরনো build server-down-কে per-item retry ভেবে retryCount বাড়িয়ে ৬ঘণ্টা backoff দিত এবং
     * retryCount≥10 হলে permanently-failed করত। এখন সেগুলো আবার eligible করা হয়:
     *  • retryCount≥10 হয়ে permanent-fail হওয়া আইটেম un-fail করা হয় (server 422 নয়, retryCount সেখানে ছোট)।
     *  • backoff window ভবিষ্যতে থাকলে তা এখনই খুলে দেওয়া হয়।
     * server 422 (unparseable, retryCount ছোট) permanently-failed-ই থাকে।
     */
    @Query("""
        UPDATE pending_sms_queue
        SET isPermanentlyFailed = 0,
            nextRetryAt = 0
        WHERE isSynced = 0
          AND (
            (isPermanentlyFailed = 1 AND retryCount >= 10)
            OR (isPermanentlyFailed = 0 AND nextRetryAt > 0)
          )
    """)
    suspend fun recoverOutageFailedItems()

    /**
     * retryCount >= 10 কিন্তু এখনো permanently failed নয় — zombie rows cleanup।
     */
    @Query("""
        UPDATE pending_sms_queue
        SET isPermanentlyFailed = 1,
            lastAttemptAt = :nowMs
        WHERE isSynced = 0
          AND isPermanentlyFailed = 0
          AND retryCount >= 10
    """)
    suspend fun markExhaustedRetriesAsPermanentlyFailed(nowMs: Long)
}
