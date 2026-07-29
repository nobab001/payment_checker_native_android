package online.paychek.app.utils

import java.text.DecimalFormat

/**
 * Shared money formatting for the app.
 * Convention: ৳ prefix, comma-separated thousands, no decimals for whole-taka amounts.
 * Examples: ৳1,200 | ৳500 | ৳12,500.50
 */
object MoneyFormat {

    private val wholeFmt = DecimalFormat("#,##0")
    private val decimalFmt = DecimalFormat("#,##0.00")

    /** Format as whole-taka (drops .0) or with decimals if needed. */
    fun taka(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            "৳${wholeFmt.format(amount.toLong())}"
        } else {
            "৳${decimalFmt.format(amount)}"
        }
    }

    /** Format with Bengali digits: ৳১,২০০ */
    fun takaBangla(amount: Double): String {
        return BanglaDateTimeFormat.toBanglaDigits(taka(amount))
    }

    /** Format per-month equivalent: "≈ ৳100/মাস" */
    fun perMonth(totalPrice: Double, months: Int): String {
        if (months <= 1) return ""
        val monthly = totalPrice / months
        return "≈ ${taka(monthly.toLong().toDouble())}/মাস"
    }
}
