package online.paychek.app.ui.components.plan

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.paychek.app.data.remote.dto.PlanFeatureDto
import online.paychek.app.utils.MoneyFormat

// ─── Card Color Palette ───────────────────────────────────────────────────────

/** Rotating color palette for cards — each card gets a distinct accent */
val CardColorPalette = listOf(
    Color(0xFF1A237E), // Royal Indigo
    Color(0xFF00897B), // Teal
    Color(0xFFC2185B), // Deep Pink
    Color(0xFFE65100), // Deep Orange
    Color(0xFF6A1B9A), // Deep Purple
    Color(0xFF1565C0), // Blue
    Color(0xFF2E7D32), // Green
    Color(0xFFAD1457), // Magenta
)

fun cardAccentColor(index: Int): Color = CardColorPalette[index % CardColorPalette.size]

// ─── Main Card ───────────────────────────────────────────────────────────────

/**
 * Premium Package Card — v3.
 *
 * - Multi-color: each card gets a unique accent via [accentColor]
 * - Offer badge (top-right) for discounted / recommended packages
 * - Strikethrough original price + savings
 * - Per-month equivalent
 * - Rich feature list
 */
@Composable
fun PremiumPackageCard(
    planName: String,
    subtitle: String,
    price: Double,
    features: List<PlanFeatureDto>,
    accentColor: Color,
    discountBadge: String? = null,
    originalPrice: Double? = null,
    perMonthText: String? = null,
    savingsText: String? = null,
    badgeTagText: String? = null,
    buyButtonText: String = "কিনুন",
    onBuyClick: () -> Unit,
    onDetailsClick: (() -> Unit)? = null
) {
    // Gradient uses the card's accent color
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            accentColor.copy(alpha = 0.05f)
        )
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.5.dp,
            color = accentColor.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Main content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gradientBrush)
                    .padding(20.dp)
            ) {
                // ── Top: Name ────────────────────────────────────────────────
                Text(
                    text = planName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 60.dp) // space for badge
                )

                Spacer(Modifier.height(10.dp))

                // ── Price Section ────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = MoneyFormat.taka(price),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        letterSpacing = (-0.5).sp
                    )
                    if (originalPrice != null && originalPrice > price) {
                        Text(
                            text = MoneyFormat.taka(originalPrice),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textDecoration = TextDecoration.LineThrough,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // Per-month + duration subtitle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!perMonthText.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accentColor.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = perMonthText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Savings line
                if (!savingsText.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = savingsText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF16A34A)
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ── Divider ──────────────────────────────────────────────────
                HorizontalDivider(
                    color = accentColor.copy(alpha = 0.15f),
                    thickness = 0.5.dp
                )

                Spacer(Modifier.height(12.dp))

                // ── Features ─────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    features.forEach { feature ->
                        val isCheck = feature.icon != PlanFeatureDto.ICON_CROSS
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isCheck) Icons.Default.CheckCircle
                                else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isCheck) Color(0xFF16A34A)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = feature.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isCheck) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── Action Buttons ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onDetailsClick != null) {
                        OutlinedButton(
                            onClick = onDetailsClick,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = "বিস্তারিত",
                                fontSize = 13.sp,
                                color = accentColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Button(
                        onClick = onBuyClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(if (onDetailsClick != null) 1.4f else 1f)
                            .height(48.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = buyButtonText,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            // ── Discount badge (top-right) ────────────────────────────────────
            if (!badgeTagText.isNullOrBlank() || !discountBadge.isNullOrBlank()) {
                DiscountOfferBadge(
                    tagText = badgeTagText,
                    percentText = discountBadge,
                    color = accentColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                )
            }
        }
    }
}

// ─── Discount offer badge ────────────────────────────────────────────────────

/**
 * Compact offer pill: optional tag chip above a bold percent capsule.
 */
@Composable
private fun DiscountOfferBadge(
    tagText: String?,
    percentText: String?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        if (!tagText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 2.dp),
                color = color.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
            ) {
                Text(
                    text = tagText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1
                )
            }
        }
        if (!percentText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color,
                shadowElevation = 3.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = percentText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
