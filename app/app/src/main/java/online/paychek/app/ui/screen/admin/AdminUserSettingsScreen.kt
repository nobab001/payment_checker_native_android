package online.paychek.app.ui.screen.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.data.remote.dto.AdminDeviceDto
import online.paychek.app.data.remote.dto.AdminUserDto
import online.paychek.app.data.remote.dto.AdminWebsiteDto
import online.paychek.app.ui.theme.RoyalIndigo
import online.paychek.app.ui.theme.StatusGreen
import online.paychek.app.ui.theme.StatusRed

private val AccentCyan = Color(0xFF22D3EE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserSettingsScreen(
    userId: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: AdminUserSettingsViewModel = viewModel(
        key = "admin_user_settings_$userId",
        factory = AdminUserSettingsViewModel.provideFactory(
            userId,
            context.applicationContext as android.app.Application
        )
    )
    val uiState by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ইউজার সেটিংস", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        uiState.user?.let {
                            Text(
                                it.name.ifEmpty { it.phone ?: it.email ?: "User #$userId" },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RoyalIndigo)
                }
            }
            uiState.user == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ইউজার লোড করা যায়নি।", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                AdminUserSettingsContent(
                    user = uiState.user!!,
                    websites = uiState.websites,
                    isSaving = uiState.isSaving,
                    onToggleBlock = { viewModel.toggleUserBlock(it) },
                    onGiveTrial = { viewModel.giveManualGrace(it) },
                    onUpdateDeviceTrial = { id, exp, locked, reason ->
                        viewModel.updateDeviceTrial(id, exp, locked, reason)
                    },
                    onPermissionChange = { siteId, payType, commission, commMenu ->
                        viewModel.setWebsitePermission(siteId, payType, commission, commMenu)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}

@Composable
private fun AdminUserSettingsContent(
    user: AdminUserDto,
    websites: List<AdminWebsiteDto>,
    isSaving: Boolean,
    onToggleBlock: (Boolean) -> Unit,
    onGiveTrial: (Int) -> Unit,
    onUpdateDeviceTrial: (Int, String?, Boolean, String?) -> Unit,
    onPermissionChange: (Int, Boolean?, Boolean?, Boolean?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        UserSummaryCard(user)
        QuickActionsRow(user, isSaving, onToggleBlock, onGiveTrial)
        DevicesSection(user.devices, onUpdateDeviceTrial)
        MerchantApiPermissionsSection(websites, isSaving, onPermissionChange)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun UserSummaryCard(user: AdminUserDto) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(RoyalIndigo.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = RoyalIndigo,
                    fontSize = 20.sp
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user.name.ifEmpty { "Pending Profile" }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(user.phone ?: user.email ?: "—", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniChip(user.role, RoyalIndigo)
                    MiniChip(if (user.isPaid) "PAID" else "FREE", if (user.isPaid) StatusGreen else Color.Gray)
                    MiniChip(if (user.blocked) "Blocked" else "Active", if (user.blocked) StatusRed else StatusGreen)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfoPill("প্ল্যান", user.activePlanName)
            InfoPill("ডিভাইস", "${user.devices.size}")
            InfoPill("মেয়াদ", user.expiryDate?.take(10) ?: "N/A")
        }
    }
}

@Composable
private fun MiniChip(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun InfoPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuickActionsRow(
    user: AdminUserDto,
    isSaving: Boolean,
    onToggleBlock: (Boolean) -> Unit,
    onGiveTrial: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledTonalButton(
            onClick = { onGiveTrial(7) },
            enabled = !isSaving,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("৭ দিন ট্রায়াল", fontSize = 12.sp)
        }
        Button(
            onClick = { onToggleBlock(!user.blocked) },
            enabled = !isSaving,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (user.blocked) StatusGreen else StatusRed
            ),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                if (user.blocked) Icons.Default.LockOpen else Icons.Default.Block,
                null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(if (user.blocked) "আনব্লক" else "ব্লক", fontSize = 12.sp)
        }
    }
}

@Composable
private fun DevicesSection(
    devices: List<AdminDeviceDto>,
    onUpdateDeviceTrial: (Int, String?, Boolean, String?) -> Unit
) {
    SectionHeader(Icons.Default.PhoneAndroid, "ডিভাইস (${devices.size})")
    if (devices.isEmpty()) {
        EmptyHint("কোনো ডিভাইস নেই।")
    } else {
        devices.forEach { dev ->
            CompactDeviceCard(dev) { exp, locked, reason ->
                onUpdateDeviceTrial(dev.id, exp, locked, reason)
            }
        }
    }
}

@Composable
private fun CompactDeviceCard(
    dev: AdminDeviceDto,
    onSave: (String?, Boolean, String?) -> Unit
) {
    var isLocked by remember(dev.id) { mutableStateOf(dev.isTrialLocked) }
    var daysStr by remember(dev.id) { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${dev.deviceName} · ${dev.deviceModel}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "Android ${dev.androidVersion} · ${dev.status} · ব্যাটারি ${dev.lastBatteryPercent ?: 0}%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "ট্রায়াল: ${dev.trialExpiresAt?.take(10) ?: "N/A"}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("লক", fontSize = 12.sp)
                Switch(checked = isLocked, onCheckedChange = { isLocked = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = daysStr,
                    onValueChange = { daysStr = it },
                    placeholder = { Text("±দিন", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )
                FilledTonalButton(
                    onClick = {
                        val addDays = daysStr.toIntOrNull() ?: 0
                        val currentExp = dev.trialExpiresAt?.let {
                            try {
                                val parts = it.substring(0, 10).split("-")
                                java.util.Calendar.getInstance().apply {
                                    set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                                }.time
                            } catch (_: Exception) {
                                java.util.Date()
                            }
                        } ?: java.util.Date()
                        val cal = java.util.Calendar.getInstance().apply { time = currentExp }
                        cal.add(java.util.Calendar.DAY_OF_YEAR, addDays)
                        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        onSave(fmt.format(cal.time), isLocked, if (isLocked) "Admin locked" else null)
                        daysStr = ""
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("সেভ", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MerchantApiPermissionsSection(
    websites: List<AdminWebsiteDto>,
    isSaving: Boolean,
    onPermissionChange: (Int, Boolean?, Boolean?, Boolean?) -> Unit
) {
    SectionHeader(Icons.Default.Api, "মার্চেন্ট API পারমিশন")
    Text(
        "মার্চেন্ট ওয়েবসাইটে পেমেন্ট টাইপ কলব্যাক ও কমিশন ফিচার চালু করতে এডমিন অনুমতি দিন।",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    if (websites.isEmpty()) {
        EmptyHint("এই ইউজারের কোনো ওয়েবসাইট নেই।")
    } else {
        websites.forEach { site ->
            WebsitePermissionCard(site, isSaving, onPermissionChange)
        }
    }
}

@Composable
private fun WebsitePermissionCard(
    site: AdminWebsiteDto,
    isSaving: Boolean,
    onPermissionChange: (Int, Boolean?, Boolean?, Boolean?) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, null, tint = RoyalIndigo, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(site.siteName ?: "Unnamed", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(site.siteUrl ?: "—", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (site.isActive == 1) MiniChip("Live", StatusGreen) else MiniChip("Off", Color.Gray)
            }
            Text(
                "Merchant: ${site.merchantId ?: "—"}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            PermissionToggleRow(
                title = "পেমেন্ট টাইপ কলব্যাক",
                subtitle = "API-তে transaction type পাঠানোর অনুমতি",
                checked = site.allowPaymentTypeCallback == 1,
                enabled = !isSaving,
                onCheckedChange = { onPermissionChange(site.id, it, null, null) }
            )
            PermissionToggleRow(
                title = "কমিশন কলব্যাক",
                subtitle = "API callback-এ কমিশন ডেটা পাঠানোর অনুমতি",
                checked = site.allowCommissionCallback == 1,
                enabled = !isSaving,
                onCheckedChange = { onPermissionChange(site.id, null, it, null) }
            )
            PermissionToggleRow(
                title = "কমিশন মেনু",
                subtitle = "মার্চেন্ট অ্যাপে কমিশন এডিটর দেখাবে",
                checked = site.commissionEnabled == 1,
                enabled = !isSaving,
                onCheckedChange = { onPermissionChange(site.id, null, null, it) }
            )
        }
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = RoyalIndigo, modifier = Modifier.size(18.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RoyalIndigo)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}
