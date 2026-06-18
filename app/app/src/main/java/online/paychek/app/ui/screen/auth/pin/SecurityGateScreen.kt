package online.paychek.app.ui.screen.auth.pin

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.ui.theme.RoyalIndigo
import online.paychek.app.ui.theme.RoyalIndigoLight

@Composable
fun SecurityGateScreen(
    onUnlockSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecurityGateViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    // Start Biometric Authentication automatically if enrolled
    LaunchedEffect(activity) {
        if (activity != null && isBiometricEnrolled(activity)) {
            showBiometricPrompt(
                activity = activity,
                onSuccess = onUnlockSuccess,
                onError = { err ->
                    // Set biometric failure or canceled error message
                }
            )
        }
    }

    // Shake animation when error occurs
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            repeat(4) {
                shakeOffset.animateTo(15f, animationSpec = tween(50, easing = LinearEasing))
                shakeOffset.animateTo(-15f, animationSpec = tween(50, easing = LinearEasing))
            }
            shakeOffset.animateTo(0f, animationSpec = tween(50, easing = LinearEasing))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Slate 900
                        Color(0xFF1E1B4B), // Indigo 950
                        Color(0xFF0F172A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(vertical = 32.dp, horizontal = 24.dp)
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 40.dp)
            ) {
                // Glowy lock icon container
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFF1E293B), shape = CircleShape)
                        .border(1.5.dp, Color(0xFF38BDF8), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "নিরাপত্তা লক",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "আপনার নিরাপত্তা পিন দিয়ে অ্যাপটি আনলক করুন",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }

            // PIN Dots Indicator Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset(x = shakeOffset.value.dp)
            ) {
                // ── v2.0.0: Tappable Overwrite Cells ──────────────────────
                // প্রতিটি ঘর tappable — click করলে cursor সেখানে যায়
                // ─────────────────────────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until SecurityGateViewModel.PIN_LENGTH) {
                        val cellChar    = uiState.cells.getOrNull(i)
                        val isCursor    = uiState.cursorIndex == i && !uiState.isFull && !uiState.isLoading
                        val isFilled    = cellChar != null
                        val hasError    = uiState.errorMessage != null

                        val borderColor = when {
                            hasError  -> Color(0xFFEF4444)
                            isCursor  -> Color(0xFF38BDF8)
                            isFilled  -> Color(0xFF38BDF8).copy(alpha = 0.5f)
                            else      -> Color(0xFF334155)
                        }
                        val bgColor = when {
                            hasError  -> Color(0xFF1E0A0A)
                            isCursor  -> Color(0xFF0F2233)
                            isFilled  -> Color(0xFF1E2A3A)
                            else      -> Color(0xFF1E293B)
                        }

                        val animatedBorder by animateDpAsState(
                            targetValue = if (isCursor) 2.dp else 1.5.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessMedium
                            ),
                            label = "CellBorder"
                        )

                        Box(
                            modifier = Modifier
                                .size(width = 38.dp, height = 46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .border(animatedBorder, borderColor, RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = { viewModel.moveCursorTo(i) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFilled) {
                                Text(
                                    text       = cellChar!!.toString(),
                                    fontSize   = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = if (hasError) Color(0xFFEF4444) else Color.White
                                )
                            } else if (isCursor) {
                                // cursor blink — ফাঁকা ঘরে underline দেখাও
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(2.dp)
                                        .background(Color(0xFF38BDF8))
                                        .align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier.height(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(visible = uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            fontSize = 13.sp,
                            color = Color(0xFFF87171),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = uiState.isLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // Custom PIN Pad
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                val numRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("biometric", "0", "delete")
                )

                numRows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.3f),
                                contentAlignment = Alignment.Center
                            ) {
                                when (item) {
                                    "biometric" -> {
                                        if (uiState.isFull) {
                                            IconButton(
                                                onClick = { viewModel.verifyPin(context, onUnlockSuccess) },
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .background(Color(0xFF10B981), shape = CircleShape)
                                                    .border(1.dp, Color(0xFF059669), shape = CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Confirm",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        } else if (activity != null && isBiometricEnrolled(activity)) {
                                            IconButton(
                                                onClick = {
                                                    showBiometricPrompt(
                                                        activity = activity,
                                                        onSuccess = onUnlockSuccess,
                                                        onError = {}
                                                    )
                                                },
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .background(Color(0xFF1E293B), shape = CircleShape)
                                                    .border(1.dp, Color(0xFF334155), shape = CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "Biometric Unlock",
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                    }
                                    "delete" -> {
                                        IconButton(
                                            onClick = { viewModel.deleteDigit() },
                                            modifier = Modifier
                                                .size(64.dp)
                                                .background(Color(0xFF1E293B), shape = CircleShape)
                                                .border(1.dp, Color(0xFF334155), shape = CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Delete Digit",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        val interactionSource = remember { MutableInteractionSource() }
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1E293B))
                                                .border(1.dp, Color(0xFF334155), shape = CircleShape)
                                                .clickable(
                                                    interactionSource = interactionSource,
                                                    indication = ripple(bounded = true, radius = 32.dp),
                                                    onClick = {
                                                        viewModel.overwriteDigitAtCursor(item.first(), context, onUnlockSuccess)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper: Context to FragmentActivity conversion
private fun Context.findFragmentActivity(): FragmentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is FragmentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

// Check if biometric authentication is available and enrolled
private fun isBiometricEnrolled(context: Context): Boolean {
    val biometricManager = BiometricManager.from(context)
    return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
}

// Trigger standard BiometricPrompt
private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("বায়োমেট্রিক মেলেনি।")
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("নিরাপত্তা লক")
        .setSubtitle("বায়োমেট্রিক ব্যবহার করে অ্যাপ আনলক করুন")
        .setNegativeButtonText("পিন ব্যবহার করুন")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()

    biometricPrompt.authenticate(promptInfo)
}
