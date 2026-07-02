package online.paychek.app.ui.screen.apicenter.website

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import online.paychek.app.data.remote.dto.CheckoutTabToggle
import online.paychek.app.data.remote.dto.OfficialGatewayDto
import online.paychek.app.data.remote.dto.UpdateWebsiteRequest

private val AccentCyan = Color(0xFF22D3EE)
private val AccentGreen = Color(0xFF10B981)
private val AccentAmber = Color(0xFFF59E0B)

private val CHECKOUT_DESIGNS = listOf(
    "design-1" to "ডিজাইন ১ — লিস্ট",
    "design-2" to "ডিজাইন ২ — কার্ড",
    "design-3" to "ডিজাইন ৩ — অ্যাকর্ডিয়ন"
)

private val CHECKOUT_TAB_KEYS = listOf(
    "send_money" to "Send Money",
    "cash_out" to "Cash Out",
    "payment" to "Payment",
    "bank" to "Bank",
    "card" to "Card Payment"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteSettingsScreen(
    websiteId: Int,
    onNavigateBack: () -> Unit,
    onOpenCheckoutNumbers: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WebsiteViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val site = state.selected

    LaunchedEffect(websiteId) { viewModel.loadWebsiteDetail(websiteId) }
    LaunchedEffect(state.error, state.infoMessage) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessages() }
        state.infoMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessages() }
    }

    val bg = MaterialTheme.colorScheme.background
    val card = MaterialTheme.colorScheme.surface
    val isDark = bg == Color(0xFF0B0E14)

    // Editable fields
    var companyName by remember(site?.id) { mutableStateOf(site?.companyName ?: "") }
    var logoUrl by remember(site?.id) { mutableStateOf(site?.logoUrl ?: "") }
    var theme by remember(site?.id) { mutableStateOf(site?.checkoutTheme?.takeIf { it.startsWith("design-") } ?: "design-1") }
    var checkoutMode by remember(site?.id) { mutableStateOf(site?.checkoutMode ?: "transaction") }
    var successUrl by remember(site?.id) { mutableStateOf(site?.successUrl ?: "") }
    var cancelUrl by remember(site?.id) { mutableStateOf(site?.cancelUrl ?: "") }
    var callbackUrl by remember(site?.id) { mutableStateOf(site?.callbackUrl ?: "") }
    var webhookUrl by remember(site?.id) { mutableStateOf(site?.webhookUrl ?: "") }
    var receivePaymentType by remember(site?.id) { mutableStateOf(site?.receivePaymentType ?: false) }
    var receiveCommission by remember(site?.id) { mutableStateOf(site?.receiveCommission ?: false) }

    val tabStates = remember(site?.id) {
        CHECKOUT_TAB_KEYS.associate { (key, _) -> key to mutableStateOf(key != "bank") }
    }
    LaunchedEffect(state.checkoutTabs) {
        CHECKOUT_TAB_KEYS.forEach { (key, _) ->
            state.checkoutTabs[key]?.let { tabStates[key]?.value = it.enabled }
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text(site?.siteName?.ifBlank { "ওয়েবসাইট সেটিংস" } ?: "ওয়েবসাইট সেটিংস", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = card)
            )
        },
        modifier = modifier
    ) { padding ->
        if (site == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Identity card (read-only)
            SettingsCard(card, isDark, "মার্চেন্ট পরিচিতি", Icons.Default.Badge) {
                ReadOnlyRow("Merchant ID", site.merchantId ?: "-")
                ReadOnlyRow("API Key", site.apiKey)
                ReadOnlyRow("Secret", "•••• ${site.secretLast4 ?: "----"}  (v${site.secretVersion})")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.regenerateSecret(site.id) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber)
                ) {
                    Icon(Icons.Default.Autorenew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Secret Key রিজেনারেট করুন")
                }
            }

            // Branding + Design selection
            SettingsCard(card, isDark, "ব্র্যান্ডিং ও চেকআউট ডিজাইন", Icons.Default.Palette) {
                EditField("Company Name", companyName) { companyName = it }
                EditField("Logo URL", logoUrl) { logoUrl = it }
                Spacer(Modifier.height(6.dp))
                Text("চেকআউট ডিজাইন (Design Selection)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CHECKOUT_DESIGNS.forEach { (id, label) ->
                        val sel = id == theme
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (sel) AccentCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (sel) AccentCyan else Color(0xFF3A3F4A), RoundedCornerShape(20.dp))
                                .clickable { theme = id }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) { Text(label, color = if (sel) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                    }
                }
            }

            // Tab customization
            SettingsCard(card, isDark, "ট্যাব কাস্টমাইজেশন", Icons.Default.Dashboard) {
                Text("চেকআউট পেজে কোন পেমেন্ট ট্যাব দেখাবে তা নির্ধারণ করুন।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                CHECKOUT_TAB_KEYS.forEach { (key, label) ->
                    val tabState = tabStates[key] ?: return@forEach
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = tabState.value,
                            onCheckedChange = { tabState.value = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
                        )
                    }
                }
            }

            // Checkout numbers (auto-synced from devices)
            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3E5E8)),
                modifier = Modifier.fillMaxWidth().clickable { onOpenCheckoutNumbers() }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SimCard, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("চেকআউট নাম্বার", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("ডিভাইসের SIM নাম্বার সাজান / চালু-বন্ধ করুন", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Checkout mode
            SettingsCard(card, isDark, "চেকআউট মোড", Icons.Default.Tune) {
                ModeOption("Transaction Mode", "গ্রাহক নাম্বার কপি করে TrxID দিয়ে ভেরিফাই করবে", checkoutMode == "transaction") { checkoutMode = "transaction" }
                Spacer(Modifier.height(8.dp))
                ModeOption("Merchant Vibe Mode", "গ্রাহক আগে নিজের নাম্বার দেবে, টাকা এলে অটো ম্যাচ", checkoutMode == "merchant_vibe") { checkoutMode = "merchant_vibe" }
            }

            // URLs
            SettingsCard(card, isDark, "রিডাইরেক্ট ও কলব্যাক URL", Icons.Default.Link) {
                EditField("Success URL", successUrl) { successUrl = it }
                EditField("Cancel URL", cancelUrl) { cancelUrl = it }
                EditField("Callback URL", callbackUrl) { callbackUrl = it }
                EditField("Webhook URL", webhookUrl) { webhookUrl = it }
            }

            // Official (redirect-based) payment gateways
            OfficialGatewaysSection(
                card = card, isDark = isDark,
                gateways = state.officialGateways,
                onAdd = { provider, url, name -> viewModel.upsertOfficialGateway(site.id, provider, url, name) },
                onDelete = { gwId -> viewModel.deleteOfficialGateway(site.id, gwId) }
            )

            // Callback preferences (gated by admin permission)
            SettingsCard(card, isDark, "কলব্যাক অপশন", Icons.Default.CallReceived) {
                PermToggle(
                    "Payment Type গ্রহণ", "receive_payment_type",
                    enabled = site.allowPaymentTypeCallback,
                    checked = receivePaymentType && site.allowPaymentTypeCallback
                ) { receivePaymentType = it }
                Spacer(Modifier.height(8.dp))
                PermToggle(
                    "Commission গ্রহণ", "receive_commission",
                    enabled = site.allowCommissionCallback,
                    checked = receiveCommission && site.allowCommissionCallback
                ) { receiveCommission = it }
            }

            // Commission menu (locked until admin permission)
            CommissionSection(
                enabled = site.commissionEnabled,
                card = card, isDark = isDark,
                commissions = state.commissions,
                onSave = { req -> viewModel.upsertCommission(site.id, req) },
                onDelete = { cid -> viewModel.deleteCommission(site.id, cid) }
            )

            // Save
            Button(
                onClick = {
                    viewModel.updateSettings(
                        site.id,
                        UpdateWebsiteRequest(
                            companyName = companyName,
                            logoUrl = logoUrl,
                            checkoutTheme = theme,
                            checkoutMode = checkoutMode,
                            successUrl = successUrl,
                            cancelUrl = cancelUrl,
                            callbackUrl = callbackUrl,
                            webhookUrl = webhookUrl,
                            receivePaymentType = receivePaymentType,
                            receiveCommission = receiveCommission,
                            checkoutTabs = CHECKOUT_TAB_KEYS.associate { (key, _) ->
                                key to CheckoutTabToggle(enabled = tabStates[key]?.value ?: true)
                            }
                        )
                    )
                },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("সেটিংস সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    // Regenerated secret reveal
    val secret = state.revealedSecret
    if (secret != null && state.createdWebsite == null) {
        SecretRevealDialog(secret) { viewModel.dismissSecretReveal() }
    }
}

@Composable
private fun SettingsCard(card: Color, isDark: Boolean, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun EditField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(value, color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        IconButton(onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipData.newPlainText(label, value).toClipEntry())
                Toast.makeText(context, "$label কপি হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.ContentCopy, "Copy", tint = AccentCyan, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val sel = opt == selected
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (sel) AccentCyan.copy(alpha = 0.2f) else Color.Transparent)
                    .border(1.dp, if (sel) AccentCyan else Color(0xFF3A3F4A), RoundedCornerShape(20.dp))
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) { Text(opt, color = if (sel) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ModeOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) AccentCyan else Color(0xFF3A3F4A), RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = AccentCyan))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PermToggle(title: String, apiField: String, enabled: Boolean, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            Text(
                if (enabled) apiField else "Admin অনুমতি প্রয়োজন",
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else AccentAmber,
                fontSize = 10.sp, fontFamily = if (enabled) FontFamily.Monospace else FontFamily.Default
            )
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
        )
    }
}

private val COMMISSION_PAYMENT_TYPES = listOf(
    "bkash_personal" to "bKash Personal",
    "nagad_personal" to "Nagad Personal",
    "rocket_personal" to "Rocket Personal",
    "upay_personal" to "Upay Personal",
    "bkash_agent" to "bKash Agent",
    "nagad_agent" to "Nagad Agent",
    "bkash_merchant" to "bKash Merchant",
    "nagad_merchant" to "Nagad Merchant",
    "card" to "Card",
    "bank" to "Bank"
)

@Composable
private fun CommissionSection(
    enabled: Boolean,
    card: Color,
    isDark: Boolean,
    commissions: List<online.paychek.app.data.remote.dto.CommissionDto>,
    onSave: (online.paychek.app.data.remote.dto.UpsertCommissionRequest) -> Unit,
    onDelete: (Int) -> Unit
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    Box {
        SettingsCard(card, isDark, "Commission", Icons.Default.Percent) {
            if (enabled) {
                Text("Type অনুযায়ী কমিশন/চার্জ যোগ করুন (Percentage বা Flat)।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                if (commissions.isEmpty()) {
                    Text("কোনো কমিশন সেট করা হয়নি।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    commissions.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    COMMISSION_PAYMENT_TYPES.firstOrNull { it.first == c.paymentType }?.second ?: c.paymentType,
                                    color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                                val comm = if (c.commissionType == "percentage") "${c.commissionValue}%" else "৳${c.commissionValue}"
                                val chg = if (c.chargeType == "percentage") "${c.chargeValue}%" else "৳${c.chargeValue}"
                                Text("Commission: $comm · Charge: $chg", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                            IconButton(onClick = { onDelete(c.id) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("কমিশন যোগ করুন")
                }
            } else {
                Column(Modifier.blur(6.dp)) {
                    Text("bKash Personal — 1.5% commission", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Nagad Merchant — flat 5৳ charge", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Card — 2.5% commission", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        if (!enabled) {
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable {
                        Toast.makeText(context, "🔒 Commission ফিচার Admin অনুমতি ছাড়া চালু হবে না।", Toast.LENGTH_LONG).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Locked — Admin অনুমতি প্রয়োজন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }

    if (showDialog) {
        CommissionEditorDialog(onDismiss = { showDialog = false }, onSave = { onSave(it); showDialog = false })
    }
}

@Composable
private fun CommissionEditorDialog(
    onDismiss: () -> Unit,
    onSave: (online.paychek.app.data.remote.dto.UpsertCommissionRequest) -> Unit
) {
    var paymentType by remember { mutableStateOf(COMMISSION_PAYMENT_TYPES.first().first) }
    var commissionType by remember { mutableStateOf("percentage") }
    var commissionValue by remember { mutableStateOf("") }
    var chargeType by remember { mutableStateOf("flat") }
    var chargeValue by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text("কমিশন / চার্জ", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                Text("Payment Type", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COMMISSION_PAYMENT_TYPES.forEach { (id, label) ->
                        val sel = id == paymentType
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (sel) AccentCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (sel) AccentCyan else Color(0xFF3A3F4A), RoundedCornerShape(20.dp))
                                .clickable { paymentType = id }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) { Text(label, color = if (sel) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Commission", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ChipRow(listOf("percentage", "flat"), commissionType) { commissionType = it }
                EditField(if (commissionType == "percentage") "Commission (%)" else "Commission (৳)", commissionValue) { commissionValue = it }
                Spacer(Modifier.height(8.dp))
                Text("Charge", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ChipRow(listOf("percentage", "flat"), chargeType) { chargeType = it }
                EditField(if (chargeType == "percentage") "Charge (%)" else "Charge (৳)", chargeValue) { chargeValue = it }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("বাতিল") }
                    Button(
                        onClick = {
                            onSave(
                                online.paychek.app.data.remote.dto.UpsertCommissionRequest(
                                    paymentType = paymentType,
                                    commissionType = commissionType,
                                    commissionValue = commissionValue.toDoubleOrNull() ?: 0.0,
                                    chargeType = chargeType,
                                    chargeValue = chargeValue.toDoubleOrNull() ?: 0.0,
                                    isActive = true
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) { Text("সংরক্ষণ") }
                }
            }
        }
    }
}

private val OFFICIAL_PROVIDERS = listOf(
    "bkash_merchant" to "bKash Merchant",
    "nagad_merchant" to "Nagad Merchant",
    "rocket_merchant" to "Rocket Merchant",
    "sslcommerz" to "SSLCommerz",
    "card" to "Card",
    "bank" to "Bank"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfficialGatewaysSection(
    card: Color,
    isDark: Boolean,
    gateways: List<OfficialGatewayDto>,
    onAdd: (provider: String, redirectUrl: String, displayName: String?) -> Unit,
    onDelete: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsCard(card, isDark, "অফিসিয়াল পেমেন্ট (রিডাইরেক্ট)", Icons.Default.OpenInNew) {
        Text(
            "bKash/Nagad/Rocket Merchant, SSLCommerz, Card, Bank — এইসব চ্যানেলে PayCheck নিজে পেমেন্ট নেয় না, গ্রাহককে সরাসরি অফিসিয়াল গেটওয়ে পেজে রিডাইরেক্ট করে।",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))

        if (gateways.isEmpty()) {
            Text("কোনো অফিসিয়াল গেটওয়ে যুক্ত করা হয়নি।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        } else {
            gateways.forEach { gw ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            OFFICIAL_PROVIDERS.firstOrNull { it.first == gw.provider }?.second ?: gw.provider,
                            color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        Text(gw.redirectUrlTemplate, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
                    }
                    if (gw.isActive) {
                        Text("Active", color = AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { onDelete(gw.id) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("গেটওয়ে যোগ করুন")
        }
    }

    if (showDialog) {
        var selectedProvider by remember { mutableStateOf(OFFICIAL_PROVIDERS.first().first) }
        var redirectUrl by remember { mutableStateOf("") }
        var displayName by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp)) {
                    Text("অফিসিয়াল গেটওয়ে", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(12.dp))
                    Text("Provider", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OFFICIAL_PROVIDERS.forEach { (id, label) ->
                            val sel = id == selectedProvider
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) AccentCyan.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(1.dp, if (sel) AccentCyan else Color(0xFF3A3F4A), RoundedCornerShape(20.dp))
                                    .clickable { selectedProvider = id }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) { Text(label, color = if (sel) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    EditField("Display Name (optional)", displayName) { displayName = it }
                    EditField("Redirect URL Template", redirectUrl) { redirectUrl = it }
                    Text(
                        "Placeholder: {amount} {order_id} {token} {callback_url} {currency}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { showDialog = false }, modifier = Modifier.weight(1f)) { Text("বাতিল") }
                        Button(
                            onClick = {
                                onAdd(selectedProvider, redirectUrl, displayName)
                                showDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                        ) { Text("সংরক্ষণ") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretRevealDialog(secret: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Dialog(onDismissRequest = { }) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(22.dp)) {
                Text("নতুন Secret Key", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(6.dp))
                Text("⚠ এখনই সংরক্ষণ করুন — পরে আর দেখা যাবে না।", color = AccentAmber, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background)
                        .border(1.dp, Color(0xFF2A2F3A), RoundedCornerShape(8.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(secret, color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipData.newPlainText("Secret Key", secret).toClipEntry())
                            Toast.makeText(context, "Secret Key কপি হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = AccentCyan, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                    Text("সংরক্ষণ করেছি", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
