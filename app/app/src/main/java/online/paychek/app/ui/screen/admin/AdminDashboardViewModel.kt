package online.paychek.app.ui.screen.admin

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

data class AdminUiState(
    val configs: Map<String, String> = emptyMap(),
    val smsTemplates: List<SmsTemplateDto> = emptyList(),
    val checkoutTemplates: List<CheckoutTemplateDto> = emptyList(),
    val emailAccounts: List<EmailAccountDto> = emptyList(),
    val smsSettings: List<SmsSettingsDto> = emptyList(),
    val users: List<AdminUserDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AdminDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val api = RetrofitClient.adminApiService
    private val prefs = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        loadAllData()
    }

    private fun getToken(): String {
        return prefs.getString(AppConfig.KEY_AUTH_TOKEN, "") ?: ""
    }

    fun loadAllData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val token = "Bearer ${getToken()}"
                
                // Fetch configs
                val configsRes = api.getConfigs(token)
                val configs = if (configsRes.isSuccessful) configsRes.body()?.configs ?: emptyMap() else emptyMap()

                // Fetch templates
                val templatesRes = api.getSmsTemplates(token)
                val templates = if (templatesRes.isSuccessful) templatesRes.body()?.templates ?: emptyList() else emptyList()

                // Fetch checkout templates
                val checkoutsRes = api.getCheckoutTemplates(token)
                val checkouts = if (checkoutsRes.isSuccessful) checkoutsRes.body()?.templates ?: emptyList() else emptyList()

                // Fetch email accounts
                val emailsRes = api.getEmailAccounts(token)
                val emails = if (emailsRes.isSuccessful) emailsRes.body()?.accounts ?: emptyList() else emptyList()

                // Fetch SMS settings
                val smsSettingsRes = api.getSmsSettings(token)
                val smsSettings = if (smsSettingsRes.isSuccessful) smsSettingsRes.body()?.settings ?: emptyList() else emptyList()

                // Fetch users
                val usersRes = api.getUsers(token)
                val users = if (usersRes.isSuccessful) usersRes.body()?.users ?: emptyList() else emptyList()

                _state.update {
                    it.copy(
                        configs = configs,
                        smsTemplates = templates,
                        checkoutTemplates = checkouts,
                        emailAccounts = emails,
                        smsSettings = smsSettings,
                        users = users,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "ডেটা লোড করতে ব্যর্থ হয়েছে: ${e.localizedMessage}") }
            }
        }
    }

    // Config Mutations
    fun updateConfig(key: String, value: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.updateConfig(token, UpdateConfigRequest(key, value))
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            configs = it.configs.toMutableMap().apply { put(key, value) },
                            successMessage = "কনফিগারেশন আপডেট সফল হয়েছে।"
                        )
                    }
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "আপডেট ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = "নেটওয়ার্ক এরর: ${e.localizedMessage}") }
            }
        }
    }

    // SMS Template Mutations
    fun saveSmsTemplate(template: SmsTemplateDto) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.saveSmsTemplate(token, template)
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "টেমপ্লেট সেভ সফল হয়েছে।") }
                    refreshTemplates()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "সেভ ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun deleteSmsTemplate(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.deleteSmsTemplate(token, id)
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "টেমপ্লেট মুছে ফেলা হয়েছে।") }
                    refreshTemplates()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "মুছে ফেলা ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    private suspend fun refreshTemplates() {
        try {
            val token = "Bearer ${getToken()}"
            val res = api.getSmsTemplates(token)
            if (res.isSuccessful) {
                _state.update { it.copy(smsTemplates = res.body()?.templates ?: emptyList(), isSaving = false) }
            } else {
                _state.update { it.copy(isSaving = false) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isSaving = false) }
        }
    }

    // Checkout Instructions Mutations
    fun saveCheckoutTemplate(template: CheckoutTemplateDto) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.saveCheckoutTemplate(token, template)
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "নির্দেশিকা সেভ সফল হয়েছে।") }
                    refreshCheckoutTemplates()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "সেভ ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    private suspend fun refreshCheckoutTemplates() {
        try {
            val token = "Bearer ${getToken()}"
            val res = api.getCheckoutTemplates(token)
            if (res.isSuccessful) {
                _state.update { it.copy(checkoutTemplates = res.body()?.templates ?: emptyList(), isSaving = false) }
            } else {
                _state.update { it.copy(isSaving = false) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isSaving = false) }
        }
    }

    // SMTP Account Mutations
    fun saveEmailAccount(account: EmailAccountDto) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.saveEmailAccount(token, account)
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "SMTP প্রোফাইল সেভ সফল হয়েছে।") }
                    refreshEmailAccounts()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "SMTP প্রোফাইল সেভ ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun deleteEmailAccount(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.deleteEmailAccount(token, id)
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "SMTP প্রোফাইল মুছে ফেলা হয়েছে।") }
                    refreshEmailAccounts()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "মুছে ফেলা ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    private suspend fun refreshEmailAccounts() {
        try {
            val token = "Bearer ${getToken()}"
            val res = api.getEmailAccounts(token)
            if (res.isSuccessful) {
                _state.update { it.copy(emailAccounts = res.body()?.accounts ?: emptyList(), isSaving = false) }
            } else {
                _state.update { it.copy(isSaving = false) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isSaving = false) }
        }
    }

    // SMS Settings Mutations
    fun saveSmsSettings(settings: SmsSettingsDto) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.saveSmsSettings(token, settings)
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "SMS গেটওয়ে সেভ সফল হয়েছে।") }
                    refreshSmsSettings()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "গেটওয়ে সেভ ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    private suspend fun refreshSmsSettings() {
        try {
            val token = "Bearer ${getToken()}"
            val res = api.getSmsSettings(token)
            if (res.isSuccessful) {
                _state.update { it.copy(smsSettings = res.body()?.settings ?: emptyList(), isSaving = false) }
            } else {
                _state.update { it.copy(isSaving = false) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isSaving = false) }
        }
    }

    // User Mutations (Block/Unblock)
    fun toggleUserBlock(userId: Int, blocked: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.toggleUserBlock(token, userId, BlockUserRequest(blocked))
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "ইউজার স্ট্যাটাস পরিবর্তিত হয়েছে।") }
                    refreshUsers()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "অপারেশন ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    // Device Trial Mutations
    fun updateDeviceTrial(deviceId: Int, trialExpiresAt: String?, isTrialLocked: Boolean, lockReason: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.updateDeviceTrial(
                    token,
                    deviceId,
                    UpdateDeviceTrialRequest(trialExpiresAt, isTrialLocked, lockReason)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "ডিভাইস প্যারামিটার আপডেট সফল হয়েছে।") }
                    refreshUsers()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "ডিভাইস আপডেট ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    private suspend fun refreshUsers() {
        try {
            val token = "Bearer ${getToken()}"
            val res = api.getUsers(token)
            if (res.isSuccessful) {
                _state.update { it.copy(users = res.body()?.users ?: emptyList(), isSaving = false) }
            } else {
                _state.update { it.copy(isSaving = false) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
