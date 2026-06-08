package online.paychek.app.data.remote.api

import online.paychek.app.config.AppConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    // Add X-Device-Id if we have one (handled dynamically in ViewModel / Repository)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    val authApiService: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }

    val paymentApiService: PaymentApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PaymentApiService::class.java)
    }

    val gatewayApiService: GatewayApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GatewayApiService::class.java)
    }

    val adminApiService: AdminApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AdminApiService::class.java)
    }
}
