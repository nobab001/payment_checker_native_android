package online.paychek.app.ui.screen.apicenter.website

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
import online.paychek.app.data.remote.dto.CommissionDto
import online.paychek.app.data.remote.dto.NumberOrderItem
import online.paychek.app.data.remote.dto.OfficialGatewayDto
import online.paychek.app.data.remote.dto.UpdateWebsiteRequest
import online.paychek.app.data.remote.dto.UpsertCommissionRequest
import online.paychek.app.data.remote.dto.UpsertOfficialGatewayRequest
import online.paychek.app.data.remote.dto.WebsiteDto
import online.paychek.app.data.repository.WebsiteRepository

/**
 * WebsiteViewModel — API Integration v2 merchant/website state holder.
 * Backs both the Website Management list/wizard and the Website Settings screen.
 */
class WebsiteViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WebsiteRepository(app.applicationContext)

    data class UiState(
        val isLoading: Boolean = false,
        val websites: List<WebsiteDto> = emptyList(),
        val error: String? = null,
        val infoMessage: String? = null,
        // Wizard / creation
        val isCreating: Boolean = false,
        val createdWebsite: WebsiteDto? = null,
        val revealedSecret: String? = null, // shown exactly once
        // Detail
        val selected: WebsiteDto? = null,
        val commissions: List<CommissionDto> = emptyList(),
        val commissionEnabled: Boolean = false,
        val numberOrder: List<NumberOrderItem> = emptyList(),
        // Auto-synced active SIM numbers merged with checkout order/enable state
        val checkoutNumbers: List<ActiveNumberDto> = emptyList(),
        // Official (redirect-based) payment gateways configured for this website
        val officialGateways: List<OfficialGatewayDto> = emptyList(),
        val checkoutTabs: Map<String, CheckoutTabDto> = emptyMap(),
        val isSaving: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var autoSaveJob: Job? = null

    fun clearMessages() = _state.update { it.copy(error = null, infoMessage = null) }

    fun dismissSecretReveal() = _state.update { it.copy(revealedSecret = null, createdWebsite = null) }

    fun loadWebsites() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repo.listWebsites()
                .onSuccess { list -> _state.update { it.copy(isLoading = false, websites = list) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    /** Website Add Wizard — only domain (required) + optional name. */
    fun createWebsite(domain: String, name: String?) {
        if (domain.isBlank()) {
            _state.update { it.copy(error = "ডোমেইন লিখুন।") }
            return
        }
        _state.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            repo.createWebsite(domain.trim(), name?.trim()?.ifBlank { null })
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            isCreating = false,
                            createdWebsite = resp.website,
                            revealedSecret = resp.apiSecret,
                            infoMessage = resp.message
                        )
                    }
                    loadWebsites()
                }
                .onFailure { e -> _state.update { it.copy(isCreating = false, error = e.message) } }
        }
    }

    fun loadWebsiteDetail(id: Int) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repo.getWebsite(id)
                .onSuccess { detail ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            selected = detail.website,
                            commissions = detail.commissions,
                            numberOrder = detail.numberOrder,
                            checkoutNumbers = detail.activeNumbers,
                            checkoutTabs = detail.checkoutTabs ?: emptyMap()
                        )
                    }
                    // Commission menu lock state comes from the commissions endpoint too
                    repo.listCommissions(id).onSuccess { cl ->
                        _state.update { it.copy(commissionEnabled = cl.commissionEnabled, commissions = cl.commissions) }
                    }
                    repo.listOfficialGateways(id).onSuccess { gws ->
                        _state.update { it.copy(officialGateways = gws) }
                    }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun updateSettings(id: Int, request: UpdateWebsiteRequest, onDone: () -> Unit = {}) {
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repo.updateWebsite(id, request)
                .onSuccess { w ->
                    _state.update { it.copy(isSaving = false, selected = w, infoMessage = "সেটিংস সংরক্ষণ হয়েছে।") }
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun regenerateSecret(id: Int) {
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repo.regenerateSecret(id)
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            revealedSecret = resp.apiSecret,
                            selected = resp.website ?: it.selected,
                            infoMessage = resp.message
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun updateWebsiteInfo(id: Int, domain: String, name: String?, onDone: () -> Unit = {}) {
        if (domain.isBlank()) {
            _state.update { it.copy(error = "ডোমেইন লিখুন।") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repo.updateWebsite(
                id,
                UpdateWebsiteRequest(
                    domain = domain.trim(),
                    websiteName = name?.trim()?.ifBlank { null }
                )
            )
                .onSuccess { w ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            selected = if (it.selected?.id == id) w else it.selected,
                            websites = it.websites.map { site -> if (site.id == id) w else site },
                            infoMessage = "ওয়েবসাইট আপডেট হয়েছে।"
                        )
                    }
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun deleteWebsiteWithPin(id: Int, pin: String, onDone: () -> Unit) {
        if (pin.isBlank()) {
            _state.update { it.copy(error = "নিরাপত্তা PIN দিন।") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repo.deleteWebsite(id, pin)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            websites = it.websites.filter { w -> w.id != id },
                            infoMessage = "ওয়েবসাইট মুছে ফেলা হয়েছে।"
                        )
                    }
                    loadWebsites()
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    /** Local reorder for the checkout numbers list (drag/drop or move buttons). */
    fun moveCheckoutNumber(from: Int, to: Int, websiteId: Int? = null) {
        _state.update { s ->
            val list = s.checkoutNumbers.toMutableList()
            if (from in list.indices && to in list.indices) {
                val item = list.removeAt(from)
                list.add(to, item)
            }
            s.copy(checkoutNumbers = list)
        }
        websiteId?.let { scheduleSaveCheckoutNumbers(it) }
    }

    /** Toggle checkout-only visibility for a number (does NOT affect SMS reader). */
    fun toggleCheckoutNumber(methodId: Int, enabled: Boolean, websiteId: Int? = null) {
        _state.update { s ->
            s.copy(checkoutNumbers = s.checkoutNumbers.map {
                if (it.methodId == methodId) it.copy(enabled = enabled) else it
            })
        }
        websiteId?.let { scheduleSaveCheckoutNumbers(it) }
    }

    private fun scheduleSaveCheckoutNumbers(websiteId: Int) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(450)
            saveCheckoutNumbers(websiteId, silent = true)
        }
    }

    /** Persist the current checkout number order + enable/disable state. */
    fun saveCheckoutNumbers(id: Int, silent: Boolean = false) {
        val order = _state.value.checkoutNumbers.mapIndexed { idx, n ->
            NumberOrderItem(methodId = n.methodId, provider = n.provider, number = n.number, enabled = n.enabled, position = idx)
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repo.updateNumberOrder(id, order)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            infoMessage = if (silent) null else "চেকআউট নাম্বার সংরক্ষণ হয়েছে।"
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun upsertCommission(id: Int, request: UpsertCommissionRequest) {
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repo.upsertCommission(id, request)
                .onSuccess {
                    _state.update { it.copy(isSaving = false, infoMessage = "Commission সংরক্ষণ হয়েছে।") }
                    repo.listCommissions(id).onSuccess { cl ->
                        _state.update { s -> s.copy(commissionEnabled = cl.commissionEnabled, commissions = cl.commissions) }
                    }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun deleteCommission(id: Int, commissionId: Int) {
        viewModelScope.launch {
            repo.deleteCommission(id, commissionId)
                .onSuccess {
                    repo.listCommissions(id).onSuccess { cl ->
                        _state.update { s -> s.copy(commissionEnabled = cl.commissionEnabled, commissions = cl.commissions) }
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    // ── Official (redirect-based) payment gateways (Phase 6) ──────────────────
    fun upsertOfficialGateway(id: Int, provider: String, redirectUrl: String, displayName: String?) {
        if (redirectUrl.isBlank()) {
            _state.update { it.copy(error = "Redirect URL লিখুন।") }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repo.upsertOfficialGateway(
                id,
                UpsertOfficialGatewayRequest(
                    provider = provider,
                    displayName = displayName?.ifBlank { null },
                    redirectUrlTemplate = redirectUrl.trim(),
                    isActive = true
                )
            )
                .onSuccess {
                    _state.update { it.copy(isSaving = false, infoMessage = "Official gateway সংরক্ষণ হয়েছে।") }
                    repo.listOfficialGateways(id).onSuccess { gws -> _state.update { s -> s.copy(officialGateways = gws) } }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun deleteOfficialGateway(id: Int, gatewayId: Int) {
        viewModelScope.launch {
            repo.deleteOfficialGateway(id, gatewayId)
                .onSuccess {
                    repo.listOfficialGateways(id).onSuccess { gws -> _state.update { s -> s.copy(officialGateways = gws) } }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }
}
