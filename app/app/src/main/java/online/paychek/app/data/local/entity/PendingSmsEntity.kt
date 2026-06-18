package online.paychek.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PendingSmsEntity — Offline SMS Queue Room Entity
 * ============================================================================
 * উদ্দেশ্য : নেট না থাকলে পেমেন্ট SMS + HMAC signature লোকাল SQLite-এ সেভ করা।
 *           অনলাইন হওয়া মাত্রই সার্ভারে push করা হবে।
 *
 * isSynced = false → queue তে আছে, পাঠানো হয়নি
 * isSynced = true  → সার্ভারে পাঠানো সম্পন্ন
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  UNIQUE INDEX: rawBodyHash  (SHA-256 of rawBody)                    │
 * │                                                                     │
 * │  কেন trxId নয়?                                                      │
 * │  • কিছু provider SMS-এ trxId সবসময় থাকে না                         │
 * │  • trxId = "" বা null হলে UNIQUE conflict হতে পারে                  │
 * │  • SHA-256(rawBody) — একই SMS-এ সবসময় identical hash হবে           │
 * │  • Provider-independent reliable duplicate detection                │
 * └─────────────────────────────────────────────────────────────────────┘
 * ============================================================================
 */
@Entity(
    tableName = "pending_sms_queue",
    indices = [
        Index(value = ["rawBodyHash"], unique = true) // SHA-256 based deduplication
    ]
)
data class PendingSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    /** পিওর RAW SMS body — HMAC verify এর জন্য অপরিবর্তিত রাখা হয় */
    val rawBody: String,

    /**
     * SHA-256(rawBody) — Unique duplicate detection key।
     * Server-side এও একই formula ব্যবহার হয়।
     * trxId-এর চেয়ে নির্ভরযোগ্য: trxId absent/empty SMS-তেও কাজ করে।
     */
    val rawBodyHash: String,

    /** HMAC-SHA256(rawBody, secretKey) — সার্ভারে পাঠানো হবে verify এর জন্য */
    val hmacSignature: String,

    /** বিকাশ / নগদ / রকেট / উপায় */
    val providerTag: String,

    /** ট্রানজেকশন আইডি (local parsed — server re-parses independently) */
    val trxId: String,

    /** টাকার পরিমাণ */
    val amount: Double,

    /** প্রেরকের নম্বর (মাস্কড হতে পারে) */
    val senderNumber: String?,

    /** SIM slot: 1 অথবা 2 */
    val simSlot: Int?,

    /** SIM-এর নিজস্ব ফোন নম্বর */
    val simNumber: String?,

    /** SMS আসার মূল timestamp (epoch ms) */
    val smsTimestamp: Long,

    /** এই রেকর্ড তৈরির সময় */
    val createdAt: Long = System.currentTimeMillis(),

    /** সার্ভারে push হয়েছে কিনা */
    val isSynced: Boolean = false,

    // ─────────────────────────────────────────────────────────────────────
    // RETRY MECHANISM — Exponential Backoff
    // Sync fail হলে Infinite Retry Loop থেকে রক্ষা করে।
    //
    // Backoff Schedule:
    //   Retry 1 → 30 seconds
    //   Retry 2 → 2 minutes
    //   Retry 3 → 10 minutes
    //   Retry 4 → 1 hour
    //   Retry 5+ → 6 hours (max)
    //   Retry > 10 → isPermanentlyFailed = true (manual resync required)
    //
    // 422 (SMS_PARSE_FAILED) → isPermanentlyFailed = true
    //   (template না থাকলে retry অর্থহীন, admin জানাতে হবে)
    // ─────────────────────────────────────────────────────────────────────

    /** কতবার sync retry করা হয়েছে */
    val retryCount: Int = 0,

    /** শেষবার sync attempt এর timestamp (epoch ms) */
    val lastAttemptAt: Long = 0L,

    /**
     * পরবর্তী retry করার earliest timestamp (epoch ms)।
     * এই সময়ের আগে retry করা হবে না।
     */
    val nextRetryAt: Long = 0L,

    /**
     * Permanently failed হয়ে গেছে কিনা।
     * true হলে automatic retry বন্ধ — manual intervention দরকার।
     * কারণ: SMS_PARSE_FAILED (422) বা retryCount > 10
     */
    val isPermanentlyFailed: Boolean = false
)
