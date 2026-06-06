package online.paychek.app.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey

/**
 * HomeScreen — placeholder.
 * ব্লুপ্রিন্ট অনুযায়ী এটি পরে Dashboard / Profile / Devices
 * BottomNavigation সহ সম্পূর্ণ তৈরি হবে।
 */
@Composable
fun HomeScreen(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "🏠 Paychek Home — Coming Soon")
    }
}
