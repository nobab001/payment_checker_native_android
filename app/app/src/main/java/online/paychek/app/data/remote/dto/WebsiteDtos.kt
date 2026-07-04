package online.paychek.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * API Integration v2 — Website / Merchant DTOs.
 * Mirrors backend /api/v1/websites responses (websiteController.toWebsiteDto).
 */

data class WebsiteDto(
    @SerializedName("id") val id: Int,
    @SerializedName("merchantId") val merchantId: String? = null,
    @SerializedName("apiKey") val apiKey: String = "",
    @SerializedName("siteName") val siteName: String = "",
    @SerializedName("companyName") val companyName: String? = null,
    @SerializedName("domain") val domain: String? = null,
    @SerializedName("logoUrl") val logoUrl: String? = null,
    @SerializedName("checkoutTheme") val checkoutTheme: String = "default",
    @SerializedName("checkoutMode") val checkoutMode: String = "transaction",
    @SerializedName("successUrl") val successUrl: String? = null,
    @SerializedName("cancelUrl") val cancelUrl: String? = null,
    @SerializedName("callbackUrl") val callbackUrl: String? = null,
    @SerializedName("webhookUrl") val webhookUrl: String? = null,
    @SerializedName("isActive") val isActive: Boolean = true,
    @SerializedName("secretLast4") val secretLast4: String? = null,
    @SerializedName("secretVersion") val secretVersion: Int = 1,
    @SerializedName("receivePaymentType") val receivePaymentType: Boolean = false,
    @SerializedName("receiveCommission") val receiveCommission: Boolean = false,
    @SerializedName("allowPaymentTypeCallback") val allowPaymentTypeCallback: Boolean = false,
    @SerializedName("allowCommissionCallback") val allowCommissionCallback: Boolean = false,
    @SerializedName("commissionEnabled") val commissionEnabled: Boolean = false,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class CommissionDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("paymentType") val paymentType: String,
    @SerializedName("commissionType") val commissionType: String = "percentage",
    @SerializedName("commissionValue") val commissionValue: Double = 0.0,
    @SerializedName("chargeType") val chargeType: String = "flat",
    @SerializedName("chargeValue") val chargeValue: Double = 0.0,
    @SerializedName("isActive") val isActive: Boolean = true
)

data class NumberOrderItem(
    @SerializedName("methodId") val methodId: Int? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("number") val number: String? = null,
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("position") val position: Int = 0
)

/** Auto-synced active SIM number available for checkout (read from gateway_methods). */
data class ActiveNumberDto(
    @SerializedName("methodId") val methodId: Int,
    @SerializedName("provider") val provider: String,
    @SerializedName("number") val number: String,
    @SerializedName("simSlot") val simSlot: Int = 1,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("tab") val tab: String? = null,
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("position") val position: Long = Long.MAX_VALUE
)

/** Official (redirect-based) payment channel configured for a website (Phase 6). */
data class OfficialGatewayDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("provider") val provider: String,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("redirectUrlTemplate") val redirectUrlTemplate: String = "",
    @SerializedName("isActive") val isActive: Boolean = true
)

// ── Requests ─────────────────────────────────────────────────────────────────

data class CreateWebsiteRequest(
    @SerializedName("domain") val domain: String,
    @SerializedName("website_name") val websiteName: String? = null
)

data class UpdateWebsiteRequest(
    @SerializedName("website_name") val websiteName: String? = null,
    @SerializedName("domain") val domain: String? = null,
    @SerializedName("company_name") val companyName: String? = null,
    @SerializedName("logo_url") val logoUrl: String? = null,
    @SerializedName("checkout_theme") val checkoutTheme: String? = null,
    @SerializedName("checkout_mode") val checkoutMode: String? = null,
    @SerializedName("success_url") val successUrl: String? = null,
    @SerializedName("cancel_url") val cancelUrl: String? = null,
    @SerializedName("callback_url") val callbackUrl: String? = null,
    @SerializedName("webhook_url") val webhookUrl: String? = null,
    @SerializedName("receive_payment_type") val receivePaymentType: Boolean? = null,
    @SerializedName("receive_commission") val receiveCommission: Boolean? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("checkout_tabs") val checkoutTabs: Map<String, CheckoutTabToggle>? = null
)

/** Tab enable/disable payload for PATCH /websites/:id */
data class CheckoutTabToggle(
    @SerializedName("enabled") val enabled: Boolean = true
)

data class NumberOrderRequest(
    @SerializedName("order") val order: List<NumberOrderItem>
)

data class UpsertOfficialGatewayRequest(
    @SerializedName("provider") val provider: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("redirect_url_template") val redirectUrlTemplate: String,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class UpsertCommissionRequest(
    @SerializedName("payment_type") val paymentType: String,
    @SerializedName("commission_type") val commissionType: String,
    @SerializedName("commission_value") val commissionValue: Double,
    @SerializedName("charge_type") val chargeType: String,
    @SerializedName("charge_value") val chargeValue: Double,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class DeleteWebsiteRequest(
    @SerializedName("pin") val pin: String
)

data class SaveGlobalCheckoutRequest(
    @SerializedName("checkout_theme") val checkoutTheme: String,
    @SerializedName("checkout_mode") val checkoutMode: String,
    @SerializedName("checkout_tabs") val checkoutTabs: Map<String, CheckoutTabToggle>? = null,
    @SerializedName("order") val order: List<NumberOrderItem> = emptyList()
)

data class GlobalCheckoutResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("checkoutTheme") val checkoutTheme: String = "design-1",
    @SerializedName("checkoutMode") val checkoutMode: String = "transaction",
    @SerializedName("checkoutTabs") val checkoutTabs: Map<String, CheckoutTabDto>? = null,
    @SerializedName("providerBranding") val providerBranding: Map<String, ProviderBrandingDto>? = null,
    @SerializedName("activeNumbers") val activeNumbers: List<ActiveNumberDto> = emptyList(),
    @SerializedName("gatewaysByCategory") val gatewaysByCategory: Map<String, List<ActiveNumberDto>>? = null,
    @SerializedName("numberOrder") val numberOrder: List<NumberOrderItem> = emptyList(),
    @SerializedName("websiteCount") val websiteCount: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("websitesUpdated") val websitesUpdated: Int? = null,
    @SerializedName("error") val error: String? = null
)


data class CreateWebsiteResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("website") val website: WebsiteDto? = null,
    @SerializedName("apiSecret") val apiSecret: String? = null,
    @SerializedName("error") val error: String? = null
)

data class ListWebsitesResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("websites") val websites: List<WebsiteDto> = emptyList()
)

data class WebsiteDetailResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("website") val website: WebsiteDto? = null,
    @SerializedName("commissions") val commissions: List<CommissionDto> = emptyList(),
    @SerializedName("numberOrder") val numberOrder: List<NumberOrderItem> = emptyList(),
    @SerializedName("activeNumbers") val activeNumbers: List<ActiveNumberDto> = emptyList(),
    @SerializedName("gatewaysByCategory") val gatewaysByCategory: Map<String, List<ActiveNumberDto>>? = null,
    @SerializedName("checkoutTabs") val checkoutTabs: Map<String, CheckoutTabDto>? = null,
    @SerializedName("providerBranding") val providerBranding: Map<String, ProviderBrandingDto>? = null
)

data class CheckoutTabDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("category") val category: String? = null
)

data class WebsiteUpdateResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("website") val website: WebsiteDto? = null,
    @SerializedName("error") val error: String? = null
)

data class RegenerateSecretResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("apiSecret") val apiSecret: String? = null,
    @SerializedName("website") val website: WebsiteDto? = null
)

data class NumberOrderResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("numberOrder") val numberOrder: List<NumberOrderItem> = emptyList()
)

data class CommissionListResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("commissionEnabled") val commissionEnabled: Boolean = false,
    @SerializedName("commissions") val commissions: List<CommissionDto> = emptyList()
)

data class CommissionUpsertResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("commission") val commission: CommissionDto? = null,
    @SerializedName("error") val error: String? = null
)

data class SimpleWebsiteActionResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null
)

data class OfficialGatewayListResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("officialGateways") val officialGateways: List<OfficialGatewayDto> = emptyList()
)

data class OfficialGatewayUpsertResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("officialGateway") val officialGateway: OfficialGatewayDto? = null,
    @SerializedName("error") val error: String? = null
)
