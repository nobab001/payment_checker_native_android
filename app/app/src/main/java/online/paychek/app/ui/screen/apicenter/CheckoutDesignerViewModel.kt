package online.paychek.app.ui.screen.apicenter

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Collections

class CheckoutDesignerViewModel : ViewModel() {

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
        _uiState.update { it.copy(isLoading = true, statusMessage = null) }
        
        // In a full implementation, we make a network call to update layout config on server.
        // We simulate success here.
        kotlinx.coroutines.GlobalScope.run {
            // Simulated delay
            _uiState.update {
                it.copy(
                    isLoading = false,
                    statusMessage = "গেটওয়ে লেআউট সফলভাবে সংরক্ষণ করা হয়েছে!"
                )
            }
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
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

data class CheckoutBlock(
    val id: String,
    val name: String,
    val providerTag: String,
    val isEnabled: Boolean
)
