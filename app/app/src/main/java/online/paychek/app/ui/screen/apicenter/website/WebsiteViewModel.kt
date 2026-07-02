package online.paychek.app.ui.screen.apicenter.website

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.paychek.app.data.remote.dto.CommissionDto
import online.paychek.app.data.remote.dto.NumberOrderItem
import online.paychek.app.data.remote.dto.UpdateWebsiteRequest
import online.paychek.app.data.remote.dto.UpsertCommissionRequest
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
        val isSaving: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

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
                            numberOrder = detail.numberOrder
                        )
                    }
                    // Commission menu lock state comes from the commissions endpoint too
                    repo.listCommissions(id).onSuccess { cl ->
                        _state.update { it.copy(commissionEnabled = cl.commissionEnabled, commissions = cl.commissions) }
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

    fun deleteWebsite(id: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteWebsite(id)
                .onSuccess {
                    _state.update { it.copy(infoMessage = "ওয়েবসাইট মুছে ফেলা হয়েছে।") }
                    loadWebsites()
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun saveNumberOrder(id: Int, order: List<NumberOrderItem>) {
        viewModelScope.launch {
            repo.updateNumberOrder(id, order)
                .onSuccess { saved -> _state.update { it.copy(numberOrder = saved) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
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
}
