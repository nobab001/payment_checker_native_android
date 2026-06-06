package online.paychek.app.domain.model

/**
 * Remote feature-flag configuration fetched from admin.
 * Corresponds to blueprint: models/remote_config.dart
 */
data class RemoteConfig(
    val maintenanceMode: Boolean = false,
    val registrationEnabled: Boolean = true,
    val smsTrackingEnabled: Boolean = true,
    val gmailTrackingEnabled: Boolean = false,
    val trialDays: Int = 7,
    val telegramSupportLink: String = "",
    val whatsappSupportLink: String = "",
)
