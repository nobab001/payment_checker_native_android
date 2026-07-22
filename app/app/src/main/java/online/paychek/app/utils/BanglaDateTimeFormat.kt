package online.paychek.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Transaction history card timestamps: Bengali digits + full Bengali month name.
 * Example: ২২ জুলাই, ০৫:৩১ PM
 */
object BanglaDateTimeFormat {

    private val bnMonths = arrayOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    private val parseFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    fun formatTrxCard(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val date = parse(raw) ?: return toBanglaDigits(raw.take(16))
        val cal = Calendar.getInstance().apply { time = date }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = bnMonths.getOrElse(cal.get(Calendar.MONTH)) { "" }
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.US)
        val time = timeFmt.format(date)
        return toBanglaDigits("$day $month, $time")
    }

    fun toBanglaDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            sb.append(if (c in '0'..'9') ('০' + (c - '0')) else c)
        }
        return sb.toString()
    }

    private fun parse(raw: String): Date? {
        val trimmed = raw.trim()
        for (pattern in parseFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                if (pattern.endsWith("'Z'") || pattern.contains("T")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(trimmed) ?: continue
                return parsed
            } catch (_: Exception) {
                // try next
            }
        }
        trimmed.toLongOrNull()?.let { epoch ->
            val ms = if (epoch < 1_000_000_000_000L) epoch * 1000L else epoch
            return Date(ms)
        }
        return null
    }
}
