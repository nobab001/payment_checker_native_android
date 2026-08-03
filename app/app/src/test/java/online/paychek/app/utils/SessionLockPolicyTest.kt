package online.paychek.app.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused unit tests for the 5-minute cold-start / resume lock policy.
 */
class SessionLockPolicyTest {

    @Test
    fun noSession_neverLocks() {
        assertFalse(
            SessionLockPolicy.shouldLock(
                hasAuthSession = false,
                lastBackgroundTimeMs = 1L,
                nowMs = SessionLockPolicy.TIMEOUT_MS + 10_000L,
            )
        )
    }

    @Test
    fun underFiveMinutes_doesNotLock() {
        val last = 1_000_000L
        assertFalse(
            SessionLockPolicy.shouldLock(
                hasAuthSession = true,
                lastBackgroundTimeMs = last,
                nowMs = last + SessionLockPolicy.TIMEOUT_MS - 1L,
            )
        )
    }

    @Test
    fun exactlyFiveMinutes_doesNotLock() {
        // Policy uses ">" so equality stays unlocked (matches prior MainActivity check).
        val last = 1_000_000L
        assertFalse(
            SessionLockPolicy.shouldLock(
                hasAuthSession = true,
                lastBackgroundTimeMs = last,
                nowMs = last + SessionLockPolicy.TIMEOUT_MS,
            )
        )
    }

    @Test
    fun overFiveMinutes_locks() {
        val last = 1_000_000L
        assertTrue(
            SessionLockPolicy.shouldLock(
                hasAuthSession = true,
                lastBackgroundTimeMs = last,
                nowMs = last + SessionLockPolicy.TIMEOUT_MS + 1L,
            )
        )
    }

    @Test
    fun unknownBackgroundStamp_locks() {
        assertTrue(
            SessionLockPolicy.shouldLock(
                hasAuthSession = true,
                lastBackgroundTimeMs = 0L,
                nowMs = 50_000L,
            )
        )
    }

    @Test
    fun systemSettingsHandoff_skipsLock() {
        val last = 1L
        assertFalse(
            SessionLockPolicy.shouldLock(
                hasAuthSession = true,
                lastBackgroundTimeMs = last,
                nowMs = last + SessionLockPolicy.TIMEOUT_MS + 60_000L,
                skipForSystemHandoff = true,
            )
        )
    }
}
