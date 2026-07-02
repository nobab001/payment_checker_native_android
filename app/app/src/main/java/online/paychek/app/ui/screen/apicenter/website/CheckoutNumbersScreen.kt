package online.paychek.app.ui.screen.apicenter.website

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val AccentCyan = Color(0xFF22D3EE)
private val AccentGreen = Color(0xFF10B981)

/**
 * CheckoutNumbersScreen — auto-synced active SIM numbers from all devices.
 * Merchant can drag to reorder and enable/disable per number FOR CHECKOUT ONLY.
 * The SMS reader (gateway_methods.is_enabled) is never affected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutNumbersScreen(
    websiteId: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebsiteViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val numbers = state.checkoutNumbers

    LaunchedEffect(websiteId) { viewModel.loadWebsiteDetail(websiteId) }
    LaunchedEffect(state.error, state.infoMessage) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessages() }
        state.infoMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessages() }
    }

    val bg = MaterialTheme.colorScheme.background
    val card = MaterialTheme.colorScheme.surface
    val isDark = bg == Color(0xFF0B0E14)

    // Drag state
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("চেকআউট নাম্বার", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = card)
            )
        },
        bottomBar = {
            Surface(color = card, shadowElevation = 8.dp) {
                Button(
                    onClick = { viewModel.saveCheckoutNumbers(websiteId) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(14.dp).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AccentCyan.copy(alpha = 0.12f)).padding(12.dp)) {
                Text(
                    "সব ডিভাইসের সক্রিয় SIM নাম্বার এখানে স্বয়ংক্রিয়ভাবে দেখাচ্ছে। লম্বা চেপে ধরে টেনে (drag) ক্রম বদলান, আর টগল দিয়ে চেকআউটে দেখানো/লুকানো নিয়ন্ত্রণ করুন। এতে SMS রিডার বন্ধ হবে না।",
                    color = AccentCyan, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(12.dp))

            if (numbers.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SimCard, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("কোনো সক্রিয় SIM নাম্বার পাওয়া যায়নি।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            } else {
                numbers.forEachIndexed { index, num ->
                    val isDragging = draggingIndex == index
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { if (isDragging) translationY = dragOffset }
                            .padding(vertical = 4.dp)
                    ) {
                        NumberRow(
                            provider = num.provider,
                            number = num.number,
                            displayName = num.displayName ?: num.provider,
                            simSlot = num.simSlot,
                            enabled = num.enabled,
                            card = card, isDark = isDark, isDragging = isDragging,
                            onToggle = { viewModel.toggleCheckoutNumber(num.methodId, it) },
                            dragModifier = Modifier.pointerInput(index, numbers.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingIndex = index; dragOffset = 0f },
                                    onDragEnd = { draggingIndex = null; dragOffset = 0f },
                                    onDragCancel = { draggingIndex = null; dragOffset = 0f },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        dragOffset += drag.y
                                        val cur = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                        if (dragOffset > rowHeightPx / 2 && cur < numbers.lastIndex) {
                                            viewModel.moveCheckoutNumber(cur, cur + 1)
                                            draggingIndex = cur + 1
                                            dragOffset -= rowHeightPx
                                        } else if (dragOffset < -rowHeightPx / 2 && cur > 0) {
                                            viewModel.moveCheckoutNumber(cur, cur - 1)
                                            draggingIndex = cur - 1
                                            dragOffset += rowHeightPx
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NumberRow(
    provider: String,
    number: String,
    displayName: String,
    simSlot: Int,
    enabled: Boolean,
    card: Color,
    isDark: Boolean,
    isDragging: Boolean,
    onToggle: (Boolean) -> Unit,
    dragModifier: Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isDragging) AccentCyan.copy(alpha = 0.18f) else card),
        shape = RoundedCornerShape(14.dp),
        border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3E5E8)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DragIndicator, "Drag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragModifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(displayName, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(number, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text("SIM $simSlot • $provider", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Switch(
                checked = enabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
            )
        }
    }
}
