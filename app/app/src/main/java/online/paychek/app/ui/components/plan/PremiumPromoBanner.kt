package online.paychek.app.ui.components.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Premium gradient promo banner (pink → violet → sky-blue).
 *
 * Layout: [star chip] [title + subtitle] [white "save %" pill], with subtle
 * sparkle accents top-right. Hidden entirely when [maxDiscount] <= 0.
 *
 * Responsive: the middle column is weighted and the title wraps to two lines on
 * narrow screens so nothing overflows.
 */
@Composable
fun PremiumPromoBanner(
    maxDiscount: Int,
    modifier: Modifier = Modifier
) {
    if (maxDiscount <= 0) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(PremiumBannerGradient))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Sparkle accents (decorative)
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp)
                .size(13.dp)
        )
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = PremiumStarGold.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 22.dp, top = 14.dp)
                .size(8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Star chip
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = PremiumStarGold,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Title + subtitle
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "বার্ষিক প্ল্যানে $maxDiscount% পর্যন্ত ছাড়",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 21.sp
                )
                Text(
                    text = "দীর্ঘ মেয়াদে বেশি সুবিধা পান",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))

            // White save pill
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Text(
                    text = "$maxDiscount% ছাড়",
                    color = Color(0xFFDB2777),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                )
            }
        }
    }
}
