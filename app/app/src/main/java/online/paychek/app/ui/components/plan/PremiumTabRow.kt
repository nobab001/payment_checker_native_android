package online.paychek.app.ui.components.plan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Icon per tab position (Gateway / Business / Personal).
private val TabIcons: List<ImageVector> = listOf(
    Icons.Filled.Language,  // globe
    Icons.Filled.Business,  // office building
    Icons.Filled.Person     // person
)

/**
 * Premium category tabs — pill style.
 *
 * Selected  = solid [PremiumNavAccent] fill, white icon + label, soft elevation.
 * Unselected = surface fill with a thin outline border, muted icon + label.
 * Equal-weight columns so it stays balanced on every screen width.
 */
@Composable
fun PremiumTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = index == selectedIndex
            val icon = TabIcons.getOrElse(index) { Icons.Filled.Person }

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) PremiumNavAccent else MaterialTheme.colorScheme.surface,
                animationSpec = tween(250),
                label = "TabBg_$index"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(250),
                label = "TabContent_$index"
            )
            val elevation by animateDpAsState(
                targetValue = if (isSelected) 4.dp else 0.dp,
                animationSpec = tween(250),
                label = "TabElev_$index"
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clickable { onTabSelected(index) },
                shape = RoundedCornerShape(27.dp),
                color = bgColor,
                shadowElevation = elevation,
                border = if (isSelected) null
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
