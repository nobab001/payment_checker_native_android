package online.paychek.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// =============================================================================
// Gateway Method — একটি SIM-এর একটি পেমেন্ট পদ্ধতি
// =============================================================================
data class GatewayMethod(
    val id: Int,

    @SerializedName("sim_slot")
    val simSlot: Int,               // 1 বা 2

    val provider: String,           // bKash | Nagad | Rocket | Upay

    val number: String?,            // SIM ফোন নম্বর

    @SerializedName("display_name")
    val displayName: String?,       // কাস্টম নাম

    @SerializedName("is_enabled")
    val isEnabled: Int,             // 0 = OFF | 1 = ON

    val priority: Int               // সাজানোর ক্রম (1 = সবচেয়ে উপরে)
)

// =============================================================================
// Gateway List Response
// =============================================================================
data class GatewayListResponse(
    val success: Boolean,
    val data: List<GatewayMethod>
)

// =============================================================================
// Priority Update — Drag & Drop-এর পর পাঠানো হয়
// =============================================================================
data class PriorityItem(
    val id: Int,
    val priority: Int
)

data class UpdatePriorityRequest(
    val items: List<PriorityItem>
)

data class BasicResponse(
    val success: Boolean,
    val message: String?
)

// =============================================================================
// Toggle — একটি method ON/OFF করার request
// =============================================================================
data class ToggleRequest(
    @SerializedName("is_enabled")
    val isEnabled: Int              // 0 বা 1
)

// =============================================================================
// Update Number — নম্বর বা নাম সম্পাদনা
// =============================================================================
data class UpdateMethodRequest(
    val number: String?,

    @SerializedName("display_name")
    val displayName: String?
)

// =============================================================================
// SIM Master Toggle — পুরো SIM চালু/বন্ধ
// =============================================================================
data class SimToggleRequest(
    @SerializedName("sim_slot")
    val simSlot: Int,

    @SerializedName("is_enabled")
    val isEnabled: Int
)
