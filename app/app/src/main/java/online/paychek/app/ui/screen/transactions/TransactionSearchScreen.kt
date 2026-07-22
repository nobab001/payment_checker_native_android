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
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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
import online.paychek.app.ui.components.ConnectionStatusBanner
import online.paychek.app.ui.components.LastUpdateRow
import online.paychek.app.utils.BanglaDateTimeFormat
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
import online.paychek.app.data.remote.dto.SmsTemplateDto
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
    val connectionBanner by viewModel.connectionBanner.collectAsStateWithLifecycle()
    val hasInternet by viewModel.hasInternet.collectAsStateWithLifecycle()
    val listState    = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val context      = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDateRangePickerState()

    val scrolledToBottom = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems  = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 1
        }
    }

    LaunchedEffect(state.refreshSkipped) {
        if (state.refreshSkipped) {
            Toast.makeText(context, "নতুন আপডেট নেই — ডেটা আপ টু ডেট", Toast.LENGTH_SHORT).show()
            viewModel.clearRefreshSkipped()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HistBg)
    ) {
        connectionBanner?.let { banner ->
            ConnectionStatusBanner(banner = banner)
        }

        SearchTopBar()

        SearchBox(
            query         = state.searchQuery,
            onQueryChange = { viewModel.onSearchQueryChanged(it) },
            onClear       = { viewModel.onSearchQueryChanged("") },
            onDone        = { focusManager.clearFocus() },
            isDateActive  = state.startDate != null || state.endDate != null,
            onDateClick   = { showDatePicker = true },
            onClearDate   = { viewModel.onDateRangeChanged(null, null) },
            modifier      = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        M3FilterChipsRow(
            templates = state.templates,
            selected = state.selectedProvider,
            onSelect = { provider ->
                val target = if (state.selectedProvider.lowercase() == provider.lowercase()) "all" else provider
                viewModel.onProviderFilterChanged(target)
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if ((!state.isInitialLoading || state.lastUpdatedAtMs != null) && state.errorMessage == null) {
            LastUpdateRow(
                lastUpdatedAtMs = state.lastUpdatedAtMs,
                isRefreshing = state.isRefreshing,
                onReload = { viewModel.onRefresh() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                accentColor = AccentCyan,
                mutedColor = TextMuted
            )
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = {
                if (!viewModel.onRefresh()) {
                    Toast.makeText(
                        context,
                        "৫ সেকেন্ড পরে আবার রিফ্রেশ করুন",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state               = listState,
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
            // ─── Loading / Skeleton ───────────────────────────────────────
            if (state.isInitialLoading) {
                items(6) { SkeletonTransactionCard() }
            }

            // ─── Error State ────────────────────────────────────────────
            if (hasInternet) {
                state.errorMessage?.let { msg ->
                    item {
                        HistoryErrorCard(
                            message = msg,
                            onRetry = {
                                if (!viewModel.onRefresh()) {
                                    Toast.makeText(
                                        context,
                                        "৫ সেকেন্ড পরে আবার রিফ্রেশ করুন",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // ─── Empty State ────────────────────────────────────────────
            if (!state.isInitialLoading &&
                state.errorMessage == null &&
                state.displayList.isEmpty()
            ) {
                item { EmptyStateCard(hasFilter = state.selectedProvider != "all") }
            }

            // ─── Transaction List ───────────────────────────────────────
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

            if (
                !state.isInitialLoading &&
                state.errorMessage == null &&
                state.displayList.isNotEmpty() &&
                scrolledToBottom.value &&
                (state.nextHistoryDays() != null || state.isLoadingMoreHistory)
            ) {
                item {
                    LoadMoreHistoryButton(
                        nextDays = state.nextHistoryDays() ?: 0,
                        isLoading = state.isLoadingMoreHistory,
                        onClick = { viewModel.loadMoreHistory() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // ─── End of List Footer ─────────────────────────────────────
            if (
                !state.isInitialLoading &&
                !state.canLoadMoreHistory &&
                state.displayList.isNotEmpty() &&
                state.historyTier != HistoryLoadTier.CUSTOM
            ) {
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
                            viewModel.onDateRangeChanged(startStr, endStr)
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
    isDateActive: Boolean,
    onDateClick: () -> Unit,
    onClearDate: () -> Unit,
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
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector     = Icons.Default.Close,
                            contentDescription = "Clear Search",
                            tint            = TextMuted,
                            modifier        = Modifier.size(18.dp)
                        )
                    }
                }
                if (isDateActive) {
                    IconButton(onClick = onClearDate) {
                        Icon(
                            imageVector     = Icons.Default.Close,
                            contentDescription = "Clear Date Range",
                            tint            = AccentRed,
                            modifier        = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onDateClick) {
                    Icon(
                        imageVector     = Icons.Default.DateRange,
                        contentDescription = "Filter by Date",
                        tint            = if (isDateActive) AccentCyan else TextMuted,
                        modifier        = Modifier.size(20.dp)
                    )
                }
            }
        },
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    )
}

// =============================================================================
// Component 3 — Material 3 Filter Chips
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun M3FilterChipsRow(
    templates: List<SmsTemplateDto>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier            = modifier,
        contentPadding      = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(templates) { template ->
            val isOther = template.isOtherDevice == true
            val isSelected = !isOther && selected.lowercase() == template.templateName.lowercase()
            val labelText = if (isOther) "${template.templateName} (অন্য ডিভাইসে তৈরি)" else template.templateName

            FilterChip(
                selected = isSelected,
                onClick = {
                    if (!isOther) {
                        // Filter by template name (= provider_tag on history), not senderId
                        onSelect(template.templateName)
                    }
                },
                label = {
                    Text(
                        text = labelText,
                        fontSize = adaptiveTextSize(11.sp, 13.sp),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = if (isOther) HistCard.copy(alpha = 0.5f) else HistCard,
                    labelColor = if (isOther) TextMuted.copy(alpha = 0.4f) else TextMuted,
                    selectedContainerColor = AccentCyan.copy(alpha = 0.18f),
                    selectedLabelColor = AccentCyan,
                    selectedLeadingIconColor = AccentCyan
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = !isOther,
                    selected = isSelected,
                    borderColor = if (isOther) TextMuted.copy(alpha = 0.15f) else TextMuted.copy(alpha = 0.25f),
                    selectedBorderColor = AccentCyan,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                )
            )
        }
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

    val providerKey = item.providerTag.lowercase(java.util.Locale.US)
    val providerColor = when {
        providerKey.contains("bkash") || providerKey.contains("বিকাশ") -> BkashPink
        providerKey.contains("nagad") || providerKey.contains("নগদ") -> NagadOrange
        providerKey.contains("rocket") || providerKey.contains("রকেট") -> RocketPurple
        providerKey.contains("upay") || providerKey.contains("উপায়") || providerKey.contains("উপায়") -> UpayTeal
        else -> AccentCyan
    }
    val isSoldOut = item.isUsed == 1

    val displaySimNumber = item.simNumber?.takeIf { it.isNotBlank() }
    val deviceName = item.deviceName?.takeIf {
        it.isNotBlank() &&
            !it.lowercase(java.util.Locale.US).contains("unknown")
    } ?: (item.deviceId?.takeIf { it.isNotBlank() } ?: "Unknown Device")

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
                            text     = item.providerTag,
                            color    = providerColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
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
                            text     = BanglaDateTimeFormat.formatTrxCard(item.smsTimestamp),
                            color    = TextMuted.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val simInfo = buildString {
                            if (item.simSlot != null) append(" • SIM ${item.simSlot}")
                            if (!displaySimNumber.isNullOrBlank()) append(" - $displaySimNumber")
                        }
                        Text(
                            text     = "Device: $deviceName$simInfo",
                            color    = TextMuted.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(x = (-12).dp)
                        )
                    }

                    // Amount + Status
                    Column(horizontalAlignment = Alignment.End) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text       = "৳ ${java.text.DecimalFormat("#,##0.00").format(item.amount)}",
                                color      = TextWhite,
                                fontSize   = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = "Expand",
                                tint = TextMuted.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(rotationState)
                            )
                        }
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
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(isSoldOut: Boolean) {
    val bgColor   = if (isSoldOut) AccentRed.copy(alpha = 0.12f) else AccentGreen.copy(alpha = 0.12f)
    val textColor = if (isSoldOut) AccentRed else AccentGreen
    val label     = if (isSoldOut) "🔴 SOLDOUT" else "✅ Available"

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
// Load More History Button
// =============================================================================
@Composable
private fun LoadMoreHistoryButton(
    nextDays: Int,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = AccentCyan
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = AccentCyan,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = if (nextDays > 0) "আরো history দেখুন ($nextDays দিন)" else "আরো history দেখুন",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

// =============================================================================
// Utility — Timestamp Format
// =============================================================================
private fun formatTrxTimestamp(raw: String): String {
    return BanglaDateTimeFormat.formatTrxCard(raw)
}
