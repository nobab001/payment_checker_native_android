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

    @SerializedName("is_parseable")
    val isParseable: Int?,

    @SerializedName("single_number_instruction")
    val singleNumberInstruction: String?,

    @SerializedName("multiple_number_instruction")
    val multipleNumberInstruction: String?,

    @SerializedName("created_at")
    val createdAt: String? = null
)

// =============================================================================
// Gateway List Response
// =============================================================================
data class GatewayListResponse(
    val success: Boolean,
    val message: String? = null,
    val data: List<GatewayMethod>? = null,
    @SerializedName("data_version") val dataVersion: Long? = null,
    val unchanged: Boolean? = false,
    @SerializedName("has_conflict") val hasConflict: Boolean? = null,
    @SerializedName("running_device_name") val runningDeviceName: String? = null
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
    @SerializedName("device_specific_pin") val deviceSpecificPin: String? = null,
    @SerializedName("device_model") val deviceModel: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("last_seen_at") val lastSeenAt: String? = null,
    @SerializedName("is_current") val isCurrent: Int = 0,
    @SerializedName("has_device_pin") val hasDevicePin: Int = 0
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
    @SerializedName("deviceRole") val deviceRole: String,
    @SerializedName("deviceSpecificPin") val deviceSpecificPin: String? = null
)

data class DeleteDeviceRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("pin") val pin: String
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
    @SerializedName("status") val status: String?,
    @SerializedName("setupCompleted") val setupCompleted: Boolean = false,
    @SerializedName("deviceSpecificPin") val deviceSpecificPin: String? = null
)

data class AddCustomSenderRequest(
    @SerializedName("sim_slot") val simSlot: Int,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("official_template_id") val officialTemplateId: Int? = null,
    @SerializedName("create_personal") val createPersonal: Boolean? = null
)

data class CustomSenderSuggestionDto(
    @SerializedName("id") val id: Int,
    @SerializedName("template_name") val templateName: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("sender_number") val senderNumber: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("is_admin_archive") val isAdminArchive: Boolean? = true
)

data class CustomSenderSuggestionsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("suggestions") val suggestions: List<CustomSenderSuggestionDto>? = null
)

data class SimSwapRequest(
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("slotIndex") val slotIndex: Int,
    @SerializedName("force_shift") val forceShift: Boolean? = null
)

data class SimSwapResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("is_known_sim") val isKnownSim: Boolean? = null,
    @SerializedName("has_conflict") val hasConflict: Boolean? = null,
    @SerializedName("running_device_name") val runningDeviceName: String? = null,
    @SerializedName("message") val message: String?,
    @SerializedName("cached_methods") val cachedMethods: List<GatewayMethod>? = null,
    @SerializedName("data") val data: List<GatewayMethod>?
)

data class SlotLookupRequest(
    @SerializedName("sim_slot") val simSlot: Int,
    @SerializedName("phone_number") val phoneNumber: String
)

data class SlotForceShiftRequest(
    @SerializedName("sim_slot") val simSlot: Int,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("force_shift") val forceShift: Boolean = true
)

data class SlotActiveRequest(
    @SerializedName("sim_slot") val simSlot: Int,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("is_active") val isActive: Int
)

data class BulkSyncMethodItem(
    @SerializedName("template_id") val templateId: Int?,
    @SerializedName("provider") val provider: String,
    @SerializedName("is_enabled") val isEnabled: Int = 1
)

data class BulkSyncRequest(
    @SerializedName("sim_slot") val simSlot: Int,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("methods") val methods: List<BulkSyncMethodItem>,
    @SerializedName("replace_slot") val replaceSlot: Boolean = false,
    @SerializedName("activate_binding") val activateBinding: Boolean = true
)

data class SlotActiveResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("has_conflict") val hasConflict: Boolean? = null,
    @SerializedName("running_device_name") val runningDeviceName: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("data") val data: List<GatewayMethod>? = null
)

data class SlotLookupResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("has_conflict") val hasConflict: Boolean? = null,
    @SerializedName("running_device_name") val runningDeviceName: String? = null,
    @SerializedName("apply_profile") val applyProfile: Boolean? = null,
    @SerializedName("cached_methods") val cachedMethods: List<GatewayMethod>? = null,
    @SerializedName("data") val data: List<GatewayMethod>? = null,
    @SerializedName("message") val message: String? = null
)

data class HeartbeatNumberItem(
    @SerializedName("sim_slot") val simSlot: Int,
    @SerializedName("phone_number") val phoneNumber: String
)

data class HeartbeatRequest(
    @SerializedName("numbers") val numbers: List<HeartbeatNumberItem>,
    @SerializedName("sms_service_active") val smsServiceActive: Boolean = true,
    @SerializedName("battery_percent") val batteryPercent: Int? = null,
    /** v2.5 presence trigger: boot_completed | network_restored (optional). */
    @SerializedName("presence_trigger") val presenceTrigger: String? = null
)

data class HeartbeatResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("skipped") val skipped: Boolean? = null,
    @SerializedName("server_time") val serverTime: Long? = null,
    @SerializedName("numbers") val numbers: List<String>? = null,
    @SerializedName("states") val states: Map<String, String>? = null,
    /** Next heartbeat interval seconds (Comm Policy). */
    @SerializedName("heartbeat") val heartbeatSec: Int? = null,
    @SerializedName("forceSync") val forceSync: Boolean? = null,
    @SerializedName("templateVersion") val templateVersion: Any? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("profile") val profile: String? = null,
    @SerializedName("use_socket") val useSocket: Boolean? = null
)

data class AccountNumberDto(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("device_name") val deviceName: String? = null,
    @SerializedName("sim_slot") val simSlot: Int = 1,
    @SerializedName("health_state") val healthState: String? = null,
    @SerializedName("method_count") val methodCount: Int = 0,
    @SerializedName("providers") val providers: List<String>? = null
)

data class AccountNumbersResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: List<AccountNumberDto>? = null,
    @SerializedName("error") val error: String? = null
)

data class DeleteAccountNumberRequest(
    @SerializedName("phone_number") val phoneNumber: String
)

data class DeleteAccountNumberResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("error") val error: String? = null
)

