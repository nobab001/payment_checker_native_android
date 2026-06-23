package online.paychek.app.data.remote.api

import online.paychek.app.config.AppConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class BooleanDeserializer : JsonDeserializer<Boolean> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Boolean {
        if (json.isJsonPrimitive) {
            val primitive = json.asJsonPrimitive
            if (primitive.isBoolean) return primitive.asBoolean
            if (primitive.isNumber) return primitive.asInt == 1
            if (primitive.isString) {
                val str = primitive.asString
                return str.equals("true", ignoreCase = true) || str == "1"
            }
        }
        return false
    }
}

object RetrofitClient {

    private val customGson = GsonBuilder()
        .registerTypeAdapter(Boolean::class.java, BooleanDeserializer())
        .registerTypeAdapter(Boolean::class.javaObjectType, BooleanDeserializer())
        .create()

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
