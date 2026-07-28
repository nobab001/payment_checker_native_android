package online.paychek.app.ui.screen.apicenter.website

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import online.paychek.app.data.remote.dto.CreateManualAccountRequest
import online.paychek.app.data.remote.dto.ManualAccountDto
import online.paychek.app.data.remote.dto.UpdateManualAccountRequest

private val AccentCyan = Color(0xFF22D3EE)
private val AccentGreen = Color(0xFF10B981)
private val AccentAmber = Color(0xFFF59E0B)

private val TAB_OPTIONS = listOf("bank" to "🏦 Bank", "card" to "💳 Card")

@Composable
fun ManualBankAccountsSection(
    card: Color,
    isDark: Boolean,
    accounts: List<ManualAccountDto>,
    onCreate: (CreateManualAccountRequest) -> Unit,
    onUpdate: (Int, UpdateManualAccountRequest) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onDelete: (Int) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ManualAccountDto?>(null) }
    var filterTab by remember { mutableStateOf("bank") }

    ManualSettingsCard(card, isDark, "ব্যাংক / কার্ড অ্যাকাউন্ট", Icons.Default.AccountBalance) {
        Text(
            "SMS টেমপ্লেট ছাড়াই ব্যাংক বা কার্ড একাউন্ট যোগ করুন। কাস্টমার চেকআউটে নম্বর কপি করবে → টাকা পাঠাবে → Verify করবে। শুধু Active অ্যাকাউন্ট দেখাবে।",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TAB_OPTIONS.forEach { (key, label) ->
                FilterChip(
                    selected = filterTab == key,
                    onClick = { filterTab = key },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        val filtered = accounts.filter { it.tab == filterTab }
        if (filtered.isEmpty()) {
            Text(
                if (filterTab == "bank") "কোনো ব্যাংক অ্যাকাউন্ট নেই।" else "কোনো কার্ড অ্যাকাউন্ট নেই।",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        } else {
            filtered.forEach { acct ->
                ManualAccountCard(
                    acct = acct,
                    onEdit = { editing = acct; showEditor = true },
                    onToggle = { onToggle(acct.id, it) },
                    onDelete = { onDelete(acct.id) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        OutlinedButton(
            onClick = {
                editing = null
                showEditor = true
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
            border = BorderStroke(1.dp, AccentGreen)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (filterTab == "bank") "ব্যাংক যোগ করুন" else "কার্ড যোগ করুন")
        }
    }

    if (showEditor) {
        ManualAccountEditorDialog(
            existing = editing,
            defaultTab = editing?.tab ?: filterTab,
            onDismiss = { showEditor = false; editing = null },
            onSaveCreate = { req ->
                onCreate(req)
                showEditor = false
                editing = null
            },
            onSaveUpdate = { id, req ->
                onUpdate(id, req)
                showEditor = false
                editing = null
            }
        )
    }
}

@Composable
private fun ManualAccountCard(
    acct: ManualAccountDto,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(acct.bankName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (acct.accountHolder.isNotBlank()) {
                        Text(acct.accountHolder, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        acct.accountNumber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = AccentCyan,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (acct.branchName.isNotBlank()) {
                        Text("শাখা: ${acct.branchName}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = acct.isActive, onCheckedChange = onToggle)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("এডিট", fontSize = 12.sp)
                }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = AccentAmber)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("মুছুন", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualAccountEditorDialog(
    existing: ManualAccountDto?,
    defaultTab: String,
    onDismiss: () -> Unit,
    onSaveCreate: (CreateManualAccountRequest) -> Unit,
    onSaveUpdate: (Int, UpdateManualAccountRequest) -> Unit,
) {
    var tab by remember(existing?.id) { mutableStateOf(existing?.tab ?: defaultTab) }
    var bankName by remember(existing?.id) { mutableStateOf(existing?.bankName ?: "") }
    var accountHolder by remember(existing?.id) { mutableStateOf(existing?.accountHolder ?: "") }
    var accountNumber by remember(existing?.id) { mutableStateOf(existing?.accountNumber ?: "") }
    var branchName by remember(existing?.id) { mutableStateOf(existing?.branchName ?: "") }
    var routingNumber by remember(existing?.id) { mutableStateOf(existing?.routingNumber ?: "") }
    var instruction by remember(existing?.id) { mutableStateOf(existing?.instruction ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (existing == null) "নতুন অ্যাকাউন্ট" else "অ্যাকাউন্ট এডিট",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TAB_OPTIONS.forEach { (key, label) ->
                        FilterChip(
                            selected = tab == key,
                            onClick = { tab = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text(if (tab == "bank") "ব্যাংকের নাম *" else "কার্ড / প্রোভাইডার নাম *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountHolder,
                    onValueChange = { accountHolder = it },
                    label = { Text("একাউন্ট হোল্ডার") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it },
                    label = { Text("একাউন্ট / কার্ড নম্বর *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    label = { Text("শাখা (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = routingNumber,
                    onValueChange = { routingNumber = it },
                    label = { Text("রাউটিং (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("নির্দেশনা (ঐচ্ছিক)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("বাতিল") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (existing == null) {
                                onSaveCreate(
                                    CreateManualAccountRequest(
                                        tab = tab,
                                        bankName = bankName.trim(),
                                        accountHolder = accountHolder.trim().ifBlank { null },
                                        accountNumber = accountNumber.trim(),
                                        branchName = branchName.trim().ifBlank { null },
                                        routingNumber = routingNumber.trim().ifBlank { null },
                                        instruction = instruction.trim().ifBlank { null }
                                    )
                                )
                            } else {
                                onSaveUpdate(
                                    existing.id,
                                    UpdateManualAccountRequest(
                                        tab = tab,
                                        bankName = bankName.trim(),
                                        accountHolder = accountHolder.trim().ifBlank { null },
                                        accountNumber = accountNumber.trim(),
                                        branchName = branchName.trim().ifBlank { null },
                                        routingNumber = routingNumber.trim().ifBlank { null },
                                        instruction = instruction.trim().ifBlank { null }
                                    )
                                )
                            }
                        },
                        enabled = bankName.isNotBlank() && accountNumber.isNotBlank()
                    ) { Text("সংরক্ষণ") }
                }
            }
        }
    }
}

@Composable
internal fun ManualSettingsCard(
    card: Color,
    isDark: Boolean,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = if (isDark) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
