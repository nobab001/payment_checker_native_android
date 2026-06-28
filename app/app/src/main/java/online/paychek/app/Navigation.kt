package online.paychek.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import online.paychek.app.ui.screen.auth.login.LoginScreen
import online.paychek.app.ui.screen.auth.signup.SignupScreen
import online.paychek.app.ui.screen.home.HomeScreen
import online.paychek.app.ui.screen.apicenter.CheckoutDesignerScreen
import online.paychek.app.ui.screen.admin.AdminDashboardScreen
import online.paychek.app.ui.screen.admin.BillingConfigScreen
import online.paychek.app.ui.screen.profile.ProfileSettingsScreen
import online.paychek.app.ui.screen.sync.SyncSettingsScreen
import online.paychek.app.utils.SecurePreferences

@Composable
fun MainNavigation() {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Dynamically determine the start destination based on saved session state
    val startDestination = androidx.compose.runtime.remember(context) {
        val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
        val profileComplete = SecurePreferences.decrypt(context, "pcu_profile_complete")
        val contact = SecurePreferences.decrypt(context, "pcu_contact")
        val role = SecurePreferences.decrypt(context, "pcu_user_role")
        
        if (token.isNotEmpty()) {
            if (profileComplete == "false") {
                NavKey.Signup(contact, token)
            } else {
                if (role == "admin") {
                    NavKey.AdminDashboard
                } else {
                    NavKey.Home
                }
            }
        } else {
            NavKey.Login
        }
    }
    
    val backStack = rememberNavBackStack(startDestination)

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
                CheckoutDesignerScreen(
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
                        backStack.add(NavKey.Login)
                    }
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


