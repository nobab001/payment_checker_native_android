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
import androidx.compose.foundation.clickable
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
    var hasShownReminder by remember { mutableStateOf(false) }
    var showExpiryReminderDialog by remember { mutableStateOf(false) }

    val successStats = (screenState.uiState as? DashboardUiState.Success)?.stats
    val isPaid = successStats?.isPaid ?: false
    val daysRemaining = remember(successStats?.expiryDate) {
        calculateDaysRemaining(successStats?.expiryDate)
    }

    LaunchedEffect(isPaid, daysRemaining) {
        if (isPaid && daysRemaining in 0..30 && !hasShownReminder) {
            showExpiryReminderDialog = true
        }
    }

    if (screenState.showPurchaseDialog) {
        SubscriptionPurchaseDialog(
            plans = screenState.plans,
            isLoading = screenState.purchaseLoading,
            onDismiss = { viewModel.setShowPurchaseDialog(false) },
            onPurchase = { planName ->
                viewModel.purchaseSubscription(planName) { result ->
                    result.fold(
                        onSuccess = {
                            android.widget.Toast.makeText(context, "${planName} প্যাকেজ সক্রিয় হয়েছে।", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            android.widget.Toast.makeText(context, error.message ?: "প্যাকেজ ক্রয় ব্যর্থ হয়েছে।", android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        )
    }

    if (showExpiryReminderDialog) {
        ExpiryReminderDialog(
            daysRemaining = daysRemaining,
            onDismiss = {
                showExpiryReminderDialog = false
                hasShownReminder = true
            },
            onBuyPlanClick = {
                showExpiryReminderDialog = false
                hasShownReminder = true
                viewModel.setShowPurchaseDialog(true)
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
                HeaderWelcomeCard(
                    userName = screenState.userName,
                    activePlanName = successStats?.activePlanName ?: "FREE_LEVEL",
                    expiryDate = successStats?.expiryDate,
                    isPaid = isPaid
                )
            }

            // ─── Subscribed Plan Info / Purchase Banner ─────────────────
            item {
                if (successStats != null) {
                    PlanStatusCard(
                        isPaid = isPaid,
                        activePlanName = successStats.activePlanName,
                        expiryDate = successStats.expiryDate,
                        onBuyPlanClick = { viewModel.setShowPurchaseDialog(true) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // ─── ২. SMS Monitor Toggle কার্ড ────────────────────────────────
            item {
                SmsMonitorToggleCard(
                    isActive    = screenState.isServiceActive,
                    onToggle    = { enable ->
                        if (enable) {
                            if (!isPaid) {
                                viewModel.setShowPurchaseDialog(true)
                            } else {
                                val simStatus = online.paychek.app.utils.DeviceIdHelper.getSimSlotIds(context)
                                if (simStatus == "no_sims" || simStatus == "permission_denied") {
                                    android.widget.Toast.makeText(
                                        context,
                                        "সক্রিয় সিম কার্ড এবং প্রয়োজনীয় পারমিশন ছাড়া মনিটরিং চালু করা সম্ভব নয়।",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    viewModel.toggleSmsService(true)
                                }
                            }
                        } else {
                            viewModel.toggleSmsService(false)
                        }
                    },
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
private fun HeaderWelcomeCard(
    userName: String,
    activePlanName: String,
    expiryDate: String?,
    isPaid: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GradientHeader)
            .padding(horizontal = 20.dp, vertical = 24.dp)
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

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text       = "স্বাগতম, $userName",
                        color      = Color.White,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Subscription Tier Badge
                    Surface(
                        color = when (activePlanName) {
                            "Premium" -> Color(0xFFFFD700) // Gold
                            "Standard" -> Color(0xFFC0C0C0) // Silver
                            "Basic" -> Color(0xFFCD7F32) // Bronze
                            else -> Color.White.copy(alpha = 0.2f) // FREE_LEVEL
                        },
                        contentColor = if (activePlanName == "FREE_LEVEL") Color.White else Color.Black,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = activePlanName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text     = "Paychek Payment Gateway",
                        color    = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp
                    )
                    
                    if (isPaid && !expiryDate.isNullOrEmpty()) {
                        val banglaDate = formatExpiryDateToBangla(expiryDate)
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            contentColor = Color(0xFF34D399),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "মেয়াদ: $banglaDate",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
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

private fun formatExpiryDateToBangla(dateStr: String?): String {
    if (dateStr.isNullOrEmpty()) return ""
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = inputFormat.parse(dateStr) ?: return dateStr
        val outputFormat = SimpleDateFormat("dd MMMM, yyyy", Locale("bn", "BD"))
        outputFormat.format(date)
    } catch (e: Exception) {
        dateStr
    }
}

private fun calculateDaysRemaining(expiryDateStr: String?): Long {
    if (expiryDateStr.isNullOrEmpty()) return -1L
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val expiryDate = format.parse(expiryDateStr) ?: return -1L
        val today = Date()
        val diffMs = expiryDate.time - today.time
        val days = diffMs / (1000 * 60 * 60 * 24)
        if (days < 0) 0 else days
    } catch (e: Exception) {
        -1L
    }
}

@Composable
private fun ExpiryReminderDialog(
    daysRemaining: Long,
    onDismiss: () -> Unit,
    onBuyPlanClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "⚠️ সাবস্ক্রিপশন মেয়াদ শেষ হচ্ছে",
                color = AccentAmber,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                text = "আপনার বর্তমান সাবস্ক্রিপশন মেয়াদের আর মাত্র $daysRemaining দিন বাকি আছে। সার্ভিস সচল রাখতে এবং পেমেন্ট মনিটরিং অব্যাহত রাখতে অনুগ্রহ করে আপনার প্যাকেজটি রিনিউ করুন।",
                color = TextWhite,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onBuyPlanClick()
                }
            ) {
                Text("রিনিউ করুন", color = AccentCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("পরে করুন", color = TextMuted)
            }
        },
        containerColor = DashCard,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun PlanStatusCard(
    isPaid: Boolean,
    activePlanName: String,
    expiryDate: String?,
    onBuyPlanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fmt = formatExpiryDateToBangla(expiryDate)
    val daysRemaining = calculateDaysRemaining(expiryDate)

    Card(
        colors = CardDefaults.cardColors(containerColor = DashCard),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isPaid) "🔒 সক্রিয় সাবস্ক্রিপশন" else "🔓 ফ্রি মেম্বারশিপ",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isPaid) "প্যাকেজ: $activePlanName" else "কোনো সক্রিয় প্যাকেজ নেই",
                        color = if (isPaid) AccentGreen else AccentAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onBuyPlanClick,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isPaid) AccentCyan else AccentAmber),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isPaid) "রিনিউ করুন" else "প্যাকেজ কিনুন",
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            if (isPaid && !expiryDate.isNullOrEmpty()) {
                HorizontalDivider(color = ToggleOff.copy(alpha = 0.3f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "মেয়াদ শেষ হবে:",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "$fmt (${daysRemaining} দিন বাকি)",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (!isPaid) {
                HorizontalDivider(color = ToggleOff.copy(alpha = 0.3f))
                Text(
                    text = "সাইট বা ডিভাইস হোস্ট করতে দয়া করে একটি সাবস্ক্রিপশন প্যাকেজ কিনুন।",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun SubscriptionPurchaseDialog(
    plans: List<online.paychek.app.data.remote.dto.SubscriptionPlanDto>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onPurchase: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "সাবস্ক্রিপশন প্যাকেজ কিনুন",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 18.sp
            )
        },
        text = {
            if (plans.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentCyan)
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "আপনার পেমেন্ট গেটওয়ে এবং সার্ভিস সচল রাখতে যেকোনো একটি প্যাকেজ বেছে নিন:",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    plans.forEach { plan ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DashCardAlt),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isLoading) { onPurchase(plan.planName) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = plan.planName,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "৳${plan.price}",
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "সীমা: ${plan.maxSites} সাইট | ${plan.maxDevices} ডিভাইস",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "মেয়াদ: ${plan.durationDays} দিন",
                                    color = AccentGreen,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("বন্ধ করুন", color = TextMuted)
            }
        },
        containerColor = DashCard,
        shape = RoundedCornerShape(20.dp)
    )
}
