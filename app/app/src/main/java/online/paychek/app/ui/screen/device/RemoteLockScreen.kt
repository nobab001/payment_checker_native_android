package online.paychek.app.ui.screen.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Slate900 = Color(0xFF0F172A)
private val Indigo950 = Color(0xFF1E1B4B)
private val AlertRed = Color(0xFFEF4444)
private val Slate800 = Color(0xFF1E293B)
private val MutedText = Color(0xFF94A3B8)

@Composable
fun RemoteLockScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Slate900,
                        Indigo950,
                        Slate900
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(vertical = 48.dp, horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Main Lock Image & Warning
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Glowy lock icon container
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(Slate800, shape = CircleShape)
                        .border(2.dp, AlertRed, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Device Blocked",
                        tint = AlertRed,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Text(
                    text = "ডিভাইস নিষ্ক্রিয় করা হয়েছে",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "নিরাপত্তা এবং সিস্টেম পলিসির কারণে প্যারেন্ট ফোন থেকে এই চাইল্ড ডিভাইসটির অ্যাক্সেস বন্ধ করে দেওয়া হয়েছে।",
                    fontSize = 14.sp,
                    color = MutedText,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🔒 ডিভাইস স্ট্যাটাস: নিষ্ক্রিয় (Deactivated)",
                        color = AlertRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "পুনরায় সচল করতে আপনার মার্চেন্ট অ্যাকাউন্টের মেইন ফোন বা প্যারেন্ট ডিভাইস থেকে 'আদার্স ডিভাইস' অপশনে গিয়ে এই চাইল্ড ফোনটিকে সচল করুন।",
                        color = MutedText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Support Button
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/paychek_support"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "গ্রাহক সহায়তা (Telegram)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
