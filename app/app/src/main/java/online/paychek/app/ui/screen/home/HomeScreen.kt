package online.paychek.app.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import online.paychek.app.NavKey as AppNavKey
import kotlinx.coroutines.launch
import online.paychek.app.ui.screen.dashboard.DashboardScreen
import online.paychek.app.ui.screen.device.DeviceScreen
import online.paychek.app.ui.screen.profile.ProfileSettingsScreen
import online.paychek.app.ui.screen.transactions.TransactionSearchScreen
import online.paychek.app.ui.screen.apicenter.ApiIntegrationScreen
import online.paychek.app.ui.theme.RoyalIndigo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowBackIosNew
import online.paychek.app.utils.SecurePreferences

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Tab সংজ্ঞা
// ─────────────────────────────────────────────────────────────────────────────
private enum class HomeTab(
    val icon: ImageVector,
    val label: String
) {
    HOME(Icons.Default.Home, "Home"),
    DEVICE(Icons.Default.Build, "Device"),
    SEARCH(Icons.Default.Search, "Search"),
    API(Icons.Default.Code, "API"),
    PROFILE(Icons.Default.Person, "Profile")
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CustomBottomBar(
    selectedTab: HomeTab,
    onTabSelect: (HomeTab) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF1E293B), // Slate container color matching GwCard
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        HomeTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelect(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    unselectedIconColor = Color(0xFF94A3B8),
                    selectedTextColor = Color.White,
                    unselectedTextColor = Color(0xFF94A3B8),
                    indicatorColor = RoyalIndigo.copy(alpha = 0.35f) // Premium indicator pill tint
                )
            )
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen — Bottom Navigation Hub
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(online.paychek.app.config.AppConfig.PREF_NAME, android.content.Context.MODE_PRIVATE) }
    
    val coroutineScope = rememberCoroutineScope()
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var plansList by remember { mutableStateOf<List<online.paychek.app.data.remote.dto.SubscriptionPlanDto>>(emptyList()) }
    var purchaseLoading by remember { mutableStateOf(false) }

    LaunchedEffect(showPurchaseDialog) {
        if (showPurchaseDialog && plansList.isEmpty()) {
            val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
            if (token.isNotEmpty()) {
                online.paychek.app.data.repository.PaymentRepository().getPlans(token).onSuccess {
                    plansList = it
                }
            }
        }
    }

    if (showPurchaseDialog) {
        online.paychek.app.ui.screen.dashboard.SubscriptionPurchaseDialog(
            plans = plansList,
            isLoading = purchaseLoading,
            onDismiss = { showPurchaseDialog = false },
            onPurchase = { planName ->
                coroutineScope.launch {
                    purchaseLoading = true
                    val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
                    val result = online.paychek.app.data.repository.PaymentRepository().purchaseSubscription(token, planName)
                    result.fold(
                        onSuccess = {
                            prefs.edit().putString("pcu_account_level", planName).apply()
                            android.widget.Toast.makeText(context, "${planName} প্যাকেজ সক্রিয় হয়েছে।", android.widget.Toast.LENGTH_SHORT).show()
                            showPurchaseDialog = false
                            onNavigate(AppNavKey.ApiCenter)
                        },
                        onFailure = { error ->
                            android.widget.Toast.makeText(context, error.message ?: "প্যাকেজ ক্রয় ব্যর্থ হয়েছে।", android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                    purchaseLoading = false
                }
            }
        )
    }

    val onNavigateToApiCenter: () -> Unit = {
        val accountLevel = prefs.getString("pcu_account_level", "FREE_LEVEL") ?: "FREE_LEVEL"
        if (accountLevel == "FREE_LEVEL") {
            showPurchaseDialog = true
        } else {
            onNavigate(AppNavKey.ApiCenter)
        }
    }
    var selectedTab by remember { mutableStateOf(HomeTab.HOME) }

    BackHandler(enabled = selectedTab != HomeTab.HOME) {
        selectedTab = HomeTab.HOME
    }

    Scaffold(
        containerColor = Color(0xFF0F172A), // Dashboard dark bg
        bottomBar = {
            CustomBottomBar(
                selectedTab = selectedTab,
                onTabSelect = { tab ->
                    selectedTab = tab
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                HomeTab.HOME -> DashboardScreen(
                    onNavigateToHistory = { selectedTab = HomeTab.SEARCH },
                    modifier = Modifier.fillMaxSize()
                )
                HomeTab.DEVICE -> DeviceScreen(
                    onNavigateBack = { selectedTab = HomeTab.HOME },
                    modifier = Modifier.fillMaxSize()
                )
                HomeTab.SEARCH -> TransactionSearchScreen(
                    modifier = Modifier.fillMaxSize()
                )
                HomeTab.API -> ApiIntegrationScreen(
                    onNavigateToCheckout = onNavigateToApiCenter,
                    modifier = Modifier.fillMaxSize()
                )
                HomeTab.PROFILE -> ProfileSettingsScreen(
                    onNavigateBack = { selectedTab = HomeTab.HOME },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Placeholder — Transaction History (ধাপ ৫-এ পূর্ণ হবে)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HistoryPlaceholderScreen() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.History,
                contentDescription = "History",
                tint               = Color(0xFF22D3EE),
                modifier           = Modifier.size(56.dp)
            )
            Text(
                text       = "ট্রানজেকশন হিস্টোরি",
                color      = Color.White,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text     = "ধাপ ৫-এ সম্পূর্ণ UI তৈরি হবে...",
                color    = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Sub-page Navigation State
// ─────────────────────────────────────────────────────────────────────────────
// SettingsSubPage enum removed as SETTINGS tab is no longer used

// ─────────────────────────────────────────────────────────────────────────────
// SettingsMenuScreen — Settings Select Menu
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsMenuScreen(
    onNavigateToGateway: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Settings Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF22D3EE).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFF22D3EE),
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = "সেটিংস",
                    color = Color(0xFFF8FAFC),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "অ্যাপ ও অ্যাকাউন্ট কনফিগারেশন",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Option 1: Gateway/Device Settings
        SettingsMenuCard(
            title = "গেটওয়ে ও ডিভাইস সেটিংস",
            description = "সিম স্লট ১/২ ও পেমেন্ট মেথড কনফিগার করুন",
            icon = Icons.Default.Tune,
            iconBgColor = Color(0xFF22D3EE),
            onClick = onNavigateToGateway
        )

        // Option 2: Profile Settings
        SettingsMenuCard(
            title = "মার্চেন্ট প্রোফাইল সেটিংস",
            description = "পিন পরিবর্তন, লিংকড মোবাইল ও জিমেইল যুক্ত করুন",
            icon = Icons.Default.Person,
            iconBgColor = Color(0xFF10B981),
            onClick = onNavigateToProfile
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SettingsMenuCard — Clickable Settings Card Item
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsMenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBgColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(0xFFF8FAFC),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
