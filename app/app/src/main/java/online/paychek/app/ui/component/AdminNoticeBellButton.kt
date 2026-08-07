package online.paychek.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.paychek.app.services.notify.AdminNoticeManager

private val BadgeRed = Color(0xFFEF4444)

/**
 * Shared admin-notice bell with unread badge + inbox sheet.
 * [circleBackground] = home-header style; null = plain TopAppBar IconButton.
 */
@Composable
fun AdminNoticeBellButton(
    tint: Color = Color.White,
    iconSize: Dp = 20.dp,
    buttonSize: Dp = 36.dp,
    circleBackground: Color? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val unreadCount by AdminNoticeManager.unreadCount.collectAsStateWithLifecycle(initialValue = 0)
    var showInbox by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AdminNoticeManager.syncUiState(context)
    }

    if (showInbox) {
        AdminNoticeInboxSheet(onDismiss = { showInbox = false })
    }

    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "নোটিফিকেশন",
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
            if (unreadCount > 0) {
                val badgeLabel = if (unreadCount > 9) "9+" else unreadCount.toString()
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .heightIn(min = 14.dp)
                        .defaultMinSize(minWidth = 14.dp)
                        .clip(CircleShape)
                        .background(BadgeRed)
                        .padding(horizontal = if (unreadCount > 9) 3.dp else 0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeLabel,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }

    if (circleBackground != null) {
        Box(
            modifier = modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(circleBackground)
                .clickable { showInbox = true },
            contentAlignment = Alignment.Center,
            content = { content() }
        )
    } else {
        IconButton(
            onClick = { showInbox = true },
            modifier = modifier.size(buttonSize),
            content = { content() }
        )
    }
}
