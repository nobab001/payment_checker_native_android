package online.paychek.app.ui.screen.dashboard

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
import online.paychek.app.utils.RefreshCooldown
import online.paychek.app.utils.BangladeshTimeUtil
import online.paychek.app.data.remote.dto.*
import online.paychek.app.data.repository.PaymentRepository
import online.paychek.app.services.foreground.SmsServiceGuard
import online.paychek.app.domain.usecase.sync.FlushOfflineQueueUseCase
import online.paychek.app.utils.SecurePreferences
import kotlinx.coroutines.delay
import online.paychek.app.services.connectivity.ConnectionEngine
import online.paychek.app.services.connectivity.ConnectionBanner
import online.paychek.app.services.connectivity.ConnectionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import online.paychek.app.ui.common.HistoryLoadTier
import online.paychek.app.ui.common.nextArchiveHistoryDays
import online.paychek.app.ui.common.nextHistoryDays
import online.paychek.app.ui.common.tierForHistoryDays
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.utils.GsonUtils
import com.google.gson.reflect.TypeToken

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
    val dateFilteredTransactions: List<TransactionItem> = emptyList(),
    val isFilterLoading: Boolean       = false,
    /** Progressive history beyond dashboard recent-20 (7/15/21/30 day windows). */
    val extendedTransactions: List<TransactionItem> = emptyList(),
    val historyTier: HistoryLoadTier = HistoryLoadTier.INITIAL_20,
    val isLoadingMoreHistory: Boolean = false,
    // Custom Sender ID Archive additions
    val selectedTab: Int               = 0, // 0 = Payment Records, 1 = Custom Archive
    val customArchives: List<CustomArchiveItem> = emptyList(),
    val isCustomArchivesLoading: Boolean = false,
    val customArchivesError: String?   = null,
    /** Progressive archive: INITIAL_20 → 7d → 15d */
    val archiveHistoryTier: HistoryLoadTier = HistoryLoadTier.INITIAL_20,
    val isLoadingMoreArchives: Boolean = false,
    val lastUpdatedAtMs: Long?         = null
)

// =============================================================================
// ViewModel — AndroidViewModel কারণ applicationContext দরকার
// =============================================================================

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PaymentRepository()
    private val prefs      = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
    private val connectionEngine = ConnectionEngine.getInstance(application)

    val connectionStatus = connectionEngine.status
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionStatus()
        )

    val connectionBanner = connectionEngine.banner
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /** True when layers 1+2 pass (actual internet, not just API success). */
    val hasInternet = connectionEngine.status
        .map { it.hasInternet }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
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

        restoreCachedDashboardStats()

        connectionEngine.startMonitoring(viewModelScope)

        viewModelScope.launch {
            connectionEngine.status
                .map { it.hasInternet }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    loadDashboardStats()
                    loadPlans()
                    if (_state.value.selectedTab == 1) {
                        loadCustomArchives()
                    }
                }
        }

        viewModelScope.launch {
            ensureSmsServiceRunning()
        }
    }

    /**
     * Prefs ON কিন্তু সার্ভিস মরে গেলে (OEM kill) আবার চালু করে; UI সিঙ্ক রাখে।
     */
    fun ensureSmsServiceRunning() {
        val context = getApplication<Application>().applicationContext
        val prefOn = prefs.getBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, false)
        if (!prefOn) {
            _state.update { it.copy(isServiceActive = false) }
            return
        }
        // Device setup incomplete → do not keep/restart monitor
        val gate = online.paychek.app.utils.DeviceMonitoringGate.check(context)
        if (!gate.ready) {
            // Prefs OFF must commit before cancel — otherwise concurrent heal/a11y can re-arm.
            prefs.edit().putBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, false).commit()
            SmsServiceGuard.stopService(context)
            SmsServiceGuard.cancelWatchdog(context)
            _state.update { it.copy(isServiceActive = false) }
            return
        }
        val running = SmsServiceGuard.ensureRunningAndSync(context)
        _state.update { it.copy(isServiceActive = running) }
    }

    private fun restoreCachedDashboardStats() {
        val cachedJson = online.paychek.app.data.local.prefs.PrefsHelper
            .getDashboardStatsCache(getApplication())
        if (cachedJson.isBlank() || cachedJson == "[]") return
        try {
            val stats = online.paychek.app.utils.GsonUtils.gson
                .fromJson(cachedJson, DashboardStats::class.java)
            _state.update {
                it.copy(uiState = DashboardUiState.Success(stats))
            }
        } catch (_: Exception) {
            // Ignore corrupt cache
        }
    }

    private fun cacheDashboardStats(stats: DashboardStats) {
        try {
            val json = online.paychek.app.utils.GsonUtils.gson.toJson(stats)
            online.paychek.app.data.local.prefs.PrefsHelper
                .setDashboardStatsCache(getApplication(), json)
        } catch (_: Exception) {
            // Ignore cache write errors
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard Stats লোড করা
    // ─────────────────────────────────────────────────────────────────────────

    fun loadDashboardStats() {
        viewModelScope.launch {
            val hasCachedStats = _state.value.uiState is DashboardUiState.Success
            if (!hasCachedStats && !_state.value.isRefreshing) {
                _state.update { it.copy(uiState = DashboardUiState.Loading) }
            }

            // Non-blocking — do not delay dashboard paint for offline flush
            viewModelScope.launch {
                try {
                    FlushOfflineQueueUseCase(getApplication()).execute()
                } catch (_: Exception) { }
            }

            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                _state.update {
                    it.copy(uiState = DashboardUiState.Error("লগইন সেশন পাওয়া যায়নি। পুনরায় লগইন করুন।"))
                }
                return@launch
            }

            val connection = connectionEngine.probe()
            if (!connection.hasInternet) {
                restoreCachedDashboardStats()
                _state.update { it.copy(isRefreshing = false) }
                return@launch
            }
            if (!connection.hasServer) {
                restoreCachedDashboardStats()
                _state.update { it.copy(isRefreshing = false) }
                return@launch
            }

            val lastSync = online.paychek.app.data.local.prefs.PrefsHelper
                .getGatewayMethodsLastSync(getApplication())
            var lastError: String? = null

            repeat(3) { attempt ->
                if (attempt > 0) {
                    connectionEngine.reportApiSyncFailure(isRetrying = true)
                    delay(1_000L * attempt)
                }

                val result = repository.fetchDashboardStats(token, lastSync)
                result.fold(
                    onSuccess = { stats ->
                        connectionEngine.clearApiSyncBanner()
                        applyDashboardStats(stats)
                        return@launch
                    },
                    onFailure = { error ->
                        lastError = error.message ?: "ডেটা সিঙ্ক ব্যর্থ"
                    }
                )
            }

            restoreCachedDashboardStats()
            connectionEngine.reportApiSyncFailure(isRetrying = false)
            if (!hasCachedStats) {
                _state.update {
                    it.copy(
                        uiState = DashboardUiState.Error(lastError ?: "ডেটা লোড ব্যর্থ হয়েছে"),
                        isRefreshing = false
                    )
                }
            } else {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun applyDashboardStats(stats: DashboardStats) {
        prefs.edit().putString("pcu_account_level", stats.activePlanName).apply()

        if (!stats.secretKey.isNullOrBlank()) {
            SecurePreferences.encrypt(
                getApplication(),
                online.paychek.app.services.sms.SmsReceiver.KEY_HMAC_SECRET,
                stats.secretKey
            )
        }

        val serverSyncTime = stats.dataVersion ?: stats.gatewayMethodsLastSync ?: 0L
        if (serverSyncTime > 0) {
            online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsLastSync(
                getApplication(),
                serverSyncTime
            )
        }

        if (stats.gatewayMethods != null) {
            try {
                val jsonStr = online.paychek.app.utils.GsonUtils.gson.toJson(stats.gatewayMethods)
                online.paychek.app.data.local.prefs.PrefsHelper
                    .setGatewayMethodsCache(getApplication(), jsonStr)
                if (online.paychek.app.data.local.prefs.PrefsHelper.isSmsServiceActive(getApplication())) {
                    online.paychek.app.services.sync.NumberHeartbeatEngine.pulse(getApplication())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        stats.globalBlockedSenders?.let { blocked ->
            online.paychek.app.data.local.prefs.PrefsHelper
                .setGlobalBlockedSenders(getApplication(), blocked)
        }

        if (stats.globalTemplates != null) {
            try {
                val jsonTemplates = online.paychek.app.utils.GsonUtils.gson.toJson(stats.globalTemplates)
                online.paychek.app.data.local.prefs.PrefsHelper
                    .setSmsTemplatesCache(getApplication(), jsonTemplates)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        cacheDashboardStats(stats)

        val templatesForUi = stats.globalTemplates ?: _state.value.globalTemplates

        _state.update {
            it.copy(
                uiState = DashboardUiState.Success(stats),
                isRefreshing = false,
                globalTemplates = templatesForUi,
                extendedTransactions = emptyList(),
                historyTier = HistoryLoadTier.INITIAL_20,
                isLoadingMoreHistory = false,
                lastUpdatedAtMs = if (it.isRefreshing) {
                    System.currentTimeMillis()
                } else {
                    BangladeshTimeUtil.latestTransactionEpochMs(stats.recentTransactions)
                        ?: it.lastUpdatedAtMs
                        ?: System.currentTimeMillis()
                }
            )
        }
    }

    // Pull-to-refresh সাপোর্ট
    fun onRefresh(): Boolean {
        return RefreshCooldown.tryRefresh {
            _state.update { it.copy(isRefreshing = true) }
            if (_state.value.selectedTab == 1) {
                loadCustomArchives(forceNetwork = true)
            } else {
                loadDashboardStats()
            }
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
    /**
     * Reinstall / fresh login: rebuild gateway + SMS template caches from server,
     * then start the monitor — stay in-app (no auto Settings jump).
     */
    fun startMonitorWithCacheRefresh(onResult: (ok: Boolean, message: String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            refreshMonitoringCachesFromServer()
            val gate = online.paychek.app.utils.DeviceMonitoringGate.check(context)
            if (!gate.ready) {
                onResult(false, gate.message)
                return@launch
            }
            toggleSmsService(true)
            onResult(true, null)
        }
    }

    /** Force-pull dashboard payload so PrefsHelper caches are rebuilt locally. */
    private suspend fun refreshMonitoringCachesFromServer() {
        val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
        if (token.isEmpty()) return
        val connection = connectionEngine.probe()
        if (!connection.hasInternet || !connection.hasServer) return
        // lastSync=0 → ask server for full gateway/templates payload when available
        repository.fetchDashboardStats(token, 0L).onSuccess { stats ->
            applyDashboardStats(stats)
        }
    }

    fun toggleSmsService(enable: Boolean) {
        val context = getApplication<Application>().applicationContext

        try {
            if (enable) {
                val gate = online.paychek.app.utils.DeviceMonitoringGate.check(context)
                if (!gate.ready) {
                    return
                }
                // Pref MUST be true before startForegroundService — heartbeat/watchdog
                // both gate on KEY_SMS_SERVICE_ACTIVE and would silently no-op otherwise.
                prefs.edit()
                    .putBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, true)
                    .commit()
                SmsServiceGuard.startService(context)
                SmsServiceGuard.scheduleWatchdog(context)
            } else {
                online.paychek.app.data.local.prefs.PrefsHelper.setPendingMonitorStart(context, false)
                prefs.edit()
                    .putBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, false)
                    .commit()
                SmsServiceGuard.stopService(context)
                SmsServiceGuard.cancelWatchdog(context)
            }

            // Optimistic UI — isAlive may lag until onStartCommand runs.
            _state.update {
                it.copy(isServiceActive = enable)
            }

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

    fun loadCustomArchives(query: String = "", forceNetwork: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isCustomArchivesLoading = true,
                    customArchivesError = null,
                    archiveHistoryTier = if (query.isBlank()) {
                        if (forceNetwork) HistoryLoadTier.INITIAL_20 else it.archiveHistoryTier
                    } else {
                        HistoryLoadTier.INITIAL_20
                    }
                )
            }
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                _state.update { it.copy(isCustomArchivesLoading = false, customArchivesError = "লগইন সেশন পাওয়া যায়নি।") }
                return@launch
            }
            val trimmed = query.trim()
            val isBaseline = trimmed.isEmpty()
            val archiveLastSync = if (!forceNetwork && isBaseline) {
                PrefsHelper.getArchiveLastSync(getApplication()).takeIf { it > 0L }
            } else {
                null
            }
            val result = repository.fetchCustomArchives(
                token = token,
                page = 1,
                limit = 20,
                query = trimmed.ifEmpty { null },
                archiveLastSync = archiveLastSync
            )
            result.fold(
                onSuccess = { pageResult ->
                    if (pageResult.cacheHit && isBaseline) {
                        if (_state.value.customArchives.isEmpty()) {
                            restoreArchivesFromLocalCache()
                        }
                        pageResult.archiveVersion?.let {
                            PrefsHelper.setArchiveLastSync(getApplication(), it)
                        }
                        _state.update {
                            it.copy(
                                isCustomArchivesLoading = false,
                                isRefreshing = false,
                                lastUpdatedAtMs = System.currentTimeMillis(),
                                archiveHistoryTier = HistoryLoadTier.INITIAL_20
                            )
                        }
                        return@fold
                    }
                    pageResult.archiveVersion?.let {
                        PrefsHelper.setArchiveLastSync(getApplication(), it)
                    }
                    if (isBaseline) {
                        saveArchivesToLocalCache(pageResult.items)
                    }
                    _state.update {
                        it.copy(
                            customArchives = pageResult.items,
                            isCustomArchivesLoading = false,
                            isRefreshing = false,
                            lastUpdatedAtMs = System.currentTimeMillis(),
                            archiveHistoryTier = HistoryLoadTier.INITIAL_20
                        )
                    }
                },
                onFailure = { error ->
                    if (_state.value.customArchives.isEmpty()) {
                        restoreArchivesFromLocalCache()
                    }
                    _state.update {
                        it.copy(
                            customArchivesError = if (it.customArchives.isEmpty()) {
                                error.message ?: "আর্কাইভ লোড ব্যর্থ হয়েছে"
                            } else {
                                null
                            },
                            isCustomArchivesLoading = false,
                            isRefreshing = false
                        )
                    }
                }
            )
        }
    }

    /** Progressive archive history: 20 → 7d → 15d (matches rolling retention). */
    fun loadMoreArchives() {
        val current = _state.value
        if (current.isLoadingMoreArchives || current.isCustomArchivesLoading) return
        val nextDays = current.archiveHistoryTier.nextArchiveHistoryDays() ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMoreArchives = true) }
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                _state.update { it.copy(isLoadingMoreArchives = false) }
                return@launch
            }
            val range = quickDateRange(nextDays)
            val result = repository.fetchCustomArchives(
                token = token,
                page = 1,
                limit = 50,
                startDate = range.first,
                endDate = range.second,
                archiveLastSync = null
            )
            result.fold(
                onSuccess = { pageResult ->
                    _state.update {
                        it.copy(
                            customArchives = pageResult.items,
                            archiveHistoryTier = tierForHistoryDays(nextDays),
                            isLoadingMoreArchives = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingMoreArchives = false) }
                }
            )
        }
    }

    private fun saveArchivesToLocalCache(items: List<CustomArchiveItem>) {
        try {
            PrefsHelper.setCustomArchiveBundle(getApplication(), GsonUtils.gson.toJson(items))
        } catch (_: Exception) { /* ignore */ }
    }

    private fun restoreArchivesFromLocalCache(): Boolean {
        return try {
            val json = PrefsHelper.getCustomArchiveBundle(getApplication())
            if (json.isBlank()) return false
            val type = object : TypeToken<List<CustomArchiveItem>>() {}.type
            val items = GsonUtils.gson.fromJson<List<CustomArchiveItem>>(json, type) ?: return false
            if (items.isEmpty()) return false
            _state.update {
                it.copy(
                    customArchives = items,
                    archiveHistoryTier = HistoryLoadTier.INITIAL_20,
                    customArchivesError = null
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun fetchDateFilteredTransactions(startDate: String, endDate: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isFilterLoading = true,
                    historyTier = HistoryLoadTier.CUSTOM,
                    extendedTransactions = emptyList()
                )
            }
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isNotEmpty()) {
                val result = repository.fetchTransactionHistory(
                    token = token,
                    page = 1,
                    limit = 100, // Load all matching range
                    provider = "all",
                    startDate = startDate,
                    endDate = endDate
                )
                result.fold(
                    onSuccess = { pageResult ->
                        _state.update {
                            it.copy(
                                dateFilteredTransactions = pageResult.items,
                                isFilterLoading = false,
                                lastUpdatedAtMs = BangladeshTimeUtil.latestTransactionEpochMs(pageResult.items)
                                    ?: it.lastUpdatedAtMs
                            )
                        }
                    },
                    onFailure = {
                        _state.update { it.copy(dateFilteredTransactions = emptyList(), isFilterLoading = false) }
                    }
                )
            } else {
                _state.update { it.copy(isFilterLoading = false) }
            }
        }
    }

    fun clearDateFilter() {
        _state.update {
            it.copy(
                dateFilteredTransactions = emptyList(),
                historyTier = HistoryLoadTier.INITIAL_20,
                extendedTransactions = emptyList()
            )
        }
    }

    /**
     * Progressive history: 20 → 7d → 15d → 21d → 30d (same as Search).
     * Always fetches provider=all; UI filters by selected template locally.
     */
    fun loadMoreHistory() {
        val current = _state.value
        if (current.isLoadingMoreHistory || current.isFilterLoading) return
        if (current.historyTier == HistoryLoadTier.CUSTOM) return
        val nextDays = current.historyTier.nextHistoryDays() ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMoreHistory = true) }
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                _state.update { it.copy(isLoadingMoreHistory = false) }
                return@launch
            }
            val range = quickDateRange(nextDays)
            val result = repository.fetchTransactionHistory(
                token = token,
                page = 1,
                limit = 200,
                provider = "all",
                startDate = range.first,
                endDate = range.second,
                historyLastSync = null
            )
            result.fold(
                onSuccess = { pageResult ->
                    _state.update {
                        it.copy(
                            extendedTransactions = pageResult.items,
                            dateFilteredTransactions = emptyList(),
                            historyTier = tierForHistoryDays(nextDays),
                            isLoadingMoreHistory = false
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingMoreHistory = false) }
                }
            )
        }
    }

    /** If template filter has zero hits on current window, auto-expand once to 7 days. */
    fun ensureHistoryForProviderFilter(hasLocalMatches: Boolean) {
        val s = _state.value
        if (hasLocalMatches) return
        if (s.historyTier != HistoryLoadTier.INITIAL_20) return
        if (s.isLoadingMoreHistory || s.isFilterLoading) return
        if (s.dateFilteredTransactions.isNotEmpty()) return
        loadMoreHistory()
    }

    private fun quickDateRange(days: Int): Pair<String, String> {
        val cal = java.util.Calendar.getInstance()
        val endStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
        val startStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        return Pair(startStr, endStr)
    }

    fun createManualTransaction(
        amount: Double,
        providerTag: String,
        trxId: String? = null,
        onResult: (Result<TransactionItem>) -> Unit
    ) {
        viewModelScope.launch {
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                onResult(Result.failure(Exception("লগইন সেশন পাওয়া যায়নি।")))
                return@launch
            }
            val result = repository.createManualTransaction(token, amount, providerTag, trxId)
            result.onSuccess {
                loadDashboardStats()
            }
            onResult(result)
        }
    }
}
