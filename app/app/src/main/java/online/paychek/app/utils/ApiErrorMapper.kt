package online.paychek.app.utils

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object ApiErrorMapper {

    fun fromHttpCode(code: Int, fallback: String = "ডেটা লোড ব্যর্থ"): String = when (code) {
        401, 403 -> "লগইন সেশন মেয়াদ শেষ। পুনরায় লগইন করুন।"
        in 500..599 -> "সার্ভার ত্রুটি ($code)। পরে আবার চেষ্টা করুন।"
        502, 503, 504 -> "সার্ভার সাময়িকভাবে ব্যস্ত। পরে আবার চেষ্টা করুন।"
        else -> "$fallback ($code)"
    }

    fun fromThrowable(error: Throwable, fallback: String = "ডেটা সিঙ্ক ব্যর্থ"): String {
        return when (error) {
            is UnknownHostException -> "DNS সমস্যা — সার্ভার ঠিকানা খুঁজে পাওয়া যায়নি।"
            is SocketTimeoutException -> "সার্ভার সাড়া দিচ্ছে না (টাইমআউট)। আবার চেষ্টা করুন।"
            is SSLException -> "SSL সংযোগ সমস্যা। পরে আবার চেষ্টা করুন।"
            is IOException -> "সংযোগ বিচ্ছিন্ন হয়েছে। আবার চেষ্টা করুন।"
            else -> error.message?.takeIf { it.isNotBlank() } ?: fallback
        }
    }
}
