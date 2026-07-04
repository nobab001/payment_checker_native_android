package online.paychek.app.ui.screen.apicenter.website

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import online.paychek.app.data.remote.dto.ActiveNumberDto
import online.paychek.app.data.remote.dto.CheckoutTabDto
import online.paychek.app.data.remote.dto.ProviderBrandingDto
import online.paychek.app.ui.common.RemoteImage

private val Purple = Color(0xFF5B21B6)
private val Bkash = Color(0xFFE2136E)
private val Nagad = Color(0xFFEF4123)
private val Rocket = Color(0xFF8C3494)
private val Upay = Color(0xFF00B99B)

private val TAB_META = listOf(
    "send_money" to ("💸" to "Send Money"),
    "cash_out" to ("💵" to "Cash Out"),
    "payment" to ("📱" to "Payment"),
    "bank" to ("🏦" to "Bank"),
    "card" to ("💳" to "Card")
)

private fun provColor(p: String) = when (p.lowercase()) {
    "bkash", "bKash".lowercase() -> Bkash
    "nagad" -> Nagad
    "rocket" -> Rocket
    "upay" -> Upay
    else -> Color(0xFF94A3B8)
}

private fun providerKey(provider: String?): String =
    (provider ?: "").lowercase().replace(Regex("[^a-z0-9]"), "")

/** Provider branding is keyed per SMS template (`t<templateId>`). */
private fun logoFor(branding: Map<String, ProviderBrandingDto>, templateId: Int?): String? =
    templateId?.let { branding["t$it"]?.logoUrl }?.takeIf { it.isNotBlank() }

/** Provider logo with a colored initial-letter fallback; sized per view. */
@Composable
private fun ProviderLogo(logoUrl: String?, provider: String, size: Dp) {
    val corner = (size.value * 0.24f).dp
    val c = provColor(provider)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(if (logoUrl.isNullOrBlank()) c else Color.White),
        contentAlignment = Alignment.Center
    ) {
        RemoteImage(
            url = logoUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(corner)),
            contentScale = ContentScale.Fit,
            fallback = {
                Text(
                    provider.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.42f).sp
                )
            }
        )
    }
}

private fun resolveTabs(checkoutTabs: Map<String, CheckoutTabDto>): List<Triple<String, String, String>> {
  return TAB_META.map { (id, fb) ->
    val t = checkoutTabs[id]
    Triple(
      id,
      t?.label?.takeIf { it.isNotBlank() } ?: fb.second,
      t?.iconUrl?.takeIf { it.isNotBlank() }?.let { "url:$it" }
        ?: (t?.icon?.takeIf { it.isNotBlank() } ?: fb.first)
    )
  }
}

private fun tabIconText(icon: String): String =
    if (icon.startsWith("url:")) "🖼" else icon

private fun tabIdForCategory(category: String?): String = when (category?.uppercase()) {
    "CASH_OUT" -> "cash_out"
    "PAYMENT" -> "payment"
    "BANK" -> "bank"
    "CARD" -> "card"
    else -> "send_money"
}

private fun numbersForTab(all: List<ActiveNumberDto>, tabId: String): List<ActiveNumberDto> =
    all.filter { n ->
        val tab = n.tab?.takeIf { it.isNotBlank() } ?: tabIdForCategory(n.category)
        tab == tabId
    }

/**
 * Live checkout editor / read-only preview used inside Website Settings.
 * When [editable] is true, mode, design, tabs and number order can be changed inline.
 */
@Composable
fun WebsiteCheckoutLiveEditor(
    companyName: String,
    logoUrl: String?,
    checkoutMode: String,
    onCheckoutModeChange: (String) -> Unit,
    design: String,
    onDesignChange: (String) -> Unit,
    tabStates: Map<String, Boolean>,
    onTabToggle: (String, Boolean) -> Unit,
    numbers: List<ActiveNumberDto>,
    editable: Boolean,
    onMoveNumber: (Int, Int) -> Unit = { _, _ -> },
    onToggleNumber: (Int, Boolean) -> Unit = { _, _ -> },
    checkoutTabs: Map<String, CheckoutTabDto> = emptyMap(),
    providerBranding: Map<String, ProviderBrandingDto> = emptyMap(),
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf("send_money") }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }

    val allTabs = remember(checkoutTabs) { resolveTabs(checkoutTabs) }
    val enabledTabs = allTabs.filter { tabStates[it.first] != false }
    LaunchedEffect(enabledTabs.size, tabStates) {
        if (enabledTabs.none { it.first == activeTab }) {
            activeTab = enabledTabs.firstOrNull()?.first ?: "send_money"
        }
    }
    val tabNumbers = remember(numbers, activeTab) { numbersForTab(numbers, activeTab) }
    val tabNumbersIndexed = remember(numbers, activeTab) {
        tabNumbers.map { n -> numbers.indexOfFirst { it.methodId == n.methodId } }
    }
    val resolvedDesign = remember(design) {
        when (design) {
            "design-4", "design-5" -> "design-3"
            else -> design
        }
    }

    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Purple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .background(Color(0xFFF8FAFC))
    ) {
        // Mini checkout header
        Box(
            Modifier.fillMaxWidth().background(Purple).padding(12.dp)
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(companyName.ifBlank { "Paychek" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("৳ 500.00", color = Color.White.copy(0.9f), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        // Tab bar preview — enabled tabs share full width equally (hidden for design-3)
        if (resolvedDesign != "design-3") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                enabledTabs.forEach { (id, label, icon) ->
                    val sel = id == activeTab
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Purple.copy(0.15f) else Color.Transparent)
                            .clickable { activeTab = id }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(tabIconText(icon), fontSize = 16.sp)
                        Text(
                            label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sel) Purple else Color.Gray,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Design-specific content area
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            when (resolvedDesign) {
                "design-2" -> Design2Preview(tabNumbers, editable, draggingIndex, dragOffset, rowHeightPx,
                    onDragStart = { localIdx -> draggingIndex = tabNumbersIndexed.getOrNull(localIdx) },
                    onDragEnd = { draggingIndex = null; dragOffset = 0f },
                    onDrag = { localIdx, dy ->
                        dragOffset += dy
                        val globalIdx = tabNumbersIndexed.getOrNull(localIdx) ?: return@Design2Preview
                        val cur = draggingIndex ?: return@Design2Preview
                        if (dragOffset > rowHeightPx / 2 && cur < numbers.lastIndex) {
                            onMoveNumber(cur, cur + 1); draggingIndex = cur + 1; dragOffset -= rowHeightPx
                        } else if (dragOffset < -rowHeightPx / 2 && cur > 0) {
                            onMoveNumber(cur, cur - 1); draggingIndex = cur - 1; dragOffset += rowHeightPx
                        }
                    },
                    onToggle = onToggleNumber,
                    providerBranding = providerBranding
                )
                "design-3" -> Design3Preview(
                    tabNumbers,
                    enabledTabs.firstOrNull { it.first == activeTab }?.second ?: "Send Money",
                    enabledTabs.filter { it.first != activeTab },
                    editable,
                    onSwitchTab = { activeTab = it },
                    onToggle = onToggleNumber,
                    providerBranding = providerBranding
                )
                else -> Design1Preview(tabNumbers, editable, draggingIndex, dragOffset, rowHeightPx,
                    onDragStart = { localIdx -> draggingIndex = tabNumbersIndexed.getOrNull(localIdx) },
                    onDragEnd = { draggingIndex = null; dragOffset = 0f },
                    onDrag = { localIdx, dy ->
                        dragOffset += dy
                        val cur = draggingIndex ?: return@Design1Preview
                        if (dragOffset > rowHeightPx / 2 && cur < numbers.lastIndex) {
                            onMoveNumber(cur, cur + 1); draggingIndex = cur + 1; dragOffset -= rowHeightPx
                        } else if (dragOffset < -rowHeightPx / 2 && cur > 0) {
                            onMoveNumber(cur, cur - 1); draggingIndex = cur - 1; dragOffset += rowHeightPx
                        }
                    },
                    onToggle = onToggleNumber,
                    globalIndices = tabNumbersIndexed,
                    providerBranding = providerBranding
                )
            }
        }

        if (editable) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("চেকআউট মোড", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Purple)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = checkoutMode == "transaction",
                        onClick = { onCheckoutModeChange("transaction") },
                        label = { Text("Transaction", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = checkoutMode == "merchant_vibe",
                        onClick = { onCheckoutModeChange("merchant_vibe") },
                        label = { Text("Vibe", fontSize = 11.sp) }
                    )
                }
                Text("ডিজাইন", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Purple)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("design-1" to "১", "design-2" to "২", "design-3" to "৩").forEach { (id, lbl) ->
                        FilterChip(selected = design == id || (id == "design-3" && (design == "design-4" || design == "design-5")), onClick = { onDesignChange(id) }, label = { Text(lbl) })
                    }
                }
                Text("ট্যাব", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Purple)
                allTabs.forEach { (key, label, icon) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${tabIconText(icon)} $label", Modifier.weight(1f), fontSize = 12.sp)
                        Switch(
                            checked = tabStates[key] != false,
                            onCheckedChange = { onTabToggle(key, it) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }
        } else if (checkoutMode == "merchant_vibe") {
            Text(
                "Vibe Mode: গ্রাহক প্রথমে নিজের নাম্বার দেবে",
                Modifier.padding(12.dp), fontSize = 11.sp, color = Color.Gray
            )
        }
    }
}

@Composable
private fun Design1Preview(
    numbers: List<ActiveNumberDto>, editable: Boolean,
    draggingIndex: Int?, dragOffset: Float, rowHeightPx: Float,
    onDragStart: (Int) -> Unit, onDragEnd: () -> Unit,
    onDrag: (Int, Float) -> Unit, onToggle: (Int, Boolean) -> Unit,
    globalIndices: List<Int> = numbers.indices.toList(),
    providerBranding: Map<String, ProviderBrandingDto> = emptyMap()
) {
    val grouped = numbers.groupBy { it.displayName ?: it.provider }
    grouped.forEach { (label, items) ->
        // Large logo next to the provider/group name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            ProviderLogo(logoFor(providerBranding, items.firstOrNull()?.templateId), items.firstOrNull()?.provider ?: label, 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(label, color = Purple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        items.forEach { num ->
            val localIdx = numbers.indexOfFirst { it.methodId == num.methodId }
            val globalIdx = globalIndices.getOrNull(localIdx) ?: localIdx
            NumberListRow(num, editable, globalIdx == draggingIndex, dragOffset, rowHeightPx, localIdx, onDragStart, onDragEnd, onDrag, onToggle, logoFor(providerBranding, num.templateId))
        }
    }
    if (numbers.isEmpty()) EmptyNumbers()
}

@Composable
private fun Design2Preview(
    numbers: List<ActiveNumberDto>, editable: Boolean,
    draggingIndex: Int?, dragOffset: Float, rowHeightPx: Float,
    onDragStart: (Int) -> Unit, onDragEnd: () -> Unit,
    onDrag: (Int, Float) -> Unit, onToggle: (Int, Boolean) -> Unit,
    providerBranding: Map<String, ProviderBrandingDto> = emptyMap()
) {
    val providers = numbers.distinctBy { it.provider }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        providers.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { num ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)).padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Card view: large logo displayed vertically above the name
                            ProviderLogo(logoFor(providerBranding, num.templateId), num.provider, 48.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(num.displayName ?: num.provider, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(num.number, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (editable && numbers.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("নাম্বার তালিকা (ড্র্যাগ করে সাজান)", fontSize = 10.sp, color = Color.Gray)
        numbers.forEachIndexed { idx, num ->
            NumberListRow(num, true, idx == draggingIndex, dragOffset, rowHeightPx, idx, onDragStart, onDragEnd, onDrag, onToggle, logoFor(providerBranding, num.templateId))
        }
    }
    if (numbers.isEmpty()) EmptyNumbers()
}

@Composable
private fun Design3Preview(
    numbers: List<ActiveNumberDto>,
    sectionTitle: String,
    otherTabs: List<Triple<String, String, String>>,
    editable: Boolean,
    onSwitchTab: (String) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    providerBranding: Map<String, ProviderBrandingDto> = emptyMap()
) {
    val grouped = numbers.groupBy { it.provider }
    Text(sectionTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Purple, modifier = Modifier.padding(bottom = 6.dp))
    grouped.forEach { (prov, items) ->
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                .padding(bottom = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Large logo next to the provider name
                    ProviderLogo(logoFor(providerBranding, items.firstOrNull()?.templateId), prov, 26.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(prov.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text("▼", color = Color.Gray)
            }
            items.forEach { num ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Small logo next to each number
                    ProviderLogo(logoFor(providerBranding, num.templateId), num.provider, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(num.displayName ?: num.provider, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(num.number, fontSize = 11.sp, color = Color.Gray)
                    }
                    if (editable) {
                        Switch(checked = num.enabled, onCheckedChange = { onToggle(num.methodId, it) }, modifier = Modifier.height(28.dp))
                    } else {
                        Text("কপি", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    otherTabs.forEach { (id, label, icon) ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                .clickable { onSwitchTab(id) }.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tabIconText(icon), fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Text("▼", color = Color.Gray)
        }
        Spacer(Modifier.height(6.dp))
    }
    if (numbers.isEmpty() && otherTabs.isEmpty()) EmptyNumbers()
}

@Composable
private fun NumberListRow(
    num: ActiveNumberDto, editable: Boolean, isDragging: Boolean, dragOffset: Float, rowHeightPx: Float,
    index: Int, onDragStart: (Int) -> Unit, onDragEnd: () -> Unit, onDrag: (Int, Float) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    logoUrl: String? = null
) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { if (isDragging) translationY = dragOffset }.padding(vertical = 2.dp)
    ) {
        Row(
            Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editable) {
                Icon(
                    Icons.Default.DragIndicator, "Drag", tint = Color.Gray,
                    modifier = Modifier.size(22.dp).pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart(index) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, drag -> change.consume(); onDrag(index, drag.y) }
                        )
                    }
                )
            }
            // Small logo next to the number
            ProviderLogo(logoUrl, num.provider, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(num.number, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (editable) {
                Switch(checked = num.enabled, onCheckedChange = { onToggle(num.methodId, it) }, modifier = Modifier.height(28.dp))
            } else {
                Text("কপি", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyNumbers() {
    Text(
        "এই ট্যাবে কোনো অফিশিয়াল পার্সেবল নাম্বার নেই — শুধু is_active=1 ও is_parseable=1 টেমপ্লেট দেখায়",
        Modifier.padding(16.dp), fontSize = 11.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}
