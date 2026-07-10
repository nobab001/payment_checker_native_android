package online.paychek.app.data.remote.api

import online.paychek.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * WebsiteApiService — API Integration v2 merchant/website management.
 * All endpoints require JWT (Authorization: Bearer ...). Base URL already ends
 * with /api/, so paths are relative to that (e.g. "v1/websites").
 */
interface WebsiteApiService {

    @GET("v1/websites/global-checkout")
    suspend fun getGlobalCheckout(
        @Header("Authorization") token: String
    ): Response<GlobalCheckoutResponse>

    @PUT("v1/websites/global-checkout")
    suspend fun saveGlobalCheckout(
        @Header("Authorization") token: String,
        @Body request: SaveGlobalCheckoutRequest
    ): Response<GlobalCheckoutResponse>

    @GET("v1/websites")
    suspend fun listWebsites(
        @Header("Authorization") token: String
    ): Response<ListWebsitesResponse>

    @POST("v1/websites")
    suspend fun createWebsite(
        @Header("Authorization") token: String,
        @Body request: CreateWebsiteRequest
    ): Response<CreateWebsiteResponse>

    @GET("v1/websites/{id}")
    suspend fun getWebsite(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<WebsiteDetailResponse>

    @PATCH("v1/websites/{id}")
    suspend fun updateWebsite(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: UpdateWebsiteRequest
    ): Response<WebsiteUpdateResponse>

    @Multipart
    @POST("v1/websites/{id}/branding/logo")
    suspend fun uploadWebsiteLogo(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Part logo: okhttp3.MultipartBody.Part
    ): Response<WebsiteLogoResponse>

    @DELETE("v1/websites/{id}/branding/logo")
    suspend fun deleteWebsiteLogo(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<WebsiteLogoResponse>

    @HTTP(method = "DELETE", path = "v1/websites/{id}", hasBody = true)
    suspend fun deleteWebsite(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: DeleteWebsiteRequest
    ): Response<SimpleWebsiteActionResponse>

    @POST("v1/websites/{id}/regenerate-secret")
    suspend fun regenerateSecret(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<RegenerateSecretResponse>

    @PUT("v1/websites/{id}/number-order")
    suspend fun updateNumberOrder(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: NumberOrderRequest
    ): Response<NumberOrderResponse>

    @GET("v1/websites/{id}/commissions")
    suspend fun listCommissions(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<CommissionListResponse>

    @POST("v1/websites/{id}/commissions")
    suspend fun upsertCommission(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: UpsertCommissionRequest
    ): Response<CommissionUpsertResponse>

    @DELETE("v1/websites/{id}/commissions/{commissionId}")
    suspend fun deleteCommission(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Path("commissionId") commissionId: Int
    ): Response<SimpleWebsiteActionResponse>

    // ── Official (redirect-based) payment gateways (Phase 6) ──────────────────
    @GET("v1/websites/{id}/official-gateways")
    suspend fun listOfficialGateways(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<OfficialGatewayListResponse>

    @POST("v1/websites/{id}/official-gateways")
    suspend fun upsertOfficialGateway(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: UpsertOfficialGatewayRequest
    ): Response<OfficialGatewayUpsertResponse>

    @DELETE("v1/websites/{id}/official-gateways/{gatewayId}")
    suspend fun deleteOfficialGateway(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Path("gatewayId") gatewayId: Int
    ): Response<SimpleWebsiteActionResponse>
}
