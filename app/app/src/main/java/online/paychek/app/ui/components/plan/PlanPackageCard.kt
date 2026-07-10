package online.paychek.app.ui.components.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.paychek.app.data.remote.dto.PlanFeatureDto
import online.paychek.app.ui.theme.RoyalIndigo

@Composable
fun PlanPackageCard(
    planName: String,
    subtitle: String,
    price: Double,
    features: List<PlanFeatureDto>,
    highlighted: Boolean = false,
    buyButtonText: String = "Buy Now",
    buyButtonTextColor: Color = if (highlighted) Color.Black else Color.White,
    isPurchasing: Boolean = false,
    onBuyClick: (() -> Unit)? = null,
    onDetailsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardColor = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (highlighted) Color(0xFF22D3EE) else RoyalIndigo

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = if (highlighted) Color(0xFF22D3EE) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = planName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = textPrimary
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (onDetailsClick != null) {
                        TextButton(
                            onClick = onDetailsClick,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("বিস্তারিত", fontSize = 12.sp, color = accent)
                        }
                    }
                    Text(
                        text = "৳${price.toInt()}",
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = accent
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                features.forEach { feature ->
                    PlanFeatureRow(feature = feature, textColor = textMuted)
                }
            }

            if (onBuyClick != null) {
                Button(
                    onClick = onBuyClick,
                    enabled = !isPurchasing,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(
                            color = buyButtonTextColor,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = buyButtonText,
                            color = buyButtonTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlanFeatureRow(
    feature: PlanFeatureDto,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    val isCheck = feature.icon != PlanFeatureDto.ICON_CROSS
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isCheck) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isCheck) Color(0xFF10B981) else Color(0xFFEF4444),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = feature.text,
            fontSize = 13.sp,
            color = textColor
        )
    }
}
