package online.paychek.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import online.paychek.app.data.local.dao.PendingSmsDao
import online.paychek.app.data.local.dao.SeenSmsCursorDao
import online.paychek.app.data.local.entity.PendingSmsEntity
import online.paychek.app.data.local.entity.SeenSmsCursorEntity

/**
 * AppDatabase — Room Database Singleton
 * ============================================================================
 * Offline-First SMS Queue এর জন্য local SQLite database।
 * Thread-safe double-checked locking singleton pattern ব্যবহার করা হয়েছে।
 *
 * Migration Policy:
 *   ⛔ fallbackToDestructiveMigration() — Production-এ BANNED।
 *      এটি ব্যবহার করলে App Update-এ user-এর offline queue মুছে যাবে।
 *   ✅ সব schema change-এ explicit Migration object তৈরি করতে হবে।
 *   ✅ প্রতিটি version increment-এ নতুন MIGRATION object যোগ করো।
 * ============================================================================
 */
@Database(
    entities = [
        PendingSmsEntity::class,
        SeenSmsCursorEntity::class   // v3: Guard-2 ContentProvider polling cursor
    ],
    version = 3,          // v2 → v3: sms_seen_cursor table for Guard-2 SMS inbox polling
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pendingSmsDao(): PendingSmsDao
    abstract fun seenSmsCursorDao(): SeenSmsCursorDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // ─────────────────────────────────────────────────────────────────
        // MIGRATION 1 → 2
        // Changes:
        //  • pending_sms_queue: rawBodyHash column যোগ (SHA-256 dedup key)
        //  • pending_sms_queue: trxId unique index → rawBodyHash unique index
        //  • pending_sms_queue: retryCount, lastAttemptAt, nextRetryAt columns
        //  • pending_sms_queue: isPermanentlyFailed column
        //
        // ⚠️  কখনো data destroy করা হচ্ছে না — additive migration।
        // ─────────────────────────────────────────────────────────────────
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. rawBodyHash column যোগ (SHA-256 based deduplication)
                database.execSQL(
                    "ALTER TABLE pending_sms_queue ADD COLUMN rawBodyHash TEXT NOT NULL DEFAULT ''"
                )

                // 2. Retry mechanism columns
                database.execSQL(
                    "ALTER TABLE pending_sms_queue ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE pending_sms_queue ADD COLUMN lastAttemptAt INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE pending_sms_queue ADD COLUMN nextRetryAt INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE pending_sms_queue ADD COLUMN isPermanentlyFailed INTEGER NOT NULL DEFAULT 0"
                )

                // 3. পুরনো trxId unique index drop করো
                //    (trxId empty/null হলে conflict হতে পারে)
                database.execSQL(
                    "DROP INDEX IF EXISTS index_pending_sms_queue_trxId"
                )

                // 4. নতুন rawBodyHash unique index তৈরি করো
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_pending_sms_queue_rawBodyHash ON pending_sms_queue (rawBodyHash)"
                )

                // 5. বিদ্যমান rows-এ rawBodyHash backfill করো
                //    (পুরনো data মুছে যাবে না — hash empty string থেকে আলাদা করা হবে না,
                //     কিন্তু sync হলে server duplicate check করবে)
                //    Note: SQLite-এ SHA256 built-in নেই, তাই পুরনো records-এ
                //          rawBodyHash = 'legacy_' + id দিয়ে unique রাখা হচ্ছে।
                //          এগুলো next sync-এ server-side-এ সঠিকভাবে process হবে।
                database.execSQL(
                    "UPDATE pending_sms_queue SET rawBodyHash = 'legacy_' || id WHERE rawBodyHash = ''"
                )
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // MIGRATION 2 → 3
        // Changes:
        //  • sms_seen_cursor টেবিল যোগ (Guard-2 ContentProvider polling cursor)
        //
        // ⚠️ Purely additive — existing pending_sms_queue data অক্ষুণ্ণ থাকে।
        // ─────────────────────────────────────────────────────────────────
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sms_seen_cursor (
                        id          INTEGER NOT NULL PRIMARY KEY,
                        lastSeenSmsId  INTEGER NOT NULL DEFAULT 0,
                        lastScannedAt  INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Thread-safe singleton instance।
         * Context হিসেবে Application context পাস করতে হবে।
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "paychek_offline_queue.db"
                )
                    // ✅ Explicit migrations — data কখনো destroy হবে না
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // ⛔ fallbackToDestructiveMigration REMOVED — Production-unsafe
                    //    এই line যোগ করলে App Update-এ offline queue মুছে যাবে
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
