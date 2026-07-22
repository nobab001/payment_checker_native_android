package online.paychek.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AdminGenericResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("error") val error: String? = null
)

data class ConfigResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("configs") val configs: Map<String, String>
)

data class UpdateConfigRequest(
    @SerializedName("key") val key: String,
    @SerializedName("value") val value: String
)

data class SmsTemplateDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("template_name") val templateName: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("sender_number") val senderNumber: String?,
    @SerializedName("matching_keyword") val matchingKeyword: String,
    @SerializedName("regex_pattern") val regexPattern: String,
    @SerializedName("is_official") val isOfficial: Int?,
    @SerializedName("is_active") val isActive: Int,
    @SerializedName("is_parseable") val isParseable: Int = 1,
    @SerializedName("display_order") val displayOrder: Int = 0,
    @SerializedName("category") val category: String? = "SEND_MONEY",
    @SerializedName("is_other_device") val isOtherDevice: Boolean? = false,
    @SerializedName("is_admin_archive") val isAdminArchive: Boolean? = false,
    @SerializedName("logo_url") val logoUrl: String? = null
)

data class SmsTemplatesResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("templates") val templates: List<SmsTemplateDto>? = null,
    @SerializedName("data_version") val dataVersion: Long? = null,
    @SerializedName("unchanged") val unchanged: Boolean? = false
)

data class SmsTemplateReorderItem(
    @SerializedName("id") val id: Int,
    @SerializedName("display_order") val displayOrder: Int
)

data class SmsTemplateReorderRequest(
    @SerializedName("items") val items: List<SmsTemplateReorderItem>
)

data class CheckoutTemplateDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("sms_template_id") val smsTemplateId: Int,
    @SerializedName("template_name") val templateName: String?,
    @SerializedName("single_number_instruction") val singleInstruction: String,
    @SerializedName("multiple_number_instruction") val multipleInstruction: String
)

data class CheckoutTemplatesResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("templates") val templates: List<CheckoutTemplateDto>
)

data class EmailAccountDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int,
    @SerializedName("secure") val secure: Int,
    @SerializedName("daily_limit") val dailyLimit: Int,
    @SerializedName("sent_today") val sentToday: Int?,
    @SerializedName("is_active") val isActive: Int
)

data class EmailAccountsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("accounts") val accounts: List<EmailAccountDto>
)

data class SmsSettingsDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("gateway_url") val gatewayUrl: String,
    @SerializedName("http_method") val httpMethod: String,
    @SerializedName("post_body_template") val postBodyTemplate: String?,
    @SerializedName("api_key") val apiKey: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("sender_id") val senderId: String?,
    @SerializedName("is_active") val isActive: Int
)

data class SmsSettingsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("settings") val settings: List<SmsSettingsDto>
)

data class AdminDeviceDto(
    @SerializedName("id") val id: Int,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("deviceName") val deviceName: String,
    @SerializedName("customName") val customName: String?,
    @SerializedName("deviceModel") val deviceModel: String,
    @SerializedName("androidVersion") val androidVersion: String,
    @SerializedName("status") val status: String,
    @SerializedName("isParent") val isParent: Boolean,
    @SerializedName("lastSeenAt") val lastSeenAt: String?,
    @SerializedName("lastBatteryPercent") val lastBatteryPercent: Int?,
    @SerializedName("trialExpiresAt") val trialExpiresAt: String?,
    @SerializedName("isTrialLocked") val isTrialLocked: Boolean,
    @SerializedName("lockReason") val lockReason: String?
)

data class AdminUserDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("role") val role: String,
    @SerializedName("is_paid") val isPaid: Boolean,
    @SerializedName("active_plan_name") val activePlanName: String,
    @SerializedName("expiry_date") val expiryDate: String? = null,
    @SerializedName("blocked") val blocked: Boolean,
    @SerializedName("profile_complete") val profileComplete: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("devices") val devices: List<AdminDeviceDto>
)

data class UsersListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("users") val users: List<AdminUserDto>
)

data class BlockUserRequest(
    @SerializedName("blocked") val blocked: Boolean
)

data class UpdateDeviceTrialRequest(
    @SerializedName("trial_expires_at") val trialExpiresAt: String?,
    @SerializedName("is_trial_locked") val isTrialLocked: Boolean?,
    @SerializedName("lock_reason") val lockReason: String?
)

data class OtpFormatResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("template") val template: String
)

data class UpdateOtpFormatRequest(
    @SerializedName("template") val template: String
)

data class BillingSettingDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("setting_key") val settingKey: String,
    @SerializedName("setting_value") val settingValue: String,
    @SerializedName("description") val description: String? = null
)

data class BillingSettingsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("settings") val settings: List<BillingSettingDto>
)

data class UpdateBillingSettingsRequest(
    @SerializedName("settings") val settings: List<BillingSettingDto>
)

data class ManualGraceRequest(
    @SerializedName("credits") val credits: Int
)

data class AdminWebsiteDto(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("site_name") val siteName: String?,
    @SerializedName("site_url") val siteUrl: String?,
    @SerializedName("merchant_id") val merchantId: String?,
    @SerializedName("api_key") val apiKey: String?,
    @SerializedName("is_active") val isActive: Int = 1,
    @SerializedName("allow_payment_type_callback") val allowPaymentTypeCallback: Int = 0,
    @SerializedName("allow_commission_callback") val allowCommissionCallback: Int = 0,
    @SerializedName("commission_enabled") val commissionEnabled: Int = 0
)

data class AdminWebsitesResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("websites") val websites: List<AdminWebsiteDto>
)

data class WebsitePermissionsRequest(
    @SerializedName("allow_payment_type_callback") val allowPaymentTypeCallback: Boolean? = null,
    @SerializedName("allow_commission_callback") val allowCommissionCallback: Boolean? = null,
    @SerializedName("commission_enabled") val commissionEnabled: Boolean? = null
)

data class WebsitePermissionsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("website") val website: AdminWebsiteDto?
)

data class SubscriptionPlanDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("plan_name") val planName: String,
    @SerializedName("price") val price: Double,
    @SerializedName("max_sites") val maxSites: Int,
    @SerializedName("max_devices") val maxDevices: Int,
    @SerializedName("is_custom_sender_allowed") val isCustomSenderAllowed: Int = 0,
    @SerializedName("duration_days") val durationDays: Int = 365,
    @SerializedName("plan_category") val planCategory: String = "payment_gateway",
    @SerializedName("perm_template") val permTemplate: Int = 1,
    @SerializedName("perm_website") val permWebsite: Int = 1,
    @SerializedName("perm_device") val permDevice: Int = 1,
    @SerializedName("perm_smart_popup") val permSmartPopup: Int = 0,
    @SerializedName("sort_order") val sortOrder: Int = 0,
    @SerializedName("features") val features: List<PlanFeatureDto>? = null
)

data class SubscriptionPlansResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("plans") val plans: List<SubscriptionPlanDto>,
    @SerializedName("tab_order") val tabOrder: List<String>? = null
)

data class AddonPlanDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("plan_name") val planName: String,
    @SerializedName("price") val price: Double,
    @SerializedName("duration_days") val durationDays: Int = 30,
    @SerializedName("description") val description: String? = null,
    @SerializedName("is_active") val isActive: Int = 1,
    @SerializedName("max_devices") val maxDevices: Int = 2,
    @SerializedName("perm_custom_sender") val permCustomSender: Int = 1,
    @SerializedName("perm_template") val permTemplate: Int = 0,
    @SerializedName("perm_website") val permWebsite: Int = 0,
    @SerializedName("perm_device") val permDevice: Int = 1,
    @SerializedName("perm_smart_popup") val permSmartPopup: Int = 0,
    @SerializedName("sort_order") val sortOrder: Int = 0,
    @SerializedName("features") val features: List<PlanFeatureDto>? = null
)

data class AddonPlansResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("plans") val plans: List<AddonPlanDto>,
    @SerializedName("tab_order") val tabOrder: List<String>? = null
)

data class PlanReorderItemDto(
    @SerializedName("id") val id: Int,
    @SerializedName("sort_order") val sortOrder: Int
)

data class PlanReorderRequest(
    @SerializedName("items") val items: List<PlanReorderItemDto>
)

data class BillingTabOrderRequest(
    @SerializedName("tab_order") val tabOrder: List<String>
)

data class PurchaseAddonRequest(
    @SerializedName("plan_id") val planId: Int
)

// ── Global Checkout Design (admin) ───────────────────────────────────────────

data class ProviderBrandingDto(
    @SerializedName("displayName") val displayName: String = "",
    @SerializedName("logoUrl") val logoUrl: String = ""
)

data class CheckoutDesignTabInput(
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("label") val label: String = "",
    @SerializedName("icon") val icon: String = "",
    @SerializedName("iconUrl") val iconUrl: String = "",
    @SerializedName("category") val category: String = ""
)

data class SaveCheckoutDesignRequest(
    @SerializedName("tabs") val tabs: Map<String, CheckoutDesignTabInput>,
    @SerializedName("providerBranding") val providerBranding: Map<String, ProviderBrandingDto>
)

data class CheckoutDesignConfigResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("tabs") val tabs: Map<String, CheckoutTabDto>? = null,
    @SerializedName("providerBranding") val providerBranding: Map<String, ProviderBrandingDto>? = null,
    @SerializedName("designs") val designs: List<String>? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null
)

// ── Direct image upload (provider logo / tab icon) ───────────────────────────

data class UploadImageRequest(
    @SerializedName("imageData") val imageData: String,
    @SerializedName("kind") val kind: String,   // "provider_logo" | "tab_icon"
    @SerializedName("key") val key: String
)

data class UploadImageResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("path") val path: String? = null,
    @SerializedName("error") val error: String? = null
)

// ── Official marketing website CMS ───────────────────────────────────────────

data class OfficialWebsiteHeroDto(
    @SerializedName("kicker") val kicker: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("lead") val lead: String = "",
    @SerializedName("ctaPrimary") val ctaPrimary: String = "",
    @SerializedName("ctaSecondary") val ctaSecondary: String = ""
)

data class OfficialWebsiteCardDto(
    @SerializedName("icon") val icon: String = "circle",
    @SerializedName("title") val title: String = "",
    @SerializedName("body") val body: String = ""
)

data class OfficialWebsiteTabDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("order") val order: Int = 0,
    @SerializedName("navLabel") val navLabel: String = "",
    @SerializedName("sectionLabel") val sectionLabel: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("lead") val lead: String = "",
    @SerializedName("cards") val cards: List<OfficialWebsiteCardDto> = emptyList()
)

data class OfficialHelplineItemDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("icon") val icon: String = "whatsapp",
    @SerializedName("label") val label: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("sortOrder") val sortOrder: Int = 0
)

data class OfficialWebsiteCmsDto(
    @SerializedName("hero") val hero: OfficialWebsiteHeroDto = OfficialWebsiteHeroDto(),
    @SerializedName("tabs") val tabs: List<OfficialWebsiteTabDto> = emptyList(),
    @SerializedName("helpline") val helpline: List<OfficialHelplineItemDto> = emptyList()
)

data class OfficialWebsiteCmsResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("content") val content: OfficialWebsiteCmsDto? = null,
    @SerializedName("icons") val icons: List<String>? = null,
    @SerializedName("error") val error: String? = null
)

data class SaveOfficialWebsiteCmsRequest(
    @SerializedName("content") val content: OfficialWebsiteCmsDto
)
