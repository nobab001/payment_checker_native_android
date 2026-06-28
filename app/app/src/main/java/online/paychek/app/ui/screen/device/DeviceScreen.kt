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
import online.paychek.app.ui.theme.*
import online.paychek.app.ui.components.ConnectivityBanner
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
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val haptic      = LocalHapticFeedback.current
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showAddSenderDialog by remember { mutableStateOf(false) }
    var activeSimSlotForCustomSender by remember { mutableStateOf(1) }
    var customSenderInput by remember { mutableStateOf("") }
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

    var showSimSwapDialogForSlot by remember { mutableStateOf<Int?>(null) }
    var detectedNewSimNumber by remember { mutableStateOf("") }

    LaunchedEffect(state.sim1Number, state.sim2Number) {
        val hasPhoneState = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (hasPhoneState) {
            val (sim1, sim2) = online.paychek.app.utils.DeviceIdHelper.getSimNumbers(context)
            
            // 1. Initial detection auto-fill if currently empty
            val hasAttempted = prefs.getBoolean("pcu_sim_auto_detected", false)
            if (!hasAttempted && state.sim1Number.isBlank() && state.sim2Number.isBlank()) {
                viewModel.autoDetectSimNumbers()
                prefs.edit().putBoolean("pcu_sim_auto_detected", true).apply()
            }
            
            // 2. SIM swap check for mismatched numbers
            if (!sim1.isNullOrBlank() && state.sim1Number.isNotBlank() && sim1 != state.sim1Number) {
                showSimSwapDialogForSlot = 1
                detectedNewSimNumber = sim1
            } else if (!sim2.isNullOrBlank() && state.sim2Number.isNotBlank() && sim2 != state.sim2Number) {
                showSimSwapDialogForSlot = 2
                detectedNewSimNumber = sim2
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

    val isOwner = remember(context) {
        online.paychek.app.utils.SecurePreferences.decrypt(context, "pcu_device_role") == "owner"
    }
    val isRestricted = !isOwner
    val currentSubTab = if (isOwner) state.selectedSubTab else 0

    if (showSimSwapDialogForSlot != null) {
        val slot = showSimSwapDialogForSlot!!
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss on outside touch */ },
            title = {
                Text(
                    text = "সিম কার্ড পরিবর্তন সনাক্ত করা হয়েছে",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "স্লট $slot-এ একটি নতুন সিম কার্ড সনাক্ত করা হয়েছে। নতুন নম্বর: $detectedNewSimNumber\n\nস্লটের বর্তমান কনফিগারেশন নিষ্ক্রিয় করা হবে এবং সার্ভারের সাথে নতুন সিম নম্বরটি সিঙ্ক করা হবে।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentEnabled = if (slot == 1) state.sim1Enabled else state.sim2Enabled
                        if (currentEnabled) {
                            viewModel.toggleSim(slot)
                        }
                        viewModel.onSimNumberChanged(slot, detectedNewSimNumber)
                        viewModel.syncAndValidateSimSwap(slot, detectedNewSimNumber)
                        showSimSwapDialogForSlot = null
                        detectedNewSimNumber = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                ) {
                    Text("ওকে", color = Color.White)
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
        if (!isNetworkAvailable) {
            ConnectivityBanner()
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
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
            // ─── Header ──────────────────────────────────────────────────────
            item { DeviceTopBar(isSaving = state.isSaving, onNavigateBack = onNavigateBack, onRefresh = refreshDeviceData) }

            // ─── Success Toast ────────────────────────────────────────────────
            state.successMessage?.let { msg ->
                item { SuccessToast(message = msg) }
            }

            // ─── Tab Row ─────────────────────────────────────────────────────
            if (isOwner) {
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
                                    "আদার্স ডিভাইস",
                                    color = if (currentSubTab == 1) AccentCyan else TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = adaptiveTextSize(13.sp, 15.sp)
                                )
                            }
                        )
                    }
                }
            }

            // ─── Loading Skeleton ─────────────────────────────────────────────
            if (state.isLoading && currentSubTab == 0) {
                items(5) { DeviceSkeletonCard() }
            }

            if (!state.isLoading && currentSubTab == 0) {
                val isOffline = !isNetworkAvailable
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
                            isRestricted = isRestricted,
                            onAddCustomSenderClick = { slot ->
                                activeSimSlotForCustomSender = slot
                                showAddSenderDialog = true
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
                            isRestricted = isRestricted,
                            onAddCustomSenderClick = { slot ->
                                activeSimSlotForCustomSender = slot
                                showAddSenderDialog = true
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
                val isOffline = !isNetworkAvailable
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
                                "কোনো অন্য ডিভাইস লিঙ্কড নেই",
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
                            modifier = Modifier.padding(horizontal = adaptivePadding(10.dp, 16.dp), vertical = 6.dp)
                        )
                    }
                }
            }
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

        // ─── Bottom Sheet — Remote Child Config ──────────────────────────────────
        val remoteDevice = state.activeRemoteDevice
        if (remoteDevice != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeRemoteDeviceSettings() },
                sheetState       = sheetState,
                containerColor   = GwCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "চাইল্ড ডিভাইস সেটিংস",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    // ─── Custom M3 Switch for Role ──────────────────────────────────
                    val isOwnerRole = remoteDevice.deviceRole == "owner"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GwBg.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isOwnerRole) "মালিক মোড (Full Access)" else "স্টাফ/সীমাবদ্ধ মোড (Read-Only)",
                                color = if (isOwnerRole) AccentCyan else TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "সতর্কতা: এই ডিভাইসের এক্সেস পলিসি পরিবর্তন করতে আপনার মূল অ্যাকাউন্ট পিন কোডটি ভেরিফাই করতে হবে।",
                                color = TextMuted,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = isOwnerRole,
                            onCheckedChange = { isChecked ->
                                val targetRole = if (isChecked) "owner" else "restricted"
                                viewModel.initiateRoleToggle(remoteDevice, targetRole)
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = AccentCyan,
                                uncheckedTrackColor = ToggleOff,
                                uncheckedBorderColor = ToggleOff
                            )
                        )
                    }

                    HorizontalDivider(color = TextMuted.copy(0.15f))

                    // Rename TextField
                    OutlinedTextField(
                        value = state.remoteDeviceEditName,
                        onValueChange = viewModel::onRemoteDeviceEditNameChanged,
                        label = { Text("ডিভাইসের নাম (Rename)", color = TextMuted) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
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

                    // Staff PIN TextField
                    if (!isOwnerRole) {
                        OutlinedTextField(
                            value = state.remoteDeviceEditPin,
                            onValueChange = viewModel::onRemoteDeviceEditPinChanged,
                            label = { Text("স্টাফ ডিভাইসের পিনকোড (ঐচ্ছিক)", color = TextMuted) },
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
                                focusedContainerColor = GwBg,
                                unfocusedContainerColor = GwBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "যদি স্টাফ এর জন্য কোনো পিন কোড না দেন, তবে মালিকের মূল পিন দিয়ে আনলক করা যাবে।",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    HorizontalDivider(color = TextMuted.copy(0.15f))

                    // SIM 1 Config row
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📱 ১ নম্বর সিম স্লট", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.remoteDeviceEditSim1Number,
                                onValueChange = viewModel::onRemoteDeviceEditSim1NumberChanged,
                                placeholder = { Text("SIM 1 Number", color = TextMuted.copy(0.4f)) },
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
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = state.remoteDeviceEditSim1Active,
                                onCheckedChange = viewModel::onRemoteDeviceEditSim1ActiveChanged,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = AccentGreen,
                                    uncheckedTrackColor = ToggleOff,
                                    uncheckedBorderColor = ToggleOff
                                )
                            )
                        }
                    }

                    // SIM 2 Config row
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📱 ২ নম্বর সিম স্লট", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.remoteDeviceEditSim2Number,
                                onValueChange = viewModel::onRemoteDeviceEditSim2NumberChanged,
                                placeholder = { Text("SIM 2 Number", color = TextMuted.copy(0.4f)) },
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
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = state.remoteDeviceEditSim2Active,
                                onCheckedChange = viewModel::onRemoteDeviceEditSim2ActiveChanged,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = AccentGreen,
                                    uncheckedTrackColor = ToggleOff,
                                    uncheckedBorderColor = ToggleOff
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = TextMuted.copy(0.15f))

                    // App active/deactive master switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("🔒 অ্যাপ অ্যাক্টিভ/ডিঅ্যাক্টিভ", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("চাইল্ড ডিভাইসের অ্যাক্সেস লক করতে এটি বন্ধ করুন", color = TextMuted, fontSize = 11.sp)
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.closeRemoteDeviceSettings() },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TextMuted.copy(0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("বাতিল")
                        }
                        Button(
                            onClick = { viewModel.saveRemoteDeviceSettings() },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("সেভ করুন", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
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

        // Add Custom Sender Dialog
        if (showAddSenderDialog) {
            Dialog(
                onDismissRequest = {
                    showAddSenderDialog = false
                    customSenderInput = ""
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
                            onValueChange = { customSenderInput = it },
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
                                        viewModel.addCustomSender(activeSimSlotForCustomSender, customSenderInput.trim()) {
                                            showAddSenderDialog = false
                                            customSenderInput = ""
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
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("ঠিক আছে", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
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
    onRefresh: (() -> Unit)? = null
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
    isRestricted: Boolean = false,
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
                    enabled         = simEnabled && !isRestricted,
                    colors          = SwitchDefaults.colors(
                        checkedTrackColor   = AccentGreen,
                        uncheckedTrackColor = ToggleOff,
                        uncheckedBorderColor = ToggleOff
                    ),
                    modifier = Modifier.height(28.dp)
                )
                if (!isRestricted) {
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
                } else {
                    Spacer(modifier = Modifier.width(32.dp))
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
private fun ChildDeviceCard(
    device: ChildDeviceDto,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAppActive = device.isAppActive == 1
    val statusText = if (isAppActive) "সক্রিয়" else "নিষ্ক্রিয়"
    val statusColor = if (isAppActive) AccentGreen else Color(0xFFEF4444)

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
                    Text(
                        text = device.customDeviceName.ifEmpty { "চাইল্ড ডিভাইস" },
                        color = TextWhite,
                        fontSize = adaptiveTextSize(14.sp, 16.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
    isRestricted: Boolean,
    onAddCustomSenderClick: (Int) -> Unit,
    onDeleteCustomSenderClick: (GatewayMethod) -> Unit,
    onToggleMethod: (GatewayMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActiveMethod = methods.any { it.simSlot == simSlot && it.isEnabled == 1 }
    val isConditionMet = simNumber.length == 11 && hasActiveMethod
    
    val switchEnabled = isConditionMet && !isRestricted
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
                enabled = !isRestricted,
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
                // Render all templates dynamically from the database live list (only official templates here)
                templates.filter { it.isOfficial == 1 }.forEach { template ->
                    val method = methods.find { it.simSlot == simSlot && it.templateId == template.id }
                    val isSelected = method != null && method.isEnabled == 1
                    
                    TemplateChip(
                        name = template.templateName,
                        dotColor = getDotColor(template.templateName),
                        isSelected = isSelected,
                        onClick = {
                            if (!isRestricted) {
                                onToggleTemplate(template)
                            }
                        }
                    )
                }

                // Render custom user chips
                methods.filter { it.simSlot == simSlot && it.isOfficial == 0 }.forEach { method ->
                    val isSelected = method.isEnabled == 1
                    val displayName = method.senderId ?: method.provider.removePrefix("Custom-")

                    TemplateChip(
                        name = displayName,
                        dotColor = Color(0xFF94A3B8),
                        isSelected = isSelected,
                        onClick = {
                            if (!isRestricted) {
                                onToggleMethod(method)
                            }
                        },
                        onLongClick = {
                            if (!isRestricted) {
                                onDeleteCustomSenderClick(method)
                            }
                        }
                    )
                }

                // Render Add Custom Sender Plus Chip
                if (!isRestricted) {
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplateChip(
    name: String,
    dotColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
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

    val textColor = if (isSelected) {
        AccentCyan
    } else {
        TextMuted
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(borderStroke, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = name,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
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


