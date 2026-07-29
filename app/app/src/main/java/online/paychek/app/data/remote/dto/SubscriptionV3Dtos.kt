package online.paychek.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SubscriptionV3CatalogResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("v3") val v3: Boolean = false,
    @SerializedName("settings") val settings: SubscriptionV3SettingsDto? = null,
    @SerializedName("tab_order") val tabOrder: List<String>? = null,
    @SerializedName("duration_segments") val durationSegments: List<DurationSegmentDto>? = null,
    @SerializedName("categories") val categories: Map<String, List<V3PackageDto>>? = null,
    @SerializedName("addons") val addons: List<V3AddonCatalogDto>? = null,
    @SerializedName("active_subscriptions") val activeSubscriptions: List<V3ActiveSubscriptionDto>? = null,
    @SerializedName("shared_expiry") val sharedExpiry: String? = null,
    @SerializedName("refund_status") val refundStatus: V3RefundStatusDto? = null,
    @SerializedName("purchase_history") val purchaseHistory: List<V3PurchaseHistoryDto>? = null,
    @SerializedName("extension_history") val extensionHistory: List<V3ExtensionHistoryDto>? = null
)

data class SubscriptionV3SettingsDto(
    @SerializedName("subscription_version") val subscriptionVersion: String? = null,
    @SerializedName("subscription_v3_enabled") val subscriptionV3Enabled: Boolean = false,
    @SerializedName("trial_days") val trialDays: Int = 7,
    @SerializedName("checkout_session_min") val checkoutSessionMin: Int = 30,
    @SerializedName("billing_tab_order") val billingTabOrder: List<String>? = null
)

data class DurationSegmentDto(
    @SerializedName("key") val key: String,
    @SerializedName("duration_key") val durationKey: String,
    @SerializedName("label") val label: String
)

data class V3PackageDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("sku_key") val skuKey: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("plan_name") val planName: String? = null,
    @SerializedName("category") val category: String,
    @SerializedName("website_display") val websiteDisplay: String? = null,
    @SerializedName("device_display") val deviceDisplay: String? = null,
    @SerializedName("price_1m") val price1m: Double = 0.0,
    @SerializedName("price_6m") val price6m: Double = 0.0,
    @SerializedName("price_12m") val price12m: Double = 0.0,
    @SerializedName("refund_days") val refundDays: Int = 7,
    @SerializedName("discounts") val discounts: Map<String, Int>? = null,
    @SerializedName("allowed_addons") val allowedAddons: List<String>? = null,
    @SerializedName("perm_template") val permTemplate: Int = 1,
    @SerializedName("perm_website") val permWebsite: Int = 1,
    @SerializedName("perm_device") val permDevice: Int = 1,
    @SerializedName("perm_smart_popup") val permSmartPopup: Int = 0,
    @SerializedName("perm_manual_transaction") val permManualTransaction: Int = 0,
    @SerializedName("is_custom_sender_allowed") val isCustomSenderAllowed: Int = 0
)

data class V3AddonCatalogDto(
    @SerializedName("addon_key") val addonKey: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("price_1m") val price1m: Double = 0.0,
    @SerializedName("price_6m") val price6m: Double = 0.0,
    @SerializedName("price_12m") val price12m: Double = 0.0
)

data class V3ActiveSubscriptionDto(
    @SerializedName("category") val category: String,
    @SerializedName("package_full_name") val packageFullName: String,
    @SerializedName("expires_at") val expiresAt: String? = null
)

data class V3PurchaseHistoryDto(
    @SerializedName("id") val id: Int,
    @SerializedName("invoice_no") val invoiceNo: String? = null,
    @SerializedName("package_full_name") val packageFullName: String,
    @SerializedName("paid_amount") val paidAmount: Double? = null,
    @SerializedName("purchased_at") val purchasedAt: String? = null,
    @SerializedName("refund_status") val refundStatus: String? = null
)

data class V3RefundStatusDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("invoice_no") val invoiceNo: String? = null
)

data class V3ExtensionHistoryDto(
    @SerializedName("id") val id: Int,
    @SerializedName("extension_type") val extensionType: String? = null,
    @SerializedName("days_added") val daysAdded: Int = 0,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("admin_id") val adminId: Int? = null,
    @SerializedName("admin_name") val adminName: String? = null,
    @SerializedName("old_expiry") val oldExpiry: String? = null,
    @SerializedName("new_expiry") val newExpiry: String? = null,
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class V3QuoteRequest(
    @SerializedName("category") val category: String,
    @SerializedName("sku_key") val skuKey: String,
    @SerializedName("duration_key") val durationKey: String,
    @SerializedName("addons") val addons: List<String> = emptyList()
)

data class V3QuoteLineItemDto(
    @SerializedName("type") val type: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("amount") val amount: Double? = null
)

data class V3PeerUpgradeLineDto(
    @SerializedName("category") val category: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("extra_cost") val extraCost: Double? = null
)

data class V3QuoteDto(
    @SerializedName("purchase_type") val purchaseType: String,
    @SerializedName("category") val category: String,
    @SerializedName("package_sku") val packageSku: String,
    @SerializedName("package_full_name") val packageFullName: String,
    @SerializedName("duration_key") val durationKey: String,
    @SerializedName("duration_days") val durationDays: Int,
    @SerializedName("list_price") val listPrice: Double,
    @SerializedName("addon_total") val addonTotal: Double = 0.0,
    @SerializedName("payable_amount") val payableAmount: Double,
    @SerializedName("remaining_days") val remainingDays: Int = 0,
    @SerializedName("shared_expiry") val sharedExpiry: String? = null,
    @SerializedName("final_expiry") val finalExpiry: String,
    @SerializedName("addons") val addons: List<String>? = null,
    @SerializedName("line_items") val lineItems: List<V3QuoteLineItemDto>? = null,
    @SerializedName("peer_upgrade_lines") val peerUpgradeLines: List<V3PeerUpgradeLineDto>? = null
)

data class V3QuoteResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("quote") val quote: V3QuoteDto? = null,
    @SerializedName("quote_token") val quoteToken: String? = null,
    @SerializedName("quote_expires_at") val quoteExpiresAt: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null
)

data class V3CheckoutInitRequest(
    @SerializedName("quote_token") val quoteToken: String
)

data class V3CheckoutInitResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("activated") val activated: Boolean = false,
    @SerializedName("orderId") val orderId: String? = null,
    @SerializedName("checkoutUrl") val checkoutUrl: String? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("invoice_no") val invoiceNo: String? = null,
    @SerializedName("message") val message: String? = null
)

data class V3RefundRequest(
    @SerializedName("purchase_id") val purchaseId: Int,
    @SerializedName("reason") val reason: String? = null
)

data class V3RefundRequestResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null
)

data class V3PurchaseHistoryResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("history") val history: List<V3PurchaseHistoryDto>? = null
)

data class V3PendingRefundDto(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("purchase_id") val purchaseId: Int,
    @SerializedName("invoice_no") val invoiceNo: String? = null,
    @SerializedName("package_full_name") val packageFullName: String? = null,
    @SerializedName("amount_paid") val amountPaid: Double? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("requested_at") val requestedAt: String? = null,
    @SerializedName("status") val status: String? = null
)

data class V3PendingRefundsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("refunds") val refunds: List<V3PendingRefundDto>? = null
)

data class V3ResolveRefundRequest(
    @SerializedName("approve") val approve: Boolean,
    @SerializedName("admin_note") val adminNote: String? = null
)

data class V3SettingsUpdateRequest(
    @SerializedName("trial_days") val trialDays: Int? = null,
    @SerializedName("subscription_v3_enabled") val subscriptionV3Enabled: Boolean? = null
)

data class V3SettingsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("settings") val settings: SubscriptionV3SettingsDto? = null
)
