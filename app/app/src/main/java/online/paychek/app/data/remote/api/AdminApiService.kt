package online.paychek.app.data.remote.api

import online.paychek.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface AdminApiService {

    // 1. App Configs (global_config)
    @GET("admin/config")
    suspend fun getConfigs(
        @Header("Authorization") token: String
    ): Response<ConfigResponse>

    @POST("admin/config")
    suspend fun updateConfig(
        @Header("Authorization") token: String,
        @Body request: UpdateConfigRequest
    ): Response<AdminGenericResponse>

    @POST("admin/config")
    suspend fun updateConfigs(
        @Header("Authorization") token: String,
        @Body request: Map<String, @JvmSuppressWildcards Map<String, String>>
    ): Response<AdminGenericResponse>

    // 2. Official SMS Templates
    @GET("admin/sms-templates")
    suspend fun getSmsTemplates(
        @Header("Authorization") token: String
    ): Response<SmsTemplatesResponse>

    @POST("admin/sms-templates")
    suspend fun saveSmsTemplate(
        @Header("Authorization") token: String,
        @Body request: SmsTemplateDto
    ): Response<AdminGenericResponse>

    @POST("admin/sms-templates/reorder")
    suspend fun reorderSmsTemplates(
        @Header("Authorization") token: String,
        @Body request: SmsTemplateReorderRequest
    ): Response<AdminGenericResponse>

    @DELETE("admin/sms-templates/{id}")
    suspend fun deleteSmsTemplate(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<AdminGenericResponse>

    @GET("admin/global-blocked-senders")
    suspend fun getGlobalBlockedSenders(
        @Header("Authorization") token: String
    ): Response<GlobalBlockedSendersResponse>

    @PUT("admin/global-blocked-senders")
    suspend fun saveGlobalBlockedSenders(
        @Header("Authorization") token: String,
        @Body request: SaveGlobalBlockedSendersRequest
    ): Response<GlobalBlockedSendersResponse>

    // 3. Checkout View Templates
    @GET("admin/checkout-templates")
    suspend fun getCheckoutTemplates(
        @Header("Authorization") token: String
    ): Response<CheckoutTemplatesResponse>

    @POST("admin/checkout-templates")
    suspend fun saveCheckoutTemplate(
        @Header("Authorization") token: String,
        @Body request: CheckoutTemplateDto
    ): Response<AdminGenericResponse>

    @DELETE("admin/checkout-templates/{id}")
    suspend fun deleteCheckoutTemplate(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<AdminGenericResponse>

    // 4. SMTP Accounts
    @GET("admin/email-accounts")
    suspend fun getEmailAccounts(
        @Header("Authorization") token: String
    ): Response<EmailAccountsResponse>

    @POST("admin/email-accounts")
    suspend fun saveEmailAccount(
        @Header("Authorization") token: String,
        @Body request: EmailAccountDto
    ): Response<AdminGenericResponse>

    @DELETE("admin/email-accounts/{id}")
    suspend fun deleteEmailAccount(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<AdminGenericResponse>

    // 5. SMS Gateway configurations
    @GET("admin/sms-settings")
    suspend fun getSmsSettings(
        @Header("Authorization") token: String
    ): Response<SmsSettingsResponse>

    @POST("admin/sms-settings")
    suspend fun saveSmsSettings(
        @Header("Authorization") token: String,
        @Body request: SmsSettingsDto
    ): Response<AdminGenericResponse>

    // 6. User and Device management
    @GET("admin/users")
    suspend fun getUsers(
        @Header("Authorization") token: String
    ): Response<UsersListResponse>

    @POST("admin/users/{id}/block")
    suspend fun toggleUserBlock(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: BlockUserRequest
    ): Response<AdminGenericResponse>

    @POST("admin/devices/{id}/trial")
    suspend fun updateDeviceTrial(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: UpdateDeviceTrialRequest
    ): Response<AdminGenericResponse>

    @GET("admin/otp-format")
    suspend fun getOtpFormat(
        @Header("Authorization") token: String
    ): Response<OtpFormatResponse>

    @POST("admin/otp-format/update")
    suspend fun updateOtpFormat(
        @Header("Authorization") token: String,
        @Body request: UpdateOtpFormatRequest
    ): Response<AdminGenericResponse>

    @POST("admin/users/{id}/extend-subscription")
    suspend fun extendSubscription(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: ExtendSubscriptionRequest
    ): Response<ExtendSubscriptionResponse>

    @POST("admin/users/{id}/manual-grace")
    suspend fun updateUserManualGrace(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: ManualGraceRequest
    ): Response<AdminGenericResponse>

    @GET("admin/plans")
    suspend fun getPlans(
        @Header("Authorization") token: String
    ): Response<SubscriptionPlansResponse>

    @POST("admin/plans")
    suspend fun savePlan(
        @Header("Authorization") token: String,
        @Body request: SubscriptionPlanDto
    ): Response<AdminGenericResponse>

    @DELETE("admin/plans/{id}")
    suspend fun deletePlan(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<AdminGenericResponse>

    @GET("admin/addon-plans")
    suspend fun getAddonPlans(
        @Header("Authorization") token: String
    ): Response<AddonPlansResponse>

    @POST("admin/addon-plans")
    suspend fun saveAddonPlan(
        @Header("Authorization") token: String,
        @Body request: AddonPlanDto
    ): Response<AdminGenericResponse>

    @DELETE("admin/addon-plans/{id}")
    suspend fun deleteAddonPlan(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<AdminGenericResponse>

    @POST("admin/plans/reorder")
    suspend fun reorderPlans(
        @Header("Authorization") token: String,
        @Body request: PlanReorderRequest
    ): Response<AdminGenericResponse>

    @POST("admin/plans/{id}/archive")
    suspend fun archivePlan(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<AdminGenericResponse>

    @GET("admin/subscription/v3/settings")
    suspend fun getV3SubscriptionSettings(
        @Header("Authorization") token: String
    ): Response<V3SettingsResponse>

    @POST("admin/subscription/v3/settings")
    suspend fun updateV3SubscriptionSettings(
        @Header("Authorization") token: String,
        @Body request: V3SettingsUpdateRequest
    ): Response<V3SettingsResponse>

    @GET("admin/subscription/refunds/pending")
    suspend fun getPendingRefunds(
        @Header("Authorization") token: String
    ): Response<V3PendingRefundsResponse>

    @POST("admin/subscription/refunds/{id}/resolve")
    suspend fun resolveRefund(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: V3ResolveRefundRequest
    ): Response<AdminGenericResponse>

    @POST("admin/addon-plans/reorder")
    suspend fun reorderAddonPlans(
        @Header("Authorization") token: String,
        @Body request: PlanReorderRequest
    ): Response<AdminGenericResponse>

    @POST("admin/billing-tab-order")
    suspend fun saveBillingTabOrder(
        @Header("Authorization") token: String,
        @Body request: BillingTabOrderRequest
    ): Response<AdminGenericResponse>

    @GET("admin/websites")
    suspend fun getWebsites(
        @Header("Authorization") token: String
    ): Response<AdminWebsitesResponse>

    @POST("admin/websites/{id}/permissions")
    suspend fun setWebsitePermissions(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: WebsitePermissionsRequest
    ): Response<WebsitePermissionsResponse>

    @GET("admin/checkout-design")
    suspend fun getCheckoutDesignConfig(
        @Header("Authorization") token: String
    ): Response<CheckoutDesignConfigResponse>

    @POST("admin/checkout-design")
    suspend fun saveCheckoutDesignConfig(
        @Header("Authorization") token: String,
        @Body request: SaveCheckoutDesignRequest
    ): Response<CheckoutDesignConfigResponse>

    @POST("admin/upload-image")
    suspend fun uploadCheckoutImage(
        @Header("Authorization") token: String,
        @Body request: UploadImageRequest
    ): Response<UploadImageResponse>

    @GET("admin/official-website")
    suspend fun getOfficialWebsiteCms(
        @Header("Authorization") token: String
    ): Response<OfficialWebsiteCmsResponse>

    @PUT("admin/official-website")
    suspend fun saveOfficialWebsiteCms(
        @Header("Authorization") token: String,
        @Body request: SaveOfficialWebsiteCmsRequest
    ): Response<OfficialWebsiteCmsResponse>

    @GET("admin/demo-payments")
    suspend fun getDemoPayments(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("refundStatus") refundStatus: String = "all"
    ): Response<DemoPaymentsResponse>

    @PATCH("admin/demo-payments/{id}/refund")
    suspend fun updateDemoPaymentRefund(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: UpdateDemoPaymentRefundRequest
    ): Response<DemoPaymentRefundResponse>

}
