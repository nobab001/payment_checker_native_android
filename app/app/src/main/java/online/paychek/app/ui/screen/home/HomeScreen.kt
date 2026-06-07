package online.paychek.app.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import online.paychek.app.ui.screen.transactions.TransactionHistoryScreen
import online.paychek.app.ui.theme.RoyalIndigo

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
    var selectedTab by remember { mutableStateOf(HomeTab.DASHBOARD) }

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
                            if (tab == HomeTab.HISTORY) {
                                // Transaction History → আলাদা স্ক্রিনে নেভিগেট
                                // (ধাপ ৫-এ পূর্ণ স্ক্রিন তৈরি হলে এখানে যোগ হবে)
                                selectedTab = tab
                            } else {
                                selectedTab = tab
                            }
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
                HomeTab.SETTINGS  -> SettingsPlaceholderScreen()
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
// Placeholder — Settings (পরবর্তী ফেজে তৈরি হবে)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsPlaceholderScreen() {
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
                imageVector        = Icons.Default.Settings,
                contentDescription = "Settings",
                tint               = Color(0xFF94A3B8),
                modifier           = Modifier.size(56.dp)
            )
            Text(
                text       = "সেটিংস",
                color      = Color.White,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text     = "শীঘ্রই আসছে...",
                color    = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
        }
    }
}
