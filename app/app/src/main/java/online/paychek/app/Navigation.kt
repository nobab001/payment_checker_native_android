package online.paychek.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import online.paychek.app.ui.screen.auth.login.LoginScreen
import online.paychek.app.ui.screen.auth.signup.SignupScreen
import online.paychek.app.ui.screen.home.HomeScreen
import online.paychek.app.ui.screen.apicenter.GlobalCheckoutScreen
import online.paychek.app.ui.screen.admin.AdminDashboardScreen
import online.paychek.app.ui.screen.admin.AdminUserSettingsScreen
import online.paychek.app.ui.screen.admin.BillingConfigScreen
import online.paychek.app.ui.screen.profile.ProfileSettingsScreen
import online.paychek.app.ui.screen.sync.SyncSettingsScreen
import online.paychek.app.utils.AccountEntitlementsStore
import online.paychek.app.utils.SecurePreferences
import online.paychek.app.utils.SessionFlags
import online.paychek.app.utils.SubscriptionLockState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MainNavigation() {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Fast start destination — plain SessionFlags (no Keystore on first frame)
    val startDestination = androidx.compose.runtime.remember(context) {
        if (SessionFlags.hasAuth(context)) {
            if (!SessionFlags.isProfileComplete(context)) {
                NavKey.Signup(SessionFlags.contact(context), "")
            } else if (SessionFlags.userRole(context) == "admin") {
                NavKey.AdminDashboard
            } else {
                NavKey.Home
            }
        } else {
            NavKey.Login
        }
    }
    
    val backStack = rememberNavBackStack(startDestination)

    val billingSuccess by MainActivity.pendingBillingSuccess
    LaunchedEffect(billingSuccess) {
        if (!billingSuccess) return@LaunchedEffect
        val orderId = MainActivity.pendingBillingOrderId
        var activated = false
        var statusMessage: String? = null

        withContext(Dispatchers.IO) {
            // Only toast success after server confirms activation (webhook fulfill).
            if (!orderId.isNullOrBlank()) {
                val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
                if (token.isNotBlank()) {
                    val repo = online.paychek.app.data.repository.PaymentRepository()
                    repeat(8) { attempt ->
                        val result = repo.getSubscriptionCheckoutStatus(token, orderId)
                        val st = result.getOrNull()
                        if (st != null) {
                            when {
                                st.activated || st.status == "activated" -> {
                                    activated = true
                                    statusMessage = st.message
                                    return@repeat
                                }
                                st.status == "failed" -> {
                                    statusMessage = st.message
                                        ?: "পেমেন্ট যাচাই হয়েছে, কিন্তু প্যাকেজ সক্রিয় হয়নি।"
                                    return@repeat
                                }
                            }
                        }
                        if (attempt < 7) kotlinx.coroutines.delay(1500)
                    }
                }
            }
            AccountEntitlementsStore.refresh(context)
            SubscriptionLockState.refresh(context)
        }
        SubscriptionLockState.notifyBillingRefresh(context)
        MainActivity.pendingBillingSuccess.value = false
        MainActivity.pendingBillingOrderId = null
        if (SessionFlags.hasAuth(context)) {
            while (backStack.size > 1) {
                val last = backStack.lastOrNull()
                if (last is NavKey.Home) break
                backStack.removeLastOrNull()
            }
            if (backStack.lastOrNull() !is NavKey.Home) {
                backStack.add(NavKey.Home)
            }
            val toastText = when {
                activated -> statusMessage ?: "পেমেন্ট সফল — সাবস্ক্রিপশন আপডেট হয়েছে।"
                !orderId.isNullOrBlank() ->
                    statusMessage
                        ?: "পেমেন্ট পাওয়া গেছে। প্যাকেজ সক্রিয় হতে কয়েক মুহূর্ত লাগতে পারে — সাবস্ক্রিপশন পেজ রিফ্রেশ করুন।"
                else -> "পেমেন্ট সফল — সাবস্ক্রিপশন আপডেট হয়েছে।"
            }
            android.widget.Toast.makeText(
                context,
                toastText,
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<NavKey.Login> {
                LoginScreen(
                    onNavigateToSignup = { contact, token ->
                        backStack.add(NavKey.Signup(contact, token))
                    },
                    onNavigateToHome = { token ->
                        SecurePreferences.encrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN, token)
                        SecurePreferences.encrypt(context, "pcu_profile_complete", "true")
                        backStack.add(NavKey.Home)
                    },
                    onNavigateToAdminDashboard = { token ->
                        SecurePreferences.encrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN, token)
                        SecurePreferences.encrypt(context, "pcu_profile_complete", "true")
                        backStack.add(NavKey.AdminDashboard)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.Signup> { key ->
                SignupScreen(
                    contact = key.contact,
                    token = key.token,
                    onSignupComplete = {
                        SecurePreferences.encrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN, key.token)
                        SecurePreferences.encrypt(context, "pcu_profile_complete", "true")
                        backStack.add(NavKey.Home)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.Home> {
                HomeScreen(
                    onNavigate = { navKey -> backStack.add(navKey) },
                    modifier = Modifier
                        .safeDrawingPadding()
                        .padding(16.dp)
                )
            }

            entry<NavKey.ApiCenter> {
                GlobalCheckoutScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.WebsiteManagement> {
                online.paychek.app.ui.screen.apicenter.website.WebsiteManagementScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onOpenWebsite = { id -> backStack.add(NavKey.WebsiteSettings(id)) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.WebsiteSettings> { key ->
                online.paychek.app.ui.screen.apicenter.website.WebsiteSettingsScreen(
                    websiteId = key.websiteId,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onOpenCheckoutNumbers = { backStack.add(NavKey.CheckoutNumbers(key.websiteId)) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.CheckoutNumbers> { key ->
                online.paychek.app.ui.screen.apicenter.website.CheckoutNumbersScreen(
                    websiteId = key.websiteId,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.ApiDocs> {
                online.paychek.app.ui.screen.apicenter.docs.ApiDocsScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.Profile> {
                ProfileSettingsScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToSubscription = { backStack.add(NavKey.SubscriptionPackages()) },
                    modifier       = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.Sync> {
                SyncSettingsScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    modifier       = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.AdminDashboard> {
                AdminDashboardScreen(
                    onLogout = {
                        SecurePreferences.remove(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
                        SecurePreferences.remove(context, "pcu_user_role")
                        SecurePreferences.remove(context, "pcu_profile_complete")
                        SecurePreferences.remove(context, "pcu_contact")
                        SessionFlags.clear(context)
                        backStack.add(NavKey.Login)
                    },
                    onOpenUserSettings = { userId ->
                        backStack.add(NavKey.AdminUserSettings(userId))
                    }
                )
            }

            entry<NavKey.AdminUserSettings> { key ->
                AdminUserSettingsScreen(
                    userId = key.userId,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.AdminBillingConfig> {
                BillingConfigScreen(
                    viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.SubscriptionPackages> { key ->
                online.paychek.app.ui.screen.billing.SubscriptionPackagesScreen(
                    initialTab = key.initialTab,
                    onNavigateToPaymentMock = { backStack.add(NavKey.PaymentGatewayMock) },
                    onNavigateBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.PaymentGatewayMock> {
                online.paychek.app.ui.screen.billing.PaymentGatewayMockScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
    )
}


