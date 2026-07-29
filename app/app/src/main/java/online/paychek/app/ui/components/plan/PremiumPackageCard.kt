package online.paychek.app.ui.components.plan

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * - Keychain/medal badge inside card (top-right) for discounted packages
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

            // ── Keychain Badge (top-right, inside card) ──────────────────────
            if (!badgeTagText.isNullOrBlank() || !discountBadge.isNullOrBlank()) {
                KeychainBadge(
                    tagText = badgeTagText,
                    percentText = discountBadge,
                    color = accentColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 10.dp)
                )
            }
        }
    }
}

// ─── Keychain / Medal Badge ──────────────────────────────────────────────────

/**
 * A keychain-style badge: square tag → ring → medal circle with percentage.
 * Resembles a price-tag/key-tag with a medal ribbon below.
 */
@Composable
private fun KeychainBadge(
    tagText: String?,
    percentText: String?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // ── Tag (square with text) ───────────────────────────────────────────
        if (!tagText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color,
                shadowElevation = 2.dp
            ) {
                Text(
                    text = tagText,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    maxLines = 1
                )
            }
        }

        // ── Ring connector ───────────────────────────────────────────────────
        Canvas(modifier = Modifier.size(width = 12.dp, height = 10.dp)) {
            val cx = size.width / 2
            // Draw a small ring/circle connector
            drawCircle(
                color = color,
                radius = 3.dp.toPx(),
                center = Offset(cx, 3.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )
            // Short line from ring to medal
            drawLine(
                color = color,
                start = Offset(cx, 6.dp.toPx()),
                end = Offset(cx, size.height),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // ── Medal circle with percentage ─────────────────────────────────────
        if (!percentText.isNullOrBlank()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color)
            ) {
                Text(
                    text = percentText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            // ── Ribbon tails ─────────────────────────────────────────────────
            Canvas(modifier = Modifier.size(width = 24.dp, height = 8.dp)) {
                val w = size.width
                val h = size.height
                // Left ribbon
                drawPath(
                    path = Path().apply {
                        moveTo(w * 0.25f, 0f)
                        lineTo(w * 0.45f, 0f)
                        lineTo(w * 0.35f, h)
                        lineTo(w * 0.15f, h)
                        close()
                    },
                    color = color.copy(alpha = 0.7f)
                )
                // Right ribbon
                drawPath(
                    path = Path().apply {
                        moveTo(w * 0.55f, 0f)
                        lineTo(w * 0.75f, 0f)
                        lineTo(w * 0.85f, h)
                        lineTo(w * 0.65f, h)
                        close()
                    },
                    color = color.copy(alpha = 0.7f)
                )
            }
        }
    }
}
