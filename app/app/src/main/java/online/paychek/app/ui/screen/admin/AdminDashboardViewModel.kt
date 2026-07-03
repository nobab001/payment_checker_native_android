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
import online.paychek.app.utils.SecurePreferences

data class AdminUiState(
    val configs: Map<String, String> = emptyMap(),
    val smsTemplates: List<SmsTemplateDto> = emptyList(),
    val checkoutTemplates: List<CheckoutTemplateDto> = emptyList(),
    val emailAccounts: List<EmailAccountDto> = emptyList(),
    val smsSettings: List<SmsSettingsDto> = emptyList(),
    val users: List<AdminUserDto> = emptyList(),
    val otpFormatTemplate: String = "",
    val plans: List<SubscriptionPlanDto> = emptyList(),
    val addonPlans: List<AddonPlanDto> = emptyList(),
    val checkoutDesignTabs: Map<String, CheckoutTabDto> = emptyMap(),
    val providerBranding: Map<String, ProviderBrandingDto> = emptyMap(),
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
        return SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
    }

    fun loadAllData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val token = "Bearer ${getToken()}"

            val jobConfigs = launch {
                try {
                    val res = api.getConfigs(token)
                    if (res.isSuccessful) {
                        val configs = res.body()?.configs ?: emptyMap()
                        _state.update { it.copy(configs = configs) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val jobTemplates = launch {
                try {
                    val res = api.getSmsTemplates(token)
                    if (res.isSuccessful) {
                        val templates = res.body()?.templates ?: emptyList()
                        _state.update { it.copy(smsTemplates = templates) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val jobCheckouts = launch {
                try {
                    val res = api.getCheckoutTemplates(token)
                    if (res.isSuccessful) {
                        val checkouts = res.body()?.templates ?: emptyList()
                        _state.update { it.copy(checkoutTemplates = checkouts) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val jobEmails = launch {
                try {
                    val res = api.getEmailAccounts(token)
                    if (res.isSuccessful) {
                        val emails = res.body()?.accounts ?: emptyList()
                        _state.update { it.copy(emailAccounts = emails) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val jobSmsSettings = launch {
                try {
                    val res = api.getSmsSettings(token)
                    if (res.isSuccessful) {
                        val smsSettings = res.body()?.settings ?: emptyList()
                        _state.update { it.copy(smsSettings = smsSettings) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val jobUsers = launch {
                try {
                    val res = api.getUsers(token)
                    if (res.isSuccessful) {
                        val users = res.body()?.users ?: emptyList()
                        _state.update { it.copy(users = users) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val jobOtpFormat = launch {
                try {
                    val res = api.getOtpFormat(token)
                    if (res.isSuccessful) {
                        val otpFormat = res.body()?.template ?: ""
                        _state.update { it.copy(otpFormatTemplate = otpFormat) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val jobPlans = launch {
                try {
                    val res = api.getPlans(token)
                    if (res.isSuccessful) {
                        val plans = res.body()?.plans ?: emptyList()
                        _state.update { it.copy(plans = plans) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val jobAddonPlans = launch {
                try {
                    val res = api.getAddonPlans(token)
                    if (res.isSuccessful) {
                        val addonPlans = res.body()?.plans ?: emptyList()
                        _state.update { it.copy(addonPlans = addonPlans) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val jobCheckoutDesign = launch {
                try {
                    val res = api.getCheckoutDesignConfig(token)
                    if (res.isSuccessful) {
                        val body = res.body()
                        _state.update {
                            it.copy(
                                checkoutDesignTabs = body?.tabs ?: emptyMap(),
                                providerBranding = body?.providerBranding ?: emptyMap()
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            kotlinx.coroutines.joinAll(
                jobConfigs, jobTemplates, jobCheckouts, jobEmails, jobSmsSettings,
                jobUsers, jobOtpFormat, jobPlans, jobAddonPlans, jobCheckoutDesign
            )
            _state.update { it.copy(isLoading = false) }
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

    fun updateConfigs(configs: Map<String, String>) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.updateConfigs(token, mapOf("configs" to configs))
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            configs = it.configs.toMutableMap().apply { putAll(configs) },
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

    fun updateOtpFormat(template: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.updateOtpFormat(token, UpdateOtpFormatRequest(template))
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            otpFormatTemplate = template,
                            successMessage = "OTP মেসেজ ফরম্যাট আপডেট সফল হয়েছে।"
                        )
                    }
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "OTP ফরম্যাট আপডেট ব্যর্থ হয়েছে।") }
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
                    val msg = parseApiErrorMessage(response.errorBody()?.string())
                        ?: response.body()?.message
                        ?: "সেভ ব্যর্থ হয়েছে। ব্যাকএন্ড সার্ভার রিস্টার্ট করে আবার চেষ্টা করুন।"
                    _state.update { it.copy(isSaving = false, errorMessage = msg) }
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

    fun giveManualGrace(userId: Int, credits: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.updateUserManualGrace(token, userId, ManualGraceRequest(credits))
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "ব্যবহারকারীকে সফলভাবে ${credits} দিনের ট্রায়াল প্রদান করা হয়েছে।") }
                    refreshUsers()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "ম্যানুয়াল ট্রায়াল প্রদান ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    private fun parseApiErrorMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val map = online.paychek.app.utils.GsonUtils.gson.fromJson(errorBody, Map::class.java)
            (map["message"] as? String)?.takeIf { it.isNotBlank() }
                ?: (map["error"] as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun savePlan(plan: SubscriptionPlanDto, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.savePlan(token, plan)
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "প্ল্যান সফলভাবে সংরক্ষিত হয়েছে।") }
                    refreshPlans()
                    onComplete?.invoke(true)
                } else {
                    val msg = parseApiErrorMessage(response.errorBody()?.string()) ?: "প্ল্যান সেভ ব্যর্থ হয়েছে।"
                    _state.update { it.copy(isSaving = false, errorMessage = msg) }
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
                onComplete?.invoke(false)
            }
        }
    }

    private suspend fun refreshPlans() {
        try {
            val token = "Bearer ${getToken()}"
            val res = api.getPlans(token)
            if (res.isSuccessful) {
                _state.update { it.copy(plans = res.body()?.plans ?: emptyList(), isSaving = false) }
            } else {
                _state.update { it.copy(isSaving = false) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun deletePlan(planId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.deletePlan(token, planId)
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "প্ল্যান সফলভাবে মুছে ফেলা হয়েছে।") }
                    refreshPlans()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "মুছে ফেলা ব্যর্থ হয়েছে।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun verifyAdminPinAndDeletePlan(pin: String, planId: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val token = "Bearer ${getToken()}"
                val response = RetrofitClient.authApiService.verifyPin(token, VerifyPinRequest(pin))
                if (response.isSuccessful && response.body()?.success == true) {
                    val deleteRes = api.deletePlan(token, planId)
                    if (deleteRes.isSuccessful && deleteRes.body()?.success == true) {
                        _state.update { it.copy(successMessage = "প্ল্যান সফলভাবে মুছে ফেলা হয়েছে।") }
                        refreshPlans()
                        onSuccess()
                    } else {
                        val errMsg = deleteRes.body()?.message ?: "মুছে ফেলা ব্যর্থ হয়েছে।"
                        onError(errMsg)
                    }
                } else {
                    onError("ভুল পিন নম্বর। অনুগ্রহ করে সঠিক পিন দিন।")
                }
            } catch (e: Exception) {
                onError("নেটওয়ার্ক ত্রুটি: ${e.localizedMessage}")
            }
        }
    }

    fun saveAddonPlan(plan: AddonPlanDto, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.saveAddonPlan(token, plan)
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "অ্যাড-অন প্যাকেজ সংরক্ষিত হয়েছে।") }
                    refreshAddonPlans()
                    onComplete?.invoke(true)
                } else {
                    val msg = parseApiErrorMessage(response.errorBody()?.string()) ?: "অ্যাড-অন সেভ ব্যর্থ হয়েছে।"
                    _state.update { it.copy(isSaving = false, errorMessage = msg) }
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
                onComplete?.invoke(false)
            }
        }
    }

    private suspend fun refreshAddonPlans() {
        try {
            val token = "Bearer ${getToken()}"
            val res = api.getAddonPlans(token)
            if (res.isSuccessful) {
                _state.update { it.copy(addonPlans = res.body()?.plans ?: emptyList(), isSaving = false) }
            } else {
                _state.update { it.copy(isSaving = false) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun verifyAdminPinAndDeleteAddonPlan(pin: String, planId: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val token = "Bearer ${getToken()}"
                val response = RetrofitClient.authApiService.verifyPin(token, VerifyPinRequest(pin))
                if (response.isSuccessful && response.body()?.success == true) {
                    val deleteRes = api.deleteAddonPlan(token, planId)
                    if (deleteRes.isSuccessful && deleteRes.body()?.success == true) {
                        _state.update { it.copy(successMessage = "অ্যাড-অন প্যাকেজ মুছে ফেলা হয়েছে।") }
                        refreshAddonPlans()
                        onSuccess()
                    } else {
                        onError(deleteRes.body()?.message ?: "মুছে ফেলা ব্যর্থ হয়েছে।")
                    }
                } else {
                    onError("ভুল পিন নম্বর। অনুগ্রহ করে সঠিক পিন দিন।")
                }
            } catch (e: Exception) {
                onError("নেটওয়ার্ক ত্রুটি: ${e.localizedMessage}")
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }

    fun saveCheckoutDesign(
        tabs: Map<String, CheckoutDesignTabInput>,
        providerBranding: Map<String, ProviderBrandingDto>
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val token = "Bearer ${getToken()}"
                val res = api.saveCheckoutDesignConfig(
                    token,
                    SaveCheckoutDesignRequest(tabs = tabs, providerBranding = providerBranding)
                )
                if (res.isSuccessful && res.body()?.success == true) {
                    val body = res.body()
                    _state.update {
                        it.copy(
                            isSaving = false,
                            checkoutDesignTabs = body?.tabs ?: tabs.mapValues { (_, v) ->
                                CheckoutTabDto(
                                    id = "",
                                    label = v.label,
                                    enabled = v.enabled,
                                    icon = v.icon,
                                    iconUrl = v.iconUrl,
                                    category = v.category
                                )
                            },
                            providerBranding = body?.providerBranding ?: providerBranding,
                            successMessage = body?.message ?: "চেকআউট ডিজাইন সংরক্ষণ হয়েছে।"
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = res.body()?.error ?: "সেভ ব্যর্থ হয়েছে।"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, errorMessage = "নেটওয়ার্ক এরর: ${e.localizedMessage}")
                }
            }
        }
    }
}
