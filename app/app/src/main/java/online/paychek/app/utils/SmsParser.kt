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

    // All hardcoded regex patterns removed for Dynamic SMS Template Builder

    /**
     * Legacy parseSms logic disabled. Now parsing depends exclusively on Dynamic SMS Templates.
     */
    fun parseSms(sender: String, body: String, timestamp: Long): ParsedPayment? {
        return null
    }

    fun parseWithDynamicRegex(
        body: String,
        regexPattern: String?,
        providerTag: String,
        senderNumber: String,
        timestamp: Long,
        simSlot: Int?,
        simNumber: String?,
        isCustomSender: Boolean
    ): ParsedPayment? {
        if (regexPattern.isNullOrBlank()) return null
        return try {
            val trimmedBody = body.trim()
            val pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val matcher = pattern.matcher(trimmedBody)
            if (matcher.matches()) {
                val amountStr = try { matcher.group("amount") } catch (e: Exception) { null }
                val parsedAmount = amountStr?.replace(",", "")?.toDoubleOrNull() ?: 0.0

                val trxId = try { matcher.group("trxid") } catch (e: Exception) { "" } ?: ""
                
                val parsedSender = try { matcher.group("sender") } catch (e: Exception) { null } ?: senderNumber

                if (trxId.isNotEmpty()) {
                    ParsedPayment(
                        amount = parsedAmount,
                        trxId = trxId.uppercase(Locale.US),
                        providerTag = providerTag,
                        senderNumber = parsedSender,
                        rawBody = body,
                        smsTimestamp = timestamp,
                        simSlot = simSlot,
                        simNumber = simNumber,
                        isCustomSender = isCustomSender,
                        fullSms = body
                    )
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
