package online.paychek.app.ui.screen.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.*
import online.paychek.app.data.repository.PaymentRepository
import online.paychek.app.services.foreground.SmsMonitorService
import online.paychek.app.domain.usecase.sync.FlushOfflineQueueUseCase
import online.paychek.app.utils.SecurePreferences
import online.paychek.app.utils.NetworkConnectivityObserver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

// =============================================================================
// UI State — Dashboard স্ক্রিনের সম্পূর্ণ অবস্থা
// =============================================================================

/**
 * Dashboard-এর তিনটি স্টেট:
 *  Loading → API call চলছে, Spinner দেখাবে
 *  Success → Stats ডেটা লোড হয়েছে, UI পূর্ণ দেখাবে
 *  Error   → API fail, "পুনরায় চেষ্টা" বাটন দেখাবে
 */
sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val stats: DashboardStats) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

data class DashboardScreenState(
    val uiState: DashboardUiState      = DashboardUiState.Loading,
    val isServiceActive: Boolean       = false,   // SMS Monitor চালু আছে কিনা
    val isRefreshing: Boolean          = false,   // Pull-to-refresh indicator
    val userName: String               = "",      // Header-এ দেখানোর জন্য
    val plans: List<SubscriptionPlanDto> = emptyList(),
    val showPurchaseDialog: Boolean    = false,
    val purchaseLoading: Boolean       = false,
    val globalTemplates: List<SmsTemplateDto> = emptyList(),
    // Custom Sender ID Archive additions
    val selectedTab: Int               = 0, // 0 = Payment Records, 1 = Custom Archive
    val customArchives: List<CustomArchiveItem> = emptyList(),
    val isCustomArchivesLoading: Boolean = false,
    val customArchivesError: String?   = null
)

// =============================================================================
// ViewModel — AndroidViewModel কারণ applicationContext দরকার
// =============================================================================

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PaymentRepository()
    private val prefs      = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
    private val connectivityObserver = NetworkConnectivityObserver(application)

    val isNetworkAvailable = connectivityObserver.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = connectivityObserver.isNetworkAvailable()
        )

    private val _state = MutableStateFlow(DashboardScreenState())
    val state: StateFlow<DashboardScreenState> = _state.asStateFlow()

    init {
        // ViewModel তৈরি হওয়ার সাথে সাথে SharedPrefs থেকে স্টেট পড়া
        val isActive = prefs.getBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, false)
        val userName = prefs.getString("pcu_user_name", "ব্যবহারকারী") ?: "ব্যবহারকারী"
        val defaultTab = prefs.getInt("pcu_default_dashboard_tab", 0)

        // Read templates cache from PrefsHelper and filter by isActive == 1
        val cachedTemplatesJson = online.paychek.app.data.local.prefs.PrefsHelper.getSmsTemplatesCache(application)
        val cachedTemplatesList = if (cachedTemplatesJson.isNotEmpty() && cachedTemplatesJson != "[]") {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<SmsTemplateDto>>() {}.type
                online.paychek.app.utils.GsonUtils.gson.fromJson<List<SmsTemplateDto>>(cachedTemplatesJson, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()
        val activeTemplates = cachedTemplatesList.filter { it.isActive == 1 }

        _state.update { 
            it.copy(
                isServiceActive = isActive, 
                userName = userName,
                selectedTab = defaultTab,
                globalTemplates = activeTemplates
            ) 
        }

        viewModelScope.launch {
            isNetworkAvailable.collect { available ->
                if (available) {
                    loadDashboardStats()
                    loadPlans()
                    if (defaultTab == 1) {
                        loadCustomArchives()
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard Stats লোড করা
    // ─────────────────────────────────────────────────────────────────────────

    fun loadDashboardStats() {
        viewModelScope.launch {
            _state.update { it.copy(uiState = DashboardUiState.Loading) }

            // Trigger offline queue flush silently
            try {
                FlushOfflineQueueUseCase(getApplication()).execute()
            } catch (e: Exception) {
                // Ignore sync errors here
            }

            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                _state.update {
                    it.copy(uiState = DashboardUiState.Error("লগইন সেশন পাওয়া যায়নি। পুনরায় লগইন করুন।"))
                }
                return@launch
            }

            val lastSync = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsLastSync(getApplication())
            val result = repository.fetchDashboardStats(token, lastSync)
            result.fold(
                onSuccess = { stats ->
                    prefs.edit().putString("pcu_account_level", stats.activePlanName).apply()
                    
                    if (!stats.secretKey.isNullOrBlank()) {
                        SecurePreferences.encrypt(
                            getApplication(),
                            online.paychek.app.services.sms.SmsReceiver.KEY_HMAC_SECRET,
                            stats.secretKey
                        )
                    }

                    // Sync gateway methods cache if provided by server
                    if (stats.gatewayMethods != null) {
                        try {
                            val jsonStr = online.paychek.app.utils.GsonUtils.gson.toJson(stats.gatewayMethods)
                            online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsCache(getApplication(), jsonStr)
                            val serverSyncTime = stats.gatewayMethodsLastSync ?: 0L
                            online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsLastSync(getApplication(), serverSyncTime)
                            android.util.Log.i("DashboardViewModel", "✅ Dashboard Sync: Updated gateway methods cache (size=${stats.gatewayMethods.size}) from stats. LastSync=$serverSyncTime")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Sync global templates cache if provided by server
                    if (stats.globalTemplates != null) {
                        try {
                            val jsonTemplates = online.paychek.app.utils.GsonUtils.gson.toJson(stats.globalTemplates)
                            online.paychek.app.data.local.prefs.PrefsHelper.setSmsTemplatesCache(getApplication(), jsonTemplates)
                            android.util.Log.i("DashboardViewModel", "✅ Dashboard Sync: Updated SMS templates cache (size=${stats.globalTemplates.size}) from stats.")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    _state.update {
                        it.copy(
                            uiState         = DashboardUiState.Success(stats),
                            isRefreshing    = false,
                            globalTemplates = stats.globalTemplates ?: emptyList()
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            uiState      = DashboardUiState.Error(error.message ?: "ডেটা লোড ব্যর্থ হয়েছে"),
                            isRefreshing = false
                        )
                    }
                }
            )
        }
    }

    // Pull-to-refresh সাপোর্ট
    fun onRefresh() {
        _state.update { it.copy(isRefreshing = true) }
        if (_state.value.selectedTab == 1) {
            viewModelScope.launch {
                loadCustomArchives()
                _state.update { it.copy(isRefreshing = false) }
            }
        } else {
            loadDashboardStats()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SMS Monitor Service — Toggle ON/OFF
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * SMS Monitor Service চালু বা বন্ধ করা।
     *
     * Toggle ON:
     *  ১. Intent ACTION_START → startForegroundService()
     *  ২. SharedPrefs-এ KEY_SMS_SERVICE_ACTIVE = true (BootReceiver এটি পড়বে)
     *  ৩. UI state আপডেট → isServiceActive = true
     *
     * Toggle OFF:
     *  ১. Intent ACTION_STOP → startService()
     *  ২. SharedPrefs-এ KEY_SMS_SERVICE_ACTIVE = false
     *  ৩. UI state আপডেট → isServiceActive = false
     */
    fun toggleSmsService(enable: Boolean) {
        val context = getApplication<Application>().applicationContext

        val serviceIntent = Intent(context, SmsMonitorService::class.java).apply {
            action = if (enable) SmsMonitorService.ACTION_START else SmsMonitorService.ACTION_STOP
        }

        try {
            if (enable) {
                // Android 8+ এর জন্য startForegroundService() আবশ্যক
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                context.startService(serviceIntent) // ACTION_STOP signal
            }

            // SharedPrefs আপডেট — BootReceiver এটি রিবুটের পর পড়বে
            prefs.edit()
                .putBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, enable)
                .apply()

            // UI state তাৎক্ষণিক আপডেট
            _state.update { it.copy(isServiceActive = enable) }

        } catch (e: Exception) {
            // Service চালু করতে ব্যর্থ হলে (বিরল ক্ষেত্রে)
            _state.update {
                it.copy(
                    uiState = DashboardUiState.Error("সার্ভিস ${if (enable) "চালু" else "বন্ধ"} করা যায়নি: ${e.message}")
                )
            }
        }
    }


    fun loadPlans() {
        viewModelScope.launch {
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) return@launch
            repository.getPlans(token).onSuccess { plansList ->
                _state.update { it.copy(plans = plansList) }
            }.onFailure {
                // Silently ignore
            }
        }
    }

    fun setShowPurchaseDialog(show: Boolean) {
        _state.update { it.copy(showPurchaseDialog = show) }
    }

    fun purchaseSubscription(planName: String, onResult: (Result<PurchaseSubscriptionResponse>) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(purchaseLoading = true) }
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                onResult(Result.failure(Exception("লগইন সেশন পাওয়া যায়নি।")))
                _state.update { it.copy(purchaseLoading = false) }
                return@launch
            }
            val result = repository.purchaseSubscription(token, planName)
            result.onSuccess {
                loadDashboardStats()
                _state.update { it.copy(purchaseLoading = false, showPurchaseDialog = false) }
            }.onFailure {
                _state.update { it.copy(purchaseLoading = false) }
            }
            onResult(result)
        }
    }

    fun markTransactionSoldOut(transactionId: Int) {
        viewModelScope.launch {
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) return@launch
            repository.markTransactionSoldOut(token, transactionId).onSuccess {
                loadDashboardStats()
            }
        }
    }

    fun setSelectedTab(tabIndex: Int) {
        _state.update { it.copy(selectedTab = tabIndex) }
        if (tabIndex == 1) {
            loadCustomArchives()
        }
    }

    fun saveDefaultTabPreference(tabIndex: Int) {
        prefs.edit().putInt("pcu_default_dashboard_tab", tabIndex).apply()
    }

    fun loadCustomArchives() {
        viewModelScope.launch {
            _state.update { it.copy(isCustomArchivesLoading = true, customArchivesError = null) }
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                _state.update { it.copy(isCustomArchivesLoading = false, customArchivesError = "লগইন সেশন পাওয়া যায়নি।") }
                return@launch
            }
            val result = repository.fetchCustomArchives(token, 1, 20)
            result.fold(
                onSuccess = { archives ->
                    _state.update { it.copy(customArchives = archives, isCustomArchivesLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(customArchivesError = error.message ?: "আর্কাইভ লোড ব্যর্থ হয়েছে", isCustomArchivesLoading = false) }
                }
            )
        }
    }
}
