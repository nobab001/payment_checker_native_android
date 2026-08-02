package online.paychek.app.services.sync

/**
 * Pure cursor commit policy for Guard-2 inbox recovery.
 *
 * Contract:
 * - Cursor advances only over a contiguous prefix of durably handled SMS (_id ASC).
 * - Retryable failures (e.g. temporary HMAC secret unavailability) block the cursor
 *   so the same SMS remains recoverable on a later poll.
 * - Permanent failures (e.g. blank body) are treated as handled so a poison SMS
 *   cannot stall recovery of later messages forever.
 * - Later candidates in the same batch are still processed by the worker; this
 *   tracker only decides how far [commitCursor] may advance.
 */
class Guard2CursorTracker {

    var committableId: Long = -1L
        private set

    var blocked: Boolean = false
        private set

    enum class Outcome {
        /** Queued, duplicate, DROP, SIM-disabled skip, old-SMS skip — safe to advance past. */
        HANDLED,
        /** Not persisted; must remain visible to the next scan (HMAC / transient). */
        RETRYABLE_FAILURE
    }

    fun onCandidate(smsId: Long, outcome: Outcome) {
        when (outcome) {
            Outcome.RETRYABLE_FAILURE -> blocked = true
            Outcome.HANDLED -> {
                if (!blocked) {
                    committableId = smsId
                }
            }
        }
    }

    fun shouldCommit(): Boolean = committableId > 0L
}

/**
 * Classifies queue pipeline failures for Guard-2 cursor policy.
 */
object SmsQueueFailureKind {
    fun isRetryable(error: Throwable): Boolean {
        return error is RetryableSmsQueueException ||
            error.message?.contains("HMAC", ignoreCase = true) == true
    }
}

/** Temporary crypto/config failure — SMS must remain recoverable. */
class RetryableSmsQueueException(message: String) : IllegalStateException(message)

/** Unrecoverable input — safe to advance cursor past this SMS. */
class PermanentSmsQueueException(message: String) : IllegalArgumentException(message)
