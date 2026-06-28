package online.paychek.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import online.paychek.app.utils.BangladeshTimeUtil
import online.paychek.app.utils.RefreshCooldown

@Composable
fun LastUpdateRow(
    lastUpdatedAtMs: Long?,
    isRefreshing: Boolean,
    onReload: () -> Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF22D3EE),
    mutedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val context = LocalContext.current
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tick = System.currentTimeMillis()
        }
    }

    val canRefresh = remember(tick) { RefreshCooldown.canRefresh() }
    val timeLabel = lastUpdatedAtMs?.let { BangladeshTimeUtil.formatDateTime(it) } ?: "—"

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "সর্বশেষ আপডেট: $timeLabel",
            color = mutedColor,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )

        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = accentColor
            )
        } else {
            IconButton(
                onClick = {
                    val started = onReload()
                    if (!started) {
                        Toast.makeText(
                            context,
                            "৫ সেকেন্ড পরে আবার রিফ্রেশ করুন",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = canRefresh,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "রিলোড",
                    tint = if (canRefresh) accentColor else mutedColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
