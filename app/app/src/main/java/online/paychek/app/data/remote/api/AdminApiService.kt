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

    @DELETE("admin/sms-templates/{id}")
    suspend fun deleteSmsTemplate(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<AdminGenericResponse>

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
}
