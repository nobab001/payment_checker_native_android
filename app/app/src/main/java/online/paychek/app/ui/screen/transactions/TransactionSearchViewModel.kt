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

import online.paychek.app.ui.common.HistoryLoadTier
import online.paychek.app.ui.common.nextHistoryDays
import online.paychek.app.ui.common.tierForHistoryDays

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    fun nextHistoryDays(): Int? = historyTier.nextHistoryDays()

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

    /** Last non-CUSTOM window — restored instantly when calendar date filter is cleared. */
    private var baselineHistory: List<TransactionItem> = emptyList()

    /** Bumps on each history reload so stale in-flight dated fetches cannot wipe the UI. */
    private var historyFetchGeneration: Int = 0

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

    private fun restoreHistoryFromLocalCache(): Boolean {
        val items = readInitialHistoryCache() ?: return false
        _state.update { current ->
            current.copy(
                rawList = items,
                isInitialLoading = false,
                lastUpdatedAtMs = BangladeshTimeUtil.latestTransactionEpochMs(items)
                    ?: current.lastUpdatedAtMs
            )
        }
        if (baselineHistory.isEmpty()) baselineHistory = items
        applyLocalFilter()
        return true
    }

    /** Durable offline cache is only the default INITIAL_20 window (no date filter). */
    private fun readInitialHistoryCache(): List<TransactionItem>? {
        val bundleJson = PrefsHelper.getTransactionHistoryBundle(getApplication())
        if (bundleJson.isBlank()) return null
        return try {
            val type = object : TypeToken<HistoryCacheBundle>() {}.type
            val bundle = GsonUtils.gson.fromJson<HistoryCacheBundle>(bundleJson, type) ?: return null
            if (
                bundle.provider != "all"
                || bundle.startDate != null
                || bundle.endDate != null
                || bundle.tier != HistoryLoadTier.INITIAL_20.name
                || bundle.items.isEmpty()
            ) {
                return null
            }
            bundle.items
        } catch (_: Exception) {
            null
        }
    }

    private fun saveHistoryToLocalCache(items: List<TransactionItem>) {
        if (items.isEmpty()) return
        val s = _state.value
        // Never overwrite the default window cache with CUSTOM / multi-day expansions.
        if (s.historyTier != HistoryLoadTier.INITIAL_20 || s.startDate != null || s.endDate != null) {
            return
        }
        try {
            val json = GsonUtils.gson.toJson(
                HistoryCacheBundle(
                    provider = "all",
                    startDate = null,
                    endDate = null,
                    tier = HistoryLoadTier.INITIAL_20.name,
                    items = items
                )
            )
            PrefsHelper.setTransactionHistoryBundle(getApplication(), json)
        } catch (_: Exception) {}
    }

    private fun rememberBaseline(items: List<TransactionItem>) {
        if (items.isEmpty()) return
        val s = _state.value
        // Clear-date fallback is the default undated ~20 window only.
        if (s.historyTier != HistoryLoadTier.INITIAL_20 || s.startDate != null || s.endDate != null) return
        baselineHistory = items
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
        if (_state.value.selectedProvider.equals(filter, ignoreCase = true)) return

        _state.update { it.copy(selectedProvider = filter, errorMessage = null) }
        applyLocalFilter()

        val provider = filter.trim()
        val isAll = provider.isEmpty() || provider.equals("all", ignoreCase = true)
        if (isAll) {
            // Keep current window; show all rows from rawList.
            return
        }

        // Template chip: filter the current window first. If empty on INITIAL_20, expand to 7 days.
        if (_state.value.displayList.isEmpty() &&
            _state.value.historyTier == HistoryLoadTier.INITIAL_20 &&
            _state.value.startDate == null
        ) {
            loadMoreHistory()
        }
    }

    fun onDateRangeChanged(start: String?, end: String?) {
        val gen = ++historyFetchGeneration
        if (start != null && end != null) {
            // Snapshot the current window before replacing with calendar range.
            rememberBaseline(_state.value.rawList)
            _state.update {
                it.copy(
                    startDate        = start,
                    endDate          = end,
                    historyTier      = HistoryLoadTier.CUSTOM,
                    rawList          = emptyList(),
                    displayList      = emptyList(),
                    isInitialLoading = true,
                    errorMessage     = null
                )
            }
            fetchDatedHistory(replaceList = true, markRefreshing = false, generation = gen)
            return
        }

        // Clear calendar filter: restore latest ~20 immediately (Home-style), then refresh.
        val fallback = when {
            baselineHistory.isNotEmpty() -> baselineHistory
            else -> readInitialHistoryCache().orEmpty()
        }
        _state.update {
            it.copy(
                startDate        = null,
                endDate          = null,
                historyTier      = HistoryLoadTier.INITIAL_20,
                rawList          = fallback,
                isInitialLoading = fallback.isEmpty(),
                isLoadingMoreHistory = false,
                errorMessage     = null
            )
        }
        applyLocalFilter()
        fetchInitialPage(forceNetwork = true, generation = gen)
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
        val newTier = tierForHistoryDays(nextDays)
        rememberBaseline(current.rawList)

        val gen = ++historyFetchGeneration
        _state.update {
            it.copy(
                startDate = range.first,
                endDate = range.second,
                historyTier = newTier,
                isLoadingMoreHistory = true,
                errorMessage = null
            )
        }
        fetchDatedHistory(replaceList = true, markRefreshing = false, generation = gen)
    }

    fun onRefresh(): Boolean {
        return RefreshCooldown.tryRefresh {
            val gen = ++historyFetchGeneration
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
            fetchInitialPage(isManualRefresh = true, forceNetwork = true, generation = gen)
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

    private fun fetchInitialPage(
        isManualRefresh: Boolean = false,
        forceNetwork: Boolean = false,
        generation: Int = historyFetchGeneration
    ) {
        viewModelScope.launch {
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                if (generation != historyFetchGeneration) return@launch
                _state.update {
                    it.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        errorMessage = if (it.rawList.isEmpty()) "লগইন সেশন পাওয়া যায়নি।" else it.errorMessage
                    )
                }
                return@launch
            }

            val provider = _state.value.selectedProvider
            // Always fetch unfiltered page for INITIAL_20; template chips filter locally.
            val historyLastSync = if (
                !isManualRefresh &&
                !forceNetwork &&
                (provider.isEmpty() || provider.equals("all", ignoreCase = true))
            ) {
                PrefsHelper.getHistoryLastSync(getApplication()).takeIf { it > 0L }
            } else null

            val result = repository.fetchTransactionHistory(
                token = token,
                page = 1,
                limit = PAGE_SIZE,
                provider = "all",
                startDate = null,
                endDate = null,
                historyLastSync = historyLastSync
            )

            if (generation != historyFetchGeneration) return@launch

            result.fold(
                onSuccess = { pageResult ->
                    handleFetchSuccess(
                        pageResult,
                        replaceList = true,
                        isManualRefresh = isManualRefresh,
                        generation = generation
                    )
                },
                onFailure = { error -> handleFetchFailure(error, generation) }
            )
        }
    }

    private fun fetchDatedHistory(
        replaceList: Boolean,
        markRefreshing: Boolean,
        generation: Int = historyFetchGeneration
    ) {
        // Capture range before suspend so a later clear cannot change this request's params.
        val start = _state.value.startDate
        val end = _state.value.endDate
        viewModelScope.launch {
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                if (generation != historyFetchGeneration) return@launch
                _state.update {
                    it.copy(
                        isInitialLoading = false,
                        isLoadingMoreHistory = false,
                        isRefreshing = false,
                        errorMessage = if (it.rawList.isEmpty()) "লগইন সেশন পাওয়া যায়নি।" else it.errorMessage
                    )
                }
                return@launch
            }

            val result = repository.fetchTransactionHistory(
                token = token,
                page = 1,
                limit = 200,
                provider = "all",
                startDate = start,
                endDate = end,
                historyLastSync = null
            )

            if (generation != historyFetchGeneration) return@launch

            result.fold(
                onSuccess = { pageResult ->
                    handleFetchSuccess(
                        pageResult,
                        replaceList = replaceList,
                        isManualRefresh = markRefreshing,
                        generation = generation
                    )
                },
                onFailure = { error -> handleFetchFailure(error, generation) }
            )
        }
    }

    private fun handleFetchSuccess(
        pageResult: TransactionHistoryResult,
        replaceList: Boolean,
        isManualRefresh: Boolean,
        generation: Int = historyFetchGeneration
    ) {
        if (generation != historyFetchGeneration) return

        if (pageResult.cacheHit && _state.value.historyTier == HistoryLoadTier.INITIAL_20) {
            if (_state.value.rawList.isEmpty()) {
                val restored = restoreHistoryFromLocalCache()
                if (!restored && _state.value.rawList.isEmpty()) {
                    fetchInitialPage(forceNetwork = true, generation = generation)
                    return
                }
            }
            rememberBaseline(_state.value.rawList)
            applyLocalFilter()
            _state.update { current ->
                current.copy(
                    isInitialLoading = false,
                    isLoadingMoreHistory = false,
                    isRefreshing = false,
                    refreshSkipped = isManualRefresh,
                    lastUpdatedAtMs = if (isManualRefresh || current.isRefreshing) {
                        System.currentTimeMillis()
                    } else {
                        current.lastUpdatedAtMs
                            ?: BangladeshTimeUtil.latestTransactionEpochMs(current.rawList)
                    },
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
            // Refresh tap updates wall-clock "last checked" even when SMS list is unchanged.
            val updatedAt = if (refreshed) {
                System.currentTimeMillis()
            } else {
                current.lastUpdatedAtMs
                    ?: BangladeshTimeUtil.latestTransactionEpochMs(merged)
                    ?: System.currentTimeMillis()
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
        rememberBaseline(_state.value.rawList)
        saveHistoryToLocalCache(_state.value.rawList)
        applyLocalFilter()
    }

    private fun handleFetchFailure(error: Throwable, generation: Int = historyFetchGeneration) {
        if (generation != historyFetchGeneration) return
        _state.update {
            it.copy(
                isInitialLoading = false,
                isLoadingMoreHistory = false,
                isRefreshing = false,
                // Keep restored baseline visible if network refresh fails after clearing date filter.
                errorMessage = if (it.rawList.isEmpty()) {
                    error.message ?: "ডেটা লোড ব্যর্থ হয়েছে"
                } else {
                    null
                }
            )
        }
    }

    private fun applyLocalFilter() {
        val current  = _state.value
        val query    = current.searchQuery.trim().lowercase()
        val provider = current.selectedProvider.trim()

        val filtered = current.rawList.filter { item ->
            val matchesSearch = query.isEmpty() ||
                    item.trxId.lowercase().contains(query) ||
                    (item.senderNumber?.lowercase()?.contains(query) == true)

            val tag = item.providerTag
            val matchesProvider = provider.isEmpty() ||
                    provider.equals("all", ignoreCase = true) ||
                    tag.equals(provider, ignoreCase = true)

            matchesSearch && matchesProvider
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
