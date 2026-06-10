package online.paychek.app.data.remote.api

import online.paychek.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.Path

interface AuthApiService {

    @GET("config/public")
    suspend fun getPublicConfig(): Response<ConfigResponse>

    @POST("check-contact")
    suspend fun checkContact(
        @Body request: ContactCheckRequest
    ): Response<ContactCheckResponse>

    @POST("send-otp")
    suspend fun sendOtp(
        @Body request: SendOtpRequest
    ): Response<OtpResponse>

    @POST("auth/register-send-otp")
    suspend fun registerSendOtp(
        @Body request: SendOtpRequest
    ): Response<OtpResponse>

    @POST("send-otp-new")
    suspend fun sendOtpNew(
        @Body request: SendOtpRequest
    ): Response<OtpResponse>

    @POST("verify-otp")
    suspend fun verifyOtp(
        @Body request: VerifyOtpRequest
    ): Response<VerifyOtpResponse>

    @POST("complete-profile")
    suspend fun completeProfile(
        @Header("Authorization") token: String,
        @Body request: CompleteProfileRequest
    ): Response<CompleteProfileResponse>

    @POST("check-device-login")
    suspend fun checkDeviceLogin(
        @Body request: DeviceCheckRequest
    ): Response<DeviceCheckResponse>

    @POST("pin/verify")
    suspend fun verifyPin(
        @Header("Authorization") token: String,
        @Body request: VerifyPinRequest
    ): Response<VerifyPinResponse>

    // ── Multi-Credential Association ─────────────────────────────────
    @GET("credentials")
    suspend fun listCredentials(
        @Header("Authorization") token: String
    ): Response<LinkedCredentialsResponse>

    @POST("credentials/send-otp")
    suspend fun sendLinkOtp(
        @Header("Authorization") token: String,
        @Body request: LinkCredentialOtpRequest
    ): Response<okhttp3.ResponseBody>

    @POST("credentials/verify")
    suspend fun verifyLinkCredential(
        @Header("Authorization") token: String,
        @Body request: VerifyLinkCredentialRequest
    ): Response<okhttp3.ResponseBody>

    @DELETE("credentials/{id}")
    suspend fun removeCredential(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @retrofit2.http.Query("pin") pin: String
    ): Response<okhttp3.ResponseBody>
}
