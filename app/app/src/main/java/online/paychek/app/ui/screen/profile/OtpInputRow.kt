package online.paychek.app.ui.screen.profile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val PsCyan = Color(0xFF22D3EE)

@Composable
fun OtpInputRow(
    otpValue: String,
    onOtpChange: (String) -> Unit,
    accentColor: Color = PsCyan,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val otpFocusRequester = remember { FocusRequester() }
    val otpInteractionSource = remember { MutableInteractionSource() }
    val isDarkTheme = MaterialTheme.colorScheme.background == Color(0xFF0B0E14)
    val textColor = MaterialTheme.colorScheme.onBackground

    var otpValueState by remember {
        val padded = otpValue.padEnd(6, ' ')
        val selIndex = selectionIndexFor(otpValue)
        mutableStateOf(TextFieldValue(text = padded, selection = TextRange(selIndex, selIndex + 1)))
    }

    LaunchedEffect(otpValue) {
        val padded = otpValue.padEnd(6, ' ')
        if (padded != otpValueState.text) {
            val selIndex = selectionIndexFor(otpValue)
            otpValueState = TextFieldValue(text = padded, selection = TextRange(selIndex, selIndex + 1))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(interactionSource = otpInteractionSource, indication = null) {
                otpFocusRequester.requestFocus()
                keyboardController?.show()
                val selIndex = selectionIndexFor(otpValue)
                otpValueState = otpValueState.copy(selection = TextRange(selIndex, selIndex + 1))
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 6) {
                val char = otpValue.getOrNull(i)?.toString() ?: " "
                val hasDigit = char.isNotBlank() && char != " "
                val isFocused = otpValueState.selection.start == i ||
                    (i == 5 && otpValueState.selection.start == 6)

                val boxBg = when {
                    hasDigit -> if (isDarkTheme) Color(0xFF1A2E3D) else Color(0xFFE0F7FA)
                    else -> if (isDarkTheme) Color(0xFF131B26) else Color.White
                }
                val borderColor = when {
                    isFocused -> accentColor
                    hasDigit -> accentColor.copy(alpha = 0.55f)
                    else -> if (isDarkTheme) Color(0xFF4A5F73) else Color(0xFF90A4AE)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(boxBg, RoundedCornerShape(10.dp))
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            otpFocusRequester.requestFocus()
                            keyboardController?.show()
                            otpValueState = otpValueState.copy(selection = TextRange(i, i + 1))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (hasDigit) char else "",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                        if (isFocused && hasDigit) {
                            OtpBlinkingCursor(color = accentColor)
                        }
                    }
                    if (isFocused && !hasDigit) {
                        OtpBlinkingCursor(color = accentColor)
                    }
                }
            }
        }

        val emptyTextToolbar = object : TextToolbar {
            override fun showMenu(
                rect: androidx.compose.ui.geometry.Rect,
                onCopy: (() -> Unit)?,
                onPaste: (() -> Unit)?,
                onCut: (() -> Unit)?,
                onSelectAll: (() -> Unit)?
            ) {}

            override fun hide() {}
            override val status: TextToolbarStatus = TextToolbarStatus.Hidden
        }

        CompositionLocalProvider(LocalTextToolbar provides emptyTextToolbar) {
            BasicTextField(
                value = otpValueState,
                onValueChange = { newValue ->
                    val (sanitized, targetSelection) = processOtpInput(
                        oldText = otpValueState.text,
                        newText = newValue.text,
                        oldSelection = otpValueState.selection
                    )
                    if (sanitized != otpValue) {
                        onOtpChange(sanitized)
                    }
                    otpValueState = TextFieldValue(text = sanitized, selection = targetSelection)
                    if (newValue.text.length < otpValueState.text.length) {
                        otpFocusRequester.requestFocus()
                        keyboardController?.show()
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                textStyle = TextStyle(
                    color = Color.Transparent,
                    fontSize = 1.sp,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Color.Transparent),
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(otpFocusRequester)
            )
        }
    }
}

@Composable
private fun OtpBlinkingCursor(color: Color) {
    val transition = rememberInfiniteTransition(label = "OtpCursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OtpCursorAlpha"
    )
    Box(
        modifier = Modifier
            .width(2.dp)
            .height(20.dp)
            .alpha(alpha)
            .background(color)
    )
}

private fun selectionIndexFor(otp: String): Int {
    val firstEmpty = otp.indexOf(' ')
    return if (firstEmpty != -1) firstEmpty else min(otp.length, 5)
}

private fun processOtpInput(
    oldText: String,
    newText: String,
    oldSelection: TextRange
): Pair<String, TextRange> {
    if (newText.length < oldText.length) {
        val i = oldSelection.start
        val isBoxEmpty = oldSelection.collapsed || i >= oldText.length || oldText[i] == ' '
        return if (!isBoxEmpty) {
            val sb = StringBuilder(oldText)
            if (i in oldText.indices) sb.setCharAt(i, ' ')
            Pair(sb.toString(), TextRange(i, i + 1))
        } else {
            val deleteIndex = i - 1
            val sb = StringBuilder(oldText)
            if (deleteIndex in oldText.indices) sb.setCharAt(deleteIndex, ' ')
            val newCursor = maxOf(0, deleteIndex)
            Pair(sb.toString(), TextRange(newCursor, newCursor + 1))
        }
    }
    if (newText != oldText) {
        val insertedLength = newText.length - oldText.length + (oldSelection.end - oldSelection.start)
        if (insertedLength > 0 && oldSelection.start < 6) {
            val insertedText = newText.substring(
                oldSelection.start,
                min(oldSelection.start + insertedLength, newText.length)
            )
            val digitsOnly = insertedText.filter { it.isDigit() }
            if (digitsOnly.isNotEmpty()) {
                val sb = StringBuilder(oldText)
                for (idx in digitsOnly.indices) {
                    val targetIdx = oldSelection.start + idx
                    if (targetIdx < 6) sb.setCharAt(targetIdx, digitsOnly[idx])
                }
                val nextIndex = oldSelection.start + digitsOnly.length
                val sel = if (nextIndex < 6) TextRange(nextIndex, nextIndex + 1) else TextRange(5, 6)
                return Pair(sb.toString(), sel)
            }
        }
    }
    return Pair(oldText, oldSelection)
}
