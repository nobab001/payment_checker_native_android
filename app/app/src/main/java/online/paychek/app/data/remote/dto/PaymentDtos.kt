package online.paychek.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// =============================================================================
// INGEST DTOs (আগে থেকে আছে)
// =============================================================================

data class PaymentIngestRequest(
    val amount: Double,
    val trxId: String,
    val providerTag: String,
    val senderNumber: String,
    val receiverNumber: String?,
    val smsTimestamp: Long,     // Epoch milliseconds
    val rawBody: String,
    val simSlot: Int?,          // 1 বা 2
    val simNumber: String?,     // SIM ফোন নম্বর
    @SerializedName("is_custom_sender") val isCustomSender: Boolean = false,
    @SerializedName("full_sms") val fullSms: String? = null,
    // v2.0.0 — HMAC security fields (nullable for backward compat)
    @SerializedName("hmacSignature") val hmacSignature: String? = null,
    @SerializedName("isOfflineSync") val isOfflineSync: Boolean = false
)

data class PaymentIngestResponse(
    val success: Boolean,
    val isDuplicate: Boolean,
    val message: String?
)

data class BulkPaymentIngestRequest(
    val items: List<PaymentIngestRequest>
)

data class BulkPaymentIngestResponse(
    val success: Boolean,
    val processed: Int,
    val failed: Int,
    val error: String? = null
)

// =============================================================================
// TRANSACTION HISTORY DTOs
// =============================================================================

/**
 * একটি ট্রানজেকশন আইটেম (LazyColumn-এ প্রতিটি রো)
 */
data class TransactionItem(
    val id: Int,

    @SerializedName("provider_tag")
    val providerTag: String,            // bKash | Nagad | Rocket | Upay

    val amount: Double,

    @SerializedName("trx_id")
    val trxId: String,

    @SerializedName("sender_number")
    val senderNumber: String?,

    @SerializedName("sim_slot")
    val simSlot: Int?,                  // 1 বা 2

    @SerializedName("sms_timestamp")
    val smsTimestamp: String,           // ISO 8601 বা epoch (ব্যাকএন্ড থেকে String)

    @SerializedName("is_used")
    val isUsed: Int,                    // 0 = UNUSED | 1 = SOLDOUT

    @SerializedName("created_at")
    val createdAt: String?,

    @SerializedName("full_sms")
    val fullSms: String?
)

/**
 * পেজিনেটেড ট্রানজেকশন লিস্ট রেসপন্স
 */
data class TransactionListResponse(
    val success: Boolean,
    val data: List<TransactionItem>,
    val page: Int,
    val limit: Int,

    @SerializedName("total_count")
    val totalCount: Int,

    @SerializedName("has_more")
    val hasMore: Boolean
)

// =============================================================================
// DASHBOARD STATS DTOs
// =============================================================================

/**
 * Dashboard statistics রেসপন্স
 */
data class DashboardStatsResponse(
    val success: Boolean,
    val data: DashboardStats?
)

data class DashboardStats(

    @SerializedName("total_earnings")
    val totalEarnings: Double,          // সব সময়ের মোট আয়

    @SerializedName("today_earnings")
    val todayEarnings: Double,          // আজকের মোট আয়

    @SerializedName("total_transactions")
    val totalTransactions: Int,         // মোট ট্রানজেকশন সংখ্যা

    @SerializedName("today_transactions")
    val todayTransactions: Int,         // আজকের ট্রানজেকশন সংখ্যা

    @SerializedName("unused_count")
    val unusedCount: Int,               // UNUSED (চেক হয়নি) সংখ্যা

    @SerializedName("soldout_count")
    val soldoutCount: Int,              // SOLDOUT মার্ক করা সংখ্যা

    @SerializedName("active_devices")
    val activeDevices: Int,             // সক্রিয় ডিভাইস সংখ্যা

    @SerializedName("is_paid")
    val isPaid: Boolean,

    @SerializedName("active_plan_name")
    val activePlanName: String,

    @SerializedName("expiry_date")
    val expiryDate: String?,

    @SerializedName("secretKey")
    val secretKey: String?,

    @SerializedName("secretKeyVersion")
    val secretKeyVersion: Int?,

    @SerializedName("recent_transactions")
    val recentTransactions: List<TransactionItem>  // সর্বশেষ ৫টি ট্রানজেকশন
)



data class FcmTokenRequest(
    val token: String?
)

data class FcmTokenResponse(
    val success: Boolean,
    val message: String?
)

data class PurchaseSubscriptionRequest(
    @SerializedName("planName") val planName: String
)

data class PurchaseSubscriptionResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("is_paid") val isPaid: Boolean,
    @SerializedName("active_plan_name") val activePlanName: String?,
    @SerializedName("expiry_date") val expiryDate: String?
)
