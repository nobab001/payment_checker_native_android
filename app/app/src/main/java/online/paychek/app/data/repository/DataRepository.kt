package online.paychek.app.data.repository

import online.paychek.app.data.remote.dto.*
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.utils.ApiErrorMapper

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
    suspend fun fetchDashboardStats(token: String, lastSync: Long): Result<DashboardStats> {
        return try {
            val response = api.getDashboardStats("Bearer $token", lastSync)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception("Stats লোড ব্যর্থ: ${body?.success}"))
                }
            } else {
                Result.failure(Exception(ApiErrorMapper.fromHttpCode(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorMapper.fromThrowable(e)))
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
        provider: String = "all",
        startDate: String? = null,
        endDate: String? = null,
        historyLastSync: Long? = null
    ): Result<TransactionHistoryResult> {
        return try {
            val syncHeader = if (page == 1 && (historyLastSync ?: 0L) > 0L) historyLastSync else null
            val response = api.getTransactionHistory(
                token    = "Bearer $token",
                historyLastSync = syncHeader,
                page     = page,
                limit    = limit,
                provider = provider,
                startDate = startDate,
                endDate = endDate
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(
                        TransactionHistoryResult(
                            items = body.data,
                            cacheHit = body.cacheHit == true,
                            historyVersion = body.historyVersion,
                            hasMore = body.hasMore
                        )
                    )
                } else {
                    Result.failure(Exception("Transaction লোড ব্যর্থ"))
                }
            } else {
                Result.failure(Exception(ApiErrorMapper.fromHttpCode(response.code(), "Transaction লোড ব্যর্থ")))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorMapper.fromThrowable(e, "Transaction লোড ব্যর্থ")))
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
                Result.failure(Exception(ApiErrorMapper.fromHttpCode(response.code(), "Transaction লোড ব্যর্থ")))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorMapper.fromThrowable(e, "Transaction লোড ব্যর্থ")))
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
                Result.failure(Exception(ApiErrorMapper.fromHttpCode(response.code(), "Transaction লোড ব্যর্থ")))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorMapper.fromThrowable(e, "Transaction লোড ব্যর্থ")))
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
                Result.failure(Exception(ApiErrorMapper.fromHttpCode(response.code(), "Transaction লোড ব্যর্থ")))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorMapper.fromThrowable(e, "Transaction লোড ব্যর্থ")))
        }
    }

    suspend fun markTransactionSoldOut(token: String, transactionId: Int): Result<Unit> {
        return try {
            val response = api.markTransactionSoldOut("Bearer $token", transactionId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "স্ট্যাটাস পরিবর্তন ব্যর্থ হয়েছে"))
                }
            } else {
                Result.failure(Exception(ApiErrorMapper.fromHttpCode(response.code(), "Transaction লোড ব্যর্থ")))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorMapper.fromThrowable(e, "Transaction লোড ব্যর্থ")))
        }
    }

    suspend fun fetchCustomArchives(token: String, page: Int = 1, limit: Int = 20): Result<List<CustomArchiveItem>> {
        return try {
            val response = api.getCustomArchives("Bearer $token", page, limit)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception("কাস্টম আর্কাইভ লোড ব্যর্থ"))
                }
            } else {
                Result.failure(Exception(ApiErrorMapper.fromHttpCode(response.code(), "Transaction লোড ব্যর্থ")))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorMapper.fromThrowable(e, "Transaction লোড ব্যর্থ")))
        }
    }

    suspend fun getAddonPlans(token: String): Result<List<AddonPlanDto>> {
        return try {
            val response = api.getAddonPlans("Bearer $token")
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(body.plans)
                } else {
                    Result.failure(Exception("অ্যাড-অন প্যাকেজ লোড ব্যর্থ হয়েছে"))
                }
            } else {
                Result.failure(Exception(ApiErrorMapper.fromHttpCode(response.code(), "Transaction লোড ব্যর্থ")))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorMapper.fromThrowable(e, "Transaction লোড ব্যর্থ")))
        }
    }

    suspend fun purchaseSubscriptionAddon(token: String, planId: Int): Result<PurchaseAddonResponse> {
        return try {
            val response = api.purchaseSubscriptionAddon("Bearer $token", PurchaseAddonRequest(planId))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body?.message ?: "অ্যাড-অন ক্রয় ব্যর্থ হয়েছে"))
                }
            } else {
                Result.failure(Exception(ApiErrorMapper.fromHttpCode(response.code(), "Transaction লোড ব্যর্থ")))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorMapper.fromThrowable(e, "Transaction লোড ব্যর্থ")))
        }
    }
}
