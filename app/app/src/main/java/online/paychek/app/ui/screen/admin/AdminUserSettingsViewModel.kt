package online.paychek.app.ui.screen.admin

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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

data class AdminUserSettingsState(
    val user: AdminUserDto? = null,
    val websites: List<AdminWebsiteDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AdminUserSettingsViewModel(
    application: Application,
    private val userId: Int
) : AndroidViewModel(application) {

    private val api = RetrofitClient.adminApiService
    private val prefs = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(AdminUserSettingsState())
    val state: StateFlow<AdminUserSettingsState> = _state.asStateFlow()

    init {
        load()
    }

    private fun getToken(): String {
        return SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val token = "Bearer ${getToken()}"
            try {
                val usersRes = api.getUsers(token)
                val websitesRes = api.getWebsites(token)
                val user = usersRes.body()?.users?.find { it.id == userId }
                val websites = websitesRes.body()?.websites
                    ?.filter { it.userId == userId }
                    ?: emptyList()
                if (user == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "ইউজার খুঁজে পাওয়া যায়নি।"
                        )
                    }
                } else {
                    _state.update {
                        it.copy(user = user, websites = websites, isLoading = false, isSaving = false)
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "লোড ব্যর্থ।")
                }
            }
        }
    }

    fun toggleUserBlock(blocked: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.toggleUserBlock(token, userId, BlockUserRequest(blocked))
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update { it.copy(successMessage = "স্ট্যাটাস আপডেট হয়েছে।") }
                    load()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "অপারেশন ব্যর্থ।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun giveManualGrace(credits: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.updateUserManualGrace(token, userId, ManualGraceRequest(credits))
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.update {
                        it.copy(successMessage = "$credits দিনের ট্রায়াল প্রদান করা হয়েছে।")
                    }
                    load()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "ট্রায়াল প্রদান ব্যর্থ।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

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
                    _state.update { it.copy(successMessage = "ডিভাইস আপডেট সফল।") }
                    load()
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "ডিভাইস আপডেট ব্যর্থ।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun setWebsitePermission(
        websiteId: Int,
        allowPaymentType: Boolean? = null,
        allowCommission: Boolean? = null,
        commissionEnabled: Boolean? = null
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val token = "Bearer ${getToken()}"
                val response = api.setWebsitePermissions(
                    token,
                    websiteId,
                    WebsitePermissionsRequest(
                        allowPaymentTypeCallback = allowPaymentType,
                        allowCommissionCallback = allowCommission,
                        commissionEnabled = commissionEnabled
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val updated = response.body()?.website
                    if (updated != null) {
                        _state.update { state ->
                            state.copy(
                                websites = state.websites.map { w ->
                                    if (w.id == websiteId) updated else w
                                },
                                isSaving = false,
                                successMessage = "API পারমিশন আপডেট হয়েছে।"
                            )
                        }
                    } else {
                        load()
                    }
                } else {
                    _state.update { it.copy(isSaving = false, errorMessage = "পারমিশন আপডেট ব্যর্থ।") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }

    companion object {
        fun provideFactory(userId: Int, application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdminUserSettingsViewModel(application, userId) as T
                }
            }
    }
}
