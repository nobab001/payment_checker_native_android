package online.paychek.app.ui.screen.profile

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.*
import online.paychek.app.utils.SecurePreferences

// =============================================================================
// UI State
// =============================================================================

data class ProfileSettingsState(
    // ── Profile Header info ──────────────────────────────────────
    val userName: String           = "",
    val primaryPhone: String?      = null,
    val primaryEmail: String?      = null,
    val userRole: String           = "",
    val subscriptionType: String   = "",   // "trial" / "premium" / "merchant_active"

    // ── Credentials List ─────────────────────────────────────────
    val credentials: List<CredentialItem> = emptyList(),
    val isLoadingCredentials: Boolean     = false,

    // ── Add Credential OTP Flow ──────────────────────────────────
    val showAddCredentialDialog: Boolean  = false,
    val addCredentialType: String         = "phone",  // "phone" or "email"
    val addCredentialContact: String      = "",
    val addCredentialOtpSent: Boolean     = false,
    val addCredentialOtpCode: String      = "",
    val addCredentialTimer: Int           = 0,

    // ── PIN Section ───────────────────────────────────────────────
    val showChangePinDialog: Boolean      = false,
    val showResetPinDialog: Boolean       = false,
    val resetPinContact: String           = "",
    val resetPinOtpSent: Boolean          = false,
    val resetPinOtpCode: String           = "",
    val resetPinNewPin: String            = "",
    val changePinOld: String              = "",
    val changePinNew: String              = "",
    val changePinConfirm: String          = "",

    // ── Global feedback ───────────────────────────────────────────
    val isLoading: Boolean                = false,
    val successMessage: String?           = null,
    val errorMessage: String?             = null
)

// =============================================================================
// ViewModel
// =============================================================================

class ProfileSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs  = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
    private val api    = RetrofitClient.profileApiService
    private val _state = MutableStateFlow(ProfileSettingsState())
    val state: StateFlow<ProfileSettingsState> = _state.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null

    init {
        // Load stored user info
        val name = prefs.getString("pcu_user_name", "") ?: ""
        val role = prefs.getString("pcu_user_role", "merchant") ?: "merchant"
        val sub  = prefs.getString("pcu_subscription_type", "trial") ?: "trial"
        _state.update { it.copy(userName = name, userRole = role, subscriptionType = sub) }
        loadCredentials()
    }

    private fun bearerToken(): String {
        val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    // ─────────────────────────────────────────────────────────────
    // Load Credentials
    // ─────────────────────────────────────────────────────────────
    fun loadCredentials() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingCredentials = true) }
            try {
                val response = api.listCredentials(bearerToken())
                if (response.isSuccessful) {
                    val body = response.body()!!
                    _state.update {
                        it.copy(
                            isLoadingCredentials = false,
                            primaryPhone  = body.primaryPhone,
                            primaryEmail  = body.primaryEmail,
                            credentials   = body.credentials
                        )
                    }
                } else {
                    _state.update { it.copy(isLoadingCredentials = false, errorMessage = "Credentials লোড ব্যর্থ।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingCredentials = false, errorMessage = "নেটওয়ার্ক সমস্যা।") }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Add Credential — Dialog open/close
    // ─────────────────────────────────────────────────────────────
    fun openAddCredentialDialog(type: String) {
        _state.update {
            it.copy(
                showAddCredentialDialog = true,
                addCredentialType       = type,
                addCredentialContact    = "",
                addCredentialOtpSent    = false,
                addCredentialOtpCode    = "",
                addCredentialTimer      = 0,
                errorMessage            = null
            )
        }
    }

    fun dismissAddCredentialDialog() {
        timerJob?.cancel()
        _state.update { it.copy(showAddCredentialDialog = false) }
    }

    fun onAddCredentialContactChange(value: String) {
        _state.update { it.copy(addCredentialContact = value) }
    }

    fun onAddCredentialOtpChange(value: String) {
        _state.update { it.copy(addCredentialOtpCode = value) }
    }

    // ─────────────────────────────────────────────────────────────
    // Add Credential — Send OTP
    // ─────────────────────────────────────────────────────────────
    fun sendCredentialOtp() {
        val contact = _state.value.addCredentialContact.trim()
        if (contact.isEmpty()) {
            _state.update { it.copy(errorMessage = "নম্বর বা ইমেইল প্রবেশ করুন।") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = api.sendCredentialOtp(bearerToken(), CredentialOtpRequest(contact))
                if (response.isSuccessful) {
                    _state.update {
                        it.copy(isLoading = false, addCredentialOtpSent = true, addCredentialTimer = 60)
                    }
                    startAddCredentialTimer()
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    _state.update {
                        it.copy(isLoading = false, errorMessage = parseErrorMessage(errBody, "OTP পাঠাতে ব্যর্থ।"))
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "নেটওয়ার্ক সমস্যা।") }
            }
        }
    }

    private fun startAddCredentialTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.addCredentialTimer > 0) {
                kotlinx.coroutines.delay(1000L)
                _state.update { it.copy(addCredentialTimer = it.addCredentialTimer - 1) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Add Credential — Verify OTP
    // ─────────────────────────────────────────────────────────────
    fun verifyCredential() {
        val contact = _state.value.addCredentialContact.trim()
        val code    = _state.value.addCredentialOtpCode.trim()
        if (code.length != 6) {
            _state.update { it.copy(errorMessage = "৬-সংখ্যার OTP দিন।") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = api.verifyCredential(bearerToken(), CredentialVerifyRequest(contact, code))
                if (response.isSuccessful) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            showAddCredentialDialog = false,
                            successMessage = "নতুন credential সফলভাবে যোগ করা হয়েছে।"
                        )
                    }
                    loadCredentials()
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    _state.update {
                        it.copy(isLoading = false, errorMessage = parseErrorMessage(errBody, "OTP যাচাই ব্যর্থ।"))
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "নেটওয়ার্ক সমস্যা।") }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Remove Credential
    // ─────────────────────────────────────────────────────────────
    fun removeCredential(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = api.removeCredential(bearerToken(), id)
                if (response.isSuccessful) {
                    _state.update { it.copy(isLoading = false, successMessage = "Credential মুছে ফেলা হয়েছে।") }
                    loadCredentials()
                } else {
                    _state.update { it.copy(isLoading = false, errorMessage = "মুছে ফেলা ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "নেটওয়ার্ক সমস্যা।") }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Change PIN
    // ─────────────────────────────────────────────────────────────
    fun openChangePinDialog() {
        _state.update { it.copy(showChangePinDialog = true, changePinOld = "", changePinNew = "", changePinConfirm = "", errorMessage = null) }
    }

    fun dismissChangePinDialog() {
        _state.update { it.copy(showChangePinDialog = false) }
    }

    fun onChangePinOldChange(v: String)     { _state.update { it.copy(changePinOld     = v) } }
    fun onChangePinNewChange(v: String)     { _state.update { it.copy(changePinNew     = v) } }
    fun onChangePinConfirmChange(v: String) { _state.update { it.copy(changePinConfirm = v) } }

    fun submitChangePin() {
        val s = _state.value
        if (s.changePinNew.length != 6) {
            _state.update { it.copy(errorMessage = "নতুন PIN ৬ সংখ্যার হতে হবে।") }; return
        }
        if (s.changePinNew != s.changePinConfirm) {
            _state.update { it.copy(errorMessage = "নতুন PIN দুটি মিলছে না।") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = RetrofitClient.profileApiService.changePin(
                    bearerToken(), ChangePinRequest(s.changePinOld, s.changePinNew)
                )
                if (response.isSuccessful) {
                    _state.update { it.copy(isLoading = false, showChangePinDialog = false, successMessage = "PIN সফলভাবে পরিবর্তন হয়েছে।") }
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    _state.update { it.copy(isLoading = false, errorMessage = parseErrorMessage(errBody, "PIN পরিবর্তন ব্যর্থ।")) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "নেটওয়ার্ক সমস্যা।") }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Reset PIN (Forgot PIN flow)
    // ─────────────────────────────────────────────────────────────
    fun openResetPinDialog() {
        val contact = _state.value.primaryPhone ?: _state.value.primaryEmail ?: ""
        _state.update {
            it.copy(
                showResetPinDialog = true,
                resetPinContact    = contact,
                resetPinOtpSent    = false,
                resetPinOtpCode    = "",
                resetPinNewPin     = "",
                errorMessage       = null
            )
        }
    }

    fun dismissResetPinDialog() {
        _state.update { it.copy(showResetPinDialog = false) }
    }

    fun onResetPinContactChange(v: String)  { _state.update { it.copy(resetPinContact = v) } }
    fun onResetPinOtpChange(v: String)      { _state.update { it.copy(resetPinOtpCode = v) } }
    fun onResetPinNewPinChange(v: String)   { _state.update { it.copy(resetPinNewPin  = v) } }

    fun sendResetPinOtp() {
        val contact = _state.value.resetPinContact.trim()
        if (contact.isEmpty()) { _state.update { it.copy(errorMessage = "নম্বর বা ইমেইল দিন।") }; return }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = RetrofitClient.profileApiService.resetPinSendOtp(ResetPinSendOtpRequest(contact))
                if (response.isSuccessful) {
                    _state.update { it.copy(isLoading = false, resetPinOtpSent = true) }
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    _state.update { it.copy(isLoading = false, errorMessage = parseErrorMessage(errBody, "OTP পাঠানো ব্যর্থ।")) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "নেটওয়ার্ক সমস্যা।") }
            }
        }
    }

    fun submitResetPin() {
        val s = _state.value
        if (s.resetPinOtpCode.length != 6) { _state.update { it.copy(errorMessage = "৬ সংখ্যার OTP দিন।") }; return }
        if (s.resetPinNewPin.length != 6)  { _state.update { it.copy(errorMessage = "নতুন PIN ৬ সংখ্যার হতে হবে।") }; return }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = RetrofitClient.profileApiService.resetPinVerify(
                    ResetPinVerifyRequest(s.resetPinContact, s.resetPinOtpCode, s.resetPinNewPin)
                )
                if (response.isSuccessful) {
                    _state.update { it.copy(isLoading = false, showResetPinDialog = false, successMessage = "PIN সফলভাবে রিসেট হয়েছে।") }
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    _state.update { it.copy(isLoading = false, errorMessage = parseErrorMessage(errBody, "PIN রিসেট ব্যর্থ।")) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "নেটওয়ার্ক সমস্যা।") }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Global dismissal
    // ─────────────────────────────────────────────────────────────
    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────
    private fun parseErrorMessage(body: String, fallback: String): String {
        return try {
            val map = com.google.gson.Gson().fromJson(body, Map::class.java)
            (map["message"] ?: map["error"])?.toString() ?: fallback
        } catch (e: Exception) { fallback }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
