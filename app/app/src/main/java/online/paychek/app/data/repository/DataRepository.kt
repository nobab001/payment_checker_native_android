package online.paychek.app.data.repository

import online.paychek.app.data.remote.dto.*
import online.paychek.app.data.remote.api.RetrofitClient

/**
 * PaymentRepository — পেমেন্ট ও Dashboard সংক্রান্ত সকল ডেটা অ্যাক্সেস
 *
 * এই Repository ViewModel থেকে সরাসরি call হয়।
 * Result<T> wrapper ব্যবহার করা হয়েছে, যাতে ViewModel-এ try/catch না লিখতে হয়।
 */
class PaymentRepository {

    private val api = RetrofitClient.paymentApiService

    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard Statistics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Dashboard-এর statistics API থেকে লোড করে।
     * @return Result.success(DashboardStats) বা Result.failure(Exception)
     */
    suspend fun fetchDashboardStats(token: String): Result<DashboardStats> {
        return try {
            val response = api.getDashboardStats("Bearer $token")
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception("Stats লোড ব্যর্থ: ${body?.success}"))
                }
            } else {
                Result.failure(Exception("Server Error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("নেটওয়ার্ক সমস্যা: ${e.message}"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transaction History (Paginated)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * পেজিনেটেড ট্রানজেকশন লিস্ট API থেকে লোড করে।
     * @param token    JWT Bearer token
     * @param page     পেজ নম্বর (1-based)
     * @param limit    প্রতি পেজে আইটেম সংখ্যা
     * @param provider ফিল্টার: "all" | "bKash" | "Nagad" | "Rocket" | "Upay"
     * @return Result<List<TransactionItem>> — সফল হলে তালিকা, না হলে error
     */
    suspend fun fetchTransactionHistory(
        token: String,
        page: Int    = 1,
        limit: Int   = 20,
        provider: String = "all"
    ): Result<List<TransactionItem>> {
        return try {
            val response = api.getTransactionHistory(
                token    = "Bearer $token",
                page     = page,
                limit    = limit,
                provider = provider
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception("Transaction লোড ব্যর্থ"))
                }
            } else {
                Result.failure(Exception("Server Error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("নেটওয়ার্ক সমস্যা: ${e.message}"))
        }
    }


    suspend fun updateFcmToken(token: String, fcmToken: String?): Result<Unit> {
        return try {
            val response = api.updateFcmToken("Bearer $token", online.paychek.app.data.remote.dto.FcmTokenRequest(fcmToken))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "FCM টোকেন আপডেট ব্যর্থ হয়েছে"))
                }
            } else {
                Result.failure(Exception("Server Error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("নেটওয়ার্ক সমস্যা: ${e.message}"))
        }
    }

    suspend fun getPlans(token: String): Result<List<SubscriptionPlanDto>> {
        return try {
            val response = api.getPlans("Bearer $token")
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(body.plans)
                } else {
                    Result.failure(Exception("প্ল্যান লোড ব্যর্থ হয়েছে"))
                }
            } else {
                Result.failure(Exception("Server Error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("নেটওয়ার্ক সমস্যা: ${e.message}"))
        }
    }

    suspend fun purchaseSubscription(token: String, planName: String): Result<PurchaseSubscriptionResponse> {
        return try {
            val response = api.purchaseSubscription("Bearer $token", PurchaseSubscriptionRequest(planName))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body?.message ?: "প্যাকেজ ক্রয় ব্যর্থ হয়েছে"))
                }
            } else {
                Result.failure(Exception("Server Error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("নেটওয়ার্ক সমস্যা: ${e.message}"))
        }
    }
}
