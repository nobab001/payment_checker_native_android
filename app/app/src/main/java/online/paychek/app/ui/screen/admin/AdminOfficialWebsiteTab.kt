package online.paychek.app.ui.screen.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.paychek.app.data.remote.dto.*
import java.util.UUID

@Composable
fun OfficialWebsiteAdminTab(
    uiState: AdminUiState,
    onSave: (OfficialWebsiteCmsDto) -> Unit,
    onRefreshDemoPayments: () -> Unit = {},
    onMarkRefund: (Int, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val cms = uiState.officialWebsiteCms
    var draft by remember(cms) {
        mutableStateOf(cms ?: OfficialWebsiteCmsDto())
    }
    var subTab by remember { mutableIntStateOf(0) }
    var editingTabIndex by remember { mutableStateOf<Int?>(null) }
    var editingHelplineIndex by remember { mutableStateOf<Int?>(null) }
    var pickingIconFor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(cms) {
        if (cms != null) draft = cms
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ওয়েবসাইট", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "মার্কেটিং সাইটের ট্যাব, লেখা, হেল্পলাইন এবং টেস্ট পেমেন্ট হিস্টরি।",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ScrollableTabRow(selectedTabIndex = subTab, edgePadding = 0.dp) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Hero") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("ট্যাব") })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("হেল্পলাইন") })
            Tab(selected = subTab == 3, onClick = { subTab = 3 }, text = { Text("ডাউনলোড") })
            Tab(selected = subTab == 4, onClick = { subTab = 4 }, text = { Text("টেস্ট পে") })
        }

        when (subTab) {
            0 -> HeroEditor(
                hero = draft.hero,
                onChange = { draft = draft.copy(hero = it) }
            )
            1 -> TabsEditor(
                tabs = draft.tabs.sortedBy { it.order },
                onChange = { list ->
                    draft = draft.copy(tabs = list.mapIndexed { i, t -> t.copy(order = i) })
                },
                onEdit = { editingTabIndex = it }
            )
            2 -> HelplineEditor(
                items = draft.helpline.sortedBy { it.sortOrder },
                onChange = { list ->
                    draft = draft.copy(helpline = list.mapIndexed { i, h -> h.copy(sortOrder = i) })
                },
                onEdit = { editingHelplineIndex = it },
                onPickIcon = { pickingIconFor = it },
                onAdd = {
                    val next = draft.helpline + OfficialHelplineItemDto(
                        id = "hl_${UUID.randomUUID().toString().take(8)}",
                        icon = "whatsapp",
                        label = "WhatsApp",
                        url = "https://wa.me/",
                        sortOrder = draft.helpline.size
                    )
                    draft = draft.copy(helpline = next)
                    editingHelplineIndex = next.lastIndex
                }
            )
            3 -> DownloadEditor(
                download = draft.download,
                onChange = { draft = draft.copy(download = it) }
            )
            4 -> DemoPaymentsAdminSection(
                payments = uiState.demoPayments,
                total = uiState.demoPaymentsTotal,
                onRefresh = onRefreshDemoPayments,
                onMarkRefund = onMarkRefund
            )
        }

        if (subTab != 4) {
            Button(
                onClick = { onSave(draft) },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("সেভ করুন")
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    editingTabIndex?.let { idx ->
        val tabs = draft.tabs.sortedBy { it.order }
        val tab = tabs.getOrNull(idx) ?: return@let
        TabEditDialog(
            tab = tab,
            onDismiss = { editingTabIndex = null },
            onSave = { updated ->
                val mutable = tabs.toMutableList()
                mutable[idx] = updated
                draft = draft.copy(tabs = mutable.mapIndexed { i, t -> t.copy(order = i) })
                editingTabIndex = null
            }
        )
    }

    editingHelplineIndex?.let { idx ->
        val items = draft.helpline.sortedBy { it.sortOrder }
        val item = items.getOrNull(idx) ?: return@let
        HelplineEditDialog(
            item = item,
            icons = uiState.helplineIconIds,
            onDismiss = { editingHelplineIndex = null },
            onPickIcon = { pickingIconFor = idx },
            onSave = { updated ->
                val mutable = items.toMutableList()
                mutable[idx] = updated
                draft = draft.copy(helpline = mutable.mapIndexed { i, h -> h.copy(sortOrder = i) })
                editingHelplineIndex = null
            }
        )
    }

    pickingIconFor?.let { idx ->
        IconPickerDialog(
            icons = uiState.helplineIconIds,
            onDismiss = { pickingIconFor = null },
            onPick = { icon ->
                val items = draft.helpline.sortedBy { it.sortOrder }.toMutableList()
                if (idx in items.indices) {
                    items[idx] = items[idx].copy(icon = icon)
                    draft = draft.copy(helpline = items.mapIndexed { i, h -> h.copy(sortOrder = i) })
                }
                pickingIconFor = null
            }
        )
    }
}

@Composable
private fun DemoPaymentsAdminSection(
    payments: List<DemoPaymentDto>,
    total: Int,
    onRefresh: () -> Unit,
    onMarkRefund: (Int, String) -> Unit
) {
    Text("টেস্ট পেমেন্ট হিস্টরি", fontWeight = FontWeight.SemiBold)
    Text(
        "ওয়েবসাইট টেস্ট থেকে আসা টাকা এখানে দেখাবে। Trx ID দেখে কাস্টমারকে রিফান্ড করুন, তারপর স্ট্যাটাস মার্ক করুন। মার্চেন্ট অ্যাকাউন্টে নয় — শুধু Admin।",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("মোট $total টি", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRefresh) { Text("রিফ্রেশ") }
    }

    if (payments.isEmpty()) {
        Text(
            "এখনো কোনো টেস্ট পেমেন্ট নেই।",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    payments.forEach { p ->
        val refund = (p.refundStatus ?: "none").lowercase()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "৳${p.amount.toInt()} · ${p.purpose ?: "pay"}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        p.status ?: "—",
                        fontSize = 12.sp,
                        color = when ((p.status ?: "").lowercase()) {
                            "success", "verified", "completed" -> Color(0xFF10B981)
                            "initiated", "pending" -> Color(0xFFF59E0B)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    p.visitorDisplayName ?: p.visitorPublicId ?: "Demo visitor",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (!p.trxId.isNullOrBlank()) {
                    Text("Trx: ${p.trxId}", fontSize = 12.sp)
                }
                if (!p.provider.isNullOrBlank() || !p.senderNumber.isNullOrBlank() || !p.receiverNumber.isNullOrBlank()) {
                    Text(
                        listOfNotNull(
                            p.provider?.takeIf { it.isNotBlank() },
                            p.senderNumber?.let { "From $it" },
                            p.receiverNumber?.let { "To $it" }
                        ).joinToString(" · "),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!p.orderId.isNullOrBlank()) {
                    Text("Order: ${p.orderId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!p.sessionToken.isNullOrBlank()) {
                    Text("Session: ${p.sessionToken}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!p.createdAt.isNullOrBlank()) {
                    Text(p.createdAt, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!p.fullSms.isNullOrBlank()) {
                    Text(
                        "Full SMS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(
                            p.fullSms,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp)
                        )
                    }
                }
                Text(
                    when (refund) {
                        "refunded" -> "রিফান্ড: সম্পন্ন"
                        "pending" -> "রিফান্ড: পেন্ডিং"
                        else -> "রিফান্ড: হয়নি"
                    },
                    fontSize = 12.sp,
                    color = when (refund) {
                        "refunded" -> Color(0xFF10B981)
                        "pending" -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (refund != "pending") {
                        OutlinedButton(onClick = { onMarkRefund(p.id, "pending") }) {
                            Text("পেন্ডিং", fontSize = 11.sp)
                        }
                    }
                    if (refund != "refunded") {
                        Button(onClick = { onMarkRefund(p.id, "refunded") }) {
                            Text("রিফান্ডেড", fontSize = 11.sp)
                        }
                    }
                    if (refund != "none") {
                        TextButton(onClick = { onMarkRefund(p.id, "none") }) {
                            Text("রিসেট", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroEditor(hero: OfficialWebsiteHeroDto, onChange: (OfficialWebsiteHeroDto) -> Unit) {
    OutlinedTextField(hero.kicker, { onChange(hero.copy(kicker = it)) }, label = { Text("Kicker") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(hero.title, { onChange(hero.copy(title = it)) }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(hero.lead, { onChange(hero.copy(lead = it)) }, label = { Text("Lead") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    OutlinedTextField(hero.ctaPrimary, { onChange(hero.copy(ctaPrimary = it)) }, label = { Text("Primary CTA") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(hero.ctaSecondary, { onChange(hero.copy(ctaSecondary = it)) }, label = { Text("Secondary CTA") }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun DownloadEditor(
    download: OfficialWebsiteDownloadDto,
    onChange: (OfficialWebsiteDownloadDto) -> Unit
) {
    Text("অ্যাপ ডাউনলোড বাটন", fontWeight = FontWeight.SemiBold)
    Text(
        "হোমে মাঝখানে বড় বাটন; স্ক্রল/অন্য পেজে ডান-উপরে ছোট বাটন।",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = download.enabled, onCheckedChange = { onChange(download.copy(enabled = it)) })
        Spacer(Modifier.width(8.dp))
        Text(if (download.enabled) "Enabled" else "Hidden")
    }
    OutlinedTextField(
        download.label,
        { onChange(download.copy(label = it)) },
        label = { Text("Button label") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        download.url,
        { onChange(download.copy(url = it)) },
        label = { Text("Download URL") },
        modifier = Modifier.fillMaxWidth(),
        supportingText = { Text("/downloads/paycheck.apk বা Play Store লিংক") }
    )
}

@Composable
private fun TabsEditor(
    tabs: List<OfficialWebsiteTabDto>,
    onChange: (List<OfficialWebsiteTabDto>) -> Unit,
    onEdit: (Int) -> Unit
) {
    Text("ট্যাব অর্ডার ও কনটেন্ট", fontWeight = FontWeight.SemiBold)
    tabs.forEachIndexed { index, tab ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tab.navLabel.ifBlank { tab.id }, fontWeight = FontWeight.SemiBold)
                    Text(tab.title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (tab.enabled) "Visible" else "Hidden", fontSize = 11.sp, color = if (tab.enabled) Color(0xFF10B981) else Color(0xFFEF4444))
                }
                IconButton(onClick = {
                    if (index <= 0) return@IconButton
                    val m = tabs.toMutableList()
                    val tmp = m[index - 1]
                    m[index - 1] = m[index]
                    m[index] = tmp
                    onChange(m)
                }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up") }
                IconButton(onClick = {
                    if (index >= tabs.lastIndex) return@IconButton
                    val m = tabs.toMutableList()
                    val tmp = m[index + 1]
                    m[index + 1] = m[index]
                    m[index] = tmp
                    onChange(m)
                }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down") }
                IconButton(onClick = { onEdit(index) }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
            }
        }
    }
}

@Composable
private fun HelplineEditor(
    items: List<OfficialHelplineItemDto>,
    onChange: (List<OfficialHelplineItemDto>) -> Unit,
    onEdit: (Int) -> Unit,
    onPickIcon: (Int) -> Unit,
    onAdd: () -> Unit
) {
    Text("ডিফল্টে একটা WhatsApp থাকবে। + দিয়ে আরও আইকন যোগ করুন — একই আইকন একাধিকবার নেওয়া যায়।", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    items.forEachIndexed { index, item ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(helplineColor(item.icon))
                        .clickable { onPickIcon(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.icon.take(2).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(item.label.ifBlank { item.icon }, fontWeight = FontWeight.SemiBold)
                    Text(item.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = {
                    if (index <= 0) return@IconButton
                    val m = items.toMutableList()
                    val tmp = m[index - 1]
                    m[index - 1] = m[index]
                    m[index] = tmp
                    onChange(m)
                }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                IconButton(onClick = {
                    if (index >= items.lastIndex) return@IconButton
                    val m = items.toMutableList()
                    val tmp = m[index + 1]
                    m[index + 1] = m[index]
                    m[index] = tmp
                    onChange(m)
                }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                IconButton(onClick = { onEdit(index) }) { Icon(Icons.Default.Edit, null) }
                IconButton(onClick = {
                    onChange(items.filterIndexed { i, _ -> i != index })
                }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) }
            }
        }
    }
    OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("আইকন যোগ করুন")
    }
}

@Composable
private fun TabEditDialog(
    tab: OfficialWebsiteTabDto,
    onDismiss: () -> Unit,
    onSave: (OfficialWebsiteTabDto) -> Unit
) {
    var navLabel by remember(tab) { mutableStateOf(tab.navLabel) }
    var sectionLabel by remember(tab) { mutableStateOf(tab.sectionLabel) }
    var title by remember(tab) { mutableStateOf(tab.title) }
    var lead by remember(tab) { mutableStateOf(tab.lead) }
    var enabled by remember(tab) { mutableStateOf(tab.enabled) }
    var cards by remember(tab) { mutableStateOf(tab.cards) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ট্যাব এডিট — ${tab.id}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Visible", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(navLabel, { navLabel = it }, label = { Text("Nav name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sectionLabel, { sectionLabel = it }, label = { Text("Section label") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lead, { lead = it }, label = { Text("Lead text") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("Cards", fontWeight = FontWeight.SemiBold)
                cards.forEachIndexed { i, card ->
                    OutlinedTextField(
                        card.title,
                        { v -> cards = cards.toMutableList().also { it[i] = card.copy(title = v) } },
                        label = { Text("Card ${i + 1} title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        card.body,
                        { v -> cards = cards.toMutableList().also { it[i] = card.copy(body = v) } },
                        label = { Text("Card ${i + 1} body") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    tab.copy(
                        navLabel = navLabel,
                        sectionLabel = sectionLabel,
                        title = title,
                        lead = lead,
                        enabled = enabled,
                        cards = cards
                    )
                )
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun HelplineEditDialog(
    item: OfficialHelplineItemDto,
    icons: List<String>,
    onDismiss: () -> Unit,
    onPickIcon: () -> Unit,
    onSave: (OfficialHelplineItemDto) -> Unit
) {
    var label by remember(item.id) { mutableStateOf(item.label) }
    var url by remember(item.id) { mutableStateOf(item.url) }
    var icon by remember(item.id) { mutableStateOf(item.icon) }
    LaunchedEffect(item.icon, item.label, item.url) {
        icon = item.icon
        label = item.label
        url = item.url
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("হেল্পলাইন এডিট") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(helplineColor(icon))
                            .clickable {
                                onPickIcon()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icon.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onPickIcon) { Text("আইকন বাছুন ($icon)") }
                }
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("Link (https://…)") }, modifier = Modifier.fillMaxWidth())
                Text("আইকন ডুপ্লিকেট করা যায় — একাধিক WhatsApp/Telegram লিঙ্ক OK।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(item.copy(label = label, url = url, icon = icon)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun IconPickerDialog(
    icons: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("আইকন সিলেক্ট") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                icons.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { icon ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(helplineColor(icon))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .clickable { onPick(icon) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(icon, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun helplineColor(icon: String): Color = when (icon.lowercase()) {
    "whatsapp" -> Color(0xFF25D366)
    "telegram" -> Color(0xFF229ED9)
    "youtube" -> Color(0xFFFF0000)
    "facebook" -> Color(0xFF1877F2)
    "messenger" -> Color(0xFF0084FF)
    "instagram" -> Color(0xFFE4405F)
    "discord" -> Color(0xFF5865F2)
    "twitter" -> Color(0xFF111827)
    "linkedin" -> Color(0xFF0A66C2)
    "phone" -> Color(0xFF0D9488)
    "mail" -> Color(0xFF64748B)
    else -> Color(0xFF0F766E)
}
