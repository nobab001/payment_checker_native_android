package online.paychek.app.ui.screen.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.data.remote.dto.TransactionItem
import online.paychek.app.ui.components.ConnectivityBanner
import online.paychek.app.utils.adaptivePadding
import online.paychek.app.utils.adaptiveTextSize
import online.paychek.app.utils.screenWidth
import online.paychek.app.ui.theme.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.utils.GsonUtils
import online.paychek.app.data.remote.dto.GatewayMethod
import com.google.gson.reflect.TypeToken

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke

// =============================================================================
// Design Tokens
// =============================================================================
private val HistBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val HistCard: Color @Composable get() = MaterialTheme.colorScheme.surface
private val AccentCyan   = Color(0xFF22D3EE)
private val AccentGreen  = Color(0xFF10B981)
private val AccentRed    = Color(0xFFEF4444)
private val TextWhite: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

// =============================================================================
// TransactionSearchScreen — Root Composable
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: TransactionSearchViewModel = viewModel()
) {
    val state        by viewModel.state.collectAsStateWithLifecycle()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val listState    = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems  = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3 && totalItems > 0
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) viewModel.loadNextPage()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HistBg)
    ) {
        if (!isNetworkAvailable) {
            ConnectivityBanner()
        }
        
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh    = { viewModel.onRefresh() },
            modifier     = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
        LazyColumn(
            state               = listState,
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ─── ১. Top Bar (Title) ────────────────────────────────────────
            item {
                SearchTopBar()
            }

            // ─── ২. Search Box ─────────────────────────────────────────────
            item {
                SearchBox(
                    query         = state.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onClear       = { viewModel.onSearchQueryChanged("") },
                    onDone        = { focusManager.clearFocus() },
                    modifier      = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ─── ৩. M3 Filter Chips Row ────────────────────────────────────
            item {
                M3FilterChipsRow(
                    selected = state.selectedProvider,
                    onSelect = { provider ->
                        val target = if (state.selectedProvider == provider) "all" else provider
                        viewModel.onProviderFilterChanged(target)
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ─── ৩.৫ Date Range Filter ─────────────────────────────────────
            item {
                DateRangeFilterRow(
                    startDate = state.startDate,
                    endDate = state.endDate,
                    onDateRangeSelected = { start, end ->
                        viewModel.onDateRangeChanged(start, end)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            // ─── ৪. Summary Row ────────────────────────────────────────────
            if (!state.isInitialLoading && state.errorMessage == null) {
                item {
                    SummaryRow(
                        displayCount = state.displayList.size,
                        rawCount     = state.rawList.size,
                        hasSearch    = state.searchQuery.isNotEmpty(),
                        modifier     = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // ─── ৫. Loading / Skeleton ───────────────────────────────────
            if (state.isInitialLoading) {
                items(6) { SkeletonTransactionCard() }
            }

            // ─── ৬. Error State ────────────────────────────────────────────
            if (isNetworkAvailable) {
                state.errorMessage?.let { msg ->
                    item {
                        HistoryErrorCard(
                            message = msg,
                            onRetry = { viewModel.onRefresh() },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // ─── ৭. Empty State ────────────────────────────────────────────
            if (!state.isInitialLoading &&
                state.errorMessage == null &&
                state.displayList.isEmpty()
            ) {
                item { EmptyStateCard(hasFilter = state.selectedProvider != "all") }
            }

            // ─── ৮. Transaction List ───────────────────────────────────────
            if (!state.isInitialLoading && state.errorMessage == null) {
                itemsIndexed(
                    items = state.displayList,
                    key   = { _, item -> item.id }
                ) { _, item ->
                    TransactionCard(
                        item     = item,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        onSoldOutClick = {
                            viewModel.markTransactionSoldOut(item.id)
                        }
                    )
                }
            }

            // ─── ৯. Loading More Spinner ────────────────────────────────────
            if (state.isLoadingMore) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color       = AccentCyan,
                            modifier    = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // ─── ১০. End of List Footer ──────────────────────────────────────
            if (!state.isInitialLoading && !state.hasMore && state.displayList.isNotEmpty()) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text     = "✅ সব ট্রানজেকশন দেখানো হয়েছে",
                            color    = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
}

// =============================================================================
// Component 1 — Top Bar
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar() {
    TopAppBar(
        modifier = Modifier.height(56.dp),
        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        title = {
            Column {
                Text(
                    text       = "ট্রানজেকশন সার্চ",
                    color      = TextWhite,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text     = "পেমেন্ট রেকর্ড খুঁজুন",
                    color    = TextMuted,
                    fontSize = 10.sp
                )
            }
        },
        navigationIcon = {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentCyan.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector     = Icons.Default.Search,
                    contentDescription = "Search",
                    tint            = AccentCyan,
                    modifier        = Modifier.size(16.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = HistBg
        )
    )
}

// =============================================================================
// Component 2 — Search Box
// =============================================================================
@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        placeholder   = {
            Text(
                text     = "ট্রানজেকশন আইডি বা নম্বর...",
                color    = TextMuted,
                fontSize = adaptiveTextSize(11.sp, 13.sp)
            )
        },
        leadingIcon = {
            Icon(
                imageVector     = Icons.Default.Search,
                contentDescription = "Search",
                tint            = AccentCyan,
                modifier        = Modifier.size(20.dp)
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector     = Icons.Default.Close,
                        contentDescription = "Clear Search",
                        tint            = TextMuted,
                        modifier        = Modifier.size(18.dp)
                    )
                }
            }
        } else null,
        singleLine    = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = adaptiveTextSize(11.sp, 13.sp)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AccentCyan,
            unfocusedBorderColor = TextMuted.copy(alpha = 0.3f),
            focusedTextColor     = TextWhite,
            unfocusedTextColor   = TextWhite,
            cursorColor          = AccentCyan,
            focusedContainerColor   = HistCard,
            unfocusedContainerColor = HistCard
        ),
        shape    = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    )
}

// =============================================================================
// Component 3 — Material 3 Filter Chips
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun M3FilterChipsRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cachedMethodsJson = PrefsHelper.getGatewayMethodsCache(context)
    val methodsType = object : TypeToken<List<GatewayMethod>>() {}.type
    val cachedMethods: List<GatewayMethod> = try {
        GsonUtils.gson.fromJson(cachedMethodsJson, methodsType) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    val dynamicFilters = mutableListOf("all" to "সব")
    val uniqueProviders = cachedMethods.map { it.provider }.distinct()
    uniqueProviders.forEach { tag ->
        dynamicFilters.add(tag to tag)
    }

    LazyRow(
        modifier            = modifier,
        contentPadding      = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dynamicFilters) { (filter, label) ->
            val isSelected = selected == filter
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        text = label,
                        fontSize = adaptiveTextSize(11.sp, 13.sp),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = HistCard,
                    labelColor = TextMuted,
                    selectedContainerColor = AccentCyan.copy(alpha = 0.18f),
                    selectedLabelColor = AccentCyan,
                    selectedLeadingIconColor = AccentCyan
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = TextMuted.copy(alpha = 0.25f),
                    selectedBorderColor = AccentCyan,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                )
            )
        }
    }
}

// =============================================================================
// Component 4 — Summary Row
// =============================================================================
@Composable
private fun SummaryRow(
    displayCount: Int,
    rawCount: Int,
    hasSearch: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier          = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = if (hasSearch)
                "$displayCount টি ফলাফল পাওয়া গেছে ($rawCount লোড হয়েছে)"
            else
                "$rawCount টি ট্রানজেকশন লোড হয়েছে",
            color    = TextMuted,
            fontSize = 11.sp
        )
    }
}

// =============================================================================
// Component 5 — Transaction Card
// =============================================================================
@Composable
private fun TransactionCard(
    item: TransactionItem,
    modifier: Modifier = Modifier,
    onSoldOutClick: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    val providerColor = when (item.providerTag.lowercase(java.util.Locale.US)) {
        "bkash"  -> BkashPink
        "nagad"  -> NagadOrange
        "rocket" -> RocketPurple
        "upay"   -> UpayTeal
        else     -> AccentCyan
    }
    val providerEmoji = when (item.providerTag.lowercase(java.util.Locale.US)) {
        "bkash"  -> "🟢"
        "nagad"  -> "🟠"
        "rocket" -> "🔵"
        "upay"   -> "🟡"
        else     -> "⚪"
    }
    val isSoldOut = item.isUsed == 1

    val context = androidx.compose.ui.platform.LocalContext.current
    val simNumber = remember(item.simSlot) {
        val methodsJson = PrefsHelper.getGatewayMethodsCache(context)
        val methodsType = object : TypeToken<List<GatewayMethod>>() {}.type
        val cachedMethods: List<GatewayMethod> = try {
            GsonUtils.gson.fromJson(methodsJson, methodsType) ?: emptyList()
        } catch (e: Exception) { emptyList() }
        cachedMethods.find { it.simSlot == item.simSlot && !it.number.isNullOrEmpty() }?.number
    }

    val deviceName = item.deviceName?.takeIf { 
        it.isNotBlank() && it.lowercase(java.util.Locale.US) != "unknown" && it.lowercase(java.util.Locale.US) != "unknown device" 
    } ?: android.os.Build.MODEL

    Card(
        colors   = CardDefaults.cardColors(containerColor = HistCard),
        shape    = RoundedCornerShape(12.dp),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                            .height(64.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(providerColor)
                    )

                    // Details
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text     = "$providerEmoji ${item.providerTag}",
                            color    = providerColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text     = "TrxID: ${item.trxId}",
                            color    = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text     = formatTrxTimestamp(item.smsTimestamp),
                            color    = TextMuted.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val simInfo = if (item.simSlot != null) " • SIM ${item.simSlot}${if (simNumber != null) " - $simNumber" else ""}" else ""
                        Text(
                            text     = "Device: $deviceName$simInfo",
                            color    = TextMuted.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Amount + Status
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text       = "৳ ${java.text.DecimalFormat("#,##0.00").format(item.amount)}",
                            color      = TextWhite,
                            fontSize   = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isSoldOut) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(AccentRed.copy(alpha = 0.12f))
                                        .border(0.5.dp, AccentRed.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text       = "SOLDOUT",
                                        color      = AccentRed,
                                        fontSize   = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(AccentGreen.copy(alpha = 0.12f))
                                        .border(0.5.dp, AccentGreen.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                        .clickable { onSoldOutClick?.invoke() }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text       = "Available",
                                        color      = AccentGreen,
                                        fontSize   = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(animationSpec = androidx.compose.animation.core.tween(300)) + fadeIn(androidx.compose.animation.core.tween(300)),
                    exit = shrinkVertically(animationSpec = androidx.compose.animation.core.tween(300)) + fadeOut(androidx.compose.animation.core.tween(300))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HistBg)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Raw SMS",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.fullSms ?: "No SMS text available",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Timestamp Details",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SMS: ${item.smsTimestamp}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        if (item.senderNumber != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sender",
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.senderNumber,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // The absolute positioned expand arrow
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Expand",
                tint = TextMuted.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(24.dp)
                    .rotate(rotationState)
            )
        }
    }
}

@Composable
private fun StatusBadge(isSoldOut: Boolean) {
    val bgColor   = if (isSoldOut) AccentRed.copy(alpha = 0.12f) else AccentGreen.copy(alpha = 0.12f)
    val textColor = if (isSoldOut) AccentRed else AccentGreen
    val label     = if (isSoldOut) "🔴 SOLDOUT" else "✅ READY"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(0.5.dp, textColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text       = label,
            color      = textColor,
            fontSize   = 9.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// =============================================================================
// Component 6 — Skeleton Loading Card
// =============================================================================
@Composable
private fun SkeletonTransactionCard() {
    Card(
        colors   = CardDefaults.cardColors(containerColor = HistCard),
        shape    = RoundedCornerShape(12.dp),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(84.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(TextMuted.copy(alpha = 0.15f))
            )
            Column(
                modifier            = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TextMuted.copy(alpha = 0.12f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TextMuted.copy(alpha = 0.08f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(9.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TextMuted.copy(alpha = 0.06f))
                )
            }
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(20.dp)
                    .padding(end = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TextMuted.copy(alpha = 0.10f))
            )
        }
    }
}

// =============================================================================
// Component 7 — Empty State
// =============================================================================
@Composable
private fun EmptyStateCard(hasFilter: Boolean) {
    Column(
        modifier                = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment     = Alignment.CenterHorizontally,
        verticalArrangement     = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector     = Icons.Default.Inbox,
            contentDescription = "Empty",
            tint            = TextMuted.copy(alpha = 0.4f),
            modifier        = Modifier.size(56.dp)
        )
        Text(
            text       = if (hasFilter) "এই প্রোভাইডারের কোনো রেকর্ড নেই" else "কোনো ট্রানজেকশন পাওয়া যায়নি",
            color      = TextMuted,
            fontSize   = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign  = TextAlign.Center
        )
        Text(
            text      = if (hasFilter) "অন্য ফিল্টার ব্যবহার করুন অথবা SMS মনিটর চালু রাখুন"
                        else "আপনার SMS মনিটর সার্ভিস সক্রিয় রাখুন",
            color     = TextMuted.copy(alpha = 0.6f),
            fontSize  = 12.sp,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 32.dp)
        )
    }
}

// =============================================================================
// Component 8 — Error Card
// =============================================================================
@Composable
private fun HistoryErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = HistCard),
        shape    = RoundedCornerShape(14.dp),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector     = Icons.Default.CloudOff,
                contentDescription = "Error",
                tint            = AccentRed,
                modifier        = Modifier.size(36.dp)
            )
            Text(
                text      = message,
                color     = TextMuted,
                fontSize  = 13.sp,
                textAlign = TextAlign.Center
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
                    text       = "পুনরায় চেষ্টা করুন",
                    color      = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =============================================================================
// Utility — Timestamp Format
// =============================================================================
private fun formatTrxTimestamp(raw: String): String {
    return try {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        var parsed: Date? = null
        for (fmt in formats) {
            try {
                parsed = SimpleDateFormat(fmt, Locale.US).parse(raw)
                break
            } catch (_: Exception) { }
        }
        parsed?.let {
            SimpleDateFormat("dd MMM yy, hh:mm a", Locale.ENGLISH).format(it)
        } ?: raw.take(16)
    } catch (_: Exception) {
        raw.take(16)
    }
}

// =============================================================================
// Component 9 — Date Range Filter Row
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeFilterRow(
    startDate: String?,
    endDate: String?,
    onDateRangeSelected: (String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDateRangePickerState()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val label = if (startDate != null && endDate != null) "$startDate to $endDate" else "Filter by Date"
        OutlinedButton(
            onClick = { showDatePicker = true },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
        ) {
            Icon(Icons.Default.DateRange, contentDescription = "Date Range", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 12.sp)
        }

        if (startDate != null || endDate != null) {
            IconButton(onClick = { onDateRangeSelected(null, null) }) {
                Icon(Icons.Default.Close, contentDescription = "Clear Dates", tint = TextMuted)
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        val startMillis = datePickerState.selectedStartDateMillis
                        val endMillis = datePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val startStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(startMillis))
                            val endStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(endMillis))
                            onDateRangeSelected(startStr, endStr)
                        }
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = datePickerState, modifier = Modifier.weight(1f))
        }
    }
}
