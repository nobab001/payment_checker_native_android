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

    val priority: Int,              // সাজানোর ক্রম (1 = সবচেয়ে উপরে)

    @SerializedName("template_id")
    val templateId: Int?,

    @SerializedName("sender_id")
    val senderId: String?,

    @SerializedName("matching_keyword")
    val matchingKeyword: String?,

    @SerializedName("regex_pattern")
    val regexPattern: String?,

    @SerializedName("is_official")
    val isOfficial: Int?,

    @SerializedName("single_number_instruction")
    val singleNumberInstruction: String?,

    @SerializedName("multiple_number_instruction")
    val multipleNumberInstruction: String?
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

// =============================================================================
// Parent-Child Control Hub DTOs
// =============================================================================
data class ChildDeviceDto(
    @SerializedName("id") val id: Int,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("custom_device_name") val customDeviceName: String,
    @SerializedName("is_parent") val isParent: Int,
    @SerializedName("sim_one_number") val simOneNumber: String?,
    @SerializedName("sim_one_active") val simOneActive: Int,
    @SerializedName("sim_two_number") val simTwoNumber: String?,
    @SerializedName("sim_two_active") val simTwoActive: Int,
    @SerializedName("is_app_active") val isAppActive: Int
)

data class ChildDeviceListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: List<ChildDeviceDto>
)

data class DeviceConfigResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: ChildDeviceDto
)

data class RemoteUpdateDeviceRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("custom_device_name") val customDeviceName: String,
    @SerializedName("sim_one_number") val simOneNumber: String?,
    @SerializedName("sim_one_active") val simOneActive: Int,
    @SerializedName("sim_two_number") val simTwoNumber: String?,
    @SerializedName("sim_two_active") val simTwoActive: Int,
    @SerializedName("is_app_active") val isAppActive: Int
)

