package online.paychek.app.ui.screen.transactions

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.TransactionHistoryResult
import online.paychek.app.data.remote.dto.TransactionItem
import online.paychek.app.data.repository.PaymentRepository
import online.paychek.app.utils.SecurePreferences
import online.paychek.app.services.connectivity.ConnectionEngine
import online.paychek.app.utils.RefreshCooldown
import online.paychek.app.utils.BangladeshTimeUtil
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.SmsTemplateDto
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.utils.GsonUtils
import com.google.gson.reflect.TypeToken

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class HistoryLoadTier {
    INITIAL_20,
    DAYS_7,
    DAYS_15,
    DAYS_21,
    DAYS_30,
    CUSTOM
}

data class TransactionSearchState(
    val rawList:     List<TransactionItem> = emptyList(),
    val displayList: List<TransactionItem> = emptyList(),
    val templates:   List<SmsTemplateDto>  = emptyList(),
    val selectedProvider: String = "all",
    val searchQuery:      String         = "",
    val isInitialLoading:   Boolean = true,
    val isLoadingMoreHistory: Boolean = false,
    val isRefreshing:       Boolean = false,
    val lastUpdatedAtMs:    Long?   = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val historyTier: HistoryLoadTier = HistoryLoadTier.INITIAL_20,
    val errorMessage: String? = null,
    val refreshSkipped: Boolean = false
) {
    fun nextHistoryDays(): Int? = when (historyTier) {
        HistoryLoadTier.INITIAL_20 -> 7
        HistoryLoadTier.DAYS_7 -> 15
        HistoryLoadTier.DAYS_15 -> 21
        HistoryLoadTier.DAYS_21 -> 30
        else -> null
    }

    val canLoadMoreHistory: Boolean
        get() = nextHistoryDays() != null && !isInitialLoading && !isLoadingMoreHistory
}

@OptIn(FlowPreview::class)
class TransactionSearchViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PAGE_SIZE = 20
    }

    private val repository = PaymentRepository()
    private val connectionEngine = ConnectionEngine.getInstance(application)

    val connectionBanner = connectionEngine.banner
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val hasInternet = connectionEngine.status
        .map { it.hasInternet }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _state = MutableStateFlow(TransactionSearchState())
    val state: StateFlow<TransactionSearchState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    private data class HistoryCacheBundle(
        val provider: String,
        val startDate: String?,
        val endDate: String?,
        val tier: String,
        val items: List<TransactionItem>
    )

    init {
        val cached = PrefsHelper.getSmsTemplatesCache(application)
        var initialTemplates = emptyList<SmsTemplateDto>()
        if (cached.isNotEmpty()) {
            try {
                val type = object : TypeToken<List<SmsTemplateDto>>() {}.type
                val parsed = GsonUtils.gson.fromJson<List<SmsTemplateDto>>(cached, type) ?: emptyList()
                initialTemplates = parsed.filter { (it.isActive == 1 || it.isOtherDevice == true) && it.isParseable == 1 }
            } catch (_: Exception) {}
        }
        _state.update { it.copy(templates = initialTemplates) }

        restoreHistoryFromLocalCache()

        connectionEngine.startMonitoring(viewModelScope)

        _searchQuery
            .debounce(300)
            .onEach { applyLocalFilter() }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            connectionEngine.status
                .map { it.hasInternet }
                .distinctUntilChanged()
                .filter { it }
                .collect { loadInitialHistory() }
        }
    }

    private fun currentCacheKey(): HistoryCacheBundle {
        val s = _state.value
        return HistoryCacheBundle(
            provider = s.selectedProvider,
            startDate = s.startDate,
            endDate = s.endDate,
            tier = s.historyTier.name,
            items = emptyList()
        )
    }

    private fun restoreHistoryFromLocalCache(): Boolean {
        val bundleJson = PrefsHelper.getTransactionHistoryBundle(getApplication())
        if (bundleJson.isBlank()) return false
        return try {
            val type = object : TypeToken<HistoryCacheBundle>() {}.type
            val bundle = GsonUtils.gson.fromJson<HistoryCacheBundle>(bundleJson, type) ?: return false
            val key = currentCacheKey()
            if (
                bundle.provider != key.provider
                || bundle.startDate != key.startDate
                || bundle.endDate != key.endDate
                || bundle.tier != key.tier
            ) {
                return false
            }
            if (bundle.items.isEmpty()) return false
            _state.update { current ->
                current.copy(
                    rawList = bundle.items,
                    isInitialLoading = false,
                    lastUpdatedAtMs = BangladeshTimeUtil.latestTransactionEpochMs(bundle.items)
                        ?: current.lastUpdatedAtMs
                )
            }
            applyLocalFilter()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun saveHistoryToLocalCache(items: List<TransactionItem>) {
        if (items.isEmpty()) return
        val s = _state.value
        try {
            val json = GsonUtils.gson.toJson(
                HistoryCacheBundle(
                    provider = s.selectedProvider,
                    startDate = s.startDate,
                    endDate = s.endDate,
                    tier = s.historyTier.name,
                    items = items
                )
            )
            PrefsHelper.setTransactionHistoryBundle(getApplication(), json)
        } catch (_: Exception) {}
    }

    private fun fetchTemplates() {
        viewModelScope.launch {
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isNotEmpty()) {
                try {
                    val lastSync = PrefsHelper.getGatewayMethodsLastSync(getApplication())
                    val response = RetrofitClient.gatewayApiService.getTemplates("Bearer $token", lastSync)
                    if (response.isSuccessful) {
                        val body = response.body()
                        body?.dataVersion?.takeIf { it > 0 }?.let {
                            PrefsHelper.setGatewayMethodsLastSync(getApplication(), it)
                        }
                        val list = body?.templates
                        if (list != null) {
                            val jsonStr = GsonUtils.gson.toJson(list)
                            PrefsHelper.setSmsTemplatesCache(getApplication(), jsonStr)
                            val activeParseable = list.filter { (it.isActive == 1 || it.isOtherDevice == true) && it.isParseable == 1 }
                            _state.update { it.copy(templates = activeParseable) }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        _searchQuery.value = query
    }

    fun onProviderFilterChanged(filter: String) {
        if (_state.value.selectedProvider == filter) return
        _state.update {
            it.copy(
                selectedProvider = filter,
                rawList          = emptyList(),
                displayList      = emptyList(),
                startDate        = null,
                endDate          = null,
                historyTier      = HistoryLoadTier.INITIAL_20,
                isInitialLoading = true,
                errorMessage     = null
            )
        }
        fetchInitialPage()
    }

    fun onDateRangeChanged(start: String?, end: String?) {
        _state.update {
            it.copy(
                startDate        = start,
                endDate          = end,
                historyTier      = if (start != null && end != null) HistoryLoadTier.CUSTOM else HistoryLoadTier.INITIAL_20,
                rawList          = emptyList(),
                displayList      = emptyList(),
                isInitialLoading = true,
                errorMessage     = null
            )
        }
        if (start != null && end != null) {
            fetchDatedHistory(replaceList = true, markRefreshing = false)
        } else {
            fetchInitialPage()
        }
    }

    private fun loadInitialHistory() {
        if (_state.value.rawList.isEmpty()) {
            restoreHistoryFromLocalCache()
        }
        if (_state.value.historyTier == HistoryLoadTier.CUSTOM) return
        _state.update {
            it.copy(
                isInitialLoading = it.rawList.isEmpty(),
                errorMessage     = null
            )
        }
        fetchTemplates()
        if (_state.value.historyTier == HistoryLoadTier.INITIAL_20 && _state.value.startDate == null) {
            fetchInitialPage()
        }
    }

    fun loadMoreHistory() {
        val current = _state.value
        val nextDays = current.nextHistoryDays() ?: return
        if (current.isLoadingMoreHistory || current.isInitialLoading) return

        val range = quickDateRange(nextDays)
        val newTier = when (nextDays) {
            7 -> HistoryLoadTier.DAYS_7
            15 -> HistoryLoadTier.DAYS_15
            21 -> HistoryLoadTier.DAYS_21
            30 -> HistoryLoadTier.DAYS_30
            else -> return
        }

        _state.update {
            it.copy(
                startDate = range.first,
                endDate = range.second,
                historyTier = newTier,
                isLoadingMoreHistory = true,
                errorMessage = null
            )
        }
        fetchDatedHistory(replaceList = true, markRefreshing = false)
    }

    fun onRefresh(): Boolean {
        return RefreshCooldown.tryRefresh {
            _state.update {
                it.copy(
                    isRefreshing = true,
                    refreshSkipped = false,
                    startDate = null,
                    endDate = null,
                    historyTier = HistoryLoadTier.INITIAL_20,
                    rawList = emptyList(),
                    displayList = emptyList()
                )
            }
            fetchTemplates()
            fetchInitialPage(isManualRefresh = true)
        }
    }

    fun clearRefreshSkipped() {
        _state.update { it.copy(refreshSkipped = false) }
    }

    private fun quickDateRange(days: Int): Pair<String, String> {
        val cal = Calendar.getInstance()
        val endStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        val startStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        return Pair(startStr, endStr)
    }

    private fun fetchInitialPage(isManualRefresh: Boolean = false) {
        viewModelScope.launch {
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                _state.update {
                    it.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        errorMessage = "লগইন সেশন পাওয়া যায়নি।"
                    )
                }
                return@launch
            }

            val provider = _state.value.selectedProvider
            val historyLastSync = if (!isManualRefresh) {
                PrefsHelper.getHistoryLastSync(getApplication()).takeIf { it > 0L }
            } else null

            val result = repository.fetchTransactionHistory(
                token = token,
                page = 1,
                limit = PAGE_SIZE,
                provider = provider,
                startDate = null,
                endDate = null,
                historyLastSync = historyLastSync
            )

            result.fold(
                onSuccess = { pageResult -> handleFetchSuccess(pageResult, replaceList = true, isManualRefresh = isManualRefresh) },
                onFailure = { error -> handleFetchFailure(error) }
            )
        }
    }

    private fun fetchDatedHistory(replaceList: Boolean, markRefreshing: Boolean) {
        viewModelScope.launch {
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                _state.update {
                    it.copy(
                        isInitialLoading = false,
                        isLoadingMoreHistory = false,
                        isRefreshing = false,
                        errorMessage = "লগইন সেশন পাওয়া যায়নি।"
                    )
                }
                return@launch
            }

            val s = _state.value
            val result = repository.fetchTransactionHistory(
                token = token,
                page = 1,
                limit = PAGE_SIZE,
                provider = s.selectedProvider,
                startDate = s.startDate,
                endDate = s.endDate,
                historyLastSync = null
            )

            result.fold(
                onSuccess = { pageResult ->
                    handleFetchSuccess(
                        pageResult,
                        replaceList = replaceList,
                        isManualRefresh = markRefreshing
                    )
                },
                onFailure = { error -> handleFetchFailure(error) }
            )
        }
    }

    private fun handleFetchSuccess(
        pageResult: TransactionHistoryResult,
        replaceList: Boolean,
        isManualRefresh: Boolean
    ) {
        if (pageResult.cacheHit && _state.value.historyTier == HistoryLoadTier.INITIAL_20) {
            if (_state.value.rawList.isEmpty()) {
                restoreHistoryFromLocalCache()
            }
            applyLocalFilter()
            _state.update { current ->
                current.copy(
                    isInitialLoading = false,
                    isLoadingMoreHistory = false,
                    isRefreshing = false,
                    refreshSkipped = isManualRefresh,
                    errorMessage = null
                )
            }
            pageResult.historyVersion?.let {
                PrefsHelper.setHistoryLastSync(getApplication(), it)
            }
            return
        }

        val newItems = pageResult.items
        pageResult.historyVersion?.let {
            PrefsHelper.setHistoryLastSync(getApplication(), it)
        }

        _state.update { current ->
            val merged = if (replaceList) newItems else current.rawList + newItems
            val refreshed = isManualRefresh || current.isRefreshing
            val updatedAt = if (refreshed || current.lastUpdatedAtMs == null) {
                BangladeshTimeUtil.latestTransactionEpochMs(merged) ?: System.currentTimeMillis()
            } else {
                BangladeshTimeUtil.latestTransactionEpochMs(merged) ?: current.lastUpdatedAtMs
            }
            current.copy(
                rawList = merged,
                isInitialLoading = false,
                isLoadingMoreHistory = false,
                isRefreshing = false,
                refreshSkipped = false,
                lastUpdatedAtMs = updatedAt,
                errorMessage = null
            )
        }
        saveHistoryToLocalCache(_state.value.rawList)
        applyLocalFilter()
    }

    private fun handleFetchFailure(error: Throwable) {
        _state.update {
            it.copy(
                isInitialLoading = false,
                isLoadingMoreHistory = false,
                isRefreshing = false,
                errorMessage = error.message ?: "ডেটা লোড ব্যর্থ হয়েছে"
            )
        }
    }

    private fun applyLocalFilter() {
        val current  = _state.value
        val query    = current.searchQuery.trim().lowercase()

        val filtered = current.rawList.filter { item ->
            val matchesSearch = query.isEmpty() ||
                    item.trxId.lowercase().contains(query) ||
                    (item.senderNumber?.lowercase()?.contains(query) == true)

            matchesSearch
        }

        _state.update { it.copy(displayList = filtered) }
    }

    fun markTransactionSoldOut(transactionId: Int) {
        viewModelScope.launch {
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) return@launch
            repository.markTransactionSoldOut(token, transactionId).onSuccess {
                _state.update { current ->
                    val updatedRaw = current.rawList.map { item ->
                        if (item.id == transactionId) item.copy(isUsed = 1) else item
                    }
                    val updatedDisplay = current.displayList.map { item ->
                        if (item.id == transactionId) item.copy(isUsed = 1) else item
                    }
                    current.copy(rawList = updatedRaw, displayList = updatedDisplay)
                }
                saveHistoryToLocalCache(_state.value.rawList)
            }
        }
    }
}
