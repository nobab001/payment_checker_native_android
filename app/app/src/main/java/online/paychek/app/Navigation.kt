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
import online.paychek.app.ui.screen.profile.ProfileSettingsScreen

@Composable
fun MainNavigation() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Start at Login screen by default
    val backStack = rememberNavBackStack(NavKey.Login)

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
                        val sharedPrefs = context.getSharedPreferences(online.paychek.app.config.AppConfig.PREF_NAME, android.content.Context.MODE_PRIVATE)
                        sharedPrefs.edit().putString(online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN, token).apply()
                        backStack.add(NavKey.Home)
                    },
                    onNavigateToAdminDashboard = { token ->
                        val sharedPrefs = context.getSharedPreferences(online.paychek.app.config.AppConfig.PREF_NAME, android.content.Context.MODE_PRIVATE)
                        sharedPrefs.edit().putString(online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN, token).apply()
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
                        val sharedPrefs = context.getSharedPreferences(online.paychek.app.config.AppConfig.PREF_NAME, android.content.Context.MODE_PRIVATE)
                        sharedPrefs.edit().putString(online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN, key.token).apply()
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
                    modifier       = Modifier.fillMaxSize()
                )
            }

            entry<NavKey.AdminDashboard> {
                AdminDashboardScreen(
                    onLogout = {
                        val sharedPrefs = context.getSharedPreferences(online.paychek.app.config.AppConfig.PREF_NAME, android.content.Context.MODE_PRIVATE)
                        sharedPrefs.edit().remove(online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN).apply()
                        backStack.add(NavKey.Login)
                    }
                )
            }
        },
    )
}


