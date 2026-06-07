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
data class TransactionHistoryState(
    // ─── Data ─────────────────────────────────────────────────────────────────
    val rawList:     List<TransactionItem> = emptyList(),  // Server থেকে আসা মূল তালিকা
    val displayList: List<TransactionItem> = emptyList(),  // Filter + Search-এর পর UI দেখায়

    // ─── Filter / Search ──────────────────────────────────────────────────────
    val selectedProvider: ProviderFilter = ProviderFilter.ALL,
    val searchQuery:      String         = "",

    // ─── Pagination ───────────────────────────────────────────────────────────
    val currentPage:        Int     = 1,
    val hasMore:            Boolean = true,
    val isInitialLoading:   Boolean = true,   // প্রথম load — Skeleton দেখাবে
    val isLoadingMore:      Boolean = false,  // পেজের নিচে Spinner

    // ─── Error ────────────────────────────────────────────────────────────────
    val errorMessage: String? = null
)

// =============================================================================
// ViewModel
// =============================================================================
@OptIn(FlowPreview::class)
class TransactionHistoryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PAGE_SIZE = 20
    }

    private val repository = PaymentRepository()
    private val prefs      = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(TransactionHistoryState())
    val state: StateFlow<TransactionHistoryState> = _state.asStateFlow()

    // Search debounce — আলাদা Flow হিসেবে রাখা হয়েছে
    private val _searchQuery = MutableStateFlow("")

    init {
        // 300ms debounce — প্রতি keystroke-এ filter নয়
        _searchQuery
            .debounce(300)
            .onEach { query ->
                _state.update { it.copy(searchQuery = query) }
                applyLocalFilter()
            }
            .launchIn(viewModelScope)

        // স্ক্রিন খোলার সাথে সাথে প্রথম পেজ লোড
        loadFirstPage()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search Query — UI থেকে কল হয়
    // ─────────────────────────────────────────────────────────────────────────
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Provider Filter — Chip ক্লিকে কল হয়
    // Filter পরিবর্তন হলে পেজ রিসেট + নতুন API call
    // ─────────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
    // Pagination — প্রথম পেজ লোড
    // ─────────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
    // Pagination — পরের পেজ লোড (Infinite Scroll)
    // LazyColumn-এর শেষ থেকে ৩য় item দেখা গেলে UI এই ফাংশন call করবে
    // ─────────────────────────────────────────────────────────────────────────
    fun loadNextPage() {
        val current = _state.value
        // Guard: loading চললে বা আর ডেটা না থাকলে skip
        if (current.isLoadingMore || !current.hasMore) return

        _state.update { it.copy(isLoadingMore = true) }
        fetchPage(page = current.currentPage + 1)
    }

    // Pull-to-Refresh
    fun onRefresh() {
        loadFirstPage()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core Fetch — API call করে rawList আপডেট করে
    // ─────────────────────────────────────────────────────────────────────────
    private fun fetchPage(page: Int) {
        viewModelScope.launch {
            val token = prefs.getString(AppConfig.KEY_AUTH_TOKEN, "") ?: ""
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
                        // পেজ ১ হলে fresh list, অন্যথায় পুরনো তালিকায় যোগ
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

    // ─────────────────────────────────────────────────────────────────────────
    // Local Filter — rawList-এর উপর Filter + Search প্রয়োগ
    // নতুন API call ছাড়াই displayList আপডেট হয়
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyLocalFilter() {
        val current  = _state.value
        val query    = current.searchQuery.trim().lowercase()

        val filtered = current.rawList.filter { item ->
            // Search filter — TrxID তে query থাকলে দেখাবে
            val matchesSearch = query.isEmpty() ||
                    item.trxId.lowercase().contains(query) ||
                    (item.senderNumber?.lowercase()?.contains(query) == true)

            matchesSearch
        }

        _state.update { it.copy(displayList = filtered) }
    }
}
