package online.paychek.app.ui.components.background

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import online.paychek.app.services.foreground.SmsServiceGuard
import online.paychek.app.utils.AccessibilityHelper
import online.paychek.app.utils.BatteryOptimizationHelper
import online.paychek.app.utils.OemBackgroundHelper

/**
 * সব ফোনে একই ২-ধাপ সেটআপ — অন্য পেমেন্ট অ্যাপের মতো:
 * ১. Accessibility ON  ২. Battery Unrestricted
 */
@Composable
fun BackgroundPersistenceCard(
    isServiceActive: Boolean,
    modifier: Modifier = Modifier,
    onRefreshService: () -> Unit = {}
) {
    val context = LocalContext.current
    var accessibilityOk by remember { mutableStateOf(AccessibilityHelper.isAccessibilityServiceEnabled(context)) }
    var batteryOk by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) }
    val serviceAlive = SmsServiceGuard.isServiceAlive()

    LaunchedEffect(isServiceActive) {
        accessibilityOk = AccessibilityHelper.isAccessibilityServiceEnabled(context)
        batteryOk = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    val allReady = accessibilityOk && batteryOk
    if (!isServiceActive && allReady) return
    if (isServiceActive && allReady && serviceAlive) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allReady) Color(0xFF10B981).copy(alpha = 0.1f)
            else Color(0xFFF59E0B).copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (allReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                    null,
                    tint = if (allReady) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                Column {
                    Text("ব্যাকগ্রাউন্ড গার্ড", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (allReady && serviceAlive) "২৪ ঘণ্টা সক্রিয় — ${OemBackgroundHelper.vendorLabel()}"
                        else if (!serviceAlive && isServiceActive) "সেটিংস ঠিক আছে কিন্তু সার্ভিস বন্ধ — পুনরায় চালু করুন"
                        else "অন্য পেমেন্ট অ্যাপের মতো নিচের ২টি সেটিংস করুন",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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

            if (isServiceActive && (!serviceAlive || !allReady)) {
                Button(
                    onClick = onRefreshService,
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
