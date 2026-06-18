package online.paychek.app.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
fun screenWidth(): Dp {
    return LocalConfiguration.current
        .screenWidthDp.dp
}

@Composable  
fun adaptiveTextSize(
    small: TextUnit, 
    normal: TextUnit
): TextUnit {
    return if (screenWidth() < 360.dp) 
        small else normal
}

@Composable
fun adaptivePadding(
    small: Dp, normal: Dp
): Dp {
    return if (screenWidth() < 360.dp) 
        small else normal
}

@Composable
fun adaptiveTextSizeFull(
    small: TextUnit,
    normal: TextUnit,
    large: TextUnit
): TextUnit {
    val width = screenWidth()
    return when {
        width < 360.dp -> small
        width >= 600.dp -> large
        else -> normal
    }
}

@Composable
fun adaptivePaddingFull(
    small: Dp,
    normal: Dp,
    large: Dp
): Dp {
    val width = screenWidth()
    return when {
        width < 360.dp -> small
        width >= 600.dp -> large
        else -> normal
    }
}

