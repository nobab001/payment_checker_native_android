package online.paychek.app.ui.screen.transactions

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.TransactionItem
import online.paychek.app.data.repository.PaymentRepository
import online.paychek.app.utils.SecurePreferences
import online.paychek.app.utils.NetworkConnectivityObserver
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

// =============================================================================
// Provider Filter Options are now dynamic strings.
// "all" is used as the default value to show all transactions.

data class TransactionSearchState(
    val rawList:     List<TransactionItem> = emptyList(),
    val displayList: List<TransactionItem> = emptyList(),
    val templates:   List<SmsTemplateDto>  = emptyList(),
    val selectedProvider: String = "all",
    val searchQuery:      String         = "",
    val currentPage:        Int     = 1,
    val hasMore:            Boolean = true,
    val isInitialLoading:   Boolean = true,
    val isLoadingMore:      Boolean = false,
    val isRefreshing:       Boolean = false,
    val lastUpdatedAtMs:    Long?   = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val errorMessage: String? = null,
    val selectedQuickDays: Int? = DEFAULT_QUICK_DAYS,
    val refreshSkipped: Boolean = false
) {
    companion object {
        const val DEFAULT_QUICK_DAYS = 2
    }
}

// =============================================================================
// ViewModel
// =============================================================================
@OptIn(FlowPreview::class)
class TransactionSearchViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PAGE_SIZE = 20
    }

    private val repository = PaymentRepository()
    private val prefs      = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
    private val connectivityObserver = NetworkConnectivityObserver(application)

    val isNetworkAvailable = connectivityObserver.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = connectivityObserver.isNetworkAvailable()
        )

    private val _state = MutableStateFlow(TransactionSearchState())
    val state: StateFlow<TransactionSearchState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    private data class HistoryCacheBundle(
        val provider: String,
        val startDate: String?,
        val endDate: String?,
        val items: List<TransactionItem>
    )

    init {
        val defaultRange = quickDateRange(TransactionSearchState.DEFAULT_QUICK_DAYS)
        _state.update {
            it.copy(
                selectedQuickDays = TransactionSearchState.DEFAULT_QUICK_DAYS,
                startDate = defaultRange.first,
                endDate = defaultRange.second
            )
        }

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

        _searchQuery
            .debounce(300)
            .onEach { applyLocalFilter() }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            isNetworkAvailable.collect { available ->
                if (available) {
                    loadFirstPage()
                }
            }
        }
    }

    private fun historyFilterKey(): Triple<String, String?, String?> {
        val s = _state.value
        return Triple(s.selectedProvider, s.startDate, s.endDate)
    }

    private fun restoreHistoryFromLocalCache(): Boolean {
        val bundleJson = PrefsHelper.getTransactionHistoryBundle(getApplication())
        if (bundleJson.isBlank()) return false
        return try {
            val type = object : TypeToken<HistoryCacheBundle>() {}.type
            val bundle = GsonUtils.gson.fromJson<HistoryCacheBundle>(bundleJson, type) ?: return false
            val (provider, start, end) = historyFilterKey()
            if (bundle.provider != provider || bundle.startDate != start || bundle.endDate != end) {
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
        val (provider, start, end) = historyFilterKey()
        try {
            val json = GsonUtils.gson.toJson(
                HistoryCacheBundle(provider = provider, startDate = start, endDate = end, items = items)
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
                currentPage      = 1,
                hasMore          = true,
                isInitialLoading = true,
                errorMessage     = null
            )
        }
        fetchPage(page = 1)
    }

    fun onDateRangeChanged(start: String?, end: String?, quickDays: Int? = null) {
        _state.update {
            it.copy(
                startDate        = start,
                endDate          = end,
                selectedQuickDays = quickDays,
                rawList          = emptyList(),
                displayList      = emptyList(),
                currentPage      = 1,
                hasMore          = true,
                isInitialLoading = true,
                errorMessage     = null
            )
        }
        fetchPage(page = 1)
    }

    private fun loadFirstPage() {
        if (_state.value.rawList.isEmpty()) {
            restoreHistoryFromLocalCache()
        }
        _state.update {
            it.copy(
                currentPage      = 1,
                hasMore          = true,
                isInitialLoading = it.rawList.isEmpty(),
                errorMessage     = null
            )
        }
        fetchTemplates()
        fetchPage(page = 1)
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.isLoadingMore || !current.hasMore || current.isInitialLoading || current.isRefreshing || current.rawList.isEmpty()) return

        _state.update { it.copy(isLoadingMore = true) }
        fetchPage(page = current.currentPage + 1)
    }

    fun onRefresh(): Boolean {
        return RefreshCooldown.tryRefresh {
            _state.update {
                it.copy(isRefreshing = true, refreshSkipped = false)
            }
            fetchTemplates()
            fetchPage(page = 1, isManualRefresh = true)
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

    private fun fetchPage(page: Int, isManualRefresh: Boolean = false) {
        viewModelScope.launch {
            val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                _state.update {
                    it.copy(
                        isInitialLoading = false,
                        isLoadingMore    = false,
                        errorMessage     = "লগইন সেশন পাওয়া যায়নি।"
                    )
                }
                return@launch
            }

            val provider = _state.value.selectedProvider
            val historyLastSync = if (page == 1) {
                PrefsHelper.getHistoryLastSync(getApplication()).takeIf { it > 0L }
            } else {
                null
            }
            if (page == 1 && historyLastSync != null) {
                android.util.Log.i(
                    "TransactionSearchVM",
                    "Sending X-History-Last-Sync=$historyLastSync (manual=$isManualRefresh)"
                )
            }
            val result   = repository.fetchTransactionHistory(
                token    = token,
                page     = page,
                limit    = PAGE_SIZE,
                provider = provider,
                startDate = _state.value.startDate,
                endDate = _state.value.endDate,
                historyLastSync = historyLastSync
            )

            result.fold(
                onSuccess = { pageResult ->
                    if (pageResult.cacheHit && page == 1) {
                        android.util.Log.i(
                            "TransactionSearchVM",
                            "History cache hit — skipped payload (version=${pageResult.historyVersion})"
                        )
                        if (_state.value.rawList.isEmpty()) {
                            restoreHistoryFromLocalCache()
                        }
                        applyLocalFilter()
                        _state.update { current ->
                            current.copy(
                                isInitialLoading = false,
                                isLoadingMore    = false,
                                isRefreshing     = false,
                                refreshSkipped   = isManualRefresh,
                                errorMessage     = null
                            )
                        }
                        pageResult.historyVersion?.let {
                            PrefsHelper.setHistoryLastSync(getApplication(), it)
                        }
                        return@launch
                    }

                    val newItems = pageResult.items
                    pageResult.historyVersion?.let {
                        PrefsHelper.setHistoryLastSync(getApplication(), it)
                    }

                    _state.update { current ->
                        val merged = if (page == 1) newItems else current.rawList + newItems
                        val refreshed = isManualRefresh || current.isRefreshing
                        val updatedAt = if (page == 1 && refreshed) {
                            BangladeshTimeUtil.latestTransactionEpochMs(merged)
                                ?: System.currentTimeMillis()
                        } else {
                            BangladeshTimeUtil.latestTransactionEpochMs(merged)
                                ?: current.lastUpdatedAtMs
                                ?: System.currentTimeMillis()
                        }
                        current.copy(
                            rawList          = merged,
                            currentPage      = page,
                            hasMore          = if (current.startDate != null && current.endDate != null) {
                                false
                            } else {
                                pageResult.hasMore && newItems.size >= PAGE_SIZE
                            },
                            isInitialLoading = false,
                            isLoadingMore    = false,
                            isRefreshing     = false,
                            refreshSkipped   = false,
                            lastUpdatedAtMs  = updatedAt,
                            errorMessage     = null
                        )
                    }
                    if (page == 1) {
                        saveHistoryToLocalCache(_state.value.rawList)
                    }
                    applyLocalFilter()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isLoadingMore    = false,
                            isRefreshing     = false,
                            errorMessage     = error.message ?: "ডেটা লোড ব্যর্থ হয়েছে"
                        )
                    }
                }
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

