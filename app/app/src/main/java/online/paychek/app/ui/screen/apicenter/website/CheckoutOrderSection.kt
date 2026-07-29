package online.paychek.app.ui.screen.apicenter.website

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import online.paychek.app.data.remote.dto.CheckoutProviderItemDto

private val OrderCyan = Color(0xFF22D3EE)
private val OrderGreen = Color(0xFF10B981)
private val OrderAmber = Color(0xFFF59E0B)

private val ORDER_TAB_LABELS = mapOf(
    "send_money" to ("💸" to "Send Money"),
    "cash_out" to ("💵" to "Cash Out"),
    "payment" to ("📱" to "Payment"),
    "bank" to ("🏦" to "Bank"),
    "card" to ("💳" to "Card Payment")
)

private fun orderTabLabel(key: String): String =
    ORDER_TAB_LABELS[key]?.let { "${it.first} ${it.second}" } ?: key

private fun orderTabText(key: String): String = ORDER_TAB_LABELS[key]?.second ?: key

private fun orderProvColor(p: String): Color = when (p.lowercase()) {
    "bkash" -> Color(0xFFE2136E)
    "nagad" -> Color(0xFFEF4123)
    "rocket" -> Color(0xFF8C3494)
    "upay" -> Color(0xFF00B99B)
    else -> Color(0xFF94A3B8)
}

/**
 * Per-website checkout ordering editor: reorder TABS (drag + ↑/↓) and reorder /
 * enable-disable PROVIDERS within each tab (↑/↓ + switch). Mirrors the proven
 * long-press-drag pattern used for numbers, but providers use buttons only to keep
 * the nested list robust (no fragile cross-list drag-index mapping).
 *
 * [tabOrder] / [providers] are working copies owned by the caller; the caller folds
 * them into the save request (tab_order / provider_order).
 */
@Composable
fun CheckoutOrderSection(
    card: Color,
    isDark: Boolean,
    tabOrder: List<String>,
    tabEnabled: Map<String, Boolean>,
    onMoveTab: (Int, Int) -> Unit,
    providers: List<CheckoutProviderItemDto>,
    onMoveProvider: (Int, Int) -> Unit,
    onToggleProvider: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3E5E8))

    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = border,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DragIndicator, null, tint = OrderCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "চেকআউট ক্রম সাজান (ট্যাব ও প্রোভাইডার)",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Text(
                "ট্যাব ধরে টেনে বা ↑↓ দিয়ে সাজান। প্রোভাইডার প্রতি ট্যাবে ↑↓ দিয়ে সাজান ও চালু/বন্ধ করুন। গ্রাহক এই ক্রমেই দেখবে।",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            // ── Tabs (drag + arrows) ─────────────────────────────────────────
            Text("ট্যাবের ক্রম", color = OrderCyan, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            if (tabOrder.isEmpty()) {
                Text("কোনো ট্যাব নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else {
                ReorderableList(
                    items = tabOrder,
                    onMove = onMoveTab,
                    rowHeight = 48.dp,
                    draggable = true,
                    border = border,
                    row = { key, index, dragMod, isDragging, onMove ->
                        TabOrderRow(
                            key = key,
                            enabled = tabEnabled[key] != false,
                            index = index,
                            size = tabOrder.size,
                            dragMod = dragMod,
                            isDragging = isDragging,
                            onMove = onMove,
                            card = card,
                            isDark = isDark
                        )
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            // ── Providers grouped by tab (arrows + on/off) ───────────────────
            Text("প্রোভাইডারের ক্রম (ট্যাব অনুযায়ী)", color = OrderCyan, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            val globalIndexByKey = remember(providers) {
                providers.withIndex().associate { it.value.key to it.index }
            }
            var anyProvider = false
            tabOrder.forEach { tabKey ->
                val list = providers.filter { it.tab == tabKey }
                if (list.isNotEmpty()) {
                    anyProvider = true
                    Row(
                        Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(OrderCyan.copy(alpha = 0.7f))
                        )
                        Text(orderTabLabel(tabKey), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                    ReorderableList(
                        items = list,
                        onMove = { lf, lt ->
                            val gf = globalIndexByKey[list[lf].key]
                            val gt = globalIndexByKey[list[lt].key]
                            if (gf != null && gt != null) onMoveProvider(gf, gt)
                        },
                        rowHeight = 56.dp,
                        draggable = false,
                        border = border,
                        row = { p, index, dragMod, isDragging, onMove ->
                            ProviderOrderRow(
                                provider = p,
                                index = index,
                                size = list.size,
                                onMove = onMove,
                                onToggle = onToggleProvider,
                                card = card,
                                isDark = isDark
                            )
                        }
                    )
                }
            }
            if (!anyProvider) {
                Text(
                    "এই সাইটে কোনো SIM প্রোভাইডার নেই (ব্যাংক/কার্ড অ্যাকাউন্ট আলাদা সেকশনে সাজান)।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/** Generic vertical reorder list. When [draggable] is true the grip starts a
 *  long-press drag (local indices); ↑/↓ buttons always work via [onMove]. */
@Composable
private fun <T> ReorderableList(
    items: List<T>,
    onMove: (Int, Int) -> Unit,
    rowHeight: Dp,
    draggable: Boolean,
    border: androidx.compose.foundation.BorderStroke?,
    row: @Composable (item: T, index: Int, dragMod: Modifier, isDragging: Boolean, onMove: (Int, Int) -> Unit) -> Unit
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, item ->
            val isDragging = draggable && draggingIndex == index
            val dragMod = if (draggable) {
                Modifier.pointerInput(index, items.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingIndex = index; dragOffset = 0f },
                        onDragEnd = { draggingIndex = null; dragOffset = 0f },
                        onDragCancel = { draggingIndex = null; dragOffset = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y
                            val cur = draggingIndex ?: return@detectDragGesturesAfterLongPress
                            if (dragOffset > rowHeightPx / 2 && cur < items.lastIndex) {
                                onMove(cur, cur + 1); draggingIndex = cur + 1; dragOffset -= rowHeightPx
                            } else if (dragOffset < -rowHeightPx / 2 && cur > 0) {
                                onMove(cur, cur - 1); draggingIndex = cur - 1; dragOffset += rowHeightPx
                            }
                        }
                    )
                }
            } else {
                Modifier
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { if (isDragging) translationY = dragOffset }
            ) {
                row(item, index, dragMod, isDragging, onMove)
            }
        }
    }
}

@Composable
private fun TabOrderRow(
    key: String,
    enabled: Boolean,
    index: Int,
    size: Int,
    dragMod: Modifier,
    isDragging: Boolean,
    onMove: (Int, Int) -> Unit,
    card: Color,
    isDark: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isDragging) OrderCyan.copy(alpha = 0.18f) else card),
        shape = RoundedCornerShape(12.dp),
        border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3E5E8)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragIndicator, "Drag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragMod.size(26.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                orderTabLabel(key),
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!enabled) {
                Text("(বন্ধ)", color = OrderAmber, fontSize = 10.sp)
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.ArrowUpward, "Up", tint = if (index > 0) OrderCyan else Color.LightGray, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onMove(index, index + 1) }, enabled = index < size - 1, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.ArrowDownward, "Down", tint = if (index < size - 1) OrderCyan else Color.LightGray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ProviderOrderRow(
    provider: CheckoutProviderItemDto,
    index: Int,
    size: Int,
    onMove: (Int, Int) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    card: Color,
    isDark: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(12.dp),
        border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(16.dp).clip(CircleShape).background(orderProvColor(provider.provider))
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    provider.label.ifBlank { provider.provider },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (provider.enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(orderTabText(provider.tab), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = provider.enabled,
                onCheckedChange = { onToggle(provider.key, it) },
                colors = SwitchDefaults.colors(checkedTrackColor = OrderGreen),
                modifier = Modifier.height(28.dp)
            )
            IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ArrowUpward, "Up", tint = if (index > 0) OrderCyan else Color.LightGray, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { onMove(index, index + 1) }, enabled = index < size - 1, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ArrowDownward, "Down", tint = if (index < size - 1) OrderCyan else Color.LightGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}
