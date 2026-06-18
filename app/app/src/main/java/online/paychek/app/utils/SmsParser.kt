package online.paychek.app.utils

import java.util.Locale
import java.util.regex.Pattern

object SmsParser {

    data class ParsedPayment(
        val amount: Double,
        val trxId: String,
        val providerTag: String,
        val senderNumber: String,
        val rawBody: String,
        val smsTimestamp: Long,
        val simSlot: Int? = null,      // 1 বা 2 — কোন SIM স্লট থেকে এলো
        val simNumber: String? = null, // ওই SIM-এর ফোন নম্বর (যদি পাওয়া যায়)
        val isCustomSender: Boolean = false,
        val fullSms: String? = null
    )

    // Regex Patterns
    private val bkashPattern = Pattern.compile(
        "You have received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID\\s*([A-Z0-9]{6,})",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val nagadPattern = Pattern.compile(
        "received cash in Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID:\\s*([A-Z0-9]{6,})",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val rocketPattern = Pattern.compile(
        "received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID:\\s*([A-Z0-9]{6,})",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val upayPattern = Pattern.compile(
        "received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID\\s*([A-Z0-9]{6,})",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    /**
     * Parses an incoming SMS message.
     * Returns a ParsedPayment if it is a match for bKash, Nagad, Rocket, or Upay, otherwise null.
     */
    fun parseSms(sender: String, body: String, timestamp: Long): ParsedPayment? {
        val cleanSender = sender.trim().lowercase(Locale.US)
        
        return when {
            cleanSender.contains("bkash") -> {
                matchPattern(body, bkashPattern, "bKash", timestamp)
            }
            cleanSender.contains("nagad") -> {
                matchPattern(body, nagadPattern, "Nagad", timestamp)
            }
            cleanSender.contains("rocket") || cleanSender == "16216" -> {
                matchPattern(body, rocketPattern, "Rocket", timestamp)
            }
            cleanSender.contains("upay") -> {
                matchPattern(body, upayPattern, "Upay", timestamp)
            }
            else -> {
                // Fallback catch-all check: attempt matching each template regardless of sender name
                matchPattern(body, bkashPattern, "bKash", timestamp)
                    ?: matchPattern(body, nagadPattern, "Nagad", timestamp)
                    ?: matchPattern(body, rocketPattern, "Rocket", timestamp)
                    ?: matchPattern(body, upayPattern, "Upay", timestamp)
            }
        }
    }

    private fun matchPattern(body: String, pattern: Pattern, providerTag: String, timestamp: Long): ParsedPayment? {
        val matcher = pattern.matcher(body)
        if (matcher.find()) {
            return try {
                val amountStr = matcher.group(1)?.replace(",", "") ?: "0.0"
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                val senderNumber = matcher.group(2) ?: "Unknown"
                val trxId = matcher.group(3) ?: ""
                
                if (trxId.isNotEmpty() && amount > 0) {
                    ParsedPayment(
                        amount = amount,
                        trxId = trxId.uppercase(Locale.US),
                        providerTag = providerTag,
                        senderNumber = senderNumber,
                        rawBody = body,
                        smsTimestamp = timestamp
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}
