package online.paychek.app.services.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard-2 cursor policy tests (HMAC stall / recovery / dedupe ordering).
 *
 * These tests encode the production contract without Android runtime:
 * A HMAC success → cursor advances
 * B HMAC failure → SMS remains recoverable (cursor blocked before it)
 * C HMAC later available → subsequent poll can advance past it (simulated)
 * D Duplicates after retry do not break cursor (HANDLED)
 * E Ordering: success after a retryable failure does not jump the cursor
 */
class Guard2CursorTrackerTest {

    @Test
    fun a_hmacSuccess_cursorAdvancesNormally() {
        val t = Guard2CursorTracker()
        t.onCandidate(10L, Guard2CursorTracker.Outcome.HANDLED)
        t.onCandidate(11L, Guard2CursorTracker.Outcome.HANDLED)
        t.onCandidate(12L, Guard2CursorTracker.Outcome.HANDLED)

        assertTrue(t.shouldCommit())
        assertEquals(12L, t.committableId)
        assertFalse(t.blocked)
    }

    @Test
    fun b_hmacFailure_smsRemainsRecoverable_cursorDoesNotPassFailedId() {
        val t = Guard2CursorTracker()
        t.onCandidate(10L, Guard2CursorTracker.Outcome.HANDLED)
        t.onCandidate(11L, Guard2CursorTracker.Outcome.RETRYABLE_FAILURE) // HMAC down
        t.onCandidate(12L, Guard2CursorTracker.Outcome.HANDLED) // later SMS may still queue

        assertTrue(t.shouldCommit())
        assertEquals(10L, t.committableId) // must NOT advance to 11 or 12
        assertTrue(t.blocked)
    }

    @Test
    fun c_hmacAvailableLater_retryPollAdvancesPastPreviouslyFailedSms() {
        // Poll 1 — HMAC unavailable for id=11
        val poll1 = Guard2CursorTracker()
        poll1.onCandidate(10L, Guard2CursorTracker.Outcome.HANDLED)
        poll1.onCandidate(11L, Guard2CursorTracker.Outcome.RETRYABLE_FAILURE)
        assertEquals(10L, poll1.committableId)

        // Poll 2 — secret restored; id=11 now HANDLED (queued); id=12 duplicate HANDLED
        val poll2 = Guard2CursorTracker()
        poll2.onCandidate(11L, Guard2CursorTracker.Outcome.HANDLED)
        poll2.onCandidate(12L, Guard2CursorTracker.Outcome.HANDLED)
        assertEquals(12L, poll2.committableId)
        assertFalse(poll2.blocked)
    }

    @Test
    fun d_duplicateAfterRetry_isHandled_noCursorStall() {
        val t = Guard2CursorTracker()
        // First poll queued 20; second poll sees duplicate → HANDLED
        t.onCandidate(20L, Guard2CursorTracker.Outcome.HANDLED)
        t.onCandidate(21L, Guard2CursorTracker.Outcome.HANDLED)
        assertEquals(21L, t.committableId)
        assertFalse(t.blocked)
    }

    @Test
    fun e_ordering_successAfterRetryableDoesNotJumpCursor() {
        val t = Guard2CursorTracker()
        t.onCandidate(1L, Guard2CursorTracker.Outcome.HANDLED)
        t.onCandidate(2L, Guard2CursorTracker.Outcome.RETRYABLE_FAILURE)
        t.onCandidate(3L, Guard2CursorTracker.Outcome.HANDLED)
        t.onCandidate(4L, Guard2CursorTracker.Outcome.HANDLED)

        assertEquals(1L, t.committableId)
        assertTrue(t.blocked)
    }

    @Test
    fun permanentFailure_doesNotBlockLaterSms() {
        val t = Guard2CursorTracker()
        t.onCandidate(1L, Guard2CursorTracker.Outcome.HANDLED)
        t.onCandidate(2L, Guard2CursorTracker.Outcome.HANDLED) // permanent skip treated as HANDLED by worker
        t.onCandidate(3L, Guard2CursorTracker.Outcome.HANDLED)

        assertEquals(3L, t.committableId)
        assertFalse(t.blocked)
    }

    @Test
    fun retryableFailureAtStart_commitsNothing() {
        val t = Guard2CursorTracker()
        t.onCandidate(5L, Guard2CursorTracker.Outcome.RETRYABLE_FAILURE)
        t.onCandidate(6L, Guard2CursorTracker.Outcome.HANDLED)

        assertFalse(t.shouldCommit())
        assertEquals(-1L, t.committableId)
        assertTrue(t.blocked)
    }

    @Test
    fun failureKind_hmacMessageIsRetryable() {
        assertTrue(SmsQueueFailureKind.isRetryable(RetryableSmsQueueException("HMAC secret missing")))
        assertTrue(SmsQueueFailureKind.isRetryable(IllegalStateException("HMAC generation failed")))
        assertFalse(SmsQueueFailureKind.isRetryable(PermanentSmsQueueException("rawBody is blank")))
    }
}
