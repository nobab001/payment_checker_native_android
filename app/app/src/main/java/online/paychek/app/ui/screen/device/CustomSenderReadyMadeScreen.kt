package online.paychek.app.ui.screen.device

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.AddCustomSenderRequest
import online.paychek.app.services.sms.SmsRoutingEngine
import online.paychek.app.utils.DeviceIdHelper
import online.paychek.app.utils.SecurePreferences

/** Kept for admin checkout reorder only (custom ready-made catalog removed). */
val ReadyMadeTabs = listOf(
    "ROBI" to "Robi",
    "AIRTEL" to "Airtel",
    "GP" to "GP",
    "BL" to "BL",
    "TELETAK" to "Teletalk",
    "BANK" to "Bank",
    "OTHERS" to "Others"
)

data class AllOnlyUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val allAlreadyEnabled: Boolean = false
)

/** User Device [+]: ALL archive only (block list is admin-global). */
class CustomSenderAllBlockViewModel : ViewModel() {
    private val _state = MutableStateFlow(AllOnlyUiState())
    val state: StateFlow<AllOnlyUiState> = _state.asStateFlow()

    fun loadSlotState(
        context: android.content.Context,
        simSlot: Int,
        targetDeviceId: String? = null
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
            if (token.isBlank()) {
                _state.update { it.copy(isLoading = false, error = "লগইন সেশন নেই") }
                return@launch
            }
            val localDeviceId = DeviceIdHelper.getHashedAndroidId(context)
            val headerDeviceId = targetDeviceId?.takeIf { it.isNotBlank() } ?: localDeviceId

            runCatching {
                RetrofitClient.gatewayApiService.getGatewayMethods(
                    token = "Bearer $token",
                    deviceId = headerDeviceId
                )
            }.onSuccess { methodsRes ->
                val methods = if (methodsRes.isSuccessful) {
                    methodsRes.body()?.data.orEmpty()
                } else emptyList()
                methodsRes.body()?.globalBlockedSenders?.let { blocked ->
                    online.paychek.app.data.local.prefs.PrefsHelper
                        .setGlobalBlockedSenders(context, blocked)
                }
                val allOn = methods.any { m ->
                    m.simSlot == simSlot &&
                        m.isEnabled == 1 &&
                        (m.isParseable ?: 1) == 0 &&
                        SmsRoutingEngine.isAllSenderPolicy(m)
                }
                _state.update {
                    it.copy(isLoading = false, allAlreadyEnabled = allOn, error = null)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "নেটওয়ার্ক সমস্যা")
                }
            }
        }
    }

    fun saveAllPolicy(
        context: android.content.Context,
        simSlot: Int,
        targetDeviceId: String?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
            if (token.isBlank()) {
                _state.update { it.copy(isSaving = false, error = "লগইন সেশন নেই") }
                return@launch
            }
            val localDeviceId = DeviceIdHelper.getHashedAndroidId(context)
            val headerDeviceId = targetDeviceId?.takeIf { it.isNotBlank() } ?: localDeviceId
            val request = AddCustomSenderRequest(
                simSlot = simSlot,
                senderId = SmsRoutingEngine.ALL_SENDER_ID,
                deviceId = headerDeviceId,
                createPersonal = true
            )
            runCatching {
                RetrofitClient.gatewayApiService.addCustomSender(
                    token = "Bearer $token",
                    request = request,
                    deviceId = headerDeviceId
                )
            }.onSuccess { res ->
                _state.update { it.copy(isSaving = false) }
                if (res.isSuccessful && res.body()?.success == true) {
                    val methods = res.body()?.data
                    res.body()?.globalBlockedSenders?.let { blocked ->
                        online.paychek.app.data.local.prefs.PrefsHelper
                            .setGlobalBlockedSenders(context, blocked)
                    }
                    val isLocalTarget = targetDeviceId.isNullOrBlank() ||
                        targetDeviceId == localDeviceId
                    if (isLocalTarget && methods != null) {
                        runCatching {
                            val json = online.paychek.app.utils.GsonUtils.gson.toJson(methods)
                            online.paychek.app.data.local.prefs.PrefsHelper
                                .setGatewayMethodsCache(context, json)
                        }
                    }
                    _state.update { it.copy(allAlreadyEnabled = true) }
                    onDone()
                } else {
                    val msg = online.paychek.app.utils.ApiErrorParser.parse(res.errorBody()?.string())
                        ?: "যোগ করতে ব্যর্থ (${res.code()})"
                    _state.update { it.copy(error = msg) }
                }
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.message ?: "নেটওয়ার্ক সমস্যা") }
            }
        }
    }
}

@Composable
fun CustomSenderAllBlockDialog(
    simSlot: Int,
    targetDeviceId: String? = null,
    onDismiss: () -> Unit,
    onChanged: () -> Unit = {},
    viewModel: CustomSenderAllBlockViewModel = viewModel(key = "all-only-sim-$simSlot-${targetDeviceId ?: "local"}")
) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(simSlot, targetDeviceId) {
        viewModel.loadSlotState(context, simSlot, targetDeviceId)
    }

    val bg = MaterialTheme.colorScheme.background
    val card = if (bg == Color(0xFF0B0E14)) Color(0xFF151A23) else Color.White
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val muted = textPrimary.copy(alpha = 0.55f)
    val accent = Color(0xFF22D3EE)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = card,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "কাস্টম সেন্ডার — SIM $simSlot",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = textPrimary
                )

                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.CenterHorizontally),
                        color = accent,
                        strokeWidth = 3.dp
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ALL", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = accent)
                        Text(
                            text = "সকল এসএমএস আর্কাইভ বক্সে রিসিভ",
                            fontSize = 12.sp,
                            color = muted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (uiState.allAlreadyEnabled) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.saveAllPolicy(context, simSlot, targetDeviceId) {
                            Toast.makeText(context, "SIM $simSlot — ALL সংরক্ষিত", Toast.LENGTH_SHORT).show()
                            onChanged()
                            onDismiss()
                        }
                    },
                    enabled = !uiState.isSaving && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF0B0E14)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF0B0E14)
                        )
                    } else {
                        Text("সাবমিট", fontWeight = FontWeight.Bold)
                    }
                }

                uiState.error?.let { err ->
                    Text(err, color = Color(0xFFEF4444), fontSize = 12.sp)
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("বন্ধ", color = muted)
                }
            }
        }
    }
}
