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
        @Header("X-History-Last-Sync") historyLastSync: Long? = null,
        @Query("page")     page: Int     = 1,
        @Query("limit")    limit: Int    = 20,
        @Query("provider") provider: String = "all",
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("trxId") trxId: String? = null
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

    @POST("sms-history/manual")
    suspend fun createManualTransaction(
        @Header("Authorization") token: String,
        @Body request: ManualTransactionRequest
    ): Response<ManualTransactionResponse>


    @POST("v1/subscription/fcm-token")
    suspend fun updateFcmToken(
        @Header("Authorization") token: String,
        @Body request: FcmTokenRequest
    ): Response<FcmTokenResponse>

    @GET("v1/plans")
    suspend fun getPlans(
        @Header("Authorization") token: String
    ): Response<SubscriptionPlansResponse>

    @GET("v1/subscription/quote")
    suspend fun getSubscriptionQuote(
        @Header("Authorization") token: String,
        @Query("planName") planName: String
    ): Response<SubscriptionQuoteResponse>

    @POST("v1/subscription/checkout-init")
    suspend fun initSubscriptionCheckout(
        @Header("Authorization") token: String,
        @Body request: SubscriptionCheckoutInitRequest
    ): Response<SubscriptionCheckoutInitResponse>

    @POST("v1/subscription/addon-checkout-init")
    suspend fun initAddonCheckout(
        @Header("Authorization") token: String,
        @Body request: AddonCheckoutInitRequest
    ): Response<SubscriptionCheckoutInitResponse>

    @GET("v1/subscription/checkout-status")
    suspend fun getSubscriptionCheckoutStatus(
        @Header("Authorization") token: String,
        @Query("orderId") orderId: String
    ): Response<SubscriptionCheckoutStatusResponse>

    @POST("v1/subscription/purchase")
    suspend fun purchaseSubscription(
        @Header("Authorization") token: String,
        @Body request: PurchaseSubscriptionRequest
    ): Response<PurchaseSubscriptionResponse>

    @GET("v1/addon-plans")
    suspend fun getAddonPlans(
        @Header("Authorization") token: String
    ): Response<AddonPlansResponse>

    @POST("v1/subscription/purchase-addon")
    suspend fun purchaseSubscriptionAddon(
        @Header("Authorization") token: String,
        @Body request: PurchaseAddonRequest
    ): Response<PurchaseAddonResponse>

    @GET("v1/account/entitlements")
    suspend fun getAccountEntitlements(
        @Header("Authorization") token: String
    ): Response<AccountEntitlementsResponse>

    @GET("v1/billing/catalog")
    suspend fun getV3BillingCatalog(
        @Header("Authorization") token: String
    ): Response<SubscriptionV3CatalogResponse>

    @POST("v1/subscription/v3/quote")
    suspend fun postV3Quote(
        @Header("Authorization") token: String,
        @Body request: V3QuoteRequest
    ): Response<V3QuoteResponse>

    @POST("v1/subscription/v3/checkout-init")
    suspend fun postV3CheckoutInit(
        @Header("Authorization") token: String,
        @Body request: V3CheckoutInitRequest
    ): Response<V3CheckoutInitResponse>

    @GET("v1/subscription/history")
    suspend fun getV3PurchaseHistory(
        @Header("Authorization") token: String
    ): Response<V3PurchaseHistoryResponse>

    @POST("v1/subscription/refund-request")
    suspend fun postV3RefundRequest(
        @Header("Authorization") token: String,
        @Body request: V3RefundRequest
    ): Response<V3RefundRequestResponse>

    @GET("custom-archives")
    suspend fun getCustomArchives(
        @Header("Authorization") token: String,
        @Header("X-Archive-Last-Sync") archiveLastSync: Long? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("q") query: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<CustomArchiveListResponse>
}
