package online.paychek.app.ui.screen.auth.pin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.VerifyPinRequest
import online.paychek.app.utils.SecurePreferences

class SecurityGateViewModel : ViewModel() {

    data class SecurityGateState(
        val pin: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isUnlocked: Boolean = false
    )

    private val _uiState = MutableStateFlow(SecurityGateState())
    val uiState: StateFlow<SecurityGateState> = _uiState.asStateFlow()

    fun appendDigit(digit: String, context: Context, onUnlockSuccess: () -> Unit) {
        if (_uiState.value.pin.length < 6 && !_uiState.value.isLoading) {
            _uiState.update { it.copy(pin = it.pin + digit, errorMessage = null) }
            if (_uiState.value.pin.length == 6) {
                verifyPinOnBackend(context, onUnlockSuccess)
            }
        }
    }

    fun deleteDigit() {
        if (_uiState.value.pin.isNotEmpty() && !_uiState.value.isLoading) {
            _uiState.update { it.copy(pin = it.pin.dropLast(1), errorMessage = null) }
        }
    }

    fun clearPin() {
        _uiState.update { it.copy(pin = "", errorMessage = null) }
    }

    fun verifyPin(context: Context, onUnlockSuccess: () -> Unit) {
        verifyPinOnBackend(context, onUnlockSuccess)
    }

    private fun verifyPinOnBackend(context: Context, onUnlockSuccess: () -> Unit) {
        val pinCode = _uiState.value.pin
        if (pinCode.length < 4 || pinCode.length > 6) return

        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
        if (token.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "সেশন অবৈধ। অনুগ্রহ করে আবার লগইন করুন।") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.authApiService.verifyPin(
                    token = "Bearer $token",
                    request = VerifyPinRequest(pin = pinCode)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.update { it.copy(isLoading = false, isUnlocked = true) }
                    onUnlockSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    val rawMsg = try {
                        val gson = com.google.gson.Gson()
                        val map = gson.fromJson(errorBody, Map::class.java)
                        map["message"] as? String ?: map["error"] as? String ?: "পিনটি সঠিক নয়।"
                    } catch (e: Exception) {
                        "পিনটি সঠিক নয়।"
                    }
                    _uiState.update { it.copy(isLoading = false, pin = "", errorMessage = rawMsg) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pin = "",
                        errorMessage = "ভেরিফিকেশন ব্যর্থ: ${e.localizedMessage ?: "নেটওয়ার্ক এরর"}"
                    )
                }
            }
        }
    }
}
