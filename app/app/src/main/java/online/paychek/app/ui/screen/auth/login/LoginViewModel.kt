package online.paychek.app.ui.screen.auth.login

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.*
import online.paychek.app.utils.DeviceIdHelper

class LoginViewModel : ViewModel() {

    private val apiService = RetrofitClient.authApiService

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun onContactChanged(contact: String) {
        _uiState.update { it.copy(contact = contact, errorMessage = null) }
    }

    fun onOtpChanged(otp: String) {
        if (otp.length <= 6) {
            _uiState.update { it.copy(otpCode = otp, errorMessage = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun checkContactAndRequestOtp(context: Context) {
        val contact = _uiState.value.contact.trim()
        if (contact.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "মোবাইল নম্বর অথবা ইমেইল লিখুন") }
            return
        }

        val isEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(contact).matches()
        val isPhone = contact.length >= 11 && contact.all { it.isDigit() }

        if (!isEmail && !isPhone) {
            _uiState.update { it.copy(errorMessage = "সঠিক ১১-ডিজিটের মোবাইল নম্বর অথবা ইমেইল দিন") }
            return
        }

        val deviceId = DeviceIdHelper.getHashedAndroidId(context)
        val fingerprint = DeviceIdHelper.getHashedFingerprint()

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                // 1. Check contact existence
                val checkResponse = apiService.checkContact(CheckContactRequest(contact))
                if (checkResponse.isSuccessful && checkResponse.body() != null) {
                    val exists = checkResponse.body()!!.exists
                    _uiState.update { it.copy(isNewUser = !exists) }

                    if (!exists) {
                        // User does not exist (New User / Signup flow)
                        // 2. Perform Device Trial Check to prevent trial abuse
                        val trialResponse = apiService.checkDeviceTrial(
                            CheckDeviceTrialRequest(deviceId, fingerprint)
                        )

                        if (trialResponse.isSuccessful && trialResponse.body() != null) {
                            val trialData = trialResponse.body()!!
                            if (!trialData.trialAllowed || trialData.isLocked) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        errorMessage = "এই ডিভাইসে ইতিমধ্যে ট্রায়াল ব্যবহার করা হয়েছে! নতুন অ্যাকাউন্ট খোলা সম্ভব নয়।",
                                        isTrialBlocked = true
                                    )
                                }
                                return@launch
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "ডিভাইস ট্রায়াল যাচাই করতে ব্যর্থ হয়েছে। আবার চেষ্টা করুন।"
                                )
                            }
                            return@launch
                        }

                        // Send Signup OTP
                        sendOtpApi(contact, deviceId, isNew = true)
                    } else {
                        // User exists (Login flow)
                        sendOtpApi(contact, deviceId, isNew = false)
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "সার্ভার রেসপন্স করছে না। অনুগ্রহ করে পরে চেষ্টা করুন।")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "নেটওয়ার্ক ত্রুটি: ${e.localizedMessage ?: "সংযোগ ব্যর্থ"}")
                }
            }
        }
    }

    private suspend fun sendOtpApi(contact: String, deviceId: String, isNew: Boolean) {
        val request = SendOtpRequest(contact, deviceId)
        val response = if (isNew) {
            apiService.sendOtpNew(request)
        } else {
            apiService.sendOtp(request)
        }

        if (response.isSuccessful && response.body()?.success == true) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isOtpSent = true,
                    otpCode = "",
                    timerSeconds = 60
                )
            }
            startTimer()
        } else {
            val errorMsg = response.body()?.message ?: "ওটিপি পাঠাতে ব্যর্থ হয়েছে।"
            _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
        }
    }

    fun verifyOtp(context: Context, onOtpVerified: (VerifyOtpResponse) -> Unit) {
        val contact = _uiState.value.contact.trim()
        val code = _uiState.value.otpCode.trim()

        if (code.length != 6) {
            _uiState.update { it.copy(errorMessage = "৬-ডিজিটের ওটিপি কোড লিখুন") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        val deviceId = DeviceIdHelper.getHashedAndroidId(context)
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVersion = Build.VERSION.RELEASE
        val fingerprint = DeviceIdHelper.getHashedFingerprint()

        viewModelScope.launch {
            try {
                val request = VerifyOtpRequest(
                    contact = contact,
                    code = code,
                    deviceId = deviceId,
                    deviceModel = deviceModel,
                    androidVersion = androidVersion,
                    fingerprint = fingerprint
                )

                val response = apiService.verifyOtp(request)
                if (response.isSuccessful && response.body() != null) {
                    val verifyResponse = response.body()!!
                    _uiState.update { it.copy(isLoading = false) }
                    onOtpVerified(verifyResponse)
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "ওটিপি কোডটি সঠিক নয়। অনুগ্রহ করে আবার চেক করুন।")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "ওটিপি যাচাই ব্যর্থ হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক এরর"}")
                }
            }
        }
    }

    fun resendOtp(context: Context) {
        if (_uiState.value.timerSeconds > 0) return
        checkContactAndRequestOtp(context)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.timerSeconds > 0) {
                delay(1000L)
                _uiState.update { it.copy(timerSeconds = it.timerSeconds - 1) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

data class LoginUiState(
    val contact: String = "",
    val isOtpSent: Boolean = false,
    val otpCode: String = "",
    val timerSeconds: Int = 0,
    val isLoading: Boolean = false,
    val isNewUser: Boolean = false,
    val isTrialBlocked: Boolean = false,
    val errorMessage: String? = null,
    val isMaintenanceMode: Boolean = false // Admin panel check placeholder
)
