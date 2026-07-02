package online.paychek.app.data.repository

import android.content.Context
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.*
import online.paychek.app.utils.SecurePreferences
import retrofit2.Response

/**
 * WebsiteRepository — API Integration v2 merchant/website data access.
 * Thin wrapper over WebsiteApiService with consistent Result<> + error parsing.
 */
class WebsiteRepository(private val context: Context) {

    private val api = RetrofitClient.websiteApiService

    private fun bearer(): String {
        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    suspend fun listWebsites(): Result<List<WebsiteDto>> = safeCall {
        val r = api.listWebsites(bearer())
        if (r.isSuccessful) Result.success(r.body()?.websites ?: emptyList())
        else Result.failure(Exception(parseError(r)))
    }

    suspend fun createWebsite(domain: String, name: String?): Result<CreateWebsiteResponse> = safeCall {
        val r = api.createWebsite(bearer(), CreateWebsiteRequest(domain = domain, websiteName = name))
        val body = r.body()
        if (r.isSuccessful && body?.success == true) Result.success(body)
        else Result.failure(Exception(body?.message ?: body?.error ?: parseError(r)))
    }

    suspend fun getWebsite(id: Int): Result<WebsiteDetailResponse> = safeCall {
        val r = api.getWebsite(bearer(), id)
        if (r.isSuccessful && r.body()?.success == true) Result.success(r.body()!!)
        else Result.failure(Exception(parseError(r)))
    }

    suspend fun updateWebsite(id: Int, request: UpdateWebsiteRequest): Result<WebsiteDto> = safeCall {
        val r = api.updateWebsite(bearer(), id, request)
        val body = r.body()
        if (r.isSuccessful && body?.success == true && body.website != null) Result.success(body.website)
        else Result.failure(Exception(body?.error ?: parseError(r)))
    }

    suspend fun deleteWebsite(id: Int): Result<Unit> = safeCall {
        val r = api.deleteWebsite(bearer(), id)
        if (r.isSuccessful && r.body()?.success == true) Result.success(Unit)
        else Result.failure(Exception(parseError(r)))
    }

    suspend fun regenerateSecret(id: Int): Result<RegenerateSecretResponse> = safeCall {
        val r = api.regenerateSecret(bearer(), id)
        val body = r.body()
        if (r.isSuccessful && body?.success == true) Result.success(body)
        else Result.failure(Exception(body?.message ?: parseError(r)))
    }

    suspend fun updateNumberOrder(id: Int, order: List<NumberOrderItem>): Result<List<NumberOrderItem>> = safeCall {
        val r = api.updateNumberOrder(bearer(), id, NumberOrderRequest(order))
        if (r.isSuccessful && r.body()?.success == true) Result.success(r.body()?.numberOrder ?: order)
        else Result.failure(Exception(parseError(r)))
    }

    suspend fun listCommissions(id: Int): Result<CommissionListResponse> = safeCall {
        val r = api.listCommissions(bearer(), id)
        if (r.isSuccessful && r.body()?.success == true) Result.success(r.body()!!)
        else Result.failure(Exception(parseError(r)))
    }

    suspend fun upsertCommission(id: Int, request: UpsertCommissionRequest): Result<CommissionDto> = safeCall {
        val r = api.upsertCommission(bearer(), id, request)
        val body = r.body()
        if (r.isSuccessful && body?.success == true && body.commission != null) Result.success(body.commission)
        else Result.failure(Exception(body?.error ?: parseError(r)))
    }

    suspend fun deleteCommission(id: Int, commissionId: Int): Result<Unit> = safeCall {
        val r = api.deleteCommission(bearer(), id, commissionId)
        if (r.isSuccessful && r.body()?.success == true) Result.success(Unit)
        else Result.failure(Exception(parseError(r)))
    }

    private inline fun <T> safeCall(block: () -> Result<T>): Result<T> =
        try { block() } catch (e: Exception) { Result.failure(e) }

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
