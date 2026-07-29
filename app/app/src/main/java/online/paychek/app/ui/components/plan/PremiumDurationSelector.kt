package online.paychek.app.ui.components.plan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One segment of the duration selector. */
data class DurationTab(
    val primary: String,        // big label, e.g. "১২ মাস"
    val secondary: String,      // small tag,  e.g. "Yearly"
    val discountPercent: Int = 0
)

/**
 * Premium duration selector — single rounded container with thin vertical
 * dividers between unselected segments. Each segment shows a calendar icon chip,
 * a two-line label (primary + secondary tag) and an optional discount pill.
 *
 * Adaptive: the calendar icon and the secondary tag are dropped on very narrow
 * cells (via [BoxWithConstraints]) so three columns always fit, from 320dp phones
 * to tablets, without overflow.
 */
@Composable
fun PremiumDurationSelector(
    segments: List<DurationTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            segments.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex

                DurationCell(
                    tab = tab,
                    isSelected = isSelected,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f)
                )

                // Short divider only between two UNSELECTED neighbours.
                if (index < segments.lastIndex &&
                    !isSelected &&
                    selectedIndex != index + 1
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationCell(
    tab: DurationTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fillColor by animateColorAsState(
        targetValue = if (isSelected) PremiumNavAccent else Color.Transparent,
        animationSpec = tween(250),
        label = "DurFill"
    )
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = tween(250),
        label = "DurElev"
    )

    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = fillColor,
        shadowElevation = elevation
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            val showIcon = maxWidth >= 92.dp
            val showSecondary = maxWidth >= 60.dp

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (showIcon) {
                    // Calendar icon chip
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = if (isSelected) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Two-line label
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = tab.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showSecondary) {
                        Text(
                            text = tab.secondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) Color.White.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Discount pill
                if (tab.discountPercent > 0) {
                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = if (isSelected) Color.White else PremiumDiscountGreenBg
                    ) {
                        Text(
                            text = "-${tab.discountPercent}%",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSelected) PremiumNavAccent else PremiumDiscountGreen,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
