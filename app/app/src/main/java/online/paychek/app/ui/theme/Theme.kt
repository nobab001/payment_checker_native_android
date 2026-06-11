package online.paychek.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import online.paychek.app.config.AppConfig

private val DarkColorScheme = darkColorScheme(
    primary          = RoyalIndigoLight,
    onPrimary        = Color(0xFFF5F7FA),
    primaryContainer = RoyalIndigoLight,
    background       = Color(0xFF0B0E14), // Ultra-premium Navy Black
    surface          = Color(0xFF131722), // Card/Container surface
    onBackground     = Color(0xFFF5F7FA), // Titles (Diamond White)
    onSurface        = Color(0xFFF5F7FA),
    onSurfaceVariant = Color(0xFF9095A1), // Small Text (Matted Grey)
    secondary        = BkashPink,
    tertiary         = UpayTeal,
)

private val LightColorScheme = lightColorScheme(
    primary          = RoyalIndigo,
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = RoyalIndigoLight,
    background       = Color(0xFFFAFAFC), // Premium Soft Off-White
    surface          = Color(0xFFFFFFFF), // Pure White Card surface
    onBackground     = Color(0xFF12161F), // Titles (Dark Charcoal)
    onSurface        = Color(0xFF12161F),
    onSurfaceVariant = Color(0xFF636E72), // Small Text (Smoky Grey)
    secondary        = BkashPink,
    tertiary         = UpayTeal,
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) {
        context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
    }

    var themePref by remember(sharedPrefs) {
        mutableStateOf(sharedPrefs.getString("pcu_app_theme", "system") ?: "system")
    }

    DisposableEffect(sharedPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "pcu_app_theme") {
                themePref = sharedPrefs.getString("pcu_app_theme", "system") ?: "system"
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val darkTheme = when (themePref) {
        "light" -> false
        "dark"  -> true
        else    -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}
