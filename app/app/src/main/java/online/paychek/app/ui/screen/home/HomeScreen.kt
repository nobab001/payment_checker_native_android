package online.paychek.app.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import online.paychek.app.NavKey as AppNavKey
import online.paychek.app.ui.screen.dashboard.DashboardScreen
import online.paychek.app.ui.screen.gateway.GatewayCustomizerScreen
import online.paychek.app.ui.screen.profile.ProfileSettingsScreen
import online.paychek.app.ui.screen.transactions.TransactionHistoryScreen
import online.paychek.app.ui.theme.RoyalIndigo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowBackIosNew

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Tab সংজ্ঞা
// ─────────────────────────────────────────────────────────────────────────────
private enum class HomeTab(
    val icon: ImageVector,
    val label: String
) {
    DASHBOARD(Icons.Default.Dashboard, "ড্যাশবোর্ড"),
    HISTORY  (Icons.Default.History,   "ট্রানজেকশন"),
    SETTINGS (Icons.Default.Settings,  "সেটিংস")
}

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen — Bottom Navigation Hub
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val onNavigateToApiCenter: () -> Unit = { onNavigate(AppNavKey.ApiCenter) }
    var selectedTab by remember { mutableStateOf(HomeTab.DASHBOARD) }
    var currentSettingsSubPage by remember { mutableStateOf(SettingsSubPage.MENU) }

    Scaffold(
        containerColor = Color(0xFF0F172A), // Dashboard dark bg
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E293B),
                contentColor   = Color.White
            ) {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected     = selectedTab == tab,
                        onClick      = {
                            if (tab == HomeTab.SETTINGS && selectedTab == HomeTab.SETTINGS) {
                                // Reset settings sub-page to menu if settings tab is clicked again
                                currentSettingsSubPage = SettingsSubPage.MENU
                            }
                            selectedTab = tab
                        },
                        icon  = {
                            Icon(
                                imageVector     = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text       = tab.label,
                                fontSize   = 10.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = Color(0xFF22D3EE),
                            selectedTextColor   = Color(0xFF22D3EE),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8),
                            indicatorColor      = Color(0xFF22D3EE).copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                HomeTab.DASHBOARD -> DashboardScreen(
                    onNavigateToHistory = { selectedTab = HomeTab.HISTORY },
                    modifier            = Modifier.fillMaxSize()
                )
                HomeTab.HISTORY   -> TransactionHistoryScreen(
                    modifier = Modifier.fillMaxSize()
                )
                HomeTab.SETTINGS  -> {
                    when (currentSettingsSubPage) {
                        SettingsSubPage.MENU -> SettingsMenuScreen(
                            onNavigateToGateway = { currentSettingsSubPage = SettingsSubPage.GATEWAY },
                            onNavigateToProfile = { currentSettingsSubPage = SettingsSubPage.PROFILE },
                            modifier = Modifier.fillMaxSize()
                        )
                        SettingsSubPage.GATEWAY -> GatewayCustomizerScreen(
                            onNavigateToApiCenter = onNavigateToApiCenter,
                            onNavigateBack = { currentSettingsSubPage = SettingsSubPage.MENU },
                            modifier = Modifier.fillMaxSize()
                        )
                        SettingsSubPage.PROFILE -> ProfileSettingsScreen(
                            onNavigateBack = { currentSettingsSubPage = SettingsSubPage.MENU },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
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
private enum class SettingsSubPage {
    MENU,
    GATEWAY,
    PROFILE
}

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
