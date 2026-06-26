package online.paychek.app.domain.repository

import online.paychek.app.data.remote.dto.DashboardStats
import online.paychek.app.data.remote.dto.PurchaseSubscriptionResponse
import online.paychek.app.data.remote.dto.SubscriptionPlanDto
import online.paychek.app.data.remote.dto.TransactionItem

/**
 * IPaymentRepository — Domain interface for payment and dashboard data operations.
 *
 * Implementation: data/repository/PaymentRepository (concrete class, unchanged)
 *
 * Why interface:
 *  - Decouples ViewModel from Retrofit/Room details.
 *  - Enables unit testing with fakes (no network required in tests).
 *  - Future: allows swapping implementation (e.g. Room cache + API) transparently.
 *
 * Rule: All methods return Result<T>. ViewModel never calls suspend directly
 *       against a concrete class — only against this interface.
 */
interface IPaymentRepository {

    /**
     * Fetches dashboard statistics summary.
     * @param token  JWT auth token (without "Bearer " prefix)
     * @param lastSync last sync timestamp
     */
    suspend fun fetchDashboardStats(token: String, lastSync: Long): Result<DashboardStats>

    /**
     * Fetches paginated transaction history.
     * @param token    JWT auth token (without "Bearer " prefix)
     * @param page     1-based page number
     * @param limit    Items per page
     * @param provider Provider filter: "all" | "bKash" | "Nagad" | "Rocket" | "Upay"
     */
    suspend fun fetchTransactionHistory(
        token: String,
        page: Int = 1,
        limit: Int = 20,
        provider: String = "all"
    ): Result<List<TransactionItem>>

    /**
     * Updates the device FCM push notification token on the server.
     * @param token    JWT auth token
     * @param fcmToken Firebase Cloud Messaging token (null to clear)
     */
    suspend fun updateFcmToken(token: String, fcmToken: String?): Result<Unit>

    /**
     * Fetches available subscription plans.
     */
    suspend fun getPlans(token: String): Result<List<SubscriptionPlanDto>>

    /**
     * Purchases a subscription plan by name.
     * @param planName  Exact plan identifier from getPlans()
     */
    suspend fun purchaseSubscription(
        token: String,
        planName: String
    ): Result<PurchaseSubscriptionResponse>
}
