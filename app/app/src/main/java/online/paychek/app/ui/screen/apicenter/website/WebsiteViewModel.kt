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
import online.paychek.app.data.remote.dto.CampaignDto
import online.paychek.app.data.remote.dto.CheckoutTabDto
import online.paychek.app.data.remote.dto.CommissionDto
import online.paychek.app.data.remote.dto.IncentiveTemplateDto
import online.paychek.app.data.remote.dto.CreateMerchantAccountRequest
import online.paychek.app.data.remote.dto.MerchantAccountDto
import online.paychek.app.data.remote.dto.NumberOrderItem
import online.paychek.app.data.remote.dto.ProviderBrandingDto
import online.paychek.app.data.remote.dto.UpdateMerchantAccountRequest
import online.paychek.app.data.remote.dto.UpdateWebsiteRequest
import online.paychek.app.data.remote.dto.UpsertCampaignRequest
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
        val campaigns: List<CampaignDto> = emptyList(),
        val incentiveTemplates: List<IncentiveTemplateDto> = emptyList(),
        val numberOrder: List<NumberOrderItem> = emptyList(),
        // Auto-synced active SIM numbers merged with checkout order/enable state
        val checkoutNumbers: List<ActiveNumberDto> = emptyList(),
        // Live merchant accounts (API credentials — multi-account per provider)
        val merchantAccounts: List<MerchantAccountDto> = emptyList(),
        val checkoutTabs: Map<String, CheckoutTabDto> = emptyMap(),
        val providerBranding: Map<String, ProviderBrandingDto> = emptyMap(),
        val isSaving: Boolean = false,
        val logoUploading: Boolean = false,
        val logoUploadError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var autoSaveJob: Job? = null
    private var lastLogoUploadBytes: ByteArray? = null

    fun clearMessages() = _state.update { it.copy(error = null, infoMessage = null, logoUploadError = null) }

    fun dismissSecretReveal() = _state.update { it.copy(revealedSecret = null, createdWebsite = null) }

    fun loadWebsites() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repo.listWebsites()
                .onSuccess { list -> _state.update { it.copy(isLoading = false, websites = list) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    /** Website Add Wizard — domain + name + purpose (locks on create). */
    fun createWebsite(domain: String, name: String?, purpose: String) {
        if (domain.isBlank()) {
            _state.update { it.copy(error = "ডোমেইন লিখুন।") }
            return
        }
        if (purpose !in listOf("add_balance", "payment", "both")) {
            _state.update { it.copy(error = "ওয়েবসাইটের উদ্দেশ্য সিলেক্ট করুন।") }
            return
        }
        _state.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            repo.createWebsite(domain.trim(), name?.trim()?.ifBlank { null }, purpose)
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
                            incentiveTemplates = detail.incentiveTemplates,
                            checkoutTabs = detail.checkoutTabs ?: emptyMap(),
                            providerBranding = detail.providerBranding ?: emptyMap()
                        )
                    }
                    // Commission menu lock state comes from the commissions endpoint too
                    repo.listCommissions(id).onSuccess { cl ->
                        _state.update {
                            it.copy(
                                commissionEnabled = cl.commissionEnabled,
                                commissions = cl.commissions,
                                incentiveTemplates = cl.incentiveTemplates.ifEmpty { it.incentiveTemplates }
                            )
                        }
                    }
                    repo.listMerchantAccounts(id).onSuccess { accts ->
                        _state.update { it.copy(merchantAccounts = accts) }
                    }
                    repo.listCampaigns(id).onSuccess { camps ->
                        _state.update { it.copy(campaigns = camps) }
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
                    _state.update {
                        it.copy(
                            isSaving = false,
                            selected = w,
                            websites = it.websites.map { site -> if (site.id == id) w else site },
                            infoMessage = "সেটিংস সংরক্ষণ হয়েছে।"
                        )
                    }
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    /** Upload a cropped company logo (PNG bytes) via multipart — replaces URL-based branding. */
    fun uploadWebsiteLogo(websiteId: Int, pngBytes: ByteArray) {
        lastLogoUploadBytes = pngBytes
        _state.update { it.copy(logoUploading = true, logoUploadError = null, error = null) }
        viewModelScope.launch {
            repo.uploadWebsiteLogo(websiteId, pngBytes)
                .onSuccess { w ->
                    _state.update {
                        it.copy(
                            logoUploading = false,
                            logoUploadError = null,
                            selected = w,
                            websites = it.websites.map { site -> if (site.id == websiteId) w else site },
                            infoMessage = "কোম্পানি লোগো আপলোড হয়েছে।"
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(logoUploading = false, logoUploadError = e.message ?: "লোগো আপলোড ব্যর্থ হয়েছে।")
                    }
                }
        }
    }

    fun retryLogoUpload(websiteId: Int) {
        val bytes = lastLogoUploadBytes ?: return
        uploadWebsiteLogo(websiteId, bytes)
    }

    fun deleteWebsiteLogo(websiteId: Int) {
        _state.update { it.copy(logoUploading = true, logoUploadError = null, error = null) }
        viewModelScope.launch {
            repo.deleteWebsiteLogo(websiteId)
                .onSuccess { w ->
                    lastLogoUploadBytes = null
                    _state.update {
                        it.copy(
                            logoUploading = false,
                            selected = w,
                            websites = it.websites.map { site -> if (site.id == websiteId) w else site },
                            infoMessage = "কোম্পানি লোগো মুছে ফেলা হয়েছে।"
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(logoUploading = false, logoUploadError = e.message ?: "লোগো মুছতে ব্যর্থ হয়েছে।")
                    }
                }
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
                        _state.update { s ->
                            s.copy(
                                commissionEnabled = cl.commissionEnabled,
                                commissions = cl.commissions,
                                incentiveTemplates = cl.incentiveTemplates.ifEmpty { s.incentiveTemplates }
                            )
                        }
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
                        _state.update { s ->
                            s.copy(
                                commissionEnabled = cl.commissionEnabled,
                                commissions = cl.commissions,
                                incentiveTemplates = cl.incentiveTemplates.ifEmpty { s.incentiveTemplates }
                            )
                        }
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    // ── Campaign / Extra incentives (amount-range) ────────────────────────────
    fun upsertCampaign(id: Int, request: UpsertCampaignRequest) {
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repo.upsertCampaign(id, request)
                .onSuccess {
                    _state.update { it.copy(isSaving = false, infoMessage = "ক্যাম্পেইন সংরক্ষণ হয়েছে।") }
                    repo.listCampaigns(id).onSuccess { camps ->
                        _state.update { s -> s.copy(campaigns = camps) }
                    }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun deleteCampaign(id: Int, campaignId: Int) {
        viewModelScope.launch {
            repo.deleteCampaign(id, campaignId)
                .onSuccess {
                    repo.listCampaigns(id).onSuccess { camps ->
                        _state.update { s -> s.copy(campaigns = camps) }
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    // ── Live merchant accounts (API credentials) ──────────────────────────────
    private fun refreshMerchantAccounts(websiteId: Int) {
        viewModelScope.launch {
            repo.listMerchantAccounts(websiteId).onSuccess { list ->
                _state.update { it.copy(merchantAccounts = list) }
            }
        }
    }

    fun createMerchantAccount(websiteId: Int, request: CreateMerchantAccountRequest) {
        if (request.merchantName.isBlank()) {
            _state.update { it.copy(error = "মার্চেন্ট নাম লিখুন।") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repo.createMerchantAccount(websiteId, request)
                .onSuccess {
                    _state.update { it.copy(isSaving = false, infoMessage = "মার্চেন্ট অ্যাকাউন্ট যোগ হয়েছে।") }
                    refreshMerchantAccounts(websiteId)
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun updateMerchantAccount(websiteId: Int, accountId: Int, request: UpdateMerchantAccountRequest) {
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repo.updateMerchantAccount(websiteId, accountId, request)
                .onSuccess {
                    _state.update { it.copy(isSaving = false, infoMessage = "মার্চেন্ট অ্যাকাউন্ট আপডেট হয়েছে।") }
                    refreshMerchantAccounts(websiteId)
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun toggleMerchantAccount(websiteId: Int, accountId: Int, active: Boolean) {
        viewModelScope.launch {
            repo.toggleMerchantAccount(websiteId, accountId, active)
                .onSuccess { refreshMerchantAccounts(websiteId) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun setDefaultMerchantAccount(websiteId: Int, accountId: Int) {
        viewModelScope.launch {
            repo.setDefaultMerchantAccount(websiteId, accountId)
                .onSuccess { refreshMerchantAccounts(websiteId) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun duplicateMerchantAccount(websiteId: Int, accountId: Int) {
        viewModelScope.launch {
            repo.duplicateMerchantAccount(websiteId, accountId)
                .onSuccess {
                    _state.update { it.copy(infoMessage = "অ্যাকাউন্ট ডুপ্লিকেট হয়েছে — ক্রেডেনশিয়াল আবার দিন।") }
                    refreshMerchantAccounts(websiteId)
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteMerchantAccount(websiteId: Int, accountId: Int) {
        viewModelScope.launch {
            repo.deleteMerchantAccount(websiteId, accountId)
                .onSuccess { refreshMerchantAccounts(websiteId) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun uploadMerchantAccountLogo(websiteId: Int, accountId: Int, pngBytes: ByteArray) {
        viewModelScope.launch {
            repo.uploadMerchantAccountLogo(websiteId, accountId, pngBytes)
                .onSuccess {
                    _state.update { it.copy(infoMessage = "মার্চেন্ট লোগো আপলোড হয়েছে।") }
                    refreshMerchantAccounts(websiteId)
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }
}
