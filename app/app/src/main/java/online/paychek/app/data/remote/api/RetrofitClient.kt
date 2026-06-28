package online.paychek.app.data.remote.api

import android.content.Context
import online.paychek.app.config.AppConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import online.paychek.app.utils.DeviceIdHelper
import online.paychek.app.utils.GsonUtils

object RetrofitClient {

    private val customGson = GsonUtils.gson

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                appContext?.let { ctx ->
                    val deviceId = DeviceIdHelper.getHashedAndroidId(ctx)
                    if (deviceId.isNotBlank()) {
                        builder.header(AppConfig.HEADER_DEVICE_ID, deviceId)
                    }
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    val authApiService: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(customGson))
            .build()
            .create(AuthApiService::class.java)
    }

    val paymentApiService: PaymentApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(customGson))
            .build()
            .create(PaymentApiService::class.java)
    }

    val gatewayApiService: GatewayApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(customGson))
            .build()
            .create(GatewayApiService::class.java)
    }

    val adminApiService: AdminApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(customGson))
            .build()
            .create(AdminApiService::class.java)
    }

    val profileApiService: ProfileApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(customGson))
            .build()
            .create(ProfileApiService::class.java)
    }
}
