package online.paychek.app.ui.components.plan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import online.paychek.app.data.remote.dto.V3ActiveSubscriptionDto

private const val ROTATE_MS = 2_600L

/** Prefer familiar product order: Basic → Personal → Business → Gateway. */
private fun packageNameSortKey(name: String): Int {
    val n = name.lowercase()
    return when {
        n.startsWith("basic") -> 0
        n.startsWith("personal") -> 1
        n.startsWith("business") -> 2
        n.startsWith("gateway") -> 3
        else -> 50
    }
}

fun durationPackageLabel(durationKey: String?): String = when (durationKey) {
    "1m" -> "Monthly Package"
    "6m" -> "Half Yearly Package"
    "12m" -> "Yearly Package"
    else -> "Active Package"
}

fun shortPackageDisplayName(fullName: String, durationKey: String?): String {
    val prefixes = buildList {
        when (durationKey) {
            "1m" -> add("Monthly")
            "6m" -> {
                add("Annually")
                add("Half Yearly")
                add("Half-Yearly")
            }
            "12m" -> add("Yearly")
        }
        add("Monthly")
        add("Yearly")
        add("Annually")
        add("Half Yearly")
        add("Half-Yearly")
    }
    var s = fullName.trim()
    for (p in prefixes) {
        val withSpace = "$p "
        if (s.startsWith(withSpace, ignoreCase = true)) {
            s = s.substring(withSpace.length).trim()
            break
        }
    }
    return s.ifBlank { fullName.trim() }
}

/**
 * Two rotating lines:
 *  1) duration — Yearly / Monthly / Half Yearly Package
 *  2) package names — Basic, or Basic + Personal, …
 */
fun buildPlanTitleFrames(subs: List<V3ActiveSubscriptionDto>): List<String> {
    val active = subs.filter { it.packageFullName.isNotBlank() || !it.displayName.isNullOrBlank() }
    if (active.isEmpty()) return emptyList()

    val durationKey = active
        .mapNotNull { it.durationKey }
        .maxByOrNull { key ->
            when (key) {
                "12m" -> 3
                "6m" -> 2
                "1m" -> 1
                else -> 0
            }
        }

    val durationLine = durationPackageLabel(durationKey)

    val names = active
        .map { sub ->
            sub.displayName?.takeIf { it.isNotBlank() }
                ?: shortPackageDisplayName(sub.packageFullName, sub.durationKey)
        }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(
            compareBy<String> { packageNameSortKey(it) }
                .thenBy { it.lowercase() }
        )

    val packagesLine = names.joinToString(" + ")
    return listOf(durationLine, packagesLine).filter { it.isNotBlank() }.distinct()
}

@Composable
fun RotatingPlanTitle(
    frames: List<String>,
    color: Color,
    fontSize: TextUnit = 15.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier,
    fallback: String = "ফ্রি প্ল্যান"
) {
    val lines = frames.ifEmpty { listOf(fallback) }
    var index by remember(lines) { mutableIntStateOf(0) }

    LaunchedEffect(lines) {
        if (lines.size <= 1) return@LaunchedEffect
        while (true) {
            delay(ROTATE_MS)
            index = (index + 1) % lines.size
        }
    }

    val safeIndex = index.coerceIn(0, lines.lastIndex)
    AnimatedContent(
        targetState = lines[safeIndex],
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "planTitleRotate",
        modifier = modifier
    ) { text ->
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.basicMarquee()
        )
    }
}
