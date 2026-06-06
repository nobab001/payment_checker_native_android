package online.paychek.app.domain.model

/**
 * Parsed payment / transaction record — core data object.
 * Corresponds to blueprint: models/payment_model.dart + models/sms_record.dart
 *
 * Deduplication Key formula (blueprint Section 3.B):
 *   dedupeKey = smsTimestamp + "|" + senderNumber + "|" + sha256(rawBody)
 */
data class PaymentRecord(
    val id: Long = 0,
    val userId: Int,
    val deviceId: String,
    val simSlot: Int?,               // 1 বা 2
    val simNumber: String?,
    val providerTag: String,         // "bKash" | "Nagad" | "Rocket" | "Upay"
    val amount: Double,
    val trxId: String,               // Unique Transaction ID
    val senderNumber: String?,
    val receiverNumber: String?,
    val smsTimestamp: Long,          // epoch millis
    val rawBody: String,
    val dedupeKey: String,           // Unique per record — prevents duplicates
    val isSynced: Boolean = false,   // false = offline queue-এ আছে
    val isUsed: Boolean = false,     // true = "SOLDOUT" চিহ্নিত
    val usedAt: Long? = null,
)
