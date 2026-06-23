package online.paychek.app.ui.screen.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import online.paychek.app.data.remote.dto.CredentialItem
import online.paychek.app.data.remote.dto.LinkCredentialOtpRequest
import online.paychek.app.data.remote.dto.VerifyLinkCredentialRequest
import online.paychek.app.data.repository.CredentialRepository

class CredentialViewModel(private val repository: CredentialRepository) : ViewModel() {

    // UI কন্ট্রোল স্টেটস
    var linkedPhones by mutableStateOf<List<CredentialItem>>(emptyList())
    var linkedEmails by mutableStateOf<List<CredentialItem>>(emptyList())
    var primaryPhone by mutableStateOf<String?>(null)
    var primaryEmail by mutableStateOf<String?>(null)
    var isOtpSentForLinking by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    companion object {
        const val MAX_PER_TYPE = 5
    }

    init {
        loadCredentials()
    }

    fun loadCredentials() {
        viewModelScope.launch {
            repository.getLinkedCredentials()
                .onSuccess { response ->
                    linkedPhones = response.phones
                    linkedEmails = response.emails
                    primaryPhone = response.primaryPhone
                    primaryEmail = response.primaryEmail
                }
                .onFailure { error ->
                    errorMessage = error.message
                }
        }
    }

    // ওটিপি পাঠানোর আগে ফ্রন্টএন্ড গেট চেক
    fun sendOtpForNewCredential(value: String, type: String) {
        val currentCount = if (type == "phone") linkedPhones.size else linkedEmails.size
        
        if (currentCount >= MAX_PER_TYPE) {
            errorMessage = "দুঃখিত ভাই! সর্বোচ্চ ৫টি ${if (type == "phone") "মোবাইল নম্বর" else "জিমেইল"} লিঙ্ক করা সম্ভব।"
            return
        }
        
        viewModelScope.launch {
            val response = repository.sendLinkOtp(LinkCredentialOtpRequest(value, type))
            if (response.isSuccessful) {
                isOtpSentForLinking = true
                errorMessage = null
            } else {
                val errBody = response.errorBody()?.string() ?: ""
                errorMessage = parseErrorMessage(errBody, "ওটিপি পাঠানো সম্ভব হয়নি")
            }
        }
    }

    // ওটিপি ভেরিফাই ও ফাইনাল লিংক রিকোয়েস্ট
    fun verifyAndLinkCredential(value: String, type: String, otp: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val response = repository.verifyLinkCredential(VerifyLinkCredentialRequest(value, type, otp))
            if (response.isSuccessful) {
                isOtpSentForLinking = false
                errorMessage = null
                loadCredentials()
                onSuccess()
            } else {
                val errBody = response.errorBody()?.string() ?: ""
                errorMessage = parseErrorMessage(errBody, "ওটিপি ভেরিফিকেশন ব্যর্থ হয়েছে")
            }
        }
    }

    // ক্রেডেনশিয়াল রিমুভ রিকোয়েস্ট
    fun removeCredential(id: Int, pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val response = repository.removeCredential(id, pin)
            if (response.isSuccessful) {
                errorMessage = null
                loadCredentials()
                onSuccess()
            } else {
                val errBody = response.errorBody()?.string() ?: ""
                errorMessage = parseErrorMessage(errBody, "ক্রেডেনশিয়াল রিমুভ করা সম্ভব হয়নি")
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    private fun parseErrorMessage(body: String, fallback: String): String {
        return try {
            val map = online.paychek.app.utils.GsonUtils.gson.fromJson(body, Map::class.java)
            (map["message"] ?: map["error"])?.toString() ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    class Factory(private val repository: CredentialRepository) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CredentialViewModel(repository) as T
        }
    }
}
