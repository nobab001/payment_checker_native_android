package online.paychek.app.data.remote.api

import online.paychek.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * PaymentApiService — পেমেন্ট সম্পর্কিত সকল API endpoint
 *
 * Endpoints:
 *  POST api/payment-sms-ingest  → SMS পার্স করে সার্ভারে পাঠানো (আগে থেকে আছে)
 *  GET  api/sms-history         → পেজিনেটেড ট্রানজেকশন লিস্ট
 *  GET  api/dashboard/stats     → Dashboard statistics (মোট আয়, আজকের আয় ইত্যাদি)
 */
interface PaymentApiService {

    // ─── SMS Ingest (আগে থেকে আছে) ──────────────────────────────────────────
    @POST("payment-sms-ingest")
    suspend fun ingestPaymentSms(
        @Header("Authorization") token: String,
        @Body request: PaymentIngestRequest
    ): Response<PaymentIngestResponse>

    @POST("payment-sms-ingest/bulk")
    suspend fun ingestPaymentSmsBulk(
        @Header("Authorization") token: String,
        @Body request: BulkPaymentIngestRequest
    ): Response<BulkPaymentIngestResponse>

    @GET("ping")
    suspend fun pingServer(): Response<okhttp3.ResponseBody>

    // ─── Transaction History ─────────────────────────────────────────────────
    /**
     * পেজিনেটেড ট্রানজেকশন লিস্ট
     * @param token    Bearer JWT token
     * @param page     পেজ নম্বর (1 থেকে শুরু)
     * @param limit    প্রতি পেজে কটি আইটেম (default 20)
     * @param provider ফিল্টার: bKash | Nagad | Rocket | Upay | all
     */
    @GET("sms-history")
    suspend fun getTransactionHistory(
        @Header("Authorization") token: String,
        @Query("page")     page: Int     = 1,
        @Query("limit")    limit: Int    = 20,
        @Query("provider") provider: String = "all",
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<TransactionListResponse>

    // ─── Dashboard Stats ─────────────────────────────────────────────────────
    /**
     * Dashboard-এর জন্য সংক্ষিপ্ত পরিসংখ্যান
     * @param token Bearer JWT token
     */
    @GET("dashboard/stats")
    suspend fun getDashboardStats(
        @Header("Authorization") token: String,
        @Header("X-Gateway-Last-Sync") lastSync: Long?
    ): Response<DashboardStatsResponse>

    @POST("sms-history/{id}/soldout")
    suspend fun markTransactionSoldOut(
        @Header("Authorization") token: String,
        @Path("id") transactionId: Int
    ): Response<CredentialActionResponse>


    @POST("v1/subscription/fcm-token")
    suspend fun updateFcmToken(
        @Header("Authorization") token: String,
        @Body request: FcmTokenRequest
    ): Response<FcmTokenResponse>

    @GET("v1/plans")
    suspend fun getPlans(
        @Header("Authorization") token: String
    ): Response<SubscriptionPlansResponse>

    @POST("v1/subscription/purchase")
    suspend fun purchaseSubscription(
        @Header("Authorization") token: String,
        @Body request: PurchaseSubscriptionRequest
    ): Response<PurchaseSubscriptionResponse>

    @POST("v1/subscription/purchase-addon")
    suspend fun purchaseSubscriptionAddon(
        @Header("Authorization") token: String
    ): Response<PurchaseAddonResponse>

    @GET("custom-archives")
    suspend fun getCustomArchives(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<CustomArchiveListResponse>
}
