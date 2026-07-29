package online.paychek.app.ui.components.plan

import androidx.compose.ui.graphics.Color

/**
 * Shared accent constants for the premium subscription UI.
 * Single source of truth so tabs, duration selector, and banner stay in harmony.
 */

/** Vivid indigo used for "selected/active" navigation states (tabs + duration). */
val PremiumNavAccent = Color(0xFF4F46E5)

/** Savings / discount green (text). */
val PremiumDiscountGreen = Color(0xFF16A34A)

/** Light green chip background for unselected discount pills. */
val PremiumDiscountGreenBg = Color(0xFFDCFCE7)

/** Star / sparkle gold for the promo banner. */
val PremiumStarGold = Color(0xFFFBBF24)

/** Promo banner gradient: hot-pink → violet → sky-blue (left → right). */
val PremiumBannerGradient = listOf(
    Color(0xFFEC4899),
    Color(0xFFA855F7),
    Color(0xFF60A5FA)
)
