package online.paychek.app.data.remote.api

import online.paychek.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ProfileApiService {

    // ── Credentials ──────────────────────────────────────────────
    @GET("credentials")
    suspend fun listCredentials(
        @Header("Authorization") token: String
    ): Response<ListCredentialsResponse>

    @POST("credentials/send-otp")
    suspend fun sendCredentialOtp(
        @Header("Authorization") token: String,
        @Body request: CredentialOtpRequest
    ): Response<CredentialActionResponse>

    @POST("credentials/verify")
    suspend fun verifyCredential(
        @Header("Authorization") token: String,
        @Body request: CredentialVerifyRequest
    ): Response<CredentialActionResponse>

    @DELETE("credentials/{id}")
    suspend fun removeCredential(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<CredentialActionResponse>

    // ── PIN Management ───────────────────────────────────────────
    @POST("pin/change")
    suspend fun changePin(
        @Header("Authorization") token: String,
        @Body request: ChangePinRequest
    ): Response<PinActionResponse>

    @POST("pin/reset-send-otp")
    suspend fun resetPinSendOtp(
        @Body request: ResetPinSendOtpRequest
    ): Response<PinActionResponse>

    @POST("pin/reset-verify")
    suspend fun resetPinVerify(
        @Body request: ResetPinVerifyRequest
    ): Response<PinActionResponse>

    @GET("v1/profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<ProfileResponse>
}
