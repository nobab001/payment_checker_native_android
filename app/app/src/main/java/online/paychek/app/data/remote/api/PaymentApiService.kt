package online.paychek.app.data.remote.api

import online.paychek.app.data.remote.dto.PaymentIngestRequest
import online.paychek.app.data.remote.dto.PaymentIngestResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface PaymentApiService {

    @POST("payment-sms-ingest")
    suspend fun ingestPaymentSms(
        @Header("Authorization") token: String,
        @Body request: PaymentIngestRequest
    ): Response<PaymentIngestResponse>
}
