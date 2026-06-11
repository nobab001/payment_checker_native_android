package online.paychek.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ১. ওটিপি পাঠানোর রিকোয়েস্ট (Send OTP for Linked Credential)
data class LinkCredentialOtpRequest(
    @SerializedName("value") val value: String, // নম্বর বা জিমেইল
    @SerializedName("type") val type: String    // "phone" বা "email"
)

// ২. ওটিপি ভেরিফাই ও ফাইনাল লিংক রিকোয়েস্ট (Verify & Link)
data class VerifyLinkCredentialRequest(
    @SerializedName("value") val value: String,
    @SerializedName("type") val type: String,
    @SerializedName("otp") val otp: String
)

// ৩. প্রোফাইলে লিংকড ক্রেডেনশিয়াল লিস্ট দেখানোর রেসপন্স
data class LinkedCredentialsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("primaryPhone") val primaryPhone: String? = null,
    @SerializedName("primaryEmail") val primaryEmail: String? = null,
    @SerializedName("phones") val phones: List<CredentialItem> = emptyList(),
    @SerializedName("emails") val emails: List<CredentialItem> = emptyList()
)

data class CredentialItem(
    @SerializedName("id") val id: Int,
    @SerializedName("value") val value: String,
    @SerializedName("verified_at") val verifiedAt: String?,
    @SerializedName("type") val type: String = ""
)
