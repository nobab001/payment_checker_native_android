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
        @Header("Authorization") token: String
    ): Response<GatewayListResponse>

    // ── Drag & Drop-এর পর Priority ক্রম সেভ করা ──────────────────────────────
    @PATCH("gateway/priority")
    suspend fun updatePriority(
        @Header("Authorization") token: String,
        @Body request: UpdatePriorityRequest
    ): Response<BasicResponse>

    // ── একটি Method চালু/বন্ধ করা ────────────────────────────────────────────
    @PATCH("gateway/methods/{id}/toggle")
    suspend fun toggleMethod(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: ToggleRequest
    ): Response<BasicResponse>

    // ── Method-এর নম্বর / নাম আপডেট করা ─────────────────────────────────────
    @PATCH("gateway/methods/{id}")
    suspend fun updateMethod(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: UpdateMethodRequest
    ): Response<BasicResponse>

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

    @POST("v1/devices/toggle-remote-role")
    suspend fun toggleRemoteRole(
        @Header("Authorization") token: String,
        @Body request: ToggleRemoteRoleRequest
    ): Response<BasicResponse>
}
