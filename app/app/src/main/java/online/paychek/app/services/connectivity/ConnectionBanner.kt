package online.paychek.app.services.connectivity

/**
 * UI banner types — each layer failure gets its own message.
 * Only [NoInternet] should use the "ইন্টারনেট সংযোগ নেই" wording.
 */
sealed class ConnectionBanner {
    abstract val message: String

    data class NoInternet(
        override val message: String = "ইন্টারনেট সংযোগ নেই। ডেটা আপডেট হচ্ছে না।"
    ) : ConnectionBanner()

    data class ServerUnavailable(
        override val message: String = "সার্ভার সাময়িকভাবে অনুপলব্ধ। ক্যাশ ডেটা দেখানো হচ্ছে।"
    ) : ConnectionBanner()

    data class DataSyncFailed(
        override val message: String,
        val isRetrying: Boolean = false
    ) : ConnectionBanner()
}
