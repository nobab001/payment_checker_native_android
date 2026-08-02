package online.paychek.app.ui.screen.device

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

/** Admin archive category tabs (Persistent-0 official catalog). Device UI uses ALL only. */
val ReadyMadeTabs = listOf(
    "ROBI" to "Robi",
    "AIRTEL" to "Airtel",
    "GP" to "GP",
    "BL" to "BL",
    "TELETAK" to "Teletalk",
    "BANK" to "Bank",
    "OTHERS" to "Others"
)

data class ReadyMadeUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val allAlreadyEnabled: Boolean = false
)

/**
 * Device Persistent-0 path: SIM [+] → ALL → Save.
 * Persistent-1 templates remain on DeviceScreen unchanged.
 */
class CustomSenderReadyMadeViewModel : ViewModel() {
    private val _state = MutableStateFlow(ReadyMadeUiState())
    val state: StateFlow<ReadyMadeUiState> = _state.asStateFlow()

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
                val allOn = if (methodsRes.isSuccessful) {
                    methodsRes.body()?.data.orEmpty().any { m ->
                        m.simSlot == simSlot &&
                            m.isEnabled == 1 &&
                            (m.isParseable ?: 1) == 0 &&
                            SmsRoutingEngine.isAllSenderPolicy(m)
                    }
                } else false
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
                officialTemplateId = null,
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
                    // Keep local routing cache in sync so Guard-1/2 see ALL immediately.
                    val isLocalTarget = targetDeviceId.isNullOrBlank() ||
                        targetDeviceId == localDeviceId
                    val methods = res.body()?.data
                    var cacheOk = !isLocalTarget
                    if (isLocalTarget && methods != null) {
                        cacheOk = runCatching {
                            val json = online.paychek.app.utils.GsonUtils.gson.toJson(methods)
                            online.paychek.app.data.local.prefs.PrefsHelper
                                .setGatewayMethodsCache(context, json)
                        }.getOrDefault(false)
                    }
                    // Only mark enabled when we have methods (or remote — DeviceScreen will refresh).
                    if (!isLocalTarget || (methods != null && cacheOk)) {
                        _state.update { it.copy(allAlreadyEnabled = true) }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSenderReadyMadeScreen(
    simSlot: Int,
    targetDeviceId: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomSenderReadyMadeViewModel = viewModel(key = "ready-made-sim-$simSlot")
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
    val accent = Color(0xFF06B6D4)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "কাস্টম সেন্ডার — ALL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(text = "SIM $simSlot", fontSize = 12.sp, color = muted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg)
            )
        },
        containerColor = bg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(24.dp),
                    color = accent
                )
            }

            uiState.error?.let { err ->
                Text(text = err, color = Color(0xFFEF4444), fontSize = 12.sp)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(card, RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = if (uiState.allAlreadyEnabled) accent else muted.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ALL",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = textPrimary
                    )
                    if (uiState.allAlreadyEnabled) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Enabled",
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "সক্রিয়",
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "এই SIM-এর যেকোনো সেন্ডার থেকে আসা SMS, known payment template-এর সাথে body match না করলেও archive (isParseable=0) হিসেবে সার্ভারে যাবে। মিললে isParseable=1 অপরিবর্তিত থাকবে।",
                    color = muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = "সতর্কতা: OTP/ব্যাংক অ্যালার্টসহ অপ্রয়োজনীয় SMSও archive হতে পারে। শুধু প্রয়োজনে চালু করুন।",
                    color = Color(0xFFF59E0B),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Button(
                onClick = {
                    viewModel.saveAllPolicy(context, simSlot, targetDeviceId) {
                        Toast.makeText(context, "SIM $simSlot — ALL সংরক্ষিত", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    }
                },
                enabled = !uiState.isSaving && !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (uiState.allAlreadyEnabled) "ALL আবার সংরক্ষণ করুন" else "ALL সংরক্ষণ করুন",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
