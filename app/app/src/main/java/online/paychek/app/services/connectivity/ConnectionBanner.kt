package online.paychek.app.services.connectivity

/**
 * Connection UI status banners.
 *
 * ✅ Connected → no banner (null)
 * 🔄 Reconnecting → reconnecting to server
 * 📡 Disconnected → no server link; showing last saved data
 * 🌐 No Internet → device offline
 * 🔧 Server Error → temporary server/API problem
 */
sealed class ConnectionBanner {
    abstract val message: String
    open val isRetrying: Boolean get() = false

    data class Reconnecting(
        override val message: String = MSG_RECONNECTING,
        override val isRetrying: Boolean = true
    ) : ConnectionBanner()

    data class Disconnected(
        override val message: String = MSG_DISCONNECTED
    ) : ConnectionBanner()

    data class NoInternet(
        override val message: String = MSG_NO_INTERNET
    ) : ConnectionBanner()

    data class ServerError(
        override val message: String = MSG_SERVER_ERROR,
        override val isRetrying: Boolean = false
    ) : ConnectionBanner()

    companion object {
        const val MSG_RECONNECTING = "সার্ভারের সাথে পুনরায় সংযোগ করা হচ্ছে..."
        const val MSG_DISCONNECTED =
            "সার্ভারের সাথে সংযোগ নেই। সর্বশেষ সংরক্ষিত তথ্য দেখানো হচ্ছে।"
        const val MSG_NO_INTERNET = "ইন্টারনেট সংযোগ নেই।"
        const val MSG_SERVER_ERROR =
            "সার্ভারে সাময়িক সমস্যা হচ্ছে। কিছুক্ষণ পরে আবার চেষ্টা করুন।"
    }
}
