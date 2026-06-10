package online.paychek.app.ui.screen.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import online.paychek.app.data.remote.dto.DashboardStats
import online.paychek.app.data.remote.dto.TransactionItem
import online.paychek.app.ui.theme.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =============================================================================
// ডিজাইন টোকেন (Dark Premium Gateway Theme)
// =============================================================================
private val DashBg        = Color(0xFF0F172A)   // গভীর নেভি
private val DashCard      = Color(0xFF1E293B)   // Surface Dark
private val DashCardAlt   = Color(0xFF253349)   // Slightly lighter card
private val AccentCyan    = Color(0xFF22D3EE)   // Cyan accent
private val AccentGreen   = Color(0xFF10B981)   // Emerald green (toggle ON)
private val AccentAmber   = Color(0xFFF59E0B)   // Amber (stats icon)
private val TextWhite     = Color(0xFFF8FAFC)
private val TextMuted     = Color(0xFF94A3B8)
private val ToggleOff     = Color(0xFF475569)   // Slate (toggle OFF)

private val GradientHeader = Brush.linearGradient(
    colors = listOf(Color(0xFF1A237E), Color(0xFF0D47A1), Color(0xFF006064))
)

// =============================================================================
// DashboardScreen — Root Composable
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val screenState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showRechargeDialog by remember { mutableStateOf(false) }

    if (showRechargeDialog) {
        RechargeDialog(
            onDismiss = { showRechargeDialog = false },
            onConfirm = { amount ->
                showRechargeDialog = false
                viewModel.rechargeWallet(amount) { result ->
                    result.fold(
                        onSuccess = {
                            android.widget.Toast.makeText(context, "রিচার্জ সফলভাবে সম্পন্ন হয়েছে।", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            android.widget.Toast.makeText(context, error.message ?: "রিচার্জ ব্যর্থ হয়েছে।", android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        )
    }

    PullToRefreshBox(
        isRefreshing = screenState.isRefreshing,
        onRefresh    = { viewModel.onRefresh() },
        modifier     = modifier
            .fillMaxSize()
            .background(DashBg)
    ) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ─── ১. হেডার গ্রেডিয়েন্ট কার্ড ───────────────────────────────
            item {
                HeaderWelcomeCard(userName = screenState.userName)
            }

            // ─── Warning Banner (for negative balance) ───────────────────
            val successStats = (screenState.uiState as? DashboardUiState.Success)?.stats
            if (successStats != null && successStats.walletCredits < 0.0) {
                item {
                    WarningBanner(
                        message = "আপনার অ্যাকাউন্টে বকেয়া বিল রয়েছে। ওটিপি এবং ট্র্যাকিং সচল রাখতে দ্রুত রিচার্জ করুন।",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // ─── Wallet Balance Card ──────────────────────────────────────
            if (successStats != null) {
                item {
                    WalletBalanceCard(
                        balance = successStats.walletCredits,
                        onRechargeClick = {
                            try {
                                val url = "${online.paychek.app.config.AppConfig.BASE_URL}checkout_gateway.html?apiKey=test_merchant_key&amount=50.00"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "ব্রাউজার ওপেন করা সম্ভব হয়নি।", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // ─── ২. SMS Monitor Toggle কার্ড ────────────────────────────────
            item {
                SmsMonitorToggleCard(
                    isActive    = screenState.isServiceActive,
                    onToggle    = { viewModel.toggleSmsService(it) },
                    modifier    = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ─── ৩. Stats কার্ড / লোডিং / এরর ──────────────────────────────
            item {
                AnimatedContent(
                    targetState = screenState.uiState,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "DashboardUiState"
                ) { uiState ->
                    when (uiState) {
                        is DashboardUiState.Loading -> StatsLoadingPlaceholder()
                        is DashboardUiState.Error   -> ErrorCard(
                            message  = uiState.message,
                            onRetry  = { viewModel.loadDashboardStats() }
                        )
                        is DashboardUiState.Success -> StatsGrid(stats = uiState.stats)
                    }
                }
            }

            // ─── ৪. সাম্প্রতিক ট্রানজেকশন ──────────────────────────────────
            val recentList = (screenState.uiState as? DashboardUiState.Success)
                ?.stats?.recentTransactions ?: emptyList()

            if (recentList.isNotEmpty()) {
                item {
                    RecentTransactionsHeader(onSeeAll = onNavigateToHistory)
                }
                items(recentList, key = { it.id }) { trx ->
                    TransactionRow(
                        item     = trx,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// Component 1 — Header Welcome Card (Gradient)
// =============================================================================

@Composable
private fun HeaderWelcomeCard(userName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GradientHeader)
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector     = Icons.Default.AccountBalanceWallet,
                    contentDescription = "Gateway Icon",
                    tint            = Color.White,
                    modifier        = Modifier.size(28.dp)
                )
            }

            Column {
                Text(
                    text       = "স্বাগতম, $userName",
                    color      = Color.White,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text     = "Paychek Payment Gateway",
                    color    = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

// =============================================================================
// Component 2 — SMS Monitor Toggle Card
// =============================================================================

@Composable
private fun SmsMonitorToggleCard(
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(
        targetValue   = if (isActive) AccentGreen else ToggleOff,
        animationSpec = tween(400),
        label         = "DotColor"
    )
    val dotScale by animateFloatAsState(
        targetValue   = if (isActive) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "DotScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = DashCard),
        shape  = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier             = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier             = Modifier.weight(1f)
            ) {
                // Animated status dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(dotScale)
                        .clip(CircleShape)
                        .background(dotColor)
                )

                Column {
                    Text(
                        text       = "💳 SMS পেমেন্ট মনিটর",
                        color      = TextWhite,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text     = if (isActive)
                            "ব্যাকগ্রাউন্ডে সক্রিয়ভাবে ট্র্যাক হচ্ছে"
                        else
                            "নিষ্ক্রিয় — চালু করতে Toggle চাপুন",
                        color    = if (isActive) AccentGreen.copy(alpha = 0.85f) else TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Switch(
                checked         = isActive,
                onCheckedChange = { onToggle(it) },
                colors          = SwitchDefaults.colors(
                    checkedThumbColor       = Color.White,
                    checkedTrackColor       = AccentGreen,
                    uncheckedThumbColor     = Color.White,
                    uncheckedTrackColor     = ToggleOff,
                    uncheckedBorderColor    = ToggleOff
                )
            )
        }
    }
}

// =============================================================================
// Component 3 — Stats Grid (২×২)
// =============================================================================

@Composable
private fun StatsGrid(
    stats: DashboardStats,
    modifier: Modifier = Modifier
) {
    val fmt = DecimalFormat("#,##0.00")

    Column(
        modifier            = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: আজকের মোট পেমেন্ট | সর্বমোট পেমেন্ট
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                icon       = Icons.Default.Today,
                iconColor  = AccentCyan,
                label      = "আজকের মোট পেমেন্ট",
                subLabel   = "Today's Received",
                value      = "৳ ${fmt.format(stats.todayEarnings)}",
                badge      = "${stats.todayTransactions} টি",
                modifier   = Modifier.weight(1f)
            )
            StatCard(
                icon       = Icons.Default.AccountBalance,
                iconColor  = AccentAmber,
                label      = "সর্বমোট পেমেন্ট",
                subLabel   = "Total Received",
                value      = "৳ ${fmt.format(stats.totalEarnings)}",
                badge      = null,
                modifier   = Modifier.weight(1f)
            )
        }

        // Row 2: মোট ট্রানজেকশন | অ্যাক্টিভ ডিভাইস
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                icon       = Icons.Default.ReceiptLong,
                iconColor  = Color(0xFF818CF8),   // Indigo
                label      = "মোট ট্রানজেকশন",
                subLabel   = "Total Transactions",
                value      = "${stats.totalTransactions}",
                badge      = if (stats.unusedCount > 0) "${stats.unusedCount} নতুন" else null,
                badgeColor = AccentGreen,
                modifier   = Modifier.weight(1f)
            )
            StatCard(
                icon       = Icons.Default.PhoneAndroid,
                iconColor  = Color(0xFFF472B6),   // Pink
                label      = "অ্যাক্টিভ ডিভাইস",
                subLabel   = "Active Devices",
                value      = "${stats.activeDevices} টি",
                badge      = null,
                modifier   = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    subLabel: String,
    value: String,
    badge: String?,
    modifier: Modifier = Modifier,
    badgeColor: Color = AccentCyan
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = DashCard),
        shape    = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment    = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector     = icon,
                        contentDescription = label,
                        tint            = iconColor,
                        modifier        = Modifier.size(20.dp)
                    )
                }

                // Badge (যেমন: "১২ নতুন")
                badge?.let {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text     = it,
                            color    = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text       = value,
                color      = TextWhite,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )

            Column {
                Text(
                    text     = label,
                    color    = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text     = subLabel,
                    color    = TextMuted.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// =============================================================================
// Component 4 — সাম্প্রতিক ট্রানজেকশন Header
// =============================================================================

@Composable
private fun RecentTransactionsHeader(
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier             = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment    = Alignment.CenterVertically
    ) {
        Text(
            text       = "🕐 সাম্প্রতিক ট্রানজেকশন",
            color      = TextWhite,
            fontSize   = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        TextButton(onClick = onSeeAll) {
            Text(
                text     = "সব দেখুন →",
                color    = AccentCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// =============================================================================
// Component 5 — একটি ট্রানজেকশন রো
// =============================================================================

@Composable
private fun TransactionRow(
    item: TransactionItem,
    modifier: Modifier = Modifier
) {
    val providerColor = when (item.providerTag.lowercase()) {
        "bkash"  -> BkashPink
        "nagad"  -> NagadOrange
        "rocket" -> RocketPurple
        "upay"   -> UpayTeal
        else     -> AccentCyan
    }
    val providerEmoji = when (item.providerTag.lowercase()) {
        "bkash"  -> "🟢"
        "nagad"  -> "🟠"
        "rocket" -> "🔵"
        "upay"   -> "🟡"
        else     -> "⚪"
    }
    val isSoldOut = item.isUsed == 1

    Card(
        colors   = CardDefaults.cardColors(containerColor = DashCard),
        shape    = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier             = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Provider Color Indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(providerColor)
            )

            // Provider + Amount
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text     = "$providerEmoji ${item.providerTag}",
                        color    = providerColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.simSlot != null) {
                        Text(
                            text     = "SIM ${item.simSlot}",
                            color    = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
                Text(
                    text     = "TrxID: ${item.trxId}",
                    color    = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text     = formatTimestamp(item.smsTimestamp),
                    color    = TextMuted.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }

            // Amount + Status
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "৳ ${DecimalFormat("#,##0.00").format(item.amount)}",
                    color      = TextWhite,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSoldOut) StatusRed.copy(alpha = 0.15f)
                            else AccentGreen.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text     = if (isSoldOut) "SOLDOUT" else "UNUSED",
                        color    = if (isSoldOut) StatusRed else AccentGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

// =============================================================================
// Component 6 — Loading Placeholder (Skeleton-style)
// =============================================================================

@Composable
private fun StatsLoadingPlaceholder() {
    Column(
        modifier            = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                Card(
                    colors   = CardDefaults.cardColors(containerColor = DashCard),
                    shape    = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(110.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color  = AccentCyan,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DashCard)
                )
            }
        }
    }
}

// =============================================================================
// Component 7 — Error Card
// =============================================================================

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = DashCard),
        shape    = RoundedCornerShape(14.dp),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier                = Modifier.padding(20.dp),
            horizontalAlignment     = Alignment.CenterHorizontally,
            verticalArrangement     = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector     = Icons.Default.CloudOff,
                contentDescription = "Error",
                tint            = StatusRed,
                modifier        = Modifier.size(40.dp)
            )
            Text(
                text      = message,
                color     = TextMuted,
                fontSize  = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors  = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape   = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector     = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint            = Color(0xFF0F172A),
                    modifier        = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text  = "পুনরায় চেষ্টা করুন",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =============================================================================
// Utility — Timestamp Format
// =============================================================================

private fun formatTimestamp(raw: String): String {
    return try {
        // ISO 8601 format handle
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = sdf.parse(raw) ?: Date()
        SimpleDateFormat("dd MMM, hh:mm a", Locale("bn", "BD")).format(date)
    } catch (e: Exception) {
        raw.take(16)
    }
}

@Composable
private fun WalletBalanceCard(
    balance: Double,
    onRechargeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isNegative = balance < 0.0
    val statusColor = if (isNegative) Color(0xFFEF4444) else AccentGreen
    val statusLabel = if (isNegative) "বকেয়া বিল" else "চলতি ব্যালেন্স"
    val fmt = DecimalFormat("#,##0.00")

    Card(
        colors = CardDefaults.cardColors(containerColor = DashCard),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusLabel,
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "৳ ${fmt.format(balance)}",
                    color = statusColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "ইউনিক ওয়ালেট আইডেন্টিফায়ার",
                    color = TextMuted.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }

            Button(
                onClick = onRechargeClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = "Recharge",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Balance",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun WarningBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert",
                tint = Color(0xFFFCA5A5),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                color = Color(0xFFFEE2E2),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RechargeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DashCard,
        title = {
            Text(
                text = "ওয়ালেট রিচার্জ",
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "রিচার্জ করার জন্য টাকার পরিমাণ লিখুন (সর্বনিম্ন ৫০ টাকা):",
                    color = TextMuted,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    placeholder = { Text("৳ পরিমাণ লিখুন", color = TextMuted.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = ToggleOff,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                errorText?.let {
                    Text(
                        text = it,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        errorText = "সঠিক পরিমাণ প্রবেশ করান।"
                    } else if (amt < 50.0) {
                        errorText = "সর্বনিম্ন রিচার্জ ৫০ টাকা।"
                    } else {
                        onConfirm(amt)
                    }
                }
            ) {
                Text("নিশ্চিত করুন", color = AccentCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", color = TextMuted)
            }
        }
    )
}
