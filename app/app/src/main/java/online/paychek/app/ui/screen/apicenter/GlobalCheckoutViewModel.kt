package online.paychek.app.ui.screen.apicenter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.paychek.app.data.remote.dto.ActiveNumberDto
import online.paychek.app.data.remote.dto.CheckoutTabDto
import online.paychek.app.data.remote.dto.CheckoutTabToggle
import online.paychek.app.data.remote.dto.NumberOrderItem
import online.paychek.app.data.remote.dto.ProviderBrandingDto
import online.paychek.app.data.remote.dto.SaveGlobalCheckoutRequest
import online.paychek.app.data.repository.WebsiteRepository

class GlobalCheckoutViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WebsiteRepository(app.applicationContext)

    data class UiState(
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val checkoutTheme: String = "design-1",
        val checkoutMode: String = "transaction",
        val checkoutTabs: Map<String, CheckoutTabDto> = emptyMap(),
        val providerBranding: Map<String, ProviderBrandingDto> = emptyMap(),
        val checkoutNumbers: List<ActiveNumberDto> = emptyList(),
        val websiteCount: Int = 0,
        val error: String? = null,
        val infoMessage: String? = null,
        val designerTab: Int = 0
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var autoSaveJob: Job? = null

    init { load() }

    fun clearMessages() = _state.update { it.copy(error = null, infoMessage = null) }

    fun selectDesignerTab(tab: Int) = _state.update { it.copy(designerTab = tab) }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repo.getGlobalCheckout()
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            checkoutTheme = resp.checkoutTheme,
                            checkoutMode = resp.checkoutMode,
                            checkoutTabs = resp.checkoutTabs ?: emptyMap(),
                            providerBranding = resp.providerBranding ?: emptyMap(),
                            checkoutNumbers = resp.activeNumbers,
                            websiteCount = resp.websiteCount
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun moveCheckoutNumber(from: Int, to: Int) {
        _state.update { s ->
            val list = s.checkoutNumbers.toMutableList()
            if (from in list.indices && to in list.indices) {
                val item = list.removeAt(from)
                list.add(to, item)
            }
            s.copy(checkoutNumbers = list)
        }
    }

    fun toggleCheckoutNumber(methodId: Int, enabled: Boolean) {
        _state.update { s ->
            s.copy(checkoutNumbers = s.checkoutNumbers.map {
                if (it.methodId == methodId) it.copy(enabled = enabled) else it
            })
        }
    }

    /** Debounced auto-save after number toggle/reorder so state is not lost on tab switch. */
    fun scheduleAutoSave(theme: String, mode: String, tabStates: Map<String, Boolean>) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(450)
            save(theme, mode, tabStates, silent = true)
        }
    }

    fun save(
        theme: String,
        mode: String,
        tabStates: Map<String, Boolean>,
        silent: Boolean = false
    ) {
        val order = _state.value.checkoutNumbers.mapIndexed { idx, n ->
            NumberOrderItem(methodId = n.methodId, provider = n.provider, number = n.number, enabled = n.enabled, position = idx)
        }
        val tabs = tabStates.mapValues { CheckoutTabToggle(enabled = it.value) }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repo.saveGlobalCheckout(
                SaveGlobalCheckoutRequest(
                    checkoutTheme = theme,
                    checkoutMode = mode,
                    checkoutTabs = tabs,
                    order = order
                )
            )
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            checkoutTheme = resp.checkoutTheme,
                            checkoutMode = resp.checkoutMode,
                            checkoutTabs = resp.checkoutTabs ?: it.checkoutTabs,
                            checkoutNumbers = it.checkoutNumbers,
                            websiteCount = resp.websitesUpdated ?: it.websiteCount,
                            infoMessage = if (silent) null else (resp.message ?: "সব ওয়েবসাইটে চেকআউট আপডেট হয়েছে।")
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
