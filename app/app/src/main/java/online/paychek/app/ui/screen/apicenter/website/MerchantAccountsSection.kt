package online.paychek.app.ui.screen.apicenter.website

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import online.paychek.app.data.remote.dto.CreateMerchantAccountRequest
import online.paychek.app.data.remote.dto.MerchantAccountDto
import online.paychek.app.data.remote.dto.UpdateMerchantAccountRequest
import online.paychek.app.ui.common.RemoteImage

private val AccentCyan = Color(0xFF22D3EE)
private val AccentGreen = Color(0xFF10B981)
private val BkashPink = Color(0xFFE2136E)

private val LIVE_PROVIDERS = listOf(
    "bkash" to "bKash",
    "nagad" to "Nagad",
    "rocket" to "Rocket",
    "upay" to "Upay",
    "sslcommerz" to "SSLCommerz",
    "card" to "Card",
    "bank" to "Bank"
)

/**
 * Multi-account live merchant credentials UI.
 * Card per account: name, logo, app key/secret, username/password, active toggle.
 */
@Composable
fun MerchantAccountsSection(
    card: Color,
    isDark: Boolean,
    accounts: List<MerchantAccountDto>,
    onCreate: (CreateMerchantAccountRequest) -> Unit,
    onUpdate: (accountId: Int, UpdateMerchantAccountRequest) -> Unit,
    onToggle: (accountId: Int, active: Boolean) -> Unit,
    onSetDefault: (accountId: Int) -> Unit,
    onDuplicate: (accountId: Int) -> Unit,
    onDelete: (accountId: Int) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MerchantAccountDto?>(null) }

    SettingsCardInner(card, isDark, "লাইভ মার্চেন্ট অ্যাকাউন্ট", Icons.Default.Storefront) {
        Text(
            "bKash/Nagad ইত্যাদির App Key, Secret, Username, Password এখানে দিন। একাধিক অ্যাকাউন্ট যোগ করতে পারবেন — চালু/বন্ধ আলাদা করে। একটা চালু থাকলে কাস্টমার সরাসরি যাবে; একাধিক চালু থাকলে কার্ড দেখাবে।",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))

        if (accounts.isEmpty()) {
            Text(
                "এখনো কোনো লাইভ মার্চেন্ট যোগ হয়নি।",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        } else {
            accounts.groupBy { it.provider }.forEach { (provider, list) ->
                Text(
                    LIVE_PROVIDERS.firstOrNull { it.first == provider }?.second ?: provider,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (provider == "bkash") BkashPink else AccentCyan,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                list.forEach { acct ->
                    MerchantAccountCard(
                        acct = acct,
                        onEdit = { editing = acct; showEditor = true },
                        onToggle = { onToggle(acct.id, it) },
                        onDefault = { onSetDefault(acct.id) },
                        onDuplicate = { onDuplicate(acct.id) },
                        onDelete = { onDelete(acct.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { editing = null; showEditor = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("মার্চেন্ট অ্যাকাউন্ট যোগ করুন")
        }
    }

    if (showEditor) {
        MerchantAccountEditorDialog(
            existing = editing,
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
private fun MerchantAccountCard(
    acct: MerchantAccountDto,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDefault: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF3A3F4A).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (!acct.logoUrl.isNullOrBlank()) {
                    RemoteImage(
                        url = acct.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        fallback = {
                            Text(acct.merchantName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                        }
                    )
                } else {
                    Text(acct.merchantName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = BkashPink)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(acct.merchantName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (acct.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Star, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                    }
                }
                if (!acct.merchantRef.isNullOrBlank()) {
                    Text(acct.merchantRef, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    buildString {
                        if (!acct.appKey.isNullOrBlank()) append("AppKey ✓  ")
                        if (acct.hasAppSecret) append("Secret ✓  ")
                        if (!acct.username.isNullOrBlank()) append("User ✓  ")
                        if (acct.hasPassword) append("Pass ✓")
                        if (isEmpty()) append("ক্রেডেনশিয়াল অসম্পূর্ণ")
                    },
                    fontSize = 10.sp,
                    color = if (acct.hasAppSecret && !acct.username.isNullOrBlank()) AccentGreen
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            Switch(
                checked = acct.isActive,
                onCheckedChange = onToggle,
                modifier = Modifier.height(28.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton("এডিট", Icons.Default.Edit, onEdit)
            if (!acct.isDefault) TextButton("ডিফল্ট", Icons.Default.Star, onDefault)
            TextButton("কপি", Icons.Default.ContentCopy, onDuplicate)
            TextButton("মুছুন", Icons.Default.Delete, onDelete, Color(0xFFEF4444))
        }
    }
}

@Composable
private fun TextButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tint: Color = AccentCyan
) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantAccountEditorDialog(
    existing: MerchantAccountDto?,
    onDismiss: () -> Unit,
    onSaveCreate: (CreateMerchantAccountRequest) -> Unit,
    onSaveUpdate: (Int, UpdateMerchantAccountRequest) -> Unit,
) {
    var provider by remember { mutableStateOf(existing?.provider ?: LIVE_PROVIDERS.first().first) }
    var merchantName by remember { mutableStateOf(existing?.merchantName ?: "") }
    var merchantRef by remember { mutableStateOf(existing?.merchantRef ?: "") }
    var appKey by remember { mutableStateOf(existing?.appKey ?: "") }
    var appSecret by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(existing?.apiKey ?: "") }
    var apiSecret by remember { mutableStateOf("") }
    var callbackUrl by remember { mutableStateOf(existing?.callbackUrl ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var showSecrets by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (existing == null) "নতুন লাইভ মার্চেন্ট" else "মার্চেন্ট এডিট",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(12.dp))

                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Provider", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LIVE_PROVIDERS.forEach { (id, label) ->
                            val sel = id == provider
                            FilterChip(
                                selected = sel,
                                onClick = { if (existing == null) provider = id },
                                enabled = existing == null,
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    CredField("মার্চেন্ট নাম *", merchantName) { merchantName = it }
                    CredField("Merchant Ref / ID (optional)", merchantRef) { merchantRef = it }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("bKash API credentials", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BkashPink)
                    CredField("App Key", appKey) { appKey = it }
                    CredField(
                        if (existing?.hasAppSecret == true) "App Secret (${existing.appSecretMask ?: "••••"}) — খালি রাখলে আগেরটা থাকবে"
                        else "App Secret",
                        appSecret,
                        secret = !showSecrets
                    ) { appSecret = it }
                    CredField("Username", username) { username = it }
                    CredField(
                        if (existing?.hasPassword == true) "Password (${existing.passwordMask ?: "••••"}) — খালি রাখলে আগেরটা থাকবে"
                        else "Password",
                        password,
                        secret = !showSecrets
                    ) { password = it }

                    TextButton(onClick = { showSecrets = !showSecrets }) {
                        Text(if (showSecrets) "সিক্রেট লুকান" else "সিক্রেট দেখান", fontSize = 11.sp)
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("ঐচ্ছিক / অন্য গেটওয়ে", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CredField("API Key (optional)", apiKey) { apiKey = it }
                    CredField(
                        if (existing?.hasApiSecret == true) "API Secret (${existing.apiSecretMask ?: "••••"})"
                        else "API Secret (optional)",
                        apiSecret,
                        secret = !showSecrets
                    ) { apiSecret = it }
                    CredField("Callback URL (optional)", callbackUrl) { callbackUrl = it }
                    CredField("Notes (optional)", notes) { notes = it }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("চালু রাখুন", Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("বাতিল") }
                    Button(
                        onClick = {
                            if (existing == null) {
                                onSaveCreate(
                                    CreateMerchantAccountRequest(
                                        provider = provider,
                                        merchantName = merchantName.trim(),
                                        merchantRef = merchantRef.trim().ifBlank { null },
                                        apiKey = apiKey.trim().ifBlank { null },
                                        apiSecret = apiSecret.ifBlank { null },
                                        username = username.trim().ifBlank { null },
                                        password = password.ifBlank { null },
                                        appKey = appKey.trim().ifBlank { null },
                                        appSecret = appSecret.ifBlank { null },
                                        callbackUrl = callbackUrl.trim().ifBlank { null },
                                        notes = notes.trim().ifBlank { null },
                                        isActive = isActive,
                                        isDefault = false
                                    )
                                )
                            } else {
                                onSaveUpdate(
                                    existing.id,
                                    UpdateMerchantAccountRequest(
                                        merchantName = merchantName.trim(),
                                        merchantRef = merchantRef.trim().ifBlank { null },
                                        apiKey = apiKey.trim().ifBlank { null },
                                        apiSecret = apiSecret.ifBlank { null },
                                        username = username.trim().ifBlank { null },
                                        password = password.ifBlank { null },
                                        appKey = appKey.trim().ifBlank { null },
                                        appSecret = appSecret.ifBlank { null },
                                        callbackUrl = callbackUrl.trim().ifBlank { null },
                                        notes = notes.trim().ifBlank { null },
                                        isActive = isActive
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        enabled = merchantName.isNotBlank()
                    ) { Text("সংরক্ষণ") }
                }
            }
        }
    }
}

@Composable
private fun CredField(
    label: String,
    value: String,
    secret: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text
        ),
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
    )
}

@Composable
private fun SettingsCardInner(
    card: Color,
    isDark: Boolean,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
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
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
