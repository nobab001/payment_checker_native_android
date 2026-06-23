package online.paychek.app.data.repository

import android.content.Context
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.*
import online.paychek.app.utils.SecurePreferences
import retrofit2.Response

class CredentialRepository(private val context: Context) {

    private val api = RetrofitClient.authApiService

    private fun getBearerToken(): String {
        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    suspend fun getLinkedCredentials(): Result<LinkedCredentialsResponse> {
        return try {
            val response = api.listCredentials(getBearerToken())
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("খালি রেসপন্স এসেছে"))
                }
            } else {
                val errText = parseError(response)
                Result.failure(Exception(errText))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendLinkOtp(request: LinkCredentialOtpRequest): Response<okhttp3.ResponseBody> {
        return api.sendLinkOtp(getBearerToken(), request)
    }

    suspend fun verifyLinkCredential(request: VerifyLinkCredentialRequest): Response<okhttp3.ResponseBody> {
        return api.verifyLinkCredential(getBearerToken(), request)
    }

    suspend fun removeCredential(id: Int, pin: String): Response<okhttp3.ResponseBody> {
        return api.removeCredential(getBearerToken(), id, pin)
    }

    private fun parseError(response: Response<*>): String {
        return try {
            val errBody = response.errorBody()?.string() ?: ""
            val map = online.paychek.app.utils.GsonUtils.gson.fromJson(errBody, Map::class.java)
            (map["message"] ?: map["error"])?.toString() ?: "সার্ভার এরর ${response.code()}"
        } catch (e: Exception) {
            "সার্ভার এরর ${response.code()}"
        }
    }
}
