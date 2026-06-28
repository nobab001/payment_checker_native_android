package online.paychek.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ContactCheckRequest(
    @SerializedName("contact") val contact: String = "",
    // Device hardware signature — sent for server-side device-binding gatekeeper
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("fingerprint") val fingerprint: String? = null,
    @SerializedName("androidId") val androidId: String? = null,
    @SerializedName("hardwareFingerprint") val hardwareFingerprint: String? = null,
    @SerializedName("simSlotIds") val simSlotIds: String? = null
)

data class ContactCheckResponse(
    @SerializedName("exists") val exists: Boolean = false
)

data class SendOtpRequest(
    @SerializedName("contact") val contact: String = "",
    @SerializedName("deviceId") val deviceId: String = "",
    @SerializedName("androidId") val androidId: String? = null,
    @SerializedName("hardwareFingerprint") val hardwareFingerprint: String? = null,
    @SerializedName("simSlotIds") val simSlotIds: String? = null
)

data class OtpResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null
)

data class VerifyOtpRequest(
    @SerializedName("contact") val contact: String = "",
    @SerializedName("code") val code: String = "",
    @SerializedName("deviceId") val deviceId: String = "",
    @SerializedName("deviceModel") val deviceModel: String = "",
    @SerializedName("androidVersion") val androidVersion: String = "",
    @SerializedName("fingerprint") val fingerprint: String = "",
    @SerializedName("androidId") val androidId: String? = null,
    @SerializedName("hardwareFingerprint") val hardwareFingerprint: String? = null,
    @SerializedName("simSlotIds") val simSlotIds: String? = null,
    @SerializedName("duration") val duration: Long? = null
)

data class VerifyOtpResponse(
    @SerializedName("token") val token: String = "",
    @SerializedName("user") val user: UserDto = UserDto(),
    @SerializedName("requiresSecurityPin") val requiresSecurityPin: Boolean = false,
    @SerializedName("device") val device: DeviceDto = DeviceDto(),
    @SerializedName("secretKey") val secretKey: String? = null
)

data class CompleteProfileRequest(
    @SerializedName("name") val name: String = "",
    @SerializedName("pin") val pin: String = "",
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("androidId") val androidId: String? = null,
    @SerializedName("hardwareFingerprint") val hardwareFingerprint: String? = null,
    @SerializedName("simSlotIds") val simSlotIds: String? = null,
    @SerializedName("fingerprint") val fingerprint: String? = null
)

data class CompleteProfileResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("user") val user: UserDto = UserDto(),
    @SerializedName("secretKey") val secretKey: String? = null
)

data class DeviceCheckRequest(
    @SerializedName("deviceId") val deviceId: String = "",
    @SerializedName("fingerprint") val fingerprint: String = "",
    @SerializedName("androidId") val androidId: String? = null,
    @SerializedName("hardwareFingerprint") val hardwareFingerprint: String? = null,
    @SerializedName("simSlotIds") val simSlotIds: String? = null
)

data class DeviceCheckResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("abused") val abused: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("trialAllowed") val trialAllowed: Boolean = false,
    @SerializedName("isLocked") val isLocked: Boolean = false,
    @SerializedName("lockReason") val lockReason: String? = null,
    @SerializedName("boundPhones") val boundPhones: List<String> = emptyList(),
    @SerializedName("boundEmails") val boundEmails: List<String> = emptyList()
)

// Simplified DTO wrappers to decouple API from Database/Domain layer if needed
data class UserDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("role") val role: String = "",
    @SerializedName("balance") val balance: Double = 0.0,
    @SerializedName("blocked") val blocked: Boolean = false,
    @SerializedName(value = "profileComplete", alternate = ["profile_complete"]) val profileComplete: Boolean = false,
    @SerializedName(value = "smsEnabled", alternate = ["sms_enabled"]) val smsEnabled: Boolean = false,
    @SerializedName(value = "gmailEnabled", alternate = ["gmail_enabled"]) val gmailEnabled: Boolean = false,
    @SerializedName(value = "isPaid", alternate = ["is_paid"]) val isPaid: Boolean = false,
    @SerializedName(value = "activePlanName", alternate = ["active_plan_name"]) val activePlanName: String? = null,
    @SerializedName(value = "expiryDate", alternate = ["expiry_date"]) val expiryDate: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName(value = "hasCustomSenderAddon", alternate = ["has_custom_sender_addon"])
    val hasCustomSenderAddon: Int = 0
)

data class DeviceDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("userId") val userId: Int = 0,
    @SerializedName("deviceId") val deviceId: String = "",
    @SerializedName("deviceName") val deviceName: String = "",
    @SerializedName("status") val status: String = "", // pending, active, rejected
    @SerializedName(value = "isParent", alternate = ["is_parent"]) val isParent: Boolean = false,
    @SerializedName(value = "isApproved", alternate = ["is_approved"]) val isApproved: Boolean = false,
    @SerializedName(value = "deviceRole", alternate = ["device_role"]) val deviceRole: String = "pending",
    @SerializedName(value = "isTrialLocked", alternate = ["is_trial_locked"]) val isTrialLocked: Boolean = false,
    @SerializedName(value = "trialExpiresAt", alternate = ["trial_expires_at"]) val trialExpiresAt: String? = null,
    @SerializedName(value = "lockReason", alternate = ["lock_reason"]) val lockReason: String? = null,
    @SerializedName(value = "isOwnerDevice", alternate = ["is_owner_device"]) val isOwnerDevice: Boolean = false,
    @SerializedName(value = "deviceSpecificPin", alternate = ["device_specific_pin"]) val deviceSpecificPin: String? = null
)

data class VerifyPinRequest(
    @SerializedName("pin") val pin: String = ""
)

data class VerifyPinResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null
)
