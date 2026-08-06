package online.paychek.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.paychek.app.data.remote.dto.AppNotificationDto
import online.paychek.app.services.notify.AdminNoticeManager

/**
 * Shows queued admin announcements one at a time.
 *
 * The status-bar notification is the primary delivery; this popup catches the
 * user who opens the app without tapping it. Dismissing clears the notice from
 * the local queue — the read receipt already went out on delivery.
 *
 * @param refreshKey bump to re-read the queue after a heartbeat lands.
 */
@Composable
fun AdminNoticeDialog(refreshKey: Any? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var queue by remember { mutableStateOf<List<AppNotificationDto>>(emptyList()) }

    // Keystore decrypt — never on the main thread.
    LaunchedEffect(refreshKey) {
        queue = withContext(Dispatchers.IO) { AdminNoticeManager.pendingPopups(context) }
    }

    val notice = queue.firstOrNull() ?: return

    val dismiss = {
        queue = queue.drop(1)
        scope.launch(Dispatchers.IO) { AdminNoticeManager.clearPopup(context, notice.id) }
        Unit
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(
                text = notice.title,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = notice.body,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = dismiss) {
                Text("বুঝেছি")
            }
        }
    )
}
