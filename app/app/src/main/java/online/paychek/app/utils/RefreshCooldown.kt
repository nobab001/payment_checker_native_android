package online.paychek.app.utils

/**
 * Global 5-second cooldown between manual refresh actions across the app.
 */
object RefreshCooldown {
    const val COOLDOWN_MS = 5_000L

    @Volatile
    private var lastRefreshAt = 0L

    fun canRefresh(): Boolean =
        System.currentTimeMillis() - lastRefreshAt >= COOLDOWN_MS

    fun tryRefresh(action: () -> Unit): Boolean {
        if (!canRefresh()) return false
        lastRefreshAt = System.currentTimeMillis()
        action()
        return true
    }

    fun secondsUntilNext(): Int {
        val remaining = COOLDOWN_MS - (System.currentTimeMillis() - lastRefreshAt)
        return if (remaining <= 0) 0 else ((remaining + 999) / 1000).toInt()
    }
}
