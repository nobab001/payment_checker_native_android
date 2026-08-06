package online.paychek.app.ui.common

/**
 * Progressive SMS history windows shared by Home + Search.
 * INITIAL_20 = latest page (~20). Then expand: 7 → 15 → 21 → 30 days.
 * Custom Archive stops at 15 days (rolling retention window).
 */
enum class HistoryLoadTier {
    INITIAL_20,
    DAYS_7,
    DAYS_15,
    DAYS_21,
    DAYS_30,
    CUSTOM
}

fun HistoryLoadTier.nextHistoryDays(): Int? = when (this) {
    HistoryLoadTier.INITIAL_20 -> 7
    HistoryLoadTier.DAYS_7 -> 15
    HistoryLoadTier.DAYS_15 -> 21
    HistoryLoadTier.DAYS_21 -> 30
    else -> null
}

/** Archive progressive load: 20 → 7d → 15d only. */
fun HistoryLoadTier.nextArchiveHistoryDays(): Int? = when (this) {
    HistoryLoadTier.INITIAL_20 -> 7
    HistoryLoadTier.DAYS_7 -> 15
    else -> null
}

fun historyLoadMoreLabelBn(nextDays: Int): String = when (nextDays) {
    7 -> "আরো সাত দিনের হিস্টরি দেখুন"
    15 -> "আরো পনেরো দিনের হিস্টরি দেখুন"
    21 -> "আরো একুশ দিনের হিস্টরি দেখুন"
    30 -> "আরো ত্রিশ দিনের হিস্টরি দেখুন"
    else -> "আরো হিস্টরি দেখুন"
}

fun tierForHistoryDays(days: Int): HistoryLoadTier = when (days) {
    7 -> HistoryLoadTier.DAYS_7
    15 -> HistoryLoadTier.DAYS_15
    21 -> HistoryLoadTier.DAYS_21
    30 -> HistoryLoadTier.DAYS_30
    else -> HistoryLoadTier.DAYS_7
}
