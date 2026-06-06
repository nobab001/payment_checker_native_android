package online.paychek.app.ui.screen.auth.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.CompleteProfileRequest

class SignupViewModel : ViewModel() {

    private val apiService = RetrofitClient.authApiService

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    private var sessionToken: String = ""

    fun initData(contact: String, token: String) {
        sessionToken = token
        val isEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(contact).matches()
        _uiState.update {
            if (isEmail) {
                it.copy(email = contact, phone = "", isEmailPreFilled = true, isPhonePreFilled = false)
            } else {
                it.copy(phone = contact, email = "", isPhonePreFilled = true, isEmailPreFilled = false)
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null) }
    }

    fun onPinChanged(pin: String) {
        if (pin.length <= 6 && pin.all { it.isDigit() }) {
            _uiState.update { it.copy(pin = pin, errorMessage = null) }
        }
    }

    fun onConfirmPinChanged(confirmPin: String) {
        if (confirmPin.length <= 6 && confirmPin.all { it.isDigit() }) {
            _uiState.update { it.copy(confirmPin = confirmPin, errorMessage = null) }
        }
    }

    fun onPhoneChanged(phone: String) {
        if (!_uiState.value.isPhonePreFilled) {
            _uiState.update { it.copy(phone = phone, errorMessage = null) }
        }
    }

    fun onEmailChanged(email: String) {
        if (!_uiState.value.isEmailPreFilled) {
            _uiState.update { it.copy(email = email, errorMessage = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun submitSignup(onSuccess: () -> Unit) {
        val state = _uiState.value
        val name = state.name.trim()
        val pin = state.pin.trim()
        val confirmPin = state.confirmPin.trim()
        val phone = state.phone?.trim()
        val email = state.email?.trim()

        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "আপনার নাম লিখুন") }
            return
        }

        if (pin.length != 6) {
            _uiState.update { it.copy(errorMessage = "সিকিউরিটি পিন অবশ্যই ৬ ডিজিটের হতে হবে") }
            return
        }

        if (pin != confirmPin) {
            _uiState.update { it.copy(errorMessage = "উভয় পিন নম্বর মিলেনি, আবার চেক করুন") }
            return
        }

        if (phone.isNullOrEmpty() && email.isNullOrEmpty()) {
            _uiState.update { it.copy(errorMessage = "মোবাইল অথবা ইমেল যেকোনো একটি থাকতে হবে") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val request = CompleteProfileRequest(
                    name = name,
                    pin = pin,
                    phone = if (phone.isNullOrEmpty()) null else phone,
                    email = if (email.isNullOrEmpty()) null else email
                )

                // Auth Header value should format as "Bearer <token>"
                val authHeader = "Bearer $sessionToken"

                val response = apiService.completeProfile(authHeader, request)
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.update { it.copy(isLoading = false, signupSuccess = true) }
                    onSuccess()
                } else {
                    val errorMsg = response.message() ?: "প্রোফাইল সম্পূর্ণ করতে ব্যর্থ হয়েছে।"
                    _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "প্রোফাইল আপডেট করতে ব্যর্থ হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক ত্রুটি"}")
                }
            }
        }
    }
}

data class SignupUiState(
    val name: String = "",
    val pin: String = "",
    val confirmPin: String = "",
    val phone: String? = "",
    val email: String? = "",
    val isPhonePreFilled: Boolean = false,
    val isEmailPreFilled: Boolean = false,
    val isLoading: Boolean = false,
    val signupSuccess: Boolean = false,
    val errorMessage: String? = null
)
