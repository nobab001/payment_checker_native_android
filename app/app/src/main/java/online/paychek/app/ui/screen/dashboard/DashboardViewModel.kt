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
import online.paychek.app.utils.SecurePreferences

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
    val purchaseLoading: Boolean       = false
)

// =============================================================================
// ViewModel — AndroidViewModel কারণ applicationContext দরকার
// =============================================================================

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PaymentRepository()
    private val prefs      = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(DashboardScreenState())
    val state: StateFlow<DashboardScreenState> = _state.asStateFlow()

    init {
        // ViewModel তৈরি হওয়ার সাথে সাথে SharedPrefs থেকে স্টেট পড়া
        val isActive = prefs.getBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, false)
        val userName = prefs.getString("pcu_user_name", "ব্যবহারকারী") ?: "ব্যবহারকারী"
        _state.update { it.copy(isServiceActive = isActive, userName = userName) }

        loadDashboardStats()
        loadPlans()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard Stats লোড করা
    // ─────────────────────────────────────────────────────────────────────────

    fun loadDashboardStats() {
        viewModelScope.launch {
            _state.update { it.copy(uiState = DashboardUiState.Loading) }

            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                _state.update {
                    it.copy(uiState = DashboardUiState.Error("লগইন সেশন পাওয়া যায়নি। পুনরায় লগইন করুন।"))
                }
                return@launch
            }

            val result = repository.fetchDashboardStats(token)
            result.fold(
                onSuccess = { stats ->
                    _state.update {
                        it.copy(
                            uiState     = DashboardUiState.Success(stats),
                            isRefreshing = false
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
        loadDashboardStats()
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
}
