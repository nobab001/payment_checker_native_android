package online.paychek.app.ui.screen.gateway

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import online.paychek.app.data.remote.dto.GatewayMethod
import online.paychek.app.ui.theme.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// =============================================================================
// Design Tokens
// =============================================================================
private val GwBg       = Color(0xFF0F172A)
private val GwCard     = Color(0xFF1E293B)
private val GwCardDrag = Color(0xFF2D3F57)  // Drag করার সময় card color
private val AccentCyan = Color(0xFF22D3EE)
private val AccentGreen= Color(0xFF10B981)
private val TextWhite  = Color(0xFFF8FAFC)
private val TextMuted  = Color(0xFF94A3B8)
private val ToggleOff  = Color(0xFF475569)

// Provider accent color helper
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
// GatewayCustomizerScreen — Root Composable
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayCustomizerScreen(
    modifier: Modifier = Modifier,
    viewModel: GatewayCustomizerViewModel = viewModel()
) {
    val state       by viewModel.state.collectAsStateWithLifecycle()
    val haptic      = LocalHapticFeedback.current
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // SIM-অনুযায়ী তালিকা ভাগ
    val sim1Methods = state.methods.filter { it.simSlot == 1 }
    val sim2Methods = state.methods.filter { it.simSlot == 2 }

    // Reorderable state — LazyColumn-এর জন্য
    val sim1ListState   = rememberReorderableLazyListState(
        lazyListState   = androidx.compose.foundation.lazy.rememberLazyListState()
    ) { from, to ->
        // from/to → পুরো methods list-এ offset প্রয়োগ করতে হবে
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
            item { GatewayTopBar(isSaving = state.isSaving) }

            // ─── Success Toast ────────────────────────────────────────────────
            state.successMessage?.let { msg ->
                item { SuccessToast(message = msg) }
            }

            // ─── Error Card ───────────────────────────────────────────────────
            state.errorMessage?.let { msg ->
                item {
                    ErrorBanner(
                        message = msg,
                        onRetry = { viewModel.loadGatewayMethods() },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // ─── Loading Skeleton ─────────────────────────────────────────────
            if (state.isLoading) {
                items(5) { GatewaySkeletonCard() }
            }

            if (!state.isLoading) {
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
                if (state.methods.isEmpty() && state.errorMessage == null) {
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
    }
}

// =============================================================================
// Component 1 — Top Bar
// =============================================================================
@Composable
private fun GatewayTopBar(isSaving: Boolean) {
    Row(
        modifier             = Modifier
            .fillMaxWidth()
            .background(GwBg)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AccentCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector     = Icons.Default.Tune,
                contentDescription = null,
                tint            = AccentCyan,
                modifier        = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "গেটওয়ে কাস্টমাইজার",
                color      = TextWhite,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "ড্র্যাগ করে মেথড সাজান ও চালু/বন্ধ করুন",
                color    = TextMuted,
                fontSize = 12.sp
            )
        }
        // Saving indicator
        if (isSaving) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CircularProgressIndicator(
                    color       = AccentCyan,
                    modifier    = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Text("সেভ হচ্ছে...", color = AccentCyan, fontSize = 11.sp)
            }
        }
    }
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
            // Provider Color Bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(70.dp)
                    .background(if (isActive) pColor else pColor.copy(alpha = 0.25f))
            )

            // Drag Handle
            Box(
                modifier         = Modifier.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) { dragHandle() }

            // Content
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

            // Toggle + Edit
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
        // Title
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

        // Number field
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

        // Display name field
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
private fun GatewaySkeletonCard() {
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
