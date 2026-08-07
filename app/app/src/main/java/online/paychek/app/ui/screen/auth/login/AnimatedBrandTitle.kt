package online.paychek.app.ui.screen.auth.login

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BrandCycleNames = listOf(
    "Payment Checker",
    "Paycheck",
    "paycheckbd.com"
)

/** Hold / delete / type pacing — deliberate, not snappy. */
private const val HOLD_FULL_MS = 2_400L
private const val DELETE_CHAR_MS = 95L
private const val TYPE_CHAR_MS = 100L
private const val EMPTY_PAUSE_MS = 380L

/**
 * Cycles brand titles by cutting letters (alternating end / front),
 * then typing the next name. Runs while the login screen is open.
 */
@Composable
fun AnimatedBrandTitle(
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp
) {
    var display by remember { mutableStateOf(BrandCycleNames.first()) }

    LaunchedEffect(Unit) {
        var index = 0
        display = BrandCycleNames[index]
        while (true) {
            delay(HOLD_FULL_MS)
            val next = BrandCycleNames[(index + 1) % BrandCycleNames.size]
            // Alternate: cut from end, then from front — matches “লাস্ট / সামনে অক্ষর কেটে”.
            val cutFromEnd = index % 2 == 0

            while (display.isNotEmpty()) {
                display = if (cutFromEnd) display.dropLast(1) else display.drop(1)
                delay(DELETE_CHAR_MS)
            }
            delay(EMPTY_PAUSE_MS)

            for (i in 1..next.length) {
                display = next.take(i)
                delay(TYPE_CHAR_MS)
            }

            index = (index + 1) % BrandCycleNames.size
        }
    }

    Text(
        text = display.ifEmpty { " " },
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
