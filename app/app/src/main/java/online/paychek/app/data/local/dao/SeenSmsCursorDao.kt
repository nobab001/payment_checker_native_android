package online.paychek.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import online.paychek.app.data.local.entity.SeenSmsCursorEntity

/**
 * SeenSmsCursorDao — Guard-2 ContentProvider Polling Cursor DAO
 * ============================================================================
 * `sms_seen_cursor` টেবিলে সর্বদা একটিমাত্র row (id=1) থাকে।
 *
 * Pattern: upsert = INSERT OR REPLACE ON CONFLICT
 *   প্রথমবার insert হবে, পরবর্তীবার আপডেট হবে — কোড সরল থাকে।
 * ============================================================================
 */
@Dao
interface SeenSmsCursorDao {

    /**
     * বর্তমান cursor পড়া।
     * প্রথমবার চলার আগে row নেই → null রিটার্ন করে।
     * null মানে: আগে কখনো স্ক্যান হয়নি → baseline mode।
     */
    @Query("SELECT * FROM sms_seen_cursor WHERE id = 1 LIMIT 1")
    suspend fun getCursor(): SeenSmsCursorEntity?

    /**
     * নতুন cursor সেভ করা (upsert — INSERT OR REPLACE)।
     * Guard-2 প্রতিটি সফল স্ক্যানের শেষে এটি কল করে।
     *
     * @param entity  id=1, lastSeenSmsId=max_seen, lastScannedAt=now
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(entity: SeenSmsCursorEntity)
}
