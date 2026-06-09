package online.paychek.app.data.remote.dto

import online.paychek.app.domain.model.DeviceStatus

data class CheckContactRequest(
    val contact: String,
    // Device hardware signature — sent for server-side device-binding gatekeeper
    val deviceId: String? = null,
    val fingerprint: String? = null,
    val androidId: String? = null,
    val hardwareFingerprint: String? = null,
    val simSlotIds: String? = null
)

data class CheckContactResponse(
    val exists: Boolean
)

data class SendOtpRequest(
    val contact: String,
    val deviceId: String,
    val androidId: String? = null,
    val hardwareFingerprint: String? = null,
    val simSlotIds: String? = null
)

data class OtpResponse(
    val success: Boolean,
    val message: String?
)

data class VerifyOtpRequest(
    val contact: String,
    val code: String,
    val deviceId: String,
    val deviceModel: String,
    val androidVersion: String,
    val fingerprint: String,
    val androidId: String? = null,
    val hardwareFingerprint: String? = null,
    val simSlotIds: String? = null
)

data class VerifyOtpResponse(
    val token: String,
    val user: UserDto,
    val requiresSecurityPin: Boolean,
    val device: DeviceDto
)

data class CompleteProfileRequest(
    val name: String,
    val pin: String,
    val phone: String?,
    val email: String?,
    val deviceId: String? = null,
    val androidId: String? = null,
    val hardwareFingerprint: String? = null,
    val simSlotIds: String? = null,
    val fingerprint: String? = null
)

data class CompleteProfileResponse(
    val success: Boolean,
    val user: UserDto
)

data class CheckDeviceTrialRequest(
    val deviceId: String,
    val fingerprint: String,
    val androidId: String? = null,
    val hardwareFingerprint: String? = null,
    val simSlotIds: String? = null
)

data class CheckDeviceTrialResponse(
    val trialAllowed: Boolean,
    val isLocked: Boolean,
    val lockReason: String?,
    val success: Boolean? = null,
    val abused: Boolean? = null,
    val message: String? = null
)

// Simplified DTO wrappers to decouple API from Database/Domain layer if needed
data class UserDto(
    val id: Int,
    val name: String,
    val phone: String?,
    val email: String?,
    val role: String,
    val balance: Double,
    val blocked: Boolean,
    val profileComplete: Boolean,
    val smsEnabled: Boolean,
    val gmailEnabled: Boolean
)

data class DeviceDto(
    val id: Int,
    val userId: Int,
    val deviceId: String,
    val deviceName: String,
    val status: String, // pending, active, rejected
    val isParent: Boolean,
    val isTrialLocked: Boolean,
    val trialExpiresAt: String?,
    val lockReason: String?
)

data class VerifyPinRequest(
    val pin: String
)

data class VerifyPinResponse(
    val success: Boolean,
    val message: String?
)
