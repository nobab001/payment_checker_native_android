package online.paychek.app.ui.screen.device

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import online.paychek.app.data.remote.dto.GatewayMethod
import online.paychek.app.data.remote.dto.ChildDeviceDto
import online.paychek.app.ui.theme.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// =============================================================================
// Design Tokens
// =============================================================================
private val GwBg       = Color(0xFF0F172A)
private val GwCard     = Color(0xFF1E293B)
private val GwCardDrag = Color(0xFF2D3F57)
private val AccentCyan = Color(0xFF22D3EE)
private val AccentGreen= Color(0xFF10B981)
private val TextWhite  = Color(0xFFF8FAFC)
private val TextMuted  = Color(0xFF94A3B8)
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
    val state       by viewModel.state.collectAsStateWithLifecycle()
    val haptic      = LocalHapticFeedback.current
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GwBg)
    ) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ─── Header ──────────────────────────────────────────────────────
            item { DeviceTopBar(isSaving = state.isSaving, onNavigateBack = onNavigateBack) }

            // ─── Success Toast ────────────────────────────────────────────────
            state.successMessage?.let { msg ->
                item { SuccessToast(message = msg) }
            }

            // ─── Tab Row ─────────────────────────────────────────────────────
            item {
                TabRow(
                    selectedTabIndex = state.selectedSubTab,
                    containerColor = GwBg,
                    contentColor = AccentCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedSubTab]),
                            color = AccentCyan
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Tab(
                        selected = state.selectedSubTab == 0,
                        onClick = { viewModel.setSubTab(0) },
                        text = {
                            Text(
                                "ডিভাইস সেটিং",
                                color = if (state.selectedSubTab == 0) AccentCyan else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    )
                    Tab(
                        selected = state.selectedSubTab == 1,
                        onClick = { viewModel.setSubTab(1) },
                        text = {
                            Text(
                                "আদার্স ডিভাইস",
                                color = if (state.selectedSubTab == 1) AccentCyan else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            // ─── Loading Skeleton ─────────────────────────────────────────────
            if (state.isLoading && state.selectedSubTab == 0) {
                items(5) { DeviceSkeletonCard() }
            }

            if (!state.isLoading && state.selectedSubTab == 0) {
                if (state.errorMessage != null) {
                    item {
                        ErrorBanner(
                            message = state.errorMessage!!,
                            onRetry = { viewModel.loadGatewayMethods() },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    // ─── SIM ১ Section ────────────────────────────────────────────
                    item {
                        SimSectionHeader(
                            simSlot   = 1,
                            isEnabled = state.sim1Enabled,
                            onToggle  = { viewModel.toggleSim(1) }
                        )
                    }
                    items(
                        items = sim1Methods,
                        key   = { it.id }
                    ) { method ->
                        ReorderableItem(
                            state   = sim1ListState,
                            key     = method.id
                        ) { isDragging ->
                            GatewayMethodCard(
                                method      = method,
                                simEnabled  = state.sim1Enabled,
                                isDragging  = isDragging,
                                onToggle    = { viewModel.toggleMethod(method) },
                                onEdit      = { viewModel.openEditSheet(method) },
                                dragHandle  = {
                                    Icon(
                                        imageVector        = Icons.Default.DragHandle,
                                        contentDescription = "Drag",
                                        tint               = TextMuted,
                                        modifier           = Modifier
                                            .size(22.dp)
                                            .draggableHandle(
                                                onDragStarted = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            )
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // ─── SIM ২ Section ────────────────────────────────────────────
                    if (sim2Methods.isNotEmpty()) {
                        item {
                            SimSectionHeader(
                                simSlot   = 2,
                                isEnabled = state.sim2Enabled,
                                onToggle  = { viewModel.toggleSim(2) },
                                modifier  = Modifier.padding(top = 12.dp)
                            )
                        }
                        items(
                            items = sim2Methods,
                            key   = { it.id }
                        ) { method ->
                            ReorderableItem(
                                state  = sim2ListState,
                                key    = method.id
                            ) { isDragging ->
                                GatewayMethodCard(
                                    method     = method,
                                    simEnabled = state.sim2Enabled,
                                    isDragging = isDragging,
                                    onToggle   = { viewModel.toggleMethod(method) },
                                    onEdit     = { viewModel.openEditSheet(method) },
                                    dragHandle = {
                                        Icon(
                                            imageVector        = Icons.Default.DragHandle,
                                            contentDescription = "Drag",
                                            tint               = TextMuted,
                                            modifier           = Modifier
                                                .size(22.dp)
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // ─── Empty state ──────────────────────────────────────────────
                    if (state.methods.isEmpty()) {
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
                                    "কোনো গেটওয়ে মেথড পাওয়া যায়নি",
                                    color    = TextMuted,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            if (state.selectedSubTab == 1) {
                // ─── Others Device Sub-Tab ─────────────────────────────────────────
                if (state.isChildDevicesLoading) {
                    items(3) { DeviceSkeletonCard() }
                } else if (state.errorMessage != null) {
                    item {
                        ErrorBanner(
                            message = state.errorMessage!!,
                            onRetry = { viewModel.loadChildDevices() },
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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

        // ─── Dialog — Remote Child Config ──────────────────────────────────────
        if (state.activeRemoteDevice != null) {
            AlertDialog(
                onDismissRequest = { viewModel.closeRemoteDeviceSettings() },
                containerColor = GwCard,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        "চাইল্ড ডিভাইস সেটিংস",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.saveRemoteDeviceSettings() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("সেভ করুন", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.closeRemoteDeviceSettings() },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TextMuted.copy(0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                    ) {
                        Text("বাতিল")
                    }
                }
            )
        }
    }
}

// =============================================================================
// Component 1 — Top Bar
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceTopBar(isSaving: Boolean, onNavigateBack: (() -> Unit)? = null) {
    TopAppBar(
        modifier = Modifier.height(56.dp),
        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        title = {
            Column {
                Text(
                    "ডিভাইস সেটিংস",
                    color      = TextWhite,
                    fontSize   = 16.sp,
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
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(
        if (isEnabled) AccentGreen else ToggleOff, tween(300), label = "simDot"
    )
    Row(
        modifier             = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                fontSize   = 14.sp,
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
            colors          = SwitchDefaults.colors(
                checkedTrackColor   = AccentGreen,
                uncheckedTrackColor = ToggleOff,
                uncheckedBorderColor = ToggleOff
            )
        )
    }
    HorizontalDivider(
        color     = TextMuted.copy(alpha = 0.1f),
        modifier  = Modifier.padding(horizontal = 16.dp)
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
                        fontSize   = 14.sp,
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
        Text(message, color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
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
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                        fontSize = 16.sp,
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

