package online.paychek.app.ui.screen.gateway

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.*
import online.paychek.app.utils.SecurePreferences

// =============================================================================
// UI State
// =============================================================================
data class GatewayUiState(
    val methods:          List<GatewayMethod> = emptyList(),
    val sim1Enabled:      Boolean             = true,
    val sim2Enabled:      Boolean             = true,
    val isLoading:        Boolean             = true,
    val isSaving:         Boolean             = false,  // Priority save চলছে
    val errorMessage:     String?             = null,
    val successMessage:   String?             = null,
    // Bottom Sheet
    val editingMethod:    GatewayMethod?      = null,   // null → sheet বন্ধ
    val editNumber:       String              = "",
    val editDisplayName:  String             = ""
)

// =============================================================================
// ViewModel
// =============================================================================
class GatewayCustomizerViewModel(application: Application) : AndroidViewModel(application) {

    private val api   = RetrofitClient.gatewayApiService
    private val prefs = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(GatewayUiState())
    val state: StateFlow<GatewayUiState> = _state.asStateFlow()

    // 1.5s debounce job — drag শেষে priority সেভের জন্য
    private var saveJob: Job? = null

    init {
        val sim1 = prefs.getBoolean(AppConfig.KEY_SIM1_ENABLED, true)
        val sim2 = prefs.getBoolean(AppConfig.KEY_SIM2_ENABLED, true)
        _state.update { it.copy(sim1Enabled = sim1, sim2Enabled = sim2, isLoading = true) }
        loadGatewayMethods()
    }

    private fun saveMethodsToCache(methods: List<GatewayMethod>) {
        try {
            val json = com.google.gson.Gson().toJson(methods)
            prefs.edit().putString(AppConfig.KEY_GATEWAY_METHODS_CACHE, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // API থেকে Method List লোড করা
    // ─────────────────────────────────────────────────────────────────────────
    fun loadGatewayMethods() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")

            runCatching { api.getGatewayMethods("Bearer $token") }
                .onSuccess { res ->
                    if (res.isSuccessful && res.body()?.success == true) {
                        val sorted = (res.body()!!.data).sortedBy { it.priority }
                        _state.update { it.copy(methods = sorted, isLoading = false) }
                        saveMethodsToCache(sorted)
                    } else {
                        setError("মেথড লোড ব্যর্থ হয়েছে (${res.code()})")
                    }
                }
                .onFailure { setError("নেটওয়ার্ক সমস্যা: ${it.message}") }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drag & Drop — Local Reorder (তাৎক্ষণিক UI update)
    // ─────────────────────────────────────────────────────────────────────────
    fun onReorder(fromIndex: Int, toIndex: Int) {
        val current = _state.value.methods.toMutableList()
        if (fromIndex == toIndex ||
            fromIndex < 0 || toIndex < 0 ||
            fromIndex >= current.size || toIndex >= current.size) return

        // Local swap
        current.add(toIndex, current.removeAt(fromIndex))
        _state.update { it.copy(methods = current) }

        // Debounced server save — drag চলাকালীন বারবার call বাতিল হয়
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1500L) // ১.৫ সেকেন্ড কোনো drag না হলে save
            savePriorityOrder(current)
        }
    }

    // Priority ক্রম Backend-এ সেভ করা
    private suspend fun savePriorityOrder(methods: List<GatewayMethod>) {
        _state.update { it.copy(isSaving = true) }
        val token = getToken() ?: return

        // priority = তালিকায় অবস্থান + 1 (1-based)
        val items = methods.mapIndexed { idx, m ->
            PriorityItem(id = m.id, priority = idx + 1)
        }

        runCatching {
            api.updatePriority("Bearer $token", UpdatePriorityRequest(items))
        }.onSuccess { res ->
            _state.update {
                it.copy(
                    isSaving       = false,
                    successMessage = if (res.isSuccessful) "ক্রম সেভ হয়েছে ✓" else null
                )
            }
            // Success message ২ সেকেন্ড পর সরিয়ে দেওয়া
            viewModelScope.launch {
                delay(2000)
                _state.update { it.copy(successMessage = null) }
            }
        }.onFailure {
            _state.update { it.copy(isSaving = false) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Individual Method Toggle (ON/OFF)
    // ─────────────────────────────────────────────────────────────────────────
    fun toggleMethod(method: GatewayMethod) {
        val newEnabled = if (method.isEnabled == 1) 0 else 1

        // 🧅 Optimistic update (UI + Local Cache)
        _state.update { current ->
            val updated = current.methods.map {
                if (it.id == method.id) it.copy(isEnabled = newEnabled) else it
            }
            saveMethodsToCache(updated)
            current.copy(methods = updated)
        }

        // API call
        viewModelScope.launch {
            val token = getToken() ?: return@launch
            runCatching {
                api.toggleMethod("Bearer $token", method.id, ToggleRequest(newEnabled))
            }.onFailure {
                // Rollback on failure
                _state.update { current ->
                    val updated = current.methods.map {
                        if (it.id == method.id) it.copy(isEnabled = method.isEnabled) else it
                    }
                    saveMethodsToCache(updated)
                    current.copy(methods = updated)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SIM Master Toggle — পুরো SIM-১ বা SIM-২ চালু/বন্ধ
    // ─────────────────────────────────────────────────────────────────────────
    fun toggleSim(simSlot: Int) {
        val isCurrentlyEnabled = if (simSlot == 1) _state.value.sim1Enabled
                                 else               _state.value.sim2Enabled
        val newValue = !isCurrentlyEnabled

        _state.update {
            if (simSlot == 1) {
                prefs.edit().putBoolean(AppConfig.KEY_SIM1_ENABLED, newValue).apply()
                it.copy(sim1Enabled = newValue)
            } else {
                prefs.edit().putBoolean(AppConfig.KEY_SIM2_ENABLED, newValue).apply()
                it.copy(sim2Enabled = newValue)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bottom Sheet — সম্পাদনা খোলা/বন্ধ করা
    // ─────────────────────────────────────────────────────────────────────────
    fun openEditSheet(method: GatewayMethod) {
        _state.update {
            it.copy(
                editingMethod   = method,
                editNumber      = method.number ?: "",
                editDisplayName = method.displayName ?: ""
            )
        }
    }

    fun closeEditSheet() {
        _state.update { it.copy(editingMethod = null) }
    }

    fun onEditNumberChanged(value: String) {
        _state.update { it.copy(editNumber = value) }
    }

    fun onEditDisplayNameChanged(value: String) {
        _state.update { it.copy(editDisplayName = value) }
    }

    fun saveMethodEdit() {
        val method = _state.value.editingMethod ?: return
        val number      = _state.value.editNumber.trim()
        val displayName = _state.value.editDisplayName.trim()

        viewModelScope.launch {
            val token = getToken() ?: return@launch
            runCatching {
                api.updateMethod(
                    "Bearer $token", method.id,
                    UpdateMethodRequest(
                        number      = number.ifEmpty { null },
                        displayName = displayName.ifEmpty { null }
                    )
                )
            }.onSuccess { res ->
                if (res.isSuccessful) {
                    // লোকাল আপডেট
                    _state.update { current ->
                        val updated = current.methods.map {
                            if (it.id == method.id)
                                it.copy(number = number.ifEmpty { null },
                                        displayName = displayName.ifEmpty { null })
                            else it
                        }
                        saveMethodsToCache(updated)
                        current.copy(
                            methods = updated,
                            editingMethod = null,
                            successMessage = "আপডেট সফল হয়েছে ✓"
                        )
                    }
                    viewModelScope.launch {
                        delay(2000)
                        _state.update { it.copy(successMessage = null) }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun getToken(): String? {
        val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
        return token.ifEmpty { null }
    }

    private fun setError(msg: String) {
        _state.update { it.copy(isLoading = false, isSaving = false, errorMessage = msg) }
    }
}
