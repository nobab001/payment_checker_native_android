package online.paychek.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * All navigation destinations for the Paychek app.
 * Each object represents one screen/destination in the nav graph.
 */
sealed interface NavKey : androidx.navigation3.runtime.NavKey {

    @Serializable data object Home   : NavKey
    @Serializable data object Splash : NavKey
    @Serializable data object Login  : NavKey
    @Serializable data object Otp    : NavKey
    @Serializable data class Signup(val contact: String, val token: String) : NavKey
    @Serializable data object Pin    : NavKey

    @Serializable data object Dashboard  : NavKey
    @Serializable data object Devices    : NavKey
    @Serializable data object Profile    : NavKey
    @Serializable data object Sync       : NavKey
    @Serializable data object ApiCenter  : NavKey
    @Serializable data object WebsiteManagement : NavKey
    @Serializable data class  WebsiteSettings(val websiteId: Int) : NavKey
    @Serializable data object ApiDocs : NavKey
    @Serializable data object AdminDashboard : NavKey
    @Serializable data object AdminBillingConfig : NavKey
    @Serializable data class SubscriptionPackages(val initialTab: Int = 0) : NavKey
    @Serializable data object PaymentGatewayMock : NavKey
}
