package online.paychek.app.data.remote.dto

// ─────────────────────────────────────────────────────────────────
// Profile / Credentials DTOs
// ─────────────────────────────────────────────────────────────────



data class ListCredentialsResponse(
    val success: Boolean,
    val primaryPhone: String?,
    val primaryEmail: String?,
    val credentials: List<CredentialItem>
)

data class CredentialOtpRequest(
    val contact: String
)

data class CredentialVerifyRequest(
    val contact: String,
    val code: String
)

data class CredentialActionResponse(
    val success: Boolean,
    val message: String?
)

// ─────────────────────────────────────────────────────────────────
// PIN DTOs
// ─────────────────────────────────────────────────────────────────

data class ChangePinRequest(
    val oldPin: String,
    val newPin: String
)

data class ResetPinSendOtpRequest(
    val contact: String
)

data class ResetPinVerifyRequest(
    val contact: String,
    val code: String,
    val newPin: String
)

data class PinActionResponse(
    val success: Boolean,
    val message: String?
)

data class ProfileResponse(
    val success: Boolean,
    val user: UserDto
)

data class UploadAvatarRequest(
    val avatarData: String
)

data class UploadAvatarResponse(
    val success: Boolean,
    val message: String?,
    val avatar: String?
)
