package online.paychek.app.ui.screen.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.data.remote.dto.AdminPurchaseHistoryDto
import online.paychek.app.data.remote.dto.AdminUserDto
import online.paychek.app.data.remote.dto.AdminWebsiteDto
import online.paychek.app.ui.theme.RoyalIndigo
import online.paychek.app.ui.theme.StatusGreen
import online.paychek.app.ui.theme.StatusRed
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi

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
    var pendingExtendDays by remember { mutableStateOf<Int?>(null) }
    var extendReason by remember { mutableStateOf("Manual Adjustment") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val extendReasons = remember {
        listOf(
            "Customer Support",
            "Bug Compensation",
            "Promotion",
            "Manual Adjustment",
            "Other"
        )
    }

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
                actions = {
                    IconButton(
                        onClick = { viewModel.openPurchaseHistory() },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(RoyalIndigo.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = "পেমেন্ট হিস্টরি",
                            tint = RoyalIndigo
                        )
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
                    onExtendSubscription = { days ->
                        extendReason = "Manual Adjustment"
                        pendingExtendDays = days
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

    if (uiState.showPurchaseHistory) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closePurchaseHistory() },
            sheetState = sheetState
        ) {
            PurchaseHistorySheet(
                purchases = uiState.purchases,
                isLoading = uiState.isLoadingPurchases,
                isSaving = uiState.isSaving,
                onMark = { id, marked -> viewModel.markPurchase(id, marked) }
            )
        }
    }

    pendingExtendDays?.let { days ->
        AlertDialog(
            onDismissRequest = { pendingExtendDays = null },
            title = { Text("Extend +$days Days") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "কারণ (ঐচ্ছিক)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    extendReasons.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = extendReason == reason,
                                onClick = { extendReason = reason }
                            )
                            Text(reason, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.extendSubscription(days, extendReason)
                        pendingExtendDays = null
                    },
                    enabled = !uiState.isSaving
                ) { Text("Extend") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExtendDays = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminUserSettingsContent(
    user: AdminUserDto,
    websites: List<AdminWebsiteDto>,
    isSaving: Boolean,
    onToggleBlock: (Boolean) -> Unit,
    onExtendSubscription: (Int) -> Unit,
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
        ExtendSubscriptionSection(isSaving = isSaving, onExtend = onExtendSubscription)
        QuickActionsRow(
            user = user,
            isSaving = isSaving,
            onToggleBlock = onToggleBlock
        )
        DevicesCountSection(deviceCount = user.devices.size)
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
private fun ExtendSubscriptionSection(
    isSaving: Boolean,
    onExtend: (Int) -> Unit
) {
    val options = listOf(1, 3, 7, 15, 30)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Extend Subscription",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = RoyalIndigo
            )
            Text(
                "বর্তমান সক্রিয় সাবস্ক্রিপশন/ট্রায়ালের মেয়াদ বাড়াবে। একাধিক ক্যাটাগরি থাকলে Shared Expiry অনুযায়ী সবগুলো একসাথে বাড়বে।",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { days ->
                    FilledTonalButton(
                        onClick = { onExtend(days) },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("+$days", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    user: AdminUserDto,
    isSaving: Boolean,
    onToggleBlock: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = { onToggleBlock(!user.blocked) },
            enabled = !isSaving,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (user.blocked) StatusGreen else StatusRed
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                if (user.blocked) Icons.Default.LockOpen else Icons.Default.Block,
                null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (user.blocked) "আনব্লক" else "ব্লক", fontSize = 12.sp)
        }
    }
}

@Composable
private fun DevicesCountSection(deviceCount: Int) {
    SectionHeader(Icons.Default.PhoneAndroid, "ডিভাইস")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "মোট ডিভাইস সংখ্যা",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$deviceCount",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RoyalIndigo
            )
        }
    }
}

@Composable
private fun PurchaseHistorySheet(
    purchases: List<AdminPurchaseHistoryDto>,
    isLoading: Boolean,
    isSaving: Boolean,
    onMark: (Int, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "পেমেন্ট হিস্টরি",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = RoyalIndigo
        )
        Text(
            "প্যাকেজ কেনার ট্রানজেকশন ও সময়সহ ডিটেইলস। মার্ক করে চিহ্নিত রাখুন।",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RoyalIndigo)
                }
            }
            purchases.isEmpty() -> {
                EmptyHint("কোনো পেমেন্ট হিস্টরি নেই।")
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    purchases.forEach { purchase ->
                        PurchaseHistoryCard(
                            purchase = purchase,
                            enabled = !isSaving,
                            onMark = { onMark(purchase.id, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseHistoryCard(
    purchase: AdminPurchaseHistoryDto,
    enabled: Boolean,
    onMark: (Boolean) -> Unit
) {
    val paid = purchase.paidAmount?.let { "৳${formatAmount(it)}" } ?: "—"
    val listPrice = purchase.listPrice?.let { "৳${formatAmount(it)}" }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (purchase.adminMarked) {
                StatusGreen.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (purchase.adminMarked) StatusGreen.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    purchase.packageFullName ?: purchase.packageSku ?: "Package",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (purchase.adminMarked) "মার্কড" else "মার্ক",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Checkbox(
                        checked = purchase.adminMarked,
                        onCheckedChange = onMark,
                        enabled = enabled
                    )
                }
            }
            DetailLine("ইনভয়েস", purchase.invoiceNo ?: "—")
            CopyableTransactionLine(transactionId = purchase.transactionId)
            DetailLine("সময়", formatDateTime(purchase.purchasedAt))
            DetailLine("পরিমাণ", paid)
            if (listPrice != null && purchase.listPrice != purchase.paidAmount) {
                DetailLine("লিস্ট প্রাইস", listPrice)
            }
            if (!purchase.creditApplied.isNullOrZero()) {
                DetailLine("ক্রেডিট", "৳${formatAmount(purchase.creditApplied!!)}")
            }
            DetailLine("মেয়াদ", "${formatDate(purchase.startedAt)} → ${formatDate(purchase.endsAt)}")
            if (!purchase.durationKey.isNullOrBlank() || purchase.durationDays != null) {
                DetailLine(
                    "ডিউরেশন",
                    listOfNotNull(
                        purchase.durationKey?.uppercase(),
                        purchase.durationDays?.let { "$it দিন" }
                    ).joinToString(" · ")
                )
            }
            if (!purchase.purchaseType.isNullOrBlank()) {
                DetailLine("টাইপ", purchase.purchaseType!!)
            }
            if (!purchase.category.isNullOrBlank()) {
                DetailLine("ক্যাটাগরি", purchase.category!!)
            }
            if (!purchase.refundStatus.isNullOrBlank()) {
                DetailLine("রিফান্ড", purchase.refundStatus!!)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyableTransactionLine(transactionId: String?) {
    val context = LocalContext.current
    val value = transactionId?.takeIf { it.isNotBlank() } ?: "—"
    val canCopy = !transactionId.isNullOrBlank()

    fun copyTrx() {
        if (!canCopy) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("transaction_id", transactionId))
        Toast.makeText(context, "ট্রানজেকশন আইডি কপি হয়েছে", Toast.LENGTH_SHORT).show()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("ট্রানজেকশন", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (canCopy) {
                        Modifier.combinedClickable(
                            onClick = { copyTrx() },
                            onLongClick = { copyTrx() }
                        )
                    } else Modifier
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (canCopy) RoyalIndigo else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            if (canCopy) {
                IconButton(
                    onClick = { copyTrx() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "কপি",
                        tint = RoyalIndigo,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun Double?.isNullOrZero(): Boolean = this == null || this == 0.0

private fun formatAmount(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.2f", value)
}

private fun formatDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return raw.take(10)
}

private fun formatDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    val cleaned = raw.replace('T', ' ')
    return if (cleaned.length >= 19) cleaned.take(19) else cleaned.take(16)
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
