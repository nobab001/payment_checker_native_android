package online.paychek.app.data.remote.dto

import online.paychek.app.domain.model.DeviceStatus

data class CheckContactRequest(
    val contact: String
)

data class CheckContactResponse(
    val exists: Boolean
)

data class SendOtpRequest(
    val contact: String,
    val deviceId: String
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
    val fingerprint: String
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
    val email: String?
)

data class CompleteProfileResponse(
    val success: Boolean,
    val user: UserDto
)

data class CheckDeviceTrialRequest(
    val deviceId: String,
    val fingerprint: String
)

data class CheckDeviceTrialResponse(
    val trialAllowed: Boolean,
    val isLocked: Boolean,
    val lockReason: String?
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
