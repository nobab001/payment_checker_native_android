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

// =============================================================================
// Provider Filter Options
// =============================================================================
enum class ProviderFilter(val label: String, val apiValue: String, val emoji: String) {
    ALL   ("সব",    "all",    "📋"),
    BKASH ("bKash", "bKash",  "🟢"),
    NAGAD ("Nagad", "Nagad",  "🟠"),
    ROCKET("Rocket","Rocket", "🔵"),
    UPAY  ("Upay",  "Upay",   "🟡")
}

// =============================================================================
// UI State
// =============================================================================
data class TransactionSearchState(
    val rawList:     List<TransactionItem> = emptyList(),
    val displayList: List<TransactionItem> = emptyList(),
    val selectedProvider: ProviderFilter = ProviderFilter.ALL,
    val searchQuery:      String         = "",
    val currentPage:        Int     = 1,
    val hasMore:            Boolean = true,
    val isInitialLoading:   Boolean = true,
    val isLoadingMore:      Boolean = false,
    val errorMessage: String? = null
)

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

    init {
        _searchQuery
            .debounce(300)
            .onEach { query ->
                _state.update { it.copy(searchQuery = query) }
                applyLocalFilter()
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            isNetworkAvailable.collect { available ->
                if (available) {
                    loadFirstPage()
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onProviderFilterChanged(filter: ProviderFilter) {
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

    private fun loadFirstPage() {
        _state.update {
            it.copy(
                rawList          = emptyList(),
                currentPage      = 1,
                hasMore          = true,
                isInitialLoading = true,
                errorMessage     = null
            )
        }
        fetchPage(page = 1)
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.isLoadingMore || !current.hasMore) return

        _state.update { it.copy(isLoadingMore = true) }
        fetchPage(page = current.currentPage + 1)
    }

    fun onRefresh() {
        loadFirstPage()
    }

    private fun fetchPage(page: Int) {
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

            val provider = _state.value.selectedProvider.apiValue
            val result   = repository.fetchTransactionHistory(
                token    = token,
                page     = page,
                limit    = PAGE_SIZE,
                provider = provider
            )

            result.fold(
                onSuccess = { newItems ->
                    _state.update { current ->
                        val merged = if (page == 1) newItems else current.rawList + newItems
                        val updated = current.copy(
                            rawList          = merged,
                            currentPage      = page,
                            hasMore          = newItems.size >= PAGE_SIZE,
                            isInitialLoading = false,
                            isLoadingMore    = false,
                            errorMessage     = null
                        )
                        updated
                    }
                    applyLocalFilter()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isLoadingMore    = false,
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
            }
        }
    }
}

