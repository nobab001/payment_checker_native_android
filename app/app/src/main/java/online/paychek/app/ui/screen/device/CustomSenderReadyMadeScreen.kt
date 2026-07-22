package online.paychek.app.ui.screen.device

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import online.paychek.app.data.remote.dto.CustomSenderSuggestionDto
import online.paychek.app.utils.DeviceIdHelper
import online.paychek.app.utils.SecurePreferences

internal val ReadyMadeTabs = listOf(
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
    val items: List<CustomSenderSuggestionDto> = emptyList(),
    val error: String? = null,
    /** Sender IDs already on the *current* SIM slot only (lowercase). */
    val addedSenderIds: Set<String> = emptySet()
)

class CustomSenderReadyMadeViewModel : ViewModel() {
    private val _state = MutableStateFlow(ReadyMadeUiState())
    val state: StateFlow<ReadyMadeUiState> = _state.asStateFlow()

    fun load(
        category: String,
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

            val existingOnSlot = runCatching {
                val methodsRes = RetrofitClient.gatewayApiService.getGatewayMethods(
                    token = "Bearer $token",
                    deviceId = headerDeviceId
                )
                if (methodsRes.isSuccessful) {
                    methodsRes.body()?.data.orEmpty()
                        .filter { it.simSlot == simSlot && (it.isParseable ?: 1) == 0 }
                        .mapNotNull { it.senderId?.trim()?.lowercase()?.takeIf { id -> id.isNotEmpty() } }
                        .toSet()
                } else {
                    emptySet()
                }
            }.getOrDefault(emptySet())

            runCatching {
                RetrofitClient.gatewayApiService.getCustomSenderSuggestions(
                    token = "Bearer $token",
                    query = "",
                    category = category,
                    deviceId = headerDeviceId
                )
            }.onSuccess { res ->
                if (res.isSuccessful && res.body()?.success == true) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            items = res.body()?.suggestions.orEmpty(),
                            addedSenderIds = existingOnSlot,
                            error = null
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            addedSenderIds = existingOnSlot,
                            error = "লিস্ট লোড করতে ব্যর্থ (${res.code()})"
                        )
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        addedSenderIds = existingOnSlot,
                        error = e.message ?: "নেটওয়ার্ক সমস্যা"
                    )
                }
            }
        }
    }

    fun addSender(
        context: android.content.Context,
        simSlot: Int,
        senderId: String,
        officialTemplateId: Int?,
        createPersonal: Boolean,
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
            val cleanId = senderId.trim()
            val request = AddCustomSenderRequest(
                simSlot = simSlot,
                senderId = cleanId,
                deviceId = headerDeviceId,
                officialTemplateId = officialTemplateId,
                createPersonal = if (createPersonal) true else null
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
                    _state.update {
                        it.copy(addedSenderIds = it.addedSenderIds + cleanId.lowercase())
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
    val pagerState = rememberPagerState(pageCount = { ReadyMadeTabs.size })
    val scope = rememberCoroutineScope()
    var showManualDialog by remember { mutableStateOf(false) }
    var manualSender by remember { mutableStateOf("") }

    val category = ReadyMadeTabs[pagerState.currentPage].first
    LaunchedEffect(category, simSlot, targetDeviceId) {
        viewModel.load(category, context, simSlot, targetDeviceId)
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
                            text = "কাস্টম সেন্ডার রেডিমেট",
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
                actions = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { showManualDialog = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "লিস্টের বাইরে",
                            tint = accent,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "*লিস্টের বাইরে",
                            color = accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
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
        ) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 12.dp,
                containerColor = bg,
                contentColor = accent
            ) {
                ReadyMadeTabs.forEachIndexed { index, pair ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                text = pair.second,
                                fontWeight = if (pagerState.currentPage == index) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                },
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            uiState.error?.let { err ->
                Text(
                    text = err,
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageCategory = ReadyMadeTabs[page].first
                // Reload when page becomes visible (LaunchedEffect on category already covers current)
                ReadyMadePageContent(
                    isActivePage = pagerState.currentPage == page,
                    category = pageCategory,
                    uiState = uiState,
                    cardColor = card,
                    textPrimary = textPrimary,
                    muted = muted,
                    accent = accent,
                    onAddFromList = { item ->
                        viewModel.addSender(
                            context = context,
                            simSlot = simSlot,
                            senderId = item.senderId,
                            officialTemplateId = item.id,
                            createPersonal = false,
                            targetDeviceId = targetDeviceId
                        ) {
                            Toast.makeText(context, "${item.senderId} যোগ হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    if (showManualDialog) {
        Dialog(onDismissRequest = { if (!uiState.isSaving) showManualDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = card,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "লিস্টের বাইরে সেন্ডার যোগ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = textPrimary
                    )
                    Text(
                        text = "নিজের মতো সেন্ডার নাম লিখে অ্যাড করুন (যেমন: GP-ALERT)",
                        color = muted,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = manualSender,
                        onValueChange = { manualSender = it },
                        label = { Text("সেন্ডার আইডি") },
                        singleLine = true,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { showManualDialog = false },
                            enabled = !uiState.isSaving,
                            modifier = Modifier.weight(1f)
                        ) { Text("বাতিল") }
                        Button(
                            onClick = {
                                val sid = manualSender.trim()
                                if (sid.isEmpty()) return@Button
                                viewModel.addSender(
                                    context = context,
                                    simSlot = simSlot,
                                    senderId = sid,
                                    officialTemplateId = null,
                                    createPersonal = true,
                                    targetDeviceId = targetDeviceId
                                ) {
                                    Toast.makeText(context, "$sid যোগ হয়েছে", Toast.LENGTH_SHORT).show()
                                    manualSender = ""
                                    showManualDialog = false
                                }
                            },
                            enabled = !uiState.isSaving && manualSender.trim().isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("অ্যাড", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyMadePageContent(
    isActivePage: Boolean,
    category: String,
    uiState: ReadyMadeUiState,
    cardColor: Color,
    textPrimary: Color,
    muted: Color,
    accent: Color,
    onAddFromList: (CustomSenderSuggestionDto) -> Unit
) {
    // Only show loading/items for the active page's loaded state (shared VM loads current category)
    if (!isActivePage) {
        Spacer(modifier = Modifier.fillMaxSize())
        return
    }
    when {
        uiState.isLoading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = accent)
            }
        }
        uiState.items.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "এই ট্যাবে এখনো কোনো রেডিমেট সেন্ডার নেই",
                    color = muted,
                    fontSize = 14.sp
                )
            }
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.items, key = { it.id }) { item ->
                    val already = uiState.addedSenderIds.contains(item.senderId.lowercase())
                    ReadyMadeSenderRow(
                        item = item,
                        alreadyAdded = already,
                        isSaving = uiState.isSaving,
                        cardColor = cardColor,
                        textPrimary = textPrimary,
                        muted = muted,
                        accent = accent,
                        onClick = { onAddFromList(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyMadeSenderRow(
    item: CustomSenderSuggestionDto,
    alreadyAdded: Boolean,
    isSaving: Boolean,
    cardColor: Color,
    textPrimary: Color,
    muted: Color,
    accent: Color,
    onClick: () -> Unit
) {
    val borderColor = if (alreadyAdded) {
        Color(0xFF22C55E).copy(alpha = 0.4f)
    } else {
        muted.copy(alpha = 0.2f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable(enabled = !isSaving && !alreadyAdded, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.templateName.ifBlank { item.senderId },
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Sender: ${item.senderId}",
                color = muted,
                fontSize = 12.sp
            )
        }
        when {
            alreadyAdded -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(22.dp)
            )
            isSaving -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = accent
            )
            else -> Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
