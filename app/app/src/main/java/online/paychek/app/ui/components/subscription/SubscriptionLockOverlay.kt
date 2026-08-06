package online.paychek.app.ui.components.subscription

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.paychek.app.ui.theme.RoyalIndigo
import online.paychek.app.ui.theme.StatusRed

/**
 * Global subscription lock — covers all bottom-nav tab content when expired/suspended.
 */
@Composable
fun SubscriptionLockOverlay(
    onRenewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0E14)
    val cardBg = if (isDark) Color(0xF21B2030) else Color(0xF5FFFFFF)
    val scrim = Color(0xCC0B0E14)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(12.dp)
                    } else {
                        Modifier
                    }
                )
                .background(scrim)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = if (isDark) 0.12f else 0.35f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Subscription expired",
                    tint = StatusRed,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "সাবস্ক্রিপশনের মেয়াদ উত্তীর্ণ হয়েছে",
                    color = if (isDark) Color.White else Color(0xFF212121),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "আপনার সাবস্ক্রিপশন বা ফ্রি ট্রায়ালের মেয়াদ শেষ হয়ে গেছে। রিয়েল-টাইম এসএমএস মনিটরিং, এপিআই ও সিঙ্ক সার্ভিস সচল রাখতে অনুগ্রহ করে আপনার প্ল্যানটি রিনিউ করুন।",
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF757575),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRenewClick,
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "প্যাকেজ রিনিউ করুন (Renew Plan)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
