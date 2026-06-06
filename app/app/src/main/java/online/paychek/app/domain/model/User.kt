package online.paychek.app.domain.model

/**
 * Core User profile model.
 * Corresponds to blueprint: models/user_model.dart
 */
data class User(
    val id: Int,
    val name: String,
    val phone: String?,
    val email: String?,
    val role: String,               // "user" | "admin"
    val balance: Double,
    val isBlocked: Boolean,
    val profileComplete: Boolean,   // false = পুনরায় signup screen দেখাবে
    val smsTrackingEnabled: Boolean = true,
    val gmailTrackingEnabled: Boolean = false,
)
