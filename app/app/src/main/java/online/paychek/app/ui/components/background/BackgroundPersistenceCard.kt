package online.paychek.app.ui.components.background

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import online.paychek.app.services.foreground.SmsServiceGuard
import online.paychek.app.utils.AccessibilityHelper
import online.paychek.app.utils.BatteryOptimizationHelper
import online.paychek.app.utils.OemBackgroundHelper

/**
 * Setup Progress Card — শুধু সেটিংস বাকি থাকলে দেখায়।
 * সম্পূর্ণ হলে একবার সফল মেসেজ → তারপর লুকিয়ে যায়; অ্যাপ আবার খুললে আর আসে না।
 */
@Composable
fun BackgroundPersistenceCard(
    isServiceActive: Boolean,
    modifier: Modifier = Modifier,
    onRefreshService: () -> Unit = {},
    onSetupStateChanged: (accessibilityOk: Boolean, batteryOk: Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var accessibilityOk by remember {
        mutableStateOf(AccessibilityHelper.isAccessibilityServiceEnabled(context))
    }
    var batteryOk by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    // শুধু incomplete → complete ট্রানজিশনে একবার celebration
    var showReadyCelebration by remember { mutableStateOf(false) }
    val serviceAlive = SmsServiceGuard.isServiceAlive()

    fun refreshChecks() {
        val wasIncomplete = !accessibilityOk || !batteryOk
        accessibilityOk = AccessibilityHelper.isAccessibilityServiceEnabled(context)
        batteryOk = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        onSetupStateChanged(accessibilityOk, batteryOk)

        val nowReady = accessibilityOk && batteryOk
        if (nowReady && wasIncomplete) {
            showReadyCelebration = true
        }
        if (nowReady && isServiceActive) {
            onRefreshService()
        }
    }

    LaunchedEffect(lifecycleOwner, isServiceActive) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refreshChecks()
        }
    }

    LaunchedEffect(showReadyCelebration) {
        if (showReadyCelebration) {
            delay(2200)
            showReadyCelebration = false
        }
    }

    val allReady = accessibilityOk && batteryOk
    val needsServiceRestart = isServiceActive && allReady && !serviceAlive

    // সম্পূর্ণ প্রস্তুত → কার্ড লুকাও (celebration চলাকালীন ছাড়া)
    if (allReady && !showReadyCelebration && !needsServiceRestart) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allReady) Color(0xFF10B981).copy(alpha = 0.12f)
            else Color(0xFFF59E0B).copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (allReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                    null,
                    tint = if (allReady) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("ব্যাকগ্রাউন্ড গার্ড", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        when {
                            showReadyCelebration -> "সেটিংস সম্পূর্ণ — ডিভাইস প্রস্তুত"
                            needsServiceRestart -> "সেটিংস ঠিক আছে কিন্তু সার্ভিস বন্ধ — পুনরায় চালু করুন"
                            else -> "২৪ ঘণ্টা অ্যাপ চালু রাখতে নিচের ২টি সেটিংস করুন"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!allReady) {
                    TextButton(
                        onClick = { refreshChecks() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("পরীক্ষা", fontSize = 11.sp)
                    }
                }
            }

            if (!allReady || showReadyCelebration) {
                SetupRow(
                    done = accessibilityOk,
                    title = "১. Accessibility চালু",
                    subtitle = "Paychek Background Guard → ON",
                    onSetup = { AccessibilityHelper.openAccessibilitySettings(context) }
                )
                SetupRow(
                    done = batteryOk,
                    title = "২. Battery Unrestricted",
                    subtitle = "অপ্টিমাইজ করবেন না / Unrestricted",
                    onSetup = { OemBackgroundHelper.openBatteryUnrestrictedSettings(context) }
                )
            }

            AnimatedVisibility(
                visible = showReadyCelebration,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "🎉 আপনার ডিভাইস প্রস্তুত",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF059669),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (!allReady) {
                OutlinedButton(
                    onClick = { refreshChecks() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("আবার পরীক্ষা করুন", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            if (needsServiceRestart) {
                Button(
                    onClick = {
                        refreshChecks()
                        onRefreshService()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("সার্ভিস চালু করুন", color = Color(0xFF0B0E14), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SetupRow(
    done: Boolean,
    title: String,
    subtitle: String,
    onSetup: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null,
            tint = if (done) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!done) {
            TextButton(onClick = onSetup, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("সেটআপ", fontSize = 11.sp)
            }
        }
    }
}
