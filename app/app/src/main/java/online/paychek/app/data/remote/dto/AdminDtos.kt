package online.paychek.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AdminGenericResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?
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
    @SerializedName("matching_keyword") val matchingKeyword: String,
    @SerializedName("regex_pattern") val regexPattern: String,
    @SerializedName("is_official") val isOfficial: Int?,
    @SerializedName("is_active") val isActive: Int
)

data class SmsTemplatesResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("templates") val templates: List<SmsTemplateDto>
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
    @SerializedName("balance") val balance: Double,
    @SerializedName("wallet_credits") val walletCredits: Double = 0.0,
    @SerializedName("custom_daily_rate") val customDailyRate: Double? = null,
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

data class UpdateCustomRateRequest(
    @SerializedName("custom_daily_rate") val customDailyRate: Double?
)
