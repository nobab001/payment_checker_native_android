package online.paychek.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.paychek.app.services.connectivity.ConnectionBanner

@Composable
fun ConnectionStatusBanner(
    banner: ConnectionBanner,
    modifier: Modifier = Modifier
) {
    val (background, textColor, icon) = when (banner) {
        is ConnectionBanner.Reconnecting ->
            Triple(Color(0xFFE0F2FE), Color(0xFF075985), Icons.Default.Sync)
        is ConnectionBanner.Disconnected ->
            Triple(Color(0xFFFFEDD5), Color(0xFF9A3412), Icons.Default.CloudOff)
        is ConnectionBanner.NoInternet ->
            Triple(Color(0xFFFEF3C7), Color(0xFF92400E), Icons.Default.WifiOff)
        is ConnectionBanner.ServerError ->
            Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), Icons.Default.Build)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = banner.message,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = banner.message,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** @deprecated Use [ConnectionStatusBanner] with [ConnectionBanner] */
@Composable
fun ConnectivityBanner(modifier: Modifier = Modifier) {
    ConnectionStatusBanner(
        banner = ConnectionBanner.NoInternet(),
        modifier = modifier
    )
}
