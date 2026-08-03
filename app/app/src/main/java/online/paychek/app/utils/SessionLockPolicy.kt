package online.paychek.app.utils

/**
 * App re-entry lock policy shared by cold start and background resume.
 *
 * Session age is measured from [lastBackgroundTimeMs] (written in Activity.onStop).
 * Unknown age (timestamp unset) requires lock — secure default.
 */
object SessionLockPolicy {
    const val TIMEOUT_MS: Long = 300_000L // 5 minutes

    fun shouldLock(
        hasAuthSession: Boolean,
        lastBackgroundTimeMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        skipForSystemHandoff: Boolean = false,
    ): Boolean {
        if (!hasAuthSession) return false
        if (skipForSystemHandoff) return false
        if (lastBackgroundTimeMs <= 0L) return true
        return nowMs - lastBackgroundTimeMs > TIMEOUT_MS
    }
}
