package online.paychek.app.data.remote.api

import online.paychek.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * GatewayApiService — গেটওয়ে কাস্টমাইজারের সকল API endpoint
 */
interface GatewayApiService {

    // ── মেথড তালিকা লোড করা ─────────────────────────────────────────────────
    @GET("gateway/methods")
    suspend fun getGatewayMethods(
        @Header("Authorization") token: String,
        @Header("X-Gateway-Last-Sync") lastSync: Long = 0L,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<GatewayListResponse>

    // ── টেমপ্লেট তালিকা লোড করা ───────────────────────────────────────────────
    @GET("gateway/templates")
    suspend fun getTemplates(
        @Header("Authorization") token: String,
        @Header("X-Gateway-Last-Sync") lastSync: Long = 0L,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<SmsTemplatesResponse>

    // ── নতুন মেথড যোগ করা ──────────────────────────────────────────────────
    @POST("gateway/methods")
    suspend fun addGatewayMethod(
        @Header("Authorization") token: String,
        @Body request: AddGatewayMethodRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<AddGatewayMethodResponse>

    // ── Drag & Drop-এর পর Priority ক্রম সেভ করা ──────────────────────────────
    @PATCH("gateway/priority")
    suspend fun updatePriority(
        @Header("Authorization") token: String,
        @Body request: UpdatePriorityRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<GatewayListResponse>

    // ── একটি Method চালু/বন্ধ করা ────────────────────────────────────────────
    @PATCH("gateway/methods/{id}/toggle")
    suspend fun toggleMethod(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: ToggleRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<GatewayListResponse>

    // ── Method-এর নম্বর / নাম আপডেট করা ─────────────────────────────────────
    @PATCH("gateway/methods/{id}")
    suspend fun updateMethod(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: UpdateMethodRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<GatewayListResponse>

    // ── Parent-Child Control Hub APIs ────────────────────────────────────────
    @GET("v1/devices")
    suspend fun getChildDevices(
        @Header("Authorization") token: String
    ): Response<ChildDeviceListResponse>

    @POST("v1/devices/remote-update")
    suspend fun remoteUpdateDevice(
        @Header("Authorization") token: String,
        @Body request: RemoteUpdateDeviceRequest
    ): Response<BasicResponse>

    @GET("v1/devices/my-config")
    suspend fun getMyDeviceConfig(
        @Header("Authorization") token: String
    ): Response<DeviceConfigResponse>

    @GET("v1/devices/pending-approvals")
    suspend fun getPendingApprovals(
        @Header("Authorization") token: String
    ): Response<ChildDeviceListResponse>

    @POST("v1/devices/approve-by-pin")
    suspend fun approveByPin(
        @Header("Authorization") token: String,
        @Body request: ApproveDeviceRequest
    ): Response<BasicResponse>

    @POST("v1/devices/submit-role")
    suspend fun submitRole(
        @Header("Authorization") token: String,
        @Body request: SubmitRoleRequest
    ): Response<BasicResponse>

    @GET("v1/devices/check-approval-status")
    suspend fun checkApprovalStatus(
        @Header("Authorization") token: String
    ): Response<ApprovalStatusResponse>

    @POST("v1/devices/mark-setup-completed")
    suspend fun markSetupCompleted(
        @Header("Authorization") token: String
    ): Response<BasicResponse>

    @POST("v1/devices/toggle-remote-role")
    suspend fun toggleRemoteRole(
        @Header("Authorization") token: String,
        @Body request: ToggleRemoteRoleRequest
    ): Response<BasicResponse>

    @POST("v1/devices/delete")
    suspend fun deleteDevice(
        @Header("Authorization") token: String,
        @Body request: DeleteDeviceRequest
    ): Response<BasicResponse>

    @POST("gateway/custom-sender")
    suspend fun addCustomSender(
        @Header("Authorization") token: String,
        @Body request: AddCustomSenderRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<GatewayListResponse>

    @GET("gateway/custom-sender/suggestions")
    suspend fun getCustomSenderSuggestions(
        @Header("Authorization") token: String,
        @Query("q") query: String = "",
        @Query("category") category: String = "",
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<CustomSenderSuggestionsResponse>

    @DELETE("gateway/methods/{id}")
    suspend fun deleteGatewayMethod(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<GatewayListResponse>

    @POST("gateway/sim-swap")
    suspend fun syncAndValidateSimSwap(
        @Header("Authorization") token: String,
        @Body request: SimSwapRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<SimSwapResponse>

    @POST("gateway/slot/lookup")
    suspend fun lookupSlotNumber(
        @Header("Authorization") token: String,
        @Body request: SlotLookupRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<SlotLookupResponse>

    @POST("gateway/slot/force-shift")
    suspend fun forceShiftSlot(
        @Header("Authorization") token: String,
        @Body request: SlotForceShiftRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<SlotLookupResponse>

    @POST("gateway/slot/active")
    suspend fun setSlotActive(
        @Header("Authorization") token: String,
        @Body request: SlotActiveRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<SlotActiveResponse>

    @POST("gateway/methods/bulk-sync")
    suspend fun bulkSyncSlotMethods(
        @Header("Authorization") token: String,
        @Body request: BulkSyncRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<GatewayListResponse>

    /** Device-level number health ping (~60s while SMS service active) */
    @POST("gateway/heartbeat")
    suspend fun postHeartbeat(
        @Header("Authorization") token: String,
        @Body request: HeartbeatRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<HeartbeatResponse>

    /** All active SIM numbers under this account */
    @GET("gateway/account-numbers")
    suspend fun getAccountNumbers(
        @Header("Authorization") token: String,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<AccountNumbersResponse>

    /** Permanently remove a phone number from the account (server-side) */
    @HTTP(method = "DELETE", path = "gateway/account-numbers", hasBody = true)
    suspend fun deleteAccountNumber(
        @Header("Authorization") token: String,
        @Body request: DeleteAccountNumberRequest,
        @Header("X-Device-Id") deviceId: String = ""
    ): Response<DeleteAccountNumberResponse>
}
