package online.paychek.app.ui.screen.auth.pin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.VerifyPinRequest
import online.paychek.app.utils.SecurePreferences

/**
 * SecurityGateViewModel — v2.0.0 Pure Overwrite Engine
 * ============================================================================
 * পূর্ববর্তী string-append লজিকটি সম্পূর্ণ `List<Char?>` ও cursorIndex
 * ভিত্তিক একটি fixed-length 6-cell overwrite engine দিয়ে প্রতিস্থাপিত হয়েছে।
 *
 * আচরণ:
 * - ইউজার নির্দিষ্ট বক্সে ক্লিক করলে cursor সেখানে যায়
 * - সংখ্যা টাইপ করলে cursor position-এ overwrite হয়, cursor এক ঘর এগোয়
 * - Delete চাপলে cursor এক ঘর পিছিয়ে যায় এবং সেই cell clear হয়
 * - সব ৬টি ঘর ভরলে স্বয়ংক্রিয়ভাবে verify হয়
 * ============================================================================
 */
class SecurityGateViewModel : ViewModel() {

    companion object {
        const val PIN_LENGTH = 6
    }

    data class SecurityGateState(
        /** Fixed 6-cell list — null মানে ঘরটি ফাঁকা */
        val cells: List<Char?> = List(PIN_LENGTH) { null },
        /** বর্তমানে active cursor এর position (0..5) */
        val cursorIndex: Int = 0,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isUnlocked: Boolean = false
    ) {
        /** বর্তমান cells থেকে PIN string তৈরি (verify এর জন্য) */
        val pin: String get() = cells.joinToString("") { it?.toString() ?: "" }
        /** সব ঘর ভরা কিনা */
        val isFull: Boolean get() = cells.all { it != null }
    }

    private val _uiState = MutableStateFlow(SecurityGateState())
    val uiState: StateFlow<SecurityGateState> = _uiState.asStateFlow()

    // ── পুরনো API — backward compat এর জন্য রাখা হয়েছে ──────────────────────
    // (পুরনো caller কোড break হবে না, কিন্তু নতুন UI এ ব্যবহার করা হবে না)
    @Deprecated("Use overwriteDigitAtCursor() instead", ReplaceWith("overwriteDigitAtCursor(digit.first(), context, onUnlockSuccess)"))
    fun appendDigit(digit: String, context: Context, onUnlockSuccess: () -> Unit) {
        if (digit.isNotEmpty() && !_uiState.value.isLoading) {
            overwriteDigitAtCursor(digit.first(), context, onUnlockSuccess)
        }
    }

    // ── নতুন Overwrite Engine API ──────────────────────────────────────────────

    /**
     * cursor বরাবর digit লিখো এবং cursor এক ঘর এগিয়ে দাও।
     * সব ঘর ভরলে auto-verify হবে।
     */
    fun overwriteDigitAtCursor(digit: Char, context: Context, onUnlockSuccess: () -> Unit) {
        if (_uiState.value.isLoading) return
        if (!digit.isDigit()) return

        val state = _uiState.value
        val idx = state.cursorIndex.coerceIn(0, PIN_LENGTH - 1)

        val newCells = state.cells.toMutableList()
        newCells[idx] = digit

        val nextCursor = if (idx < PIN_LENGTH - 1) idx + 1 else idx

        _uiState.update {
            it.copy(
                cells       = newCells,
                cursorIndex = nextCursor,
                errorMessage = null
            )
        }

        // সব ঘর ভরলে auto-verify
        if (newCells.all { it != null }) {
            verifyPinOnBackend(context, onUnlockSuccess)
        }
    }

    /**
     * নির্দিষ্ট cell-এ cursor নিয়ে যাও (tappable box এ click করলে)।
     */
    fun moveCursorTo(index: Int) {
        if (_uiState.value.isLoading) return
        val safeIndex = index.coerceIn(0, PIN_LENGTH - 1)
        _uiState.update { it.copy(cursorIndex = safeIndex, errorMessage = null) }
    }

    /**
     * Delete: cursor এক ঘর পিছিয়ে সেই cell clear করো।
     * যদি cursor শুরুতে থাকে এবং সেই ঘর ভরা থাকে, শুধু সেই ঘর clear করো।
     */
    fun deleteDigit() {
        if (_uiState.value.isLoading) return

        val state = _uiState.value
        val newCells = state.cells.toMutableList()
        var newCursor = state.cursorIndex

        if (newCursor > 0 && newCells[newCursor] == null) {
            // cursor এর আগের ঘরে গিয়ে clear করো
            newCursor--
            newCells[newCursor] = null
        } else if (newCells[newCursor] != null) {
            // cursor এর ঘরটি clear করো
            newCells[newCursor] = null
        } else if (newCursor > 0) {
            newCursor--
            newCells[newCursor] = null
        }

        _uiState.update {
            it.copy(
                cells       = newCells,
                cursorIndex = newCursor,
                errorMessage = null
            )
        }
    }

    /** সম্পূর্ণ PIN clear করো এবং cursor শুরুতে নিয়ে যাও। */
    fun clearPin() {
        _uiState.update {
            it.copy(
                cells       = List(PIN_LENGTH) { null },
                cursorIndex = 0,
                errorMessage = null
            )
        }
    }

    /** বাইরে থেকে verify trigger করার জন্য (প্রয়োজনে) */
    fun verifyPin(context: Context, onUnlockSuccess: () -> Unit) {
        verifyPinOnBackend(context, onUnlockSuccess)
    }

    private fun verifyPinOnBackend(context: Context, onUnlockSuccess: () -> Unit) {
        val pinCode = _uiState.value.pin
        if (pinCode.length < 4 || pinCode.length > 6) return

        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
        if (token.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "সেশন অবৈধ। অনুগ্রহ করে আবার লগইন করুন।") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.authApiService.verifyPin(
                    token   = "Bearer $token",
                    request = VerifyPinRequest(pin = pinCode)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.update { it.copy(isLoading = false, isUnlocked = true) }
                    onUnlockSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    val rawMsg = try {
                        val gson = com.google.gson.Gson()
                        val map  = gson.fromJson(errorBody, Map::class.java)
                        map["message"] as? String ?: map["error"] as? String ?: "পিনটি সঠিক নয়।"
                    } catch (e: Exception) {
                        "পিনটি সঠিক নয়।"
                    }
                    _uiState.update {
                        it.copy(
                            isLoading   = false,
                            cells       = List(PIN_LENGTH) { null },
                            cursorIndex = 0,
                            errorMessage = rawMsg
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading   = false,
                        cells       = List(PIN_LENGTH) { null },
                        cursorIndex = 0,
                        errorMessage = "ভেরিফিকেশন ব্যর্থ: ${e.localizedMessage ?: "নেটওয়ার্ক এরর"}"
                    )
                }
            }
        }
    }
}
