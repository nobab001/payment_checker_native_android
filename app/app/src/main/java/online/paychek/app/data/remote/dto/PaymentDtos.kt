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
    @SerializedName("is_parseable") val isParseable: Int = 1,
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
    val providerTag: String,            // Full template name e.g. "bKash Personal"

    val amount: Double,

    @SerializedName("trx_id")
    val trxId: String,

    @SerializedName("sender_number")
    val senderNumber: String?,

    @SerializedName("sim_slot")
    val simSlot: Int?,                  // 1 বা 2

    @SerializedName("sim_number")
    val simNumber: String? = null,      // Source device SIM number at ingest

    @SerializedName("device_id")
    val deviceId: String? = null,       // Source device that uploaded SMS

    @SerializedName("sms_timestamp")
    val smsTimestamp: String,           // ISO 8601 বা epoch (ব্যাকএন্ড থেকে String)

    @SerializedName("is_used")
    val isUsed: Int,                    // 0 = UNUSED | 1 = SOLDOUT

    @SerializedName("created_at")
    val createdAt: String?,

    @SerializedName("full_sms")
    val fullSms: String?,

    @SerializedName("device_name")
    val deviceName: String?
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
    val hasMore: Boolean,

    @SerializedName("cache_hit")
    val cacheHit: Boolean? = false,

    @SerializedName("history_version")
    val historyVersion: Long? = null
)

data class TransactionHistoryResult(
    val items: List<TransactionItem>,
    val cacheHit: Boolean = false,
    val historyVersion: Long? = null,
    val hasMore: Boolean = true
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

data class TrialWelcomeDto(
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("title") val title: String = "অভিনন্দন!",
    @SerializedName("message") val message: String = "",
    @SerializedName("features") val features: List<String> = emptyList(),
    @SerializedName("show_once") val showOnce: Boolean = true,
    @SerializedName("button_text") val buttonText: String = "এখনই শুরু করুন",
    @SerializedName("trial_days") val trialDays: Int = 7
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

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("trial_welcome")
    val trialWelcome: TrialWelcomeDto? = null,

    @SerializedName("global_templates")
    val globalTemplates: List<SmsTemplateDto>? = emptyList(),

    @SerializedName("gateway_methods")
    val gatewayMethods: List<GatewayMethod>? = null,

    @SerializedName("gateway_methods_last_sync")
    val gatewayMethodsLastSync: Long? = 0L,

    @SerializedName("data_version")
    val dataVersion: Long? = null,

    @SerializedName("cache_hit")
    val cacheHit: Boolean? = false,

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

data class SubscriptionQuoteDto(
    @SerializedName("plan_name") val planName: String,
    @SerializedName("purchase_type") val purchaseType: String,
    @SerializedName("list_price") val listPrice: Double,
    @SerializedName("credit_applied") val creditApplied: Double,
    @SerializedName("payable_amount") val payableAmount: Double,
    @SerializedName("duration_days") val durationDays: Int,
    @SerializedName("new_expiry_date") val newExpiryDate: String,
    @SerializedName("current_plan_name") val currentPlanName: String? = null,
    @SerializedName("current_expiry_date") val currentExpiryDate: String? = null,
    @SerializedName("remaining_days") val remainingDays: Int = 0,
    @SerializedName("credit_source_plan") val creditSourcePlan: String? = null
)

data class SubscriptionQuoteResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("quote") val quote: SubscriptionQuoteDto?
)

data class PurchaseSubscriptionResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("is_paid") val isPaid: Boolean,
    @SerializedName("active_plan_name") val activePlanName: String?,
    @SerializedName("expiry_date") val expiryDate: String?,
    @SerializedName("purchase_type") val purchaseType: String? = null,
    @SerializedName("list_price") val listPrice: Double? = null,
    @SerializedName("credit_applied") val creditApplied: Double? = null,
    @SerializedName("payable_amount") val payableAmount: Double? = null
)

data class CustomArchiveItem(
    val id: Int,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String?,
    @SerializedName("provider_tag") val providerTag: String,
    @SerializedName("full_sms") val fullSms: String,
    @SerializedName("created_at") val createdAt: String
)

data class CustomArchiveListResponse(
    val success: Boolean,
    val data: List<CustomArchiveItem>
)

data class PurchaseAddonResponse(
    val success: Boolean,
    val message: String?,
    @SerializedName("has_custom_sender_addon") val hasCustomSenderAddon: Int,
    @SerializedName("custom_sender_ends_at") val customSenderEndsAt: String? = null
)
