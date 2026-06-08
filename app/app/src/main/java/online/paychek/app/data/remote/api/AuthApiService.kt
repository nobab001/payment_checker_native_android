package online.paychek.app.data.remote.api

import online.paychek.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApiService {

    @POST("check-contact")
    suspend fun checkContact(
        @Body request: CheckContactRequest
    ): Response<CheckContactResponse>

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

    @POST("check-device-trial")
    suspend fun checkDeviceTrial(
        @Body request: CheckDeviceTrialRequest
    ): Response<CheckDeviceTrialResponse>
}
