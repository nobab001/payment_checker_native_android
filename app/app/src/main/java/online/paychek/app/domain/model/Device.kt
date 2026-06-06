package online.paychek.app.domain.model

/**
 * Device hardware profile model.
 * Corresponds to blueprint: models/device_model.dart
 *
 * Device States (blueprint Section 4.B.2):
 *   PENDING  → ApprovalOverlay দেখাবে, 12s পর পর poll করবে
 *   ACTIVE   → স্বাভাবিকভাবে app চলবে
 *   REJECTED → session শেষ করে logout করবে
 */
data class Device(
    val id: Int,
    val userId: Int,
    val deviceId: String,           // Android Hardware ID (android_id)
    val deviceName: String,
    val customName: String?,
    val status: DeviceStatus,
    val isParent: Boolean,
    val deviceModel: String,
    val androidVersion: String,
    val sim1Number: String?,
    val sim2Number: String?,
    val lastSeenAt: Long?,          // epoch millis
    val lastBatteryPercent: Int?,
    val simSettings: String?,       // JSON blob — full slot config
    // Trial Lock fields
    val isTrialLocked: Boolean = false,
    val trialExpiresAt: Long? = null,
    val lockReason: String? = null,
)

enum class DeviceStatus { PENDING, ACTIVE, REJECTED }
