package online.paychek.app.ui.screen.dashboard

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.filled.ContentCopy
import android.content.Intent
import android.net.Uri
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import online.paychek.app.data.remote.dto.DashboardStats
import online.paychek.app.data.remote.dto.TransactionItem
import online.paychek.app.ui.components.ConnectivityBanner
import online.paychek.app.utils.adaptivePadding
import online.paychek.app.utils.adaptiveTextSize
import online.paychek.app.utils.screenWidth
import online.paychek.app.ui.theme.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

// =============================================================================
// ডিজাইন টোকেন (Dark Premium Gateway Theme)
// =============================================================================
private val DashBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val DashCard: Color @Composable get() = MaterialTheme.colorScheme.surface
private val DashCardAlt: Color @Composable get() = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF1B2030) else Color(0xFFF1F3F5)
private val AccentCyan    = Color(0xFF22D3EE)   // Cyan accent
private val AccentGreen   = Color(0xFF10B981)   // Emerald green (toggle ON)
private val AccentAmber   = Color(0xFFF59E0B)   // Amber (stats icon)
private val TextWhite: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
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
    onNavigateToSubscription: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val screenState by viewModel.state.collectAsStateWithLifecycle()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(true) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.currentStateFlow.collect { state ->
            if (state == androidx.lifecycle.Lifecycle.State.RESUMED) {
                isAccessibilityEnabled = online.paychek.app.utils.AccessibilityHelper.isAccessibilityServiceEnabled(context)
            }
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var hasShownReminder by remember { mutableStateOf(false) }
    var showExpiryReminderDialog by remember { mutableStateOf(false) }

    var showSmsPermissionRationaleDialog by remember { mutableStateOf(false) }

    val smsPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        online.paychek.app.MainActivity.isRequestingPermission = false
        val granted = permissions[Manifest.permission.RECEIVE_SMS] == true &&
                      permissions[Manifest.permission.READ_SMS] == true
        if (granted) {
            viewModel.toggleSmsService(true)
        } else {
            showSmsPermissionRationaleDialog = true
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf<String?>("today") }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }

    val successStats = (screenState.uiState as? DashboardUiState.Success)?.stats
    val isPaid = successStats?.isPaid ?: false
    val daysRemaining = remember(successStats?.expiryDate) {
        calculateDaysRemaining(successStats?.expiryDate)
    }

    val prefs = context.getSharedPreferences(online.paychek.app.config.AppConfig.PREF_NAME, android.content.Context.MODE_PRIVATE)

    LaunchedEffect(isPaid, daysRemaining) {
        if (isPaid && daysRemaining in 0..30 && !hasShownReminder) {
            val lastAlertTime = prefs.getLong("last_trial_alert_time", 0)
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            if (lastAlertTime < todayStart) {
                showExpiryReminderDialog = true
            } else {
                hasShownReminder = true
            }
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
                prefs.edit().putLong("last_trial_alert_time", System.currentTimeMillis()).apply()
            },
            onBuyPlanClick = {
                showExpiryReminderDialog = false
                hasShownReminder = true
                prefs.edit().putLong("last_trial_alert_time", System.currentTimeMillis()).apply()
                onNavigateToSubscription()
            }
        )
    }

    if (showDateRangePicker) {
        val datePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDateRangePicker = false
                        val startMillis = datePickerState.selectedStartDateMillis
                        val endMillis = datePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            customStartDate = startMillis
                            customEndDate = endMillis
                            selectedDate = "custom"
                        }
                    }
                ) { Text("OK", color = AccentCyan, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel", color = TextMuted) }
            },
            colors = DatePickerDefaults.colors(containerColor = DashCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            DateRangePicker(
                state = datePickerState,
                modifier = Modifier.weight(1f),
                colors = DatePickerDefaults.colors(
                    containerColor = DashCard,
                    titleContentColor = TextWhite,
                    headlineContentColor = TextWhite,
                    weekdayContentColor = TextMuted,
                    subheadContentColor = TextWhite,
                    navigationContentColor = TextWhite,
                    yearContentColor = TextWhite,
                    disabledYearContentColor = TextMuted,
                    currentYearContentColor = AccentCyan,
                    selectedYearContentColor = Color.White,
                    disabledSelectedYearContentColor = Color.White.copy(alpha = 0.5f),
                    selectedYearContainerColor = AccentCyan,
                    disabledSelectedYearContainerColor = AccentCyan.copy(alpha = 0.5f),
                    dayContentColor = TextWhite,
                    disabledDayContentColor = TextMuted,
                    selectedDayContentColor = Color.White,
                    disabledSelectedDayContentColor = Color.White.copy(alpha = 0.5f),
                    selectedDayContainerColor = AccentCyan,
                    disabledSelectedDayContainerColor = AccentCyan.copy(alpha = 0.5f),
                    todayContentColor = AccentCyan,
                    todayDateBorderColor = AccentCyan,
                    dayInSelectionRangeContentColor = TextWhite,
                    dayInSelectionRangeContainerColor = AccentCyan.copy(alpha = 0.2f)
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(DashBg)
        ) {
        if (!isNetworkAvailable) {
            ConnectivityBanner()
        }

        if (!isAccessibilityEnabled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable {
                        online.paychek.app.MainActivity.isRequestingPermission = true
                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "পেমেন্ট অটো-সিঙ্ক করতে এক্সেসিবিলিটি পারমিশন দিন",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "অনুমোদন করুন ➔",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = screenState.isRefreshing,
            onRefresh    = { viewModel.onRefresh() },
            modifier     = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ১. Dashboard Header Block (স্বাগতম, প্যাকেজ ও SMS মনিটর সম্বলিত একটি কার্ড)
            item {
                DashboardHeaderBlock(
                    userName = screenState.userName,
                    isPaid = isPaid,
                    activePlanName = successStats?.activePlanName ?: "FREE_LEVEL",
                    expiryDate = successStats?.expiryDate,
                    onBuyPlanClick = onNavigateToSubscription,
                    isServiceActive = screenState.isServiceActive,
                    onServiceToggle = { enable ->
                        if (enable) {
                            if (!isAccessibilityEnabled) {
                                android.widget.Toast.makeText(
                                    context,
                                    "প্রথমে এক্সেসিবিলিটি পারমিশন সক্রিয় করুন",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                val prefs = context.getSharedPreferences(online.paychek.app.config.AppConfig.PREF_NAME, android.content.Context.MODE_PRIVATE)
                                val sim1Enabled = prefs.getBoolean(online.paychek.app.config.AppConfig.KEY_SIM1_ENABLED, true)
                                val sim2Enabled = prefs.getBoolean(online.paychek.app.config.AppConfig.KEY_SIM2_ENABLED, true)
                                val isAnySimActive = sim1Enabled || sim2Enabled

                                if (!isPaid) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "SMS মনিটর চালু করতে প্যাকেজ কিনুন",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } else if (!isAnySimActive) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "ডিভাইস সেটিংসে গিয়ে SIM সক্রিয় করুন",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    val simStatus = online.paychek.app.utils.DeviceIdHelper.getSimSlotIds(context)
                                    if (simStatus == "no_sims" || simStatus == "permission_denied") {
                                        android.widget.Toast.makeText(
                                            context,
                                            "সক্রিয় সিম কার্ড এবং প্রয়োজনীয় পারমিশন ছাড়া মনিটরিং চালু করা সম্ভব নয়।",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        val hasReceiveSms = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECEIVE_SMS
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        val hasReadSms = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.READ_SMS
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                        if (hasReceiveSms && hasReadSms) {
                                            viewModel.toggleSmsService(true)
                                        } else {
                                            online.paychek.app.MainActivity.isRequestingPermission = true
                                            smsPermissionsLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.RECEIVE_SMS,
                                                    Manifest.permission.READ_SMS
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            viewModel.toggleSmsService(false)
                        }
                    }
                )
            }

            // Dual-Tab Toggle Row
            item {
                TabRow(
                    selectedTabIndex = screenState.selectedTab,
                    containerColor = DashBg,
                    contentColor = AccentCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[screenState.selectedTab]),
                            color = AccentCyan
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Tab(
                        selected = screenState.selectedTab == 0,
                        onClick = {
                            viewModel.setSelectedTab(0)
                            viewModel.saveDefaultTabPreference(0)
                        },
                        text = {
                            Text(
                                text = "পেমেন্ট রেকর্ডস",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    )
                    Tab(
                        selected = screenState.selectedTab == 1,
                        onClick = {
                            viewModel.setSelectedTab(1)
                            viewModel.saveDefaultTabPreference(1)
                        },
                        text = {
                            Text(
                                text = "কাস্টম আর্কাইভ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            if (screenState.selectedTab == 0) {
                // ৩. Search box (filter button সহ)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "ট্রানজেকশন খুঁজুন...",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear Search",
                                                tint = TextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { showDateRangePicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = "Date Filter",
                                            tint = if (selectedDate == "custom") AccentCyan else TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = TextMuted.copy(alpha = 0.3f),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                cursorColor = AccentCyan,
                                focusedContainerColor = DashCard,
                                unfocusedContainerColor = DashCard
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ৪. Provider chips (বিকাশ, নগদ, রকেট, উপায়)
                // - search এ কিছু type করলে chips HIDE হবে
                if (searchQuery.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val providerFilters = screenState.globalTemplates
                                .distinctBy { it.templateName.split(" ")[0].lowercase(Locale.US) }
                                .map { template ->
                                    val providerName = template.templateName
                                    val tag = providerName.split(" ")[0].lowercase(Locale.US)
                                    val label = providerName
                                    val color = when (tag) {
                                        "bkash" -> Color(0xFF10B981)
                                        "nagad" -> Color(0xFFF97316)
                                        "rocket" -> Color(0xFF8B5CF6)
                                        "upay" -> Color(0xFFEAB308)
                                        else -> AccentCyan
                                    }
                                    tag to (label to color)
                                }
                            providerFilters.forEach { (tag, info) ->
                                val (label, dotColor) = info
                                val isSelected = selectedProvider == tag
                                val chipBgColor = if (isSelected) AccentCyan.copy(alpha = 0.18f) else DashCard
                                val chipBorderColor = if (isSelected) AccentCyan else TextMuted.copy(alpha = 0.25f)
                                val chipTextColor = if (isSelected) AccentCyan else TextMuted

                                Box(
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = chipBorderColor,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(chipBgColor)
                                        .clickable {
                                            selectedProvider = if (isSelected) null else tag
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                        )
                                        Text(
                                            text = label,
                                            color = chipTextColor,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ৫. Stats cards (আজকের পেমেন্ট, সর্বমোট, মোট ট্রানজেকশন, অ্যাক্টিভ ডিভাইস)
                // - search এ কিছু type করলে stats cards HIDE হবে
                if (searchQuery.isEmpty()) {
                    item {
                        val isOffline = !isNetworkAvailable
                        if (isOffline) {
                            StatsGrid(stats = null, isOffline = true)
                        } else {
                            AnimatedContent(
                                targetState = screenState.uiState,
                                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                                modifier = Modifier.padding(bottom = 4.dp),
                                label = "DashboardUiState"
                            ) { uiState ->
                                when (uiState) {
                                    is DashboardUiState.Loading -> StatsLoadingPlaceholder()
                                    is DashboardUiState.Error   -> ErrorCard(
                                        message  = uiState.message,
                                        onRetry  = { viewModel.loadDashboardStats() }
                                    )
                                    is DashboardUiState.Success -> StatsGrid(stats = uiState.stats, isOffline = false)
                                }
                            }
                        }
                    }
                }

                // ৬. আজকের ট্রানজেকশন list
                // - "সব দেখুন" বাটন সহ
                val recentList = (screenState.uiState as? DashboardUiState.Success)
                    ?.stats?.recentTransactions ?: emptyList()

                val filteredList = recentList.filter { trx ->
                    val matchesQuery = searchQuery.isEmpty() ||
                        trx.trxId.contains(searchQuery, ignoreCase = true) ||
                        (trx.senderNumber != null && trx.senderNumber.contains(searchQuery, ignoreCase = true)) ||
                        trx.amount.toString().contains(searchQuery)

                    val matchesProvider = selectedProvider == null ||
                        trx.providerTag.equals(selectedProvider, ignoreCase = true)

                    val matchesDate = isDateMatching(trx.smsTimestamp, selectedDate, customStartDate, customEndDate)

                    matchesQuery && matchesProvider && matchesDate
                }

                if (filteredList.isNotEmpty()) {
                    item {
                        RecentTransactionsHeader(onSeeAll = onNavigateToHistory)
                    }
                    items(filteredList, key = { it.id }) { trx ->
                        TransactionRow(
                            item     = trx,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onSoldOutClick = {
                                viewModel.markTransactionSoldOut(trx.id)
                            }
                        )
                    }
                } else if (searchQuery.isNotEmpty() || selectedProvider != null || selectedDate != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "কোনো ট্রানজেকশন পাওয়া যায়নি",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            if (screenState.selectedTab == 1) {
                if (screenState.isCustomArchivesLoading) {
                    items(5) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .height(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(DashCard),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = AccentCyan,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                } else if (screenState.customArchivesError != null) {
                    item {
                        ErrorCard(
                            message = screenState.customArchivesError!!,
                            onRetry = { viewModel.loadCustomArchives() }
                        )
                    }
                } else if (screenState.customArchives.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = TextMuted.copy(alpha = 0.4f),
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "কাস্টম আর্কাইভে কোনো বার্তা নেই",
                                color = TextMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(
                        items = screenState.customArchives,
                        key = { it.id }
                    ) { archive ->
                        CustomArchiveRow(
                            item = archive,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    if (screenState.uiState is DashboardUiState.Success && !isPaid) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE60B0E14)) // Semi-transparent dark overlay
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DashCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Expired",
                        tint = StatusRed,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "প্যাকেজের মেয়াদ শেষ",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "আপনার সাবস্ক্রিপশন বা ফ্রি ট্রায়াল প্যাকেজের মেয়াদ শেষ হয়ে গেছে। এসএমএস মনিটরিং সচল করতে অনুগ্রহ করে একটি প্যাকেজ কিনুন বা রিনিউ করুন।",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onNavigateToSubscription,
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("প্যাকেজ কিনুন (Buy Package)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

    if (showSmsPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { /* Non-dismissible */ },
            title = {
                Text(
                    text = "SMS পারমিশন প্রয়োজন",
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "SMS পেমেন্ট মনিটর चालू করতে SMS পারমিশন দেওয়া আবশ্যক। অনুগ্রহ করে পারমিশন অ্যালাও করুন।",
                    color = TextWhite,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSmsPermissionRationaleDialog = false
                        online.paychek.app.MainActivity.isRequestingPermission = true
                        smsPermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS
                            )
                        )
                    }
                ) {
                    Text("অনুমোদন দিন (Allow)", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DashCard,
            shape = RoundedCornerShape(20.dp),
            modifier = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Modifier else Modifier.border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(20.dp))
        )
    }
}
}

// =============================================================================
// Component 1 — Unified Header & Subscription Block
// =============================================================================

@Composable
private fun DashboardHeaderBlock(
    userName: String,
    isPaid: Boolean,
    activePlanName: String,
    expiryDate: String?,
    onBuyPlanClick: () -> Unit,
    isServiceActive: Boolean,
    onServiceToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val daysRemaining = calculateDaysRemaining(expiryDate)
    val formattedDate = formatExpiryDateToBangla(expiryDate)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF162A5E),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
            .padding(adaptivePadding(12.dp, 16.dp))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Row 1: Avatar, Welcome Text, Standard Badge, Menu Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Icon",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Welcome texts
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "স্বাগতম",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = userName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Plan Badge
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = activePlanName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Three-dots menu button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable {
                            // Can show more options or info
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More options",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Row 2: Inner Card (Plan details and renew button)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isPaid) "$activePlanName প্যাকেজ" else "কোনো সক্রিয় প্যাকেজ নেই",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isPaid && !expiryDate.isNullOrEmpty()) {
                            Text(
                                text = "মেয়াদ শেষ: $formattedDate • ${daysRemaining} দিন বাকি",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        } else if (!isPaid) {
                            Text(
                                text = "গেটওয়ে সচল করতে প্যাকেজ কিনুন",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Button(
                        onClick = onBuyPlanClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isPaid) "রিনিউ করুন" else "প্যাকেজ কিনুন",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Row 3: SMS Payment Monitor Switch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = "Monitor Icon",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "SMS পেমেন্ট মনিটর",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Switch(
                    checked = isServiceActive,
                    onCheckedChange = onServiceToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.3f),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
        }
    }
}


// =============================================================================
// Component 3 — Stats Grid (২×২)
// =============================================================================

@Composable
private fun StatsGrid(
    stats: DashboardStats?,
    isOffline: Boolean = false,
    modifier: Modifier = Modifier
) {
    val fmt = DecimalFormat("#,##0.00")
    val todayEarningsVal = if (isOffline || stats == null) "--" else "৳ ${fmt.format(stats.todayEarnings)}"
    val totalEarningsVal = if (isOffline || stats == null) "--" else "৳ ${fmt.format(stats.totalEarnings)}"
    val totalTransactionsVal = if (isOffline || stats == null) "--" else "${stats.totalTransactions}"
    val activeDevicesVal = if (isOffline || stats == null) "--" else "${stats.activeDevices} টি"
    val todayTransactionsBadge = if (isOffline || stats == null) null else "${stats.todayTransactions} টি"
    val totalTransactionsBadge = if (isOffline || stats == null) null else (if (stats.unusedCount > 0) "${stats.unusedCount} নতুন" else null)

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
                value      = todayEarningsVal,
                badge      = todayTransactionsBadge,
                modifier   = Modifier.weight(1f)
            )
            StatCard(
                icon       = Icons.Default.AccountBalance,
                iconColor  = AccentAmber,
                label      = "সর্বমোট পেমেন্ট",
                subLabel   = "Total Received",
                value      = totalEarningsVal,
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
                icon       = Icons.AutoMirrored.Filled.ReceiptLong,
                iconColor  = Color(0xFF818CF8),   // Indigo
                label      = "মোট ট্রানজেকশন",
                subLabel   = "Total Transactions",
                value      = totalTransactionsVal,
                badge      = totalTransactionsBadge,
                badgeColor = AccentGreen,
                modifier   = Modifier.weight(1f)
            )
            StatCard(
                icon       = Icons.Default.PhoneAndroid,
                iconColor  = Color(0xFFF472B6),   // Pink
                label      = "অ্যাক্টিভ ডিভাইস",
                subLabel   = "Active Devices",
                value      = activeDevicesVal,
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
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(adaptivePadding(8.dp, 12.dp)),
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
                fontSize   = adaptiveTextSize(14.sp, 18.sp),
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
            text       = "আজকের ট্রানজেকশন",
            color      = TextWhite,
            fontSize   = 15.sp,
            fontWeight = FontWeight.Bold
        )
        TextButton(onClick = onSeeAll) {
            Text(
                text     = "সব দেখুন",
                color    = AccentCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
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
    modifier: Modifier = Modifier,
    onSoldOutClick: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    val providerColor = when (item.providerTag.lowercase(Locale.US)) {
        "bkash"  -> BkashPink
        "nagad"  -> NagadOrange
        "rocket" -> RocketPurple
        "upay"   -> UpayTeal
        else     -> AccentCyan
    }
    val providerEmoji = when (item.providerTag.lowercase(Locale.US)) {
        "bkash"  -> "🟢"
        "nagad"  -> "🟠"
        "rocket" -> "🔵"
        "upay"   -> "🟡"
        else     -> "⚪"
    }
    val isSoldOut = item.isUsed == 1

    val context = androidx.compose.ui.platform.LocalContext.current
    val simNumber = remember(item.simSlot) {
        val methodsJson = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsCache(context)
        val methodsType = object : com.google.gson.reflect.TypeToken<List<online.paychek.app.data.remote.dto.GatewayMethod>>() {}.type
        val cachedMethods: List<online.paychek.app.data.remote.dto.GatewayMethod> = try {
            online.paychek.app.utils.GsonUtils.gson.fromJson(methodsJson, methodsType) ?: emptyList()
        } catch (e: Exception) { emptyList() }
        cachedMethods.find { it.simSlot == item.simSlot && !it.number.isNullOrEmpty() }?.number
    }

    val deviceName = item.deviceName?.takeIf { 
        it.isNotBlank() && it.lowercase(Locale.US) != "unknown" && it.lowercase(Locale.US) != "unknown device" 
    } ?: android.os.Build.MODEL

    Card(
        colors   = CardDefaults.cardColors(containerColor = DashCard),
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
                            text     = formatTimestamp(item.smsTimestamp),
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
                            text       = "৳ ${DecimalFormat("#,##0.00").format(item.amount)}",
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
                                        .background(StatusRed.copy(alpha = 0.12f))
                                        .border(0.5.dp, StatusRed.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text       = "SOLDOUT",
                                        color      = StatusRed,
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
                
                
                // Expanded Area (this empty comment just replacing broken leftovers)
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                exit = androidx.compose.animation.shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DashCardAlt)
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
                    border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
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
                        .border(
                            if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) 0.dp else 1.dp,
                            if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color.Transparent else Color(0xFFE3E5E8),
                            RoundedCornerShape(14.dp)
                        )
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
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
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
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(raw) ?: Date()
        
        val outSdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.forLanguageTag("bn-BD"))
        outSdf.timeZone = java.util.TimeZone.getDefault()
        outSdf.format(date)
    } catch (e: Exception) {
        raw.take(16)
    }
}

private fun formatExpiryDateToBangla(dateStr: String?): String {
    if (dateStr.isNullOrEmpty()) return ""
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = inputFormat.parse(dateStr) ?: return dateStr
        val outputFormat = SimpleDateFormat("dd MMMM, yyyy", Locale.forLanguageTag("bn-BD"))
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
        
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val expiryCal = Calendar.getInstance().apply {
            time = expiryDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val diffMs = expiryCal.timeInMillis - todayCal.timeInMillis
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "আপনার বর্তমান সাবস্ক্রিপশন মেয়াদের আর মাত্র $daysRemaining দিন বাকি আছে। সার্ভিস সচল রাখতে এবং পেমেন্ট মনিটরিং অব্যাহত রাখতে অনুগ্রহ করে আপনার প্যাকেজটি রিনিউ করুন।",
                    color = TextWhite,
                    fontSize = 14.sp
                )
            }
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
        shape = RoundedCornerShape(20.dp),
        modifier = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Modifier else Modifier.border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(20.dp))
    )
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
                color = TextWhite,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
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
                            border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
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
                                        color = TextWhite,
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
        shape = RoundedCornerShape(20.dp),
        modifier = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Modifier else Modifier.border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(20.dp))
    )
}

private fun isDateMatching(
    rawTimestamp: String,
    filter: String?,
    customStartDate: Long? = null,
    customEndDate: Long? = null
): Boolean {
    if (filter == null || filter == "all") return true
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = sdf.parse(rawTimestamp) ?: return false

        val todayCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val todayMidnight = todayCal.timeInMillis

        val trxCal = java.util.Calendar.getInstance().apply {
            time = date
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val trxMidnight = trxCal.timeInMillis

        when (filter) {
            "today" -> trxMidnight == todayMidnight
            "last_2_days" -> {
                val limit = todayMidnight - 1 * 24L * 60 * 60 * 1000
                trxMidnight >= limit
            }
            "last_7_days" -> {
                val limit = todayMidnight - 6 * 24L * 60 * 60 * 1000
                trxMidnight >= limit
            }
            "last_15_days" -> {
                val limit = todayMidnight - 14 * 24L * 60 * 60 * 1000
                trxMidnight >= limit
            }
            "last_21_days" -> {
                val limit = todayMidnight - 20 * 24L * 60 * 60 * 1000
                trxMidnight >= limit
            }
            "last_30_days" -> {
                val limit = todayMidnight - 29 * 24L * 60 * 60 * 1000
                trxMidnight >= limit
            }
            "custom" -> {
                val localStart = customStartDate?.let { utcMidnightToLocalMidnight(it) }
                val localEnd = customEndDate?.let { utcMidnightToLocalMidnight(it) }
                val matchesStart = localStart == null || trxMidnight >= localStart
                val matchesEnd = localEnd == null || trxMidnight <= localEnd
                matchesStart && matchesEnd
            }
            else -> true
        }
    } catch (e: Exception) {
        false
    }
}

private fun utcMidnightToLocalMidnight(utcMs: Long): Long {
    val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMs
    }
    return java.util.Calendar.getInstance().apply {
        set(
            utcCal.get(java.util.Calendar.YEAR),
            utcCal.get(java.util.Calendar.MONTH),
            utcCal.get(java.util.Calendar.DAY_OF_MONTH),
            0, 0, 0
        )
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

@Composable
private fun CustomArchiveRow(
    item: online.paychek.app.data.remote.dto.CustomArchiveItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = DashCard),
        shape = RoundedCornerShape(16.dp),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Sender / Device & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AccentCyan.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = item.providerTag, // Sender ID e.g. GP, BL, bKash
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            fontSize = 14.sp
                        )
                        Text(
                            text = item.deviceName ?: "মূল ডিভাইস",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                
                Text(
                    text = formatCustomArchiveTime(item.createdAt),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            // Raw SMS Text Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DashCardAlt)
                    .padding(12.dp)
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = item.fullSms,
                        color = TextWhite,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Copy Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("SMS Content", item.fullSms)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "টেক্সট কপি করা হয়েছে ✓", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("কপি করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatCustomArchiveTime(isoString: String): String {
    return try {
        // e.g. "2026-06-25T10:05:59.000Z"
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = inputFormat.parse(isoString) ?: Date()
        val outputFormat = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.US)
        outputFormat.format(date)
    } catch (e: Exception) {
        isoString
    }
}




