package online.paychek.app.ui.components.background

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import online.paychek.app.utils.BackgroundSetupStep
import online.paychek.app.utils.BatteryOptimizationHelper
import online.paychek.app.utils.OemBackgroundHelper

@Composable
fun BackgroundPersistenceCard(
    isServiceActive: Boolean,
    modifier: Modifier = Modifier,
    onRefreshService: () -> Unit = {}
) {
    val context = LocalContext.current
    var steps by remember { mutableStateOf(OemBackgroundHelper.getRequiredSteps(context)) }
    val serviceAlive = SmsServiceGuard.isServiceAlive()
    val batteryOk = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)

    LaunchedEffect(isServiceActive, batteryOk) {
        steps = OemBackgroundHelper.getRequiredSteps(context)
    }

    if (!isServiceActive && steps.isEmpty()) return

    val showWarning = isServiceActive && (!serviceAlive || steps.isNotEmpty() || !batteryOk)
    if (!showWarning && steps.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFF59E0B))
                Column {
                    Text(
                        "ব্যাকগ্রাউন্ড সেটআপ (${OemBackgroundHelper.vendorLabel()})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        if (!serviceAlive && isServiceActive) {
                            "সার্ভিস বন্ধ হয়ে গেছে — নিচের সেটিংস সম্পন্ন করুন"
                        } else {
                            "লক স্ক্রিনে ২৪/৭ চালু রাখতে এই ধাপগুলো প্রয়োজন"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!serviceAlive && isServiceActive) {
                Button(
                    onClick = onRefreshService,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("সার্ভিস পুনরায় চালু করুন", color = Color(0xFF0B0E14), fontWeight = FontWeight.Bold)
                }
            }

            steps.forEach { step ->
                BackgroundStepRow(
                    step = step,
                    onOpen = { OemBackgroundHelper.openStep(context, step.id) },
                    onDone = {
                        OemBackgroundHelper.acknowledgeStep(context, step.id)
                        steps = OemBackgroundHelper.getRequiredSteps(context)
                    }
                )
            }

            if (steps.isEmpty() && serviceAlive) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                    Text("ব্যাকগ্রাউন্ড সেটআপ সম্পন্ন — সার্ভিস সচল", fontSize = 12.sp, color = Color(0xFF10B981))
                }
            }
        }
    }
}

@Composable
private fun BackgroundStepRow(
    step: BackgroundSetupStep,
    onOpen: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(step.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(step.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Text("সেটিংস খুলুন", fontSize = 11.sp)
            }
            TextButton(onClick = onDone, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("সম্পন্ন ✓", fontSize = 11.sp)
            }
        }
    }
}
