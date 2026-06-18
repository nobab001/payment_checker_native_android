package online.paychek.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SeenSmsCursorEntity — Guard-2 ContentProvider Polling Cursor (Singleton Row)
 * ============================================================================
 * উদ্দেশ্য: SMS inbox এ শেষ যে SMS স্ক্যান করা হয়েছে তার ContentProvider `_id`
 *          এবং timestamp সংরক্ষণ করা।
 *
 * ডিজাইন:
 *  - `id = 1` — সবসময় একটিমাত্র row থাকবে (singleton pattern)
 *  - `lastSeenSmsId` — ContentProvider-এর `content://sms/inbox` এর `_id` column
 *    এর সর্বোচ্চ দেখা মান। পরের স্ক্যানে `WHERE _id > lastSeenSmsId` দিয়ে
 *    শুধুমাত্র নতুন SMS খোঁজা হবে।
 *  - `lastScannedAt` — শেষবার স্ক্যান করার epoch ms timestamp (diagnostics)
 *
 * Guarantee:
 *  Guard-1 (BroadcastReceiver) এবং Guard-2 (polling) উভয়েই একই SMS process করলে
 *  `rawBodyHash` UNIQUE index Guard করে — Room-এ duplicate insert হবে না।
 * ============================================================================
 */
@Entity(tableName = "sms_seen_cursor")
data class SeenSmsCursorEntity(
    @PrimaryKey
    val id: Int = 1, // সবসময় ১ — singleton row

    /**
     * ContentProvider `content://sms/inbox` এর `_id` column এর
     * শেষ দেখা সর্বোচ্চ মান।
     * 0 = প্রথমবার স্ক্যান (কোনো baseline নেই) — শুধু বর্তমান
     * inbox এর সর্বোচ্চ ID দিয়ে baseline সেট করবে, পুরনো SMS process করবে না।
     */
    val lastSeenSmsId: Long = 0L,

    /** শেষবার স্ক্যান করার সময় (epoch ms) — diagnostics এর জন্য */
    val lastScannedAt: Long = 0L
)
