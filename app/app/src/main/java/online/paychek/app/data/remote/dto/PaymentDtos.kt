package online.paychek.app.data.remote.dto

data class PaymentIngestRequest(
    val amount: Double,
    val trxId: String,
    val providerTag: String,
    val senderNumber: String,
    val receiverNumber: String?,
    val smsTimestamp: Long, // Epoch timestamp (Long)
    val rawBody: String,
    val simSlot: Int?,
    val simNumber: String?
)

data class PaymentIngestResponse(
    val success: Boolean,
    val isDuplicate: Boolean,
    val message: String?
)
