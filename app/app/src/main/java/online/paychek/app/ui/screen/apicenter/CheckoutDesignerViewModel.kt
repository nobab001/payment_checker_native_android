package online.paychek.app.ui.screen.apicenter

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.GatewayMethod
import java.util.Collections

class CheckoutDesignerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(CheckoutDesignerUiState())
    val uiState: StateFlow<CheckoutDesignerUiState> = _uiState.asStateFlow()

    init {
        // Load default layout blocks matching the blueprint
        val defaultBlocks = listOf(
            CheckoutBlock("bkash", "bKash (বিকাশ) পেমেন্ট", "bKash", true),
            CheckoutBlock("nagad", "Nagad (নগদ) পেমেন্ট", "Nagad", true),
            CheckoutBlock("rocket", "Rocket (রকেট) পেমেন্ট", "Rocket", true),
            CheckoutBlock("upay", "Upay (উপায়) পেমেন্ট", "Upay", false)
        )
        _uiState.update { it.copy(blocks = defaultBlocks) }
        loadGatewayMethodsCache()
    }

    // SharedPreferences ক্যাশ থেকে গেটওয়ে মেথড ও নাম্বার লোড করা
    fun loadGatewayMethodsCache() {
        val json = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsCache(getApplication())
        val type = object : com.google.gson.reflect.TypeToken<List<GatewayMethod>>() {}.type
        val methods: List<GatewayMethod> = try {
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }

        // শুধুমাত্র সচল (isEnabled == 1) এবং যেগুলোতে নাম্বার ইনপুট দেওয়া আছে
        val active = methods.filter { it.isEnabled == 1 && !it.number.isNullOrBlank() }

        _uiState.update {
            it.copy(
                activeMethods = active
            )
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
        if (tabIndex == 1) {
            // Preview মোডে যাওয়ার সময় ফ্রেশ ক্যাশ লোড করা
            loadGatewayMethodsCache()
        }
    }

    fun toggleBlock(blockId: String) {
        _uiState.update { state ->
            val updated = state.blocks.map { block ->
                if (block.id == blockId) block.copy(isEnabled = !block.isEnabled) else block
            }
            state.copy(blocks = updated)
        }
    }

    fun moveBlockUp(index: Int) {
        if (index <= 0 || index >= _uiState.value.blocks.size) return
        _uiState.update { state ->
            val mutableList = state.blocks.toMutableList()
            Collections.swap(mutableList, index, index - 1)
            state.copy(blocks = mutableList)
        }
    }

    fun moveBlockDown(index: Int) {
        if (index < 0 || index >= _uiState.value.blocks.size - 1) return
        _uiState.update { state ->
            val mutableList = state.blocks.toMutableList()
            Collections.swap(mutableList, index, index + 1)
            state.copy(blocks = mutableList)
        }
    }

    fun updateSimConfig(
        sim1Method: String,
        sim1Number: String,
        sim2Method: String,
        sim2Number: String
    ) {
        _uiState.update {
            it.copy(
                sim1Method = sim1Method,
                sim1Number = sim1Number,
                sim2Method = sim2Method,
                sim2Number = sim2Number
            )
        }
    }

    fun saveLayout() {
        _uiState.update {
            it.copy(
                isLoading = false,
                statusMessage = "গেটওয়ে লেআউট সফলভাবে সংরক্ষণ করা হয়েছে!"
            )
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}

data class CheckoutDesignerUiState(
    val blocks: List<CheckoutBlock> = emptyList(),
    val sim1Method: String = "bKash",
    val sim1Number: String = "01700000000",
    val sim2Method: String = "Nagad",
    val sim2Number: String = "01800000000",
    val activeMethods: List<GatewayMethod> = emptyList(), // SharedPreferences থেকে সচল কনফিগ
    val selectedTab: Int = 0, // ০ = Edit Mode, ১ = Customer Preview Mode
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

data class CheckoutBlock(
    val id: String,
    val name: String,
    val providerTag: String,
    val isEnabled: Boolean
)
