package online.paychek.app.ui.screen.profile

import android.util.Patterns

object CredentialInputUtils {

    fun formatPhoneInput(raw: String): String =
        raw.filter { it.isDigit() }.take(11)

    fun formatEmailInput(raw: String): String {
        var s = raw.trim().lowercase().filter { !it.isWhitespace() }
        if (s.endsWith("@") && s.count { it == '@' } == 1) {
            s = "${s}gmail.com"
        }
        return s
    }

    fun validatePhone(phone: String): String? {
        val digits = phone.trim()
        if (digits.isEmpty()) return "মোবাইল নম্বর লিখুন"
        if (digits.length != 11) return "সঠিক ১১-সংখ্যার মোবাইল নম্বর দিন (যেমন: 01XXXXXXXXX)"
        if (!digits.all { it.isDigit() }) return "মোবাইল নম্বরে শুধু সংখ্যা ব্যবহার করুন"
        if (!digits.startsWith("01")) return "মোবাইল নম্বর 01 দিয়ে শুরু হতে হবে"
        return null
    }

    fun validateEmail(email: String): String? {
        val normalized = email.trim().lowercase()
        if (normalized.isEmpty()) return "জিমেইল অ্যাড্রেস লিখুন"
        if (!normalized.contains("@")) return "সম্পূর্ণ জিমেইল অ্যাড্রেস লিখুন (@ সহ)"
        if (!Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            return "বৈধ জিমেইল অ্যাড্রেস লিখুন (যেমন: name@gmail.com)"
        }
        if (!normalized.endsWith("@gmail.com")) {
            return "শুধুমাত্র @gmail.com অ্যাড্রেস গ্রহণযোগ্য"
        }
        val local = normalized.substringBefore("@")
        if (local.isBlank() || local.length < 2) {
            return "জিমেইল @ এর আগে কমপক্ষে ২ অক্ষর লিখুন"
        }
        return null
    }

    fun validateCredential(value: String, type: String): String? =
        if (type == "phone") validatePhone(value) else validateEmail(value)

    fun cleanOtp(otp: String): String =
        otp.filter { it.isDigit() }.take(6)
}
