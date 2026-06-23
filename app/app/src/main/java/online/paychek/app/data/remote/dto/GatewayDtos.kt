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

    @SerializedName("sender_number")
    val senderNumber: String?,

    @SerializedName("matching_keyword")
    val matchingKeyword: String?,

    @SerializedName("regex_pattern")
    val regexPattern: String?,

    @SerializedName("custom_patterns")
    val customPatterns: List<String>?,

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
    val message: String? = null,
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
// Add Gateway Method — নতুন মেথড যোগ করা
// =============================================================================
data class AddGatewayMethodRequest(
    @SerializedName("sim_slot") val simSlot: Int,
    @SerializedName("provider") val provider: String,
    @SerializedName("template_id") val templateId: Int?,
    @SerializedName("number") val number: String?
)

data class AddGatewayMethodResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("id") val id: Int?,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: List<GatewayMethod>? = null
)

// =============================================================================
// Parent-Child Control Hub DTOs
// =============================================================================
data class ChildDeviceDto(
    @SerializedName("id") val id: Int,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("custom_device_name") val customDeviceName: String,
    @SerializedName("is_parent") val isParent: Int,
    @SerializedName("is_approved") val isApproved: Int = 0,
    @SerializedName("device_role") val deviceRole: String = "pending",
    @SerializedName("sim_one_number") val simOneNumber: String?,
    @SerializedName("sim_one_active") val simOneActive: Int,
    @SerializedName("sim_two_number") val simTwoNumber: String?,
    @SerializedName("sim_two_active") val simTwoActive: Int,
    @SerializedName("is_app_active") val isAppActive: Int,
    @SerializedName("is_owner") val isOwner: Int? = null,
    @SerializedName("device_specific_pin") val deviceSpecificPin: String? = null
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
    @SerializedName("is_app_active") val isAppActive: Int,
    @SerializedName("is_owner") val isOwner: Int? = null,
    @SerializedName("device_specific_pin") val deviceSpecificPin: String? = null
)

data class ApproveDeviceRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("pin") val pin: String,
    @SerializedName("deviceRole") val deviceRole: String
)

data class ToggleRemoteRoleRequest(
    @SerializedName("remoteDeviceId") val remoteDeviceId: String,
    @SerializedName("newRole") val newRole: String,
    @SerializedName("pin") val pin: String
)

data class SubmitRoleRequest(
    @SerializedName("role") val role: String
)

data class ApprovalStatusResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("isApproved") val isApproved: Boolean,
    @SerializedName("deviceRole") val deviceRole: String?,
    @SerializedName("status") val status: String?
)


