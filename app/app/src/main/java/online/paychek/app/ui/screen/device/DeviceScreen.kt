package online.paychek.app.ui.screen.device

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.content.Intent
import online.paychek.app.data.remote.dto.GatewayMethod
import online.paychek.app.data.remote.dto.ChildDeviceDto
import online.paychek.app.data.remote.dto.SmsTemplateDto
import online.paychek.app.data.remote.dto.AccountNumberDto
import online.paychek.app.ui.common.RemoteImage
import androidx.compose.ui.layout.ContentScale
import online.paychek.app.ui.theme.*
import online.paychek.app.ui.components.ConnectionStatusBanner
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.BorderStroke
import online.paychek.app.utils.RefreshCooldown
import online.paychek.app.utils.adaptivePadding
import online.paychek.app.utils.adaptiveTextSize
import online.paychek.app.utils.adaptiveTextSize

// =============================================================================
// Design Tokens
// =============================================================================
private val GwBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val GwCard: Color @Composable get() = MaterialTheme.colorScheme.surface
private val GwCardDrag: Color @Composable get() = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF2D3F57) else Color(0xFFE2E8F0)
private val AccentCyan = Color(0xFF22D3EE)
private val AccentGreen= Color(0xFF10B981)
private val TextWhite: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ToggleOff  = Color(0xFF475569)

private fun providerColor(tag: String): Color = when (tag.lowercase()) {
    "bkash"  -> Color(0xFFE2136E)
    "nagad"  -> Color(0xFFEF4123)
    "rocket" -> Color(0xFF6A2C91)
    "upay"   -> Color(0xFF00B99B)
    else     -> Color(0xFF22D3EE)
}
private fun providerEmoji(tag: String): String = when (tag.lowercase()) {
    "bkash"  -> "🟢"
    "nagad"  -> "🟠"
    "rocket" -> "🔵"
    "upay"   -> "🟡"
    else     -> "⚪"
}

// =============================================================================
// DeviceScreen — Root Composable
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToSubscription: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DeviceViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(true) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.currentStateFlow.collect { state ->
            if (state == androidx.lifecycle.Lifecycle.State.RESUMED) {
                isAccessibilityEnabled = online.paychek.app.utils.AccessibilityHelper.isAccessibilityServiceEnabled(context)
                viewModel.loadGatewayMethods()
                viewModel.loadTemplates()
                viewModel.syncPhysicalSimNumbers()
            }
        }
    }

    val refreshCooldownMessage = "৫ সেকেন্ড পরে আবার রিফ্রেশ করুন"
    val refreshDeviceData: () -> Unit = {
        if (!RefreshCooldown.tryRefresh {
            viewModel.loadGatewayMethods()
            viewModel.loadTemplates()
            viewModel.loadChildDevices()
        }) {
            android.widget.Toast.makeText(context, refreshCooldownMessage, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val state       by viewModel.state.collectAsStateWithLifecycle()
    val connectionBanner by viewModel.connectionBanner.collectAsStateWithLifecycle()
    val hasInternet by viewModel.hasInternet.collectAsStateWithLifecycle()
    val haptic      = LocalHapticFeedback.current
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accountNumbersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showAddSenderDialog by remember { mutableStateOf(false) }
    var activeSimSlotForCustomSender by remember { mutableStateOf(1) }
    var showRemoteAddSenderDialog by remember { mutableStateOf(false) }
    var remoteActiveSimSlotForCustomSender by remember { mutableStateOf(1) }
    var remoteMethodToDelete by remember { mutableStateOf<GatewayMethod?>(null) }
    var customSenderInput by remember { mutableStateOf("") }
    var selectedOfficialTemplateId by remember { mutableStateOf<Int?>(null) }
    var forcePersonalSender by remember { mutableStateOf(false) }
    var methodToDelete by remember { mutableStateOf<GatewayMethod?>(null) }

    val prefs = remember(context) {
        context.getSharedPreferences(online.paychek.app.config.AppConfig.PREF_NAME, android.content.Context.MODE_PRIVATE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        online.paychek.app.MainActivity.isRequestingPermission = false
        prefs.edit().putBoolean("pcu_sim_auto_detected", true).apply()
        val granted = permissions[Manifest.permission.READ_PHONE_STATE] == true ||
                      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && permissions[Manifest.permission.READ_PHONE_NUMBERS] == true)
        if (granted) {
            viewModel.autoDetectSimNumbers()
        }
    }

    LaunchedEffect(Unit) {
        val hasPhoneState = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPhoneState) {
            val hasAttempted = prefs.getBoolean("pcu_sim_auto_detected", false)
            if (!hasAttempted && state.sim1Number.isBlank() && state.sim2Number.isBlank()) {
                viewModel.autoDetectSimNumbers()
                prefs.edit().putBoolean("pcu_sim_auto_detected", true).apply()
            }
        } else {
            val hasAttempted = prefs.getBoolean("pcu_sim_auto_detected", false)
            if (!hasAttempted && state.sim1Number.isBlank() && state.sim2Number.isBlank()) {
                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
                } else {
                    arrayOf(Manifest.permission.READ_PHONE_STATE)
                }
                online.paychek.app.MainActivity.isRequestingPermission = true
                permissionLauncher.launch(permissionsToRequest)
            }
        }
    }

    val sim1Methods = state.methods.filter { it.simSlot == 1 }
    val sim2Methods = state.methods.filter { it.simSlot == 2 }

    val sim1ListState   = rememberReorderableLazyListState(
        lazyListState   = androidx.compose.foundation.lazy.rememberLazyListState()
    ) { from, to ->
        val fromReal = state.methods.indexOfFirst { it.id == sim1Methods[from.index].id }
        val toReal   = state.methods.indexOfFirst { it.id == sim1Methods[to.index].id }
        viewModel.onReorder(fromReal, toReal)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val sim2ListState   = rememberReorderableLazyListState(
        lazyListState   = androidx.compose.foundation.lazy.rememberLazyListState()
    ) { from, to ->
        val fromReal = state.methods.indexOfFirst { it.id == sim2Methods[from.index].id }
        val toReal   = state.methods.indexOfFirst { it.id == sim2Methods[to.index].id }
        viewModel.onReorder(fromReal, toReal)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val currentSubTab = state.selectedSubTab

    if (state.pendingSimConflict != null) {
        val conflict = state.pendingSimConflict!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissSimConflict() },
            title = {
                Text(
                    text = "সিম অন্য ডিভাইসে সক্রিয়",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "নম্বর ${conflict.phoneNumber} বর্তমানে \"${conflict.runningDeviceName}\" ডিভাইসে সক্রিয় আছে।\n\nঠিক চাপলে এই নম্বরটি বর্তমান ডিভাইসের স্লট ${conflict.simSlot}-এ স্থানান্তর হবে এবং পূর্বের ডিভাইসে নিষ্ক্রিয় হয়ে যাবে।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmForceShift() },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                ) {
                    Text("ঠিক আছে", color = Color.White)
                }
            },
            containerColor = GwCard
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GwBg)
    ) {
        connectionBanner?.let { banner ->
            ConnectionStatusBanner(banner = banner)
        }

        if (!isAccessibilityEnabled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable {
                        online.paychek.app.MainActivity.isRequestingPermission = true
                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "স্বয়ংক্রিয় পেমেন্ট ট্র্যাকিং সচল করতে অ্যাক্সেসিবিলিটি পারমিশন অনুমোদন করুন। (যদি Restricted Settings দেখায়, তবে হোম স্ক্রিন থেকে অ্যাপ আইকনে চাপ দিয়ে ধরে App Info-তে যান এবং ডানদিকের উপরের ৩-ডট মেনু থেকে Allow Restricted Settings সচল করুন)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 18.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val remoteDevice = state.activeRemoteDevice
            if (remoteDevice != null) {
                RemoteDeviceSettingsFullScreen(
                    device = remoteDevice,
                    state = state,
                    viewModel = viewModel,
                    onBack = { viewModel.closeRemoteDeviceSettings() },
                    onRemoteAddSender = { slot ->
                        viewModel.onAddCustomSenderClick(slot) { allowedSlot ->
                            remoteActiveSimSlotForCustomSender = allowedSlot
                            showRemoteAddSenderDialog = true
                        }
                    },
                    onRemoteDeleteSender = { remoteMethodToDelete = it }
                )
            } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
            // ─── Header ──────────────────────────────────────────────────────
            item { DeviceTopBar(
                isSaving = state.isSaving,
                onNavigateBack = onNavigateBack,
                onRefresh = refreshDeviceData,
                onAccountNumbers = { viewModel.setShowAccountNumbersSheet(true) }
            ) }

            // ─── Success Toast ────────────────────────────────────────────────
            state.successMessage?.let { msg ->
                item { SuccessToast(message = msg) }
            }

            // ─── Tab Row ─────────────────────────────────────────────────────
            item {
                SecondaryTabRow(
                        selectedTabIndex = currentSubTab,
                        containerColor = GwBg,
                        contentColor = AccentCyan,
                        indicator = {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(currentSubTab),
                                color = AccentCyan
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = adaptivePadding(12.dp, 16.dp), vertical = 8.dp)
                    ) {
                        Tab(
                            selected = currentSubTab == 0,
                            onClick = { viewModel.setSubTab(0) },
                            text = {
                                Text(
                                    "ডিভাইস সেটিং",
                                    color = if (currentSubTab == 0) AccentCyan else TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = adaptiveTextSize(13.sp, 15.sp)
                                )
                            }
                        )
                        Tab(
                            selected = currentSubTab == 1,
                            onClick = { viewModel.setSubTab(1) },
                            text = {
                                Text(
                                    "লগইন ডিভাইস",
                                    color = if (currentSubTab == 1) AccentCyan else TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = adaptiveTextSize(13.sp, 15.sp)
                                )
                            }
                        )
                    }
            }

            // ─── Loading Skeleton ─────────────────────────────────────────────
            if (state.isLoading && currentSubTab == 0) {
                items(5) { DeviceSkeletonCard() }
            }

            if (!state.isLoading && currentSubTab == 0) {
                val isOffline = !hasInternet
                if (state.errorMessage != null && !isOffline) {
                    item {
                        ErrorBanner(
                            message = state.errorMessage!!,
                            onRetry = refreshDeviceData,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    // ─── SIM 1 Card ────────────────────────────────────────────
                    item {
                        SimCard(
                            simSlot = 1,
                            simNumber = state.sim1Number,
                            simEnabled = state.sim1Enabled,
                            onSimNumberChange = { viewModel.onSimNumberChanged(1, it) },
                            onToggleSim = { viewModel.toggleSim(1) },
                            templates = state.templates,
                            methods = state.methods,
                            onToggleTemplate = { viewModel.toggleTemplate(1, it) },
                            onAddCustomSenderClick = { slot ->
                                viewModel.onAddCustomSenderClick(slot) { allowedSlot ->
                                    activeSimSlotForCustomSender = allowedSlot
                                    showAddSenderDialog = true
                                }
                            },
                            onDeleteCustomSenderClick = { method ->
                                methodToDelete = method
                            },
                            onToggleMethod = { viewModel.toggleMethod(it) }
                        )
                    }

                    // ─── SIM 2 Card ────────────────────────────────────────────
                    item {
                        SimCard(
                            simSlot = 2,
                            simNumber = state.sim2Number,
                            simEnabled = state.sim2Enabled,
                            onSimNumberChange = { viewModel.onSimNumberChanged(2, it) },
                            onToggleSim = { viewModel.toggleSim(2) },
                            templates = state.templates,
                            methods = state.methods,
                            onToggleTemplate = { viewModel.toggleTemplate(2, it) },
                            onAddCustomSenderClick = { slot ->
                                viewModel.onAddCustomSenderClick(slot) { allowedSlot ->
                                    activeSimSlotForCustomSender = allowedSlot
                                    showAddSenderDialog = true
                                }
                            },
                            onDeleteCustomSenderClick = { method ->
                                methodToDelete = method
                            },
                            onToggleMethod = { viewModel.toggleMethod(it) }
                        )
                    }
                }
            }

            if (currentSubTab == 1) {
                // ─── Others Device Sub-Tab ─────────────────────────────────────────
                val isOffline = !hasInternet
                if (state.isChildDevicesLoading) {
                    items(3) { DeviceSkeletonCard() }
                } else if (state.errorMessage != null && !isOffline) {
                    item {
                        ErrorBanner(
                            message = state.errorMessage!!,
                            onRetry = refreshDeviceData,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else if (state.childDevices.isEmpty()) {
                    item {
                        Column(
                            modifier            = Modifier.fillMaxWidth().padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector     = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint            = TextMuted.copy(alpha = 0.4f),
                                modifier        = Modifier.size(56.dp)
                            )
                            Text(
                                "কোনো লগইন ডিভাইস নেই",
                                color    = TextMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(
                        items = state.childDevices,
                        key = { it.deviceId }
                    ) { device ->
                        ChildDeviceCard(
                            device = device,
                            onConfigure = { viewModel.openRemoteDeviceSettings(device) },
                            onDelete = { viewModel.initiateDeleteDevice(device) },
                            modifier = Modifier.padding(horizontal = adaptivePadding(10.dp, 16.dp), vertical = 6.dp)
                        )
                    }
                }
            }
            }
        }

        // ─── Account active numbers sheet ─────────────────────────────────────
        if (state.showAccountNumbersSheet) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.setShowAccountNumbersSheet(false) },
                sheetState = accountNumbersSheetState,
                containerColor = GwCard
            ) {
                AccountNumbersSheet(
                    numbers = state.accountNumbers,
                    isLoading = state.isAccountNumbersLoading,
                    onRefresh = { viewModel.loadAccountNumbers() },
                    onDelete = { viewModel.requestDeleteAccountNumber(it) },
                    onClose = { viewModel.setShowAccountNumbersSheet(false) }
                )
            }
        }

        state.accountNumberToDelete?.let { toDelete ->
            AlertDialog(
                onDismissRequest = { if (!state.isDeletingAccountNumber) viewModel.dismissDeleteAccountNumber() },
                title = {
                    Text("নাম্বার মুছবেন?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                },
                text = {
                    Text(
                        "${toDelete.phoneNumber} নাম্বারটি সার্ভার থেকে সম্পূর্ণ মুছে যাবে।\n\nএই নাম্বারের সব প্রোভাইডার/টেমপ্লেট এবং চেকআউট থেকেও সরে যাবে।",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmDeleteAccountNumber() },
                        enabled = !state.isDeletingAccountNumber,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        if (state.isDeletingAccountNumber) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("মুছে ফেলুন", color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissDeleteAccountNumber() },
                        enabled = !state.isDeletingAccountNumber
                    ) { Text("বাতিল") }
                },
                containerColor = GwCard
            )
        }

        // ─── Bottom Sheet — Method Edit ───────────────────────────────────────
        if (state.editingMethod != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeEditSheet() },
                sheetState       = sheetState,
                containerColor   = GwCard
            ) {
                MethodEditSheet(
                    method      = state.editingMethod!!,
                    number      = state.editNumber,
                    displayName = state.editDisplayName,
                    onNumberChange      = viewModel::onEditNumberChanged,
                    onDisplayNameChange = viewModel::onEditDisplayNameChanged,
                    onSave       = { viewModel.saveMethodEdit() },
                    onDismiss    = { viewModel.closeEditSheet() }
                )
            }
        }

        // ─── Dialog — Role PIN Verification ──────────────────────────────────
        if (state.showRolePinDialog) {
            Dialog(
                onDismissRequest = { viewModel.dismissRolePinDialog() },
                properties = DialogProperties(usePlatformDefaultWidth = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = GwCard,
                    border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight()
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AccentCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock",
                                tint = AccentCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "রোল পরিবর্তন নিশ্চিত করুন",
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "ডিভাইসের রোল পরিবর্তন করতে আপনার মূল অ্যাকাউন্ট পিন কোডটি ভেরিফাই করুন।",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = state.rolePinInput,
                            onValueChange = viewModel::onRolePinInputChanged,
                            label = { Text("পিন কোড", color = TextMuted) },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = ToggleOff,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        state.rolePinError?.let {
                            Text(it, color = Color(0xFFEF4444), fontSize = 12.sp, textAlign = TextAlign.Center)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.dismissRolePinDialog() },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text("বাতিল")
                            }
                            Button(
                                onClick = {
                                    viewModel.submitRoleToggle()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text("নিশ্চিত করুন", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ─── Dialog — Delete Device PIN ──────────────────────────────────────
        if (state.showDeleteDevicePinDialog) {
            Dialog(
                onDismissRequest = { if (!state.isDeletingDevice) viewModel.dismissDeleteDeviceDialog() },
                properties = DialogProperties(usePlatformDefaultWidth = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = GwCard,
                    border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "ডিভাইস মুছুন",
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "'${state.deviceToDelete?.customDeviceName ?: ""}' ডিভাইসটি মুছতে অ্যাকাউন্ট পিন দিন।",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        OutlinedTextField(
                            value = state.deleteDevicePinInput,
                            onValueChange = viewModel::onDeleteDevicePinChanged,
                            label = { Text("পিন কোড", color = TextMuted) },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = ToggleOff,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        state.deleteDevicePinError?.let {
                            Text(it, color = Color(0xFFEF4444), fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.dismissDeleteDeviceDialog() },
                                enabled = !state.isDeletingDevice,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("বাতিল") }
                            Button(
                                onClick = { viewModel.confirmDeleteDevice() },
                                enabled = !state.isDeletingDevice,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (state.isDeletingDevice) {
                                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("মুছুন", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Custom Sender Dialog
        if (showAddSenderDialog) {
            Dialog(
                onDismissRequest = {
                    showAddSenderDialog = false
                    customSenderInput = ""
                    selectedOfficialTemplateId = null
                    forcePersonalSender = false
                    viewModel.clearDialogErrorMessage()
                },
                properties = DialogProperties(usePlatformDefaultWidth = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = GwCard,
                    border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AccentCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = AccentCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "কাস্টম সেন্ডার আইডি যোগ করুন",
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "SIM $activeSimSlotForCustomSender এর জন্য একটি কাস্টম সেন্ডার নাম (যেমন: GP, BL) লিখুন।",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = customSenderInput,
                            onValueChange = {
                                customSenderInput = it
                                selectedOfficialTemplateId = null
                                forcePersonalSender = false
                            },
                            label = { Text("সেন্ডার আইডি (যেমন: GP-ALERT)", color = TextMuted) },
                            singleLine = true,
                            enabled = !state.isSaving,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = ToggleOff,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        val archiveSuggestions = remember(customSenderInput, state.templates) {
                            val q = customSenderInput.trim()
                            if (q.length < 2) emptyList()
                            else state.templates.filter {
                                it.isOfficial == 1 && it.isParseable == 0 &&
                                    (it.senderId.contains(q, ignoreCase = true) ||
                                        it.templateName.contains(q, ignoreCase = true))
                            }.take(5)
                        }

                        if (archiveSuggestions.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "এডমিন আর্কাইভ সাজেশন",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                archiveSuggestions.forEach { suggestion ->
                                    val selected = selectedOfficialTemplateId == suggestion.id
                                    Surface(
                                        onClick = {
                                            selectedOfficialTemplateId = suggestion.id
                                            customSenderInput = suggestion.senderId
                                            forcePersonalSender = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) AccentCyan.copy(alpha = 0.15f) else GwBg,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) AccentCyan else TextMuted.copy(alpha = 0.25f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (selected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = AccentCyan,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = suggestion.senderId,
                                                    color = TextWhite,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = suggestion.templateName,
                                                    color = TextMuted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                if (archiveSuggestions.any { it.senderId.equals(customSenderInput.trim(), ignoreCase = true) }) {
                                    TextButton(
                                        onClick = {
                                            selectedOfficialTemplateId = null
                                            forcePersonalSender = true
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = "নিজের নামে আলাদা যোগ করুন",
                                            color = AccentCyan,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        state.dialogErrorMessage?.let { errMsg ->
                            Text(
                                text = errMsg,
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showAddSenderDialog = false
                                    customSenderInput = ""
                                    selectedOfficialTemplateId = null
                                    viewModel.clearDialogErrorMessage()
                                },
                                enabled = !state.isSaving,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text("বাতিল")
                            }
                            Button(
                                onClick = {
                                    if (customSenderInput.trim().isNotEmpty()) {
                                        val createPersonal = forcePersonalSender
                                        viewModel.addCustomSender(
                                            simSlot = activeSimSlotForCustomSender,
                                            senderId = customSenderInput.trim(),
                                            officialTemplateId = selectedOfficialTemplateId,
                                            createPersonal = createPersonal
                                        ) {
                                            showAddSenderDialog = false
                                            customSenderInput = ""
                                            selectedOfficialTemplateId = null
                                            forcePersonalSender = false
                                        }
                                    }
                                },
                                enabled = !state.isSaving && customSenderInput.trim().isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("যুক্ত করুন", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Remote Add Custom Sender Dialog
        if (showRemoteAddSenderDialog) {
            Dialog(
                onDismissRequest = {
                    showRemoteAddSenderDialog = false
                    customSenderInput = ""
                    selectedOfficialTemplateId = null
                    forcePersonalSender = false
                    viewModel.clearDialogErrorMessage()
                },
                properties = DialogProperties(usePlatformDefaultWidth = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = GwCard,
                    border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "রিমোট কাস্টম সেন্ডার যোগ",
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "SIM $remoteActiveSimSlotForCustomSender — টার্গেট ডিভাইসে সেন্ডার যোগ হবে",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        OutlinedTextField(
                            value = customSenderInput,
                            onValueChange = {
                                customSenderInput = it
                                selectedOfficialTemplateId = null
                                forcePersonalSender = false
                            },
                            label = { Text("সেন্ডার আইডি", color = TextMuted) },
                            singleLine = true,
                            enabled = !state.isSaving,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = ToggleOff,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        state.dialogErrorMessage?.let { errMsg ->
                            Text(errMsg, color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showRemoteAddSenderDialog = false
                                    customSenderInput = ""
                                    viewModel.clearDialogErrorMessage()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("বাতিল") }
                            Button(
                                onClick = {
                                    if (customSenderInput.trim().isNotEmpty()) {
                                        viewModel.remoteAddCustomSender(
                                            simSlot = remoteActiveSimSlotForCustomSender,
                                            senderId = customSenderInput.trim(),
                                            officialTemplateId = selectedOfficialTemplateId,
                                            createPersonal = forcePersonalSender
                                        ) {
                                            showRemoteAddSenderDialog = false
                                            customSenderInput = ""
                                            selectedOfficialTemplateId = null
                                            forcePersonalSender = false
                                        }
                                    }
                                },
                                enabled = !state.isSaving && customSenderInput.trim().isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("যুক্ত করুন", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        // Remote Delete Custom Sender Confirmation
        if (remoteMethodToDelete != null) {
            val method = remoteMethodToDelete!!
            Dialog(
                onDismissRequest = { remoteMethodToDelete = null },
                properties = DialogProperties(usePlatformDefaultWidth = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = GwCard,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("সেন্ডার মুছবেন?", fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 18.sp)
                        Text(method.senderId ?: method.provider, color = TextMuted, fontSize = 14.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { remoteMethodToDelete = null },
                                modifier = Modifier.weight(1f)
                            ) { Text("বাতিল") }
                            Button(
                                onClick = {
                                    viewModel.remoteDeleteCustomSender(method.id)
                                    remoteMethodToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier.weight(1f)
                            ) { Text("মুছুন", color = Color.White) }
                        }
                    }
                }
            }
        }

        // Delete Custom Sender Confirmation Dialog
        if (methodToDelete != null) {
            val method = methodToDelete!!
            Dialog(
                onDismissRequest = { methodToDelete = null },
                properties = DialogProperties(usePlatformDefaultWidth = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = GwCard,
                    border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "সেন্ডার আইডি মুছে ফেলুন",
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 18.sp
                        )
                        val cleanName = method.senderId ?: method.provider.removePrefix("Custom-")
                        Text(
                            text = "আপনি কি নিশ্চিতভাবে '$cleanName' কাস্টম সেন্ডার আইডিটি ডিলিট করতে চান?",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { methodToDelete = null },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text("বাতিল")
                            }
                            Button(
                                onClick = {
                                    viewModel.deleteCustomSender(method.id)
                                    methodToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text("মুছে ফেলুন", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Premium Upgrade Gated Feature Dialog (403 Handled)
        if (state.showPremiumUpgradeDialog) {
            Dialog(
                onDismissRequest = { viewModel.setShowPremiumUpgradeDialog(false) },
                properties = DialogProperties(usePlatformDefaultWidth = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = GwCard,
                    border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "প্রিমিয়াম ফিচার লকড",
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "কাস্টম সেন্ডার আইডি ব্যবহার করতে হলে আপনার প্যাকেজ আপগ্রেড করুন অথবা অ্যাড-অন ক্রয় করুন।",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Button(
                            onClick = {
                                viewModel.setShowPremiumUpgradeDialog(false)
                                onNavigateToSubscription(1)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("প্যাকেজ কিনুন", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.setShowPremiumUpgradeDialog(false) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("বাতিল")
                        }
                    }
                }
            }
        }
    }
}
}

// =============================================================================
// Component 1 — Top Bar
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceTopBar(
    isSaving: Boolean,
    onNavigateBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onAccountNumbers: (() -> Unit)? = null
) {
    TopAppBar(
        modifier = Modifier.height(adaptivePadding(56.dp, 62.dp)),
        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        title = {
            Column {
                Text(
                    "ডিভাইস সেটিংস",
                    color      = TextWhite,
                    fontSize   = adaptiveTextSize(14.sp, 16.sp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "সিম স্লট ১/২ কনফিগার ও সাজান",
                    color    = TextMuted,
                    fontSize = 10.sp
                )
            }
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint               = AccentCyan,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentCyan.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector     = Icons.Default.Tune,
                        contentDescription = null,
                        tint            = AccentCyan,
                        modifier        = Modifier.size(16.dp)
                    )
                }
            }
        },
        actions = {
            if (!isSaving && onAccountNumbers != null) {
                IconButton(onClick = onAccountNumbers, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = "সক্রিয় নাম্বার",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (!isSaving && onRefresh != null) {
                IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "রিফ্রেশ",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (isSaving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    CircularProgressIndicator(
                        color       = AccentCyan,
                        modifier    = Modifier.size(12.dp),
                        strokeWidth = 2.dp
                    )
                    Text("সেভ হচ্ছে...", color = AccentCyan, fontSize = 10.sp)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = GwBg
        )
    )
}

// =============================================================================
// Component 2 — SIM Section Header with Master Toggle
// =============================================================================
@Composable
private fun SimSectionHeader(
    simSlot: Int,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(
        if (isEnabled) AccentGreen else ToggleOff, tween(300), label = "simDot"
    )
    Row(
        modifier             = modifier
            .fillMaxWidth()
            .padding(horizontal = adaptivePadding(12.dp, 16.dp), vertical = 8.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text       = "📱 SIM $simSlot",
                color      = TextWhite,
                fontSize   = adaptiveTextSize(12.sp, 14.sp),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text     = if (isEnabled) "(সক্রিয়)" else "(বন্ধ)",
                color    = if (isEnabled) AccentGreen.copy(0.8f) else TextMuted,
                fontSize = 11.sp
            )
        }
        Switch(
            checked         = isEnabled,
            onCheckedChange = { onToggle() },
            enabled         = enabled,
            colors          = SwitchDefaults.colors(
                checkedTrackColor   = AccentGreen,
                uncheckedTrackColor = ToggleOff,
                uncheckedBorderColor = ToggleOff
            )
        )
    }
    HorizontalDivider(
        color     = TextMuted.copy(alpha = 0.1f),
        modifier  = Modifier.padding(horizontal = adaptivePadding(12.dp, 16.dp))
    )
}

// =============================================================================
// Component 3 — Gateway Method Card (Draggable)
// =============================================================================
@Composable
private fun GatewayMethodCard(
    method:     GatewayMethod,
    simEnabled: Boolean,
    isDragging: Boolean,
    onToggle:   () -> Unit,
    onEdit:     () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier:   Modifier = Modifier
) {
    val isActive   = simEnabled && method.isEnabled == 1
    val cardColor by animateColorAsState(
        if (isDragging) GwCardDrag else GwCard, tween(200), label = "cardBg"
    )
    val pColor = providerColor(method.provider)

    Card(
        colors   = CardDefaults.cardColors(containerColor = cardColor),
        shape    = RoundedCornerShape(12.dp),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDragging) Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
                else Modifier
            )
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(70.dp)
                    .background(if (isActive) pColor else pColor.copy(alpha = 0.25f))
            )

            Box(
                modifier         = Modifier.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) { dragHandle() }

            Column(
                modifier            = Modifier.weight(1f).padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "${providerEmoji(method.provider)} ${method.provider}",
                        color      = if (isActive) pColor else pColor.copy(0.45f),
                        fontSize   = adaptiveTextSize(12.sp, 14.sp),
                        fontWeight = FontWeight.Bold
                    )
                    if (!isActive && method.isEnabled == 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ToggleOff.copy(0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) { Text("OFF", color = ToggleOff, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                val displayText = method.displayName?.takeIf { it.isNotEmpty() }
                    ?: method.number ?: "নম্বর সেট করা হয়নি"
                Text(
                    displayText,
                    color    = if (isActive) TextMuted else TextMuted.copy(0.5f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier             = Modifier.padding(end = 8.dp)
            ) {
                Switch(
                    checked         = method.isEnabled == 1,
                    onCheckedChange = { onToggle() },
                    enabled         = simEnabled,
                    colors          = SwitchDefaults.colors(
                        checkedTrackColor   = AccentGreen,
                        uncheckedTrackColor = ToggleOff,
                        uncheckedBorderColor = ToggleOff
                    ),
                    modifier = Modifier.height(28.dp)
                )
                IconButton(
                    onClick  = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector     = Icons.Default.Settings,
                        contentDescription = "সম্পাদনা",
                        tint            = TextMuted,
                        modifier        = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// Component 4 — Method Edit Bottom Sheet
// =============================================================================
@Composable
private fun MethodEditSheet(
    method:             GatewayMethod,
    number:             String,
    displayName:        String,
    onNumberChange:     (String) -> Unit,
    onDisplayNameChange:(String) -> Unit,
    onSave:             () -> Unit,
    onDismiss:          () -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${providerEmoji(method.provider)} ${method.provider}",
                color      = providerColor(method.provider),
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text("— SIM ${method.simSlot}", color = TextMuted, fontSize = 14.sp)
        }
        HorizontalDivider(color = TextMuted.copy(0.15f))

        OutlinedTextField(
            value       = number,
            onValueChange = onNumberChange,
            label       = { Text("ফোন নম্বর", color = TextMuted) },
            placeholder = { Text("017xxxxxxxx", color = TextMuted.copy(0.5f)) },
            leadingIcon = {
                Icon(Icons.Default.Phone, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine  = true,
            colors      = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = AccentCyan,
                unfocusedBorderColor    = TextMuted.copy(0.3f),
                focusedTextColor        = TextWhite,
                unfocusedTextColor      = TextWhite,
                focusedContainerColor   = GwBg,
                unfocusedContainerColor = GwBg
            ),
            shape    = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value         = displayName,
            onValueChange = onDisplayNameChange,
            label         = { Text("প্রদর্শন নাম (ঐচ্ছিক)", color = TextMuted) },
            placeholder   = { Text("যেমন: My bKash", color = TextMuted.copy(0.5f)) },
            leadingIcon   = {
                Icon(Icons.Default.Edit, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
            },
            singleLine    = true,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = AccentCyan,
                unfocusedBorderColor    = TextMuted.copy(0.3f),
                focusedTextColor        = TextWhite,
                unfocusedTextColor      = TextWhite,
                focusedContainerColor   = GwBg,
                unfocusedContainerColor = GwBg
            ),
            shape    = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick  = onDismiss,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                border   = androidx.compose.foundation.BorderStroke(1.dp, TextMuted.copy(0.3f))
            ) { Text("বাতিল") }

            Button(
                onClick  = onSave,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) { Text("সেভ করুন", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) }
        }
    }
}

// =============================================================================
// Component 5 — Success Toast
// =============================================================================
@Composable
private fun SuccessToast(message: String) {
    Row(
        modifier             = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AccentGreen.copy(0.15f))
            .border(1.dp, AccentGreen.copy(0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
        Text(message, color = AccentGreen, fontSize = adaptiveTextSize(11.sp, 12.sp), fontWeight = FontWeight.Medium)
    }
}

// =============================================================================
// Component 6 — Error Banner
// =============================================================================
@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = GwCard),
        shape    = RoundedCornerShape(12.dp),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.padding(adaptivePadding(12.dp, 16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CloudOff, null, tint = StatusRed, modifier = Modifier.size(32.dp))
            Text(message, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            Button(
                onClick = onRetry,
                colors  = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape   = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("পুনরায় চেষ্টা", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =============================================================================
// Component 7 — Skeleton Loading Card
// =============================================================================
@Composable
private fun DeviceSkeletonCard() {
    Card(
        colors   = CardDefaults.cardColors(containerColor = GwCard),
        shape    = RoundedCornerShape(12.dp),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = adaptivePadding(10.dp, 16.dp), vertical = 4.dp)
            .height(70.dp)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(TextMuted.copy(0.12f)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(TextMuted.copy(0.12f)))
                Box(Modifier.fillMaxWidth(0.6f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(TextMuted.copy(0.08f)))
            }
        }
    }
}

@Composable
private fun RemoteDeviceSettingsFullScreen(
    device: ChildDeviceDto,
    state: DeviceUiState,
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    onRemoteAddSender: (Int) -> Unit,
    onRemoteDeleteSender: (GatewayMethod) -> Unit
) {
    val activeDevice = state.activeRemoteDevice ?: device
    val isOwnerRole = activeDevice.deviceRole == "owner"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GwBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "পিছনে", tint = TextWhite)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeDevice.customDeviceName.ifEmpty { "ডিভাইস সেটিংস" },
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isOwnerRole) "মালিক ডিভাইস" else "স্টাফ ডিভাইস",
                    color = AccentCyan,
                    fontSize = 12.sp
                )
            }
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = AccentCyan,
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(onClick = { viewModel.saveRemoteDeviceSettings() }) {
                    Text("সেভ", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GwCard, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isOwnerRole) "মালিক মোড" else "স্টাফ মোড",
                        color = if (isOwnerRole) AccentCyan else TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "রোল পরিবর্তনে অ্যাকাউন্ট পিন লাগবে।",
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
                Switch(
                    checked = isOwnerRole,
                    onCheckedChange = { isChecked ->
                        val targetRole = if (isChecked) "owner" else "restricted"
                        viewModel.initiateRoleToggle(activeDevice, targetRole)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AccentCyan,
                        uncheckedTrackColor = ToggleOff,
                        uncheckedBorderColor = ToggleOff
                    )
                )
            }

            OutlinedTextField(
                value = state.remoteDeviceEditName,
                onValueChange = viewModel::onRemoteDeviceEditNameChanged,
                label = { Text("ডিভাইসের নাম", color = TextMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Edit, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = TextMuted.copy(0.3f),
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedContainerColor = GwCard,
                    unfocusedContainerColor = GwCard
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (!isOwnerRole) {
                OutlinedTextField(
                    value = state.remoteDeviceEditPin,
                    onValueChange = viewModel::onRemoteDeviceEditPinChanged,
                    label = { Text("স্টাফ পিন (ঐচ্ছিক)", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = TextMuted.copy(0.3f),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = GwCard,
                        unfocusedContainerColor = GwCard
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "পিন দিলে শুধু ওই ফোনে ওই পিন কাজ করবে। পিন না দিলে একাউন্টের মূল পিন কাজ করবে।",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (state.isRemoteGatewayLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = AccentCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("গেটওয়ে লোড হচ্ছে...", color = TextMuted, fontSize = 12.sp)
                }
            }

            listOf(1, 2).forEach { simSlot ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (simSlot == 1) "📱 ১ নম্বর সিম স্লট" else "📱 ২ নম্বর সিম স্লট",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = if (simSlot == 1) state.remoteDeviceEditSim1Number else state.remoteDeviceEditSim2Number,
                            onValueChange = { viewModel.remoteUpdateSimNumber(simSlot, it) },
                            placeholder = { Text("01XXXXXXXXX", color = TextMuted.copy(0.4f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = TextMuted.copy(0.3f),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = GwCard,
                                unfocusedContainerColor = GwCard
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = if (simSlot == 1) state.remoteDeviceEditSim1Active else state.remoteDeviceEditSim2Active,
                            onCheckedChange = { viewModel.remoteToggleSim(simSlot, it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = AccentGreen,
                                uncheckedTrackColor = ToggleOff,
                                uncheckedBorderColor = ToggleOff
                            )
                        )
                    }
                    RemoteSimTemplateSection(
                        simSlot = simSlot,
                        simActive = if (simSlot == 1) state.remoteDeviceEditSim1Active else state.remoteDeviceEditSim2Active,
                        templates = state.remoteTemplates,
                        methods = state.remoteGatewayMethods,
                        onToggleTemplate = { viewModel.remoteToggleTemplate(simSlot, it) },
                        onToggleMethod = { viewModel.remoteToggleMethod(it) },
                        onDeleteCustomSender = onRemoteDeleteSender,
                        onAddCustomSenderClick = { onRemoteAddSender(simSlot) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("🔒 অ্যাপ অ্যাক্টিভ/ডিঅ্যাক্টিভ", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("ডিভাইসের অ্যাক্সেস লক করতে এটি বন্ধ করুন", color = TextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = state.remoteDeviceEditAppActive,
                    onCheckedChange = viewModel::onRemoteDeviceEditAppActiveChanged,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AccentGreen,
                        uncheckedTrackColor = ToggleOff,
                        uncheckedBorderColor = ToggleOff
                    )
                )
            }
        }
    }
}

@Composable
private fun ChildDeviceCard(
    device: ChildDeviceDto,
    onConfigure: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAppActive = device.isAppActive == 1
    val statusText = if (isAppActive) "সক্রিয়" else "নিষ্ক্রিয়"
    val statusColor = if (isAppActive) AccentGreen else Color(0xFFEF4444)
    val roleLabel = if (device.deviceRole == "owner") "মালিক" else "স্টাফ"
    val roleColor = if (device.deviceRole == "owner") AccentCyan else Color(0xFFF59E0B)

    Card(
        colors = CardDefaults.cardColors(containerColor = GwCard),
        shape = RoundedCornerShape(16.dp),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(adaptivePadding(12.dp, 16.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AccentCyan.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = device.customDeviceName.ifEmpty { "চাইল্ড ডিভাইস" },
                            color = TextWhite,
                            fontSize = adaptiveTextSize(14.sp, 16.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (device.isCurrent == 1) {
                            Text(
                                text = "এই ডিভাইস",
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(AccentCyan.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = roleLabel,
                            color = roleColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(roleColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "• SIM1: ${if(device.simOneActive == 1) "ON" else "OFF"} • SIM2: ${if(device.simTwoActive == 1) "ON" else "OFF"}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onConfigure,
                modifier = Modifier
                    .size(40.dp)
                    .background(GwBg, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "কনফিগার করুন",
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (device.isCurrent != 1) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(40.dp)
                        .background(GwBg, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "মুছুন",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            }
        }
    }
}

// =============================================================================
// Redesigned SIM Card Component
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SimCard(
    simSlot: Int,
    simNumber: String,
    simEnabled: Boolean,
    onSimNumberChange: (String) -> Unit,
    onToggleSim: () -> Unit,
    templates: List<SmsTemplateDto>,
    methods: List<GatewayMethod>,
    onToggleTemplate: (SmsTemplateDto) -> Unit,
    onAddCustomSenderClick: (Int) -> Unit,
    onDeleteCustomSenderClick: (GatewayMethod) -> Unit,
    onToggleMethod: (GatewayMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActiveMethod = methods.any { it.simSlot == simSlot && it.isEnabled == 1 }
    val isConditionMet = simNumber.length == 11 && hasActiveMethod
    
    val switchEnabled = isConditionMet
    val displayEnabled = if (isConditionMet) simEnabled else false

    val dotColor by animateColorAsState(
        if (displayEnabled) AccentGreen else ToggleOff, 
        tween(300), 
        label = "simDot"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = GwCard),
        shape = RoundedCornerShape(16.dp),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = adaptivePadding(10.dp, 16.dp), vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = if (displayEnabled) AccentGreen else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "SIM $simSlot",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (displayEnabled) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentGreen.copy(0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "সক্রিয়",
                                color = AccentGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Switch(
                    checked = displayEnabled,
                    onCheckedChange = { onToggleSim() },
                    enabled = switchEnabled,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AccentGreen,
                        uncheckedTrackColor = ToggleOff,
                        uncheckedBorderColor = ToggleOff
                    )
                )
            }

            // Number Input Row
            OutlinedTextField(
                value = simNumber,
                onValueChange = onSimNumberChange,
                placeholder = { Text("মোবাইল নম্বর দিন", color = TextMuted.copy(0.4f)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = TextMuted.copy(0.3f),
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedContainerColor = GwBg,
                    unfocusedContainerColor = GwBg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Templates selection (FlowRow)
            Text(
                text = "পেমেন্ট পদ্ধতিসমূহ:",
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Render official payment templates (is_parseable = 1)
                templates.filter { it.isOfficial == 1 && it.isParseable == 1 }.forEach { template ->
                    val method = methods.find { it.simSlot == simSlot && it.templateId == template.id }
                    val isSelected = method != null && method.isEnabled == 1
                    
                    TemplateChip(
                        name = template.templateName,
                        dotColor = getDotColor(template.templateName),
                        isSelected = isSelected,
                        logoUrl = template.logoUrl,
                        onClick = { onToggleTemplate(template) }
                    )
                }

                // Render archive/custom senders on this device (is_parseable = 0)
                methods.filter { it.simSlot == simSlot && (it.isParseable ?: 1) == 0 }.forEach { method ->
                    val isSelected = method.isEnabled == 1
                    val displayName = method.senderId ?: method.provider.removePrefix("Custom-")

                    TemplateChip(
                        name = displayName,
                        dotColor = Color(0xFF94A3B8),
                        isSelected = isSelected,
                        onClick = { onToggleMethod(method) },
                        onLongClick = { onDeleteCustomSenderClick(method) }
                    )
                }

                // Other devices' custom senders — visible but inactive on this device
                val localArchiveSenderIds = methods
                    .filter { it.simSlot == simSlot && (it.isParseable ?: 1) == 0 }
                    .mapNotNull { it.senderId?.lowercase() }
                    .toSet()
                templates
                    .filter {
                        it.isOfficial == 0 &&
                            it.isOtherDevice == true &&
                            !localArchiveSenderIds.contains(it.senderId?.lowercase())
                    }
                    .forEach { template ->
                        TemplateChip(
                            name = template.senderId ?: template.templateName,
                            dotColor = Color(0xFF64748B),
                            isSelected = false,
                            enabled = false,
                            onClick = {}
                        )
                    }

                // Render Add Custom Sender Plus Chip
                Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Transparent)
                            .border(BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
                            .clickable { onAddCustomSenderClick(simSlot) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "কাস্টম সেন্ডার যোগ করুন",
                            tint = AccentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "যোগ করুন",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplateChip(
    name: String,
    dotColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    logoUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val borderStroke = if (isSelected) {
        BorderStroke(1.dp, AccentCyan)
    } else {
        BorderStroke(1.dp, TextMuted.copy(alpha = 0.3f))
    }
    
    val bg = if (isSelected) {
        AccentCyan.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

    val textColor = when {
        !enabled -> TextMuted.copy(alpha = 0.45f)
        isSelected -> AccentCyan
        else -> TextMuted
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(borderStroke, RoundedCornerShape(16.dp))
            .then(
                if (enabled) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // প্রোভাইডারের লোগো থাকলে ছোট আকারে দেখাও; না থাকলে রঙিন ডট fallback।
        if (!logoUrl.isNullOrBlank()) {
            RemoteImage(
                url = logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentScale = ContentScale.Fit,
                fallback = {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Text(
            text = name,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteSimTemplateSection(
    simSlot: Int,
    simActive: Boolean,
    templates: List<SmsTemplateDto>,
    methods: List<GatewayMethod>,
    onToggleTemplate: (SmsTemplateDto) -> Unit,
    onToggleMethod: (GatewayMethod) -> Unit,
    onDeleteCustomSender: (GatewayMethod) -> Unit,
    onAddCustomSenderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "পেমেন্ট পদ্ধতিসমূহ:",
            color = TextWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            templates.filter { it.isOfficial == 1 && it.isParseable == 1 }.forEach { template ->
                val method = methods.find { it.simSlot == simSlot && it.templateId == template.id }
                val isSelected = method != null && method.isEnabled == 1
                TemplateChip(
                    name = template.templateName,
                    dotColor = getDotColor(template.templateName),
                    isSelected = isSelected,
                    enabled = simActive,
                    logoUrl = template.logoUrl,
                    onClick = { if (simActive) onToggleTemplate(template) }
                )
            }
            methods.filter { it.simSlot == simSlot && (it.isParseable ?: 1) == 0 }.forEach { method ->
                val isSelected = method.isEnabled == 1
                val displayName = method.senderId ?: method.provider.removePrefix("Custom-")
                TemplateChip(
                    name = displayName,
                    dotColor = Color(0xFF94A3B8),
                    isSelected = isSelected,
                    enabled = simActive,
                    onClick = { if (simActive) onToggleMethod(method) },
                    onLongClick = { if (simActive) onDeleteCustomSender(method) }
                )
            }
            val localArchiveSenderIds = methods
                .filter { it.simSlot == simSlot && (it.isParseable ?: 1) == 0 }
                .mapNotNull { it.senderId?.lowercase() }
                .toSet()
            templates
                .filter {
                    it.isOfficial == 0 &&
                        it.isOtherDevice == true &&
                        !localArchiveSenderIds.contains(it.senderId?.lowercase())
                }
                .forEach { template ->
                    TemplateChip(
                        name = template.senderId ?: template.templateName,
                        dotColor = Color(0xFF64748B),
                        isSelected = false,
                        enabled = false,
                        onClick = {}
                    )
                }
            if (simActive) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
                        .clickable { onAddCustomSenderClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                    Text("যোগ করুন", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!simActive) {
            Text(
                "সিম চালু করলে টেমপ্লেট সিলেক্ট/এডিট করা যাবে",
                color = TextMuted.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }
    }
}

private fun getDotColor(providerName: String): Color {
    return when (providerName.lowercase()) {
        "bkash" -> Color(0xFFE2136E)
        "nagad" -> Color(0xFFEF4123)
        "rocket" -> Color(0xFF6A2C91)
        "upay" -> Color(0xFF00B99B)
        else -> Color(0xFF94A3B8) // Custom sender gets gray dot
    }
}

@Composable
private fun AccountNumbersSheet(
    numbers: List<AccountNumberDto>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDelete: (AccountNumberDto) -> Unit,
    onClose: () -> Unit
) {
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("সক্রিয় নাম্বার", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "একাউন্টের সব অ্যাক্টিভ SIM নাম্বার",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                Row {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "রিফ্রেশ", tint = AccentCyan)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "বন্ধ", tint = TextMuted)
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentCyan.copy(alpha = 0.1f))
                    .padding(10.dp)
            ) {
                Text(
                    "চেকআউটে নাম্বার লুকাতে API Center → চেকআউট নাম্বার মেনু ব্যবহার করুন। এখানে মুছলে সার্ভার থেকে সম্পূর্ণ ডিলিট হবে।",
                    color = AccentCyan,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        when {
            isLoading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                }
            }
            numbers.isEmpty() -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SimCard, null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("কোনো সক্রিয় নাম্বার নেই", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
            else -> {
                items(
                    items = numbers,
                    key = { "${it.phoneNumber}_${it.deviceId}_${it.simSlot}" }
                ) { item ->
                    AccountNumberRow(item = item, onDelete = { onDelete(item) })
                }
            }
        }
    }
}

@Composable
private fun AccountNumberRow(
    item: AccountNumberDto,
    onDelete: () -> Unit
) {
    val healthColor = when (item.healthState?.uppercase()) {
        "ONLINE" -> AccentGreen
        "GRACE" -> Color(0xFFF59E0B)
        "OFFLINE" -> Color(0xFFEF4444)
        "DISABLED" -> Color(0xFF64748B)
        "STALE" -> Color(0xFF94A3B8)
        else -> TextMuted
    }
    val healthLabel = when (item.healthState?.uppercase()) {
        "ONLINE" -> "অনলাইন"
        "GRACE" -> "গ্রেস"
        "OFFLINE" -> "অফলাইন"
        "DISABLED" -> "বন্ধ"
        "STALE" -> "নিষ্ক্রিয়"
        else -> "—"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GwBg.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.phoneNumber,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                "${item.deviceName ?: "ডিভাইস"} • SIM ${item.simSlot}",
                color = TextMuted,
                fontSize = 11.sp
            )
            if (!item.providers.isNullOrEmpty()) {
                Text(
                    item.providers.joinToString(", "),
                    color = TextMuted.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(healthColor)
                )
                Text(healthLabel, color = healthColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "মুছুন", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
            }
        }
    }
}

