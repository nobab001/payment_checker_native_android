package online.paychek.app.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BangladeshTimeUtil {
    private val bdTimeZone = TimeZone.getTimeZone("Asia/Dhaka")

    fun formatDateTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.forLanguageTag("bn-BD"))
        sdf.timeZone = bdTimeZone
        return sdf.format(Date(epochMs))
    }

    fun parseCreatedAtToEpochMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(raw)?.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    fun latestTransactionEpochMs(items: List<online.paychek.app.data.remote.dto.TransactionItem>): Long? {
        return items.mapNotNull { parseCreatedAtToEpochMs(it.createdAt) }.maxOrNull()
    }
}
