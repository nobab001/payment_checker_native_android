package online.paychek.app.services.smartpopup

import online.paychek.app.data.remote.dto.TransactionItem

object SmartPopupState {
    @Volatile var isOpen: Boolean = false

    @Volatile var isMinimized: Boolean = false

    @Volatile var isScanning: Boolean = false

    @Volatile var currentQuery: String = ""

    /** Idle | Result (after scan) | SessionHistory */
    @Volatile var viewMode: ViewMode = ViewMode.IDLE

    /** Searched / sold-out items since this popup session opened. */
    private val sessionHistory = LinkedHashMap<Int, TransactionItem>()

    enum class ViewMode { IDLE, RESULT, SESSION_HISTORY }

    fun clearSession() {
        sessionHistory.clear()
        currentQuery = ""
        viewMode = ViewMode.IDLE
        isScanning = false
    }

    fun trackSessionItems(items: List<TransactionItem>) {
        items.forEach { sessionHistory[it.id] = it }
    }

    fun trackSoldOut(item: TransactionItem) {
        sessionHistory[item.id] = item.copy(isUsed = 1)
    }

    fun sessionItems(): List<TransactionItem> = sessionHistory.values.toList().asReversed()

    fun hasSessionHistory(): Boolean = sessionHistory.isNotEmpty()
}
