package online.paychek.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Admin-authored announcements delivered over the HTTP heartbeat
 * (Comm Policy v1.2 — no socket, no FCM).
 *
 * `categories` targets the three subscription categories; selecting all three
 * makes the notice a broadcast that also reaches trial / expired accounts.
 */
object NotificationCategories {
    const val GATEWAY = "gateway"
    const val PERSONAL_BUSINESS = "personal_business"
    const val PERSONAL = "personal"

    val ALL = listOf(GATEWAY, PERSONAL_BUSINESS, PERSONAL)

    fun labelOf(category: String): String = when (category) {
        GATEWAY -> "পেমেন্ট গেটওয়ে"
        PERSONAL_BUSINESS -> "পার্সোনাল বিজনেস"
        PERSONAL -> "পার্সোনাল"
        else -> category
    }
}

/** One notice as returned to the app (heartbeat piggyback + /notifications). */
data class AppNotificationDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("created_at") val createdAt: String? = null
)

/** Admin-side view — adds targeting + delivery stats. */
data class AdminNotificationDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("categories") val categories: List<String> = emptyList(),
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("read_count") val readCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class AdminNotificationListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("notifications") val notifications: List<AdminNotificationDto> = emptyList(),
    @SerializedName("error") val error: String? = null
)

data class SendNotificationRequest(
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("categories") val categories: List<String>
)

data class SendNotificationResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("notification") val notification: AdminNotificationDto? = null,
    @SerializedName("error") val error: String? = null
)

data class MyNotificationsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("notifications") val notifications: List<AppNotificationDto> = emptyList(),
    @SerializedName("error") val error: String? = null
)
