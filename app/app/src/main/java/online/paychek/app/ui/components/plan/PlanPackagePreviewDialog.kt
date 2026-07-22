package online.paychek.app.ui.components.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import online.paychek.app.data.remote.dto.PlanFeatureDto

@Composable
fun PlanPackagePreviewDialog(
    title: String,
    subtitle: String,
    price: Double,
    features: List<PlanFeatureDto>,
    permissionLines: List<Pair<String, Boolean>> = emptyList(),
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "বন্ধ")
                    }
                }
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("৳${price.toInt()}", fontWeight = FontWeight.Black, fontSize = 26.sp, color = Color(0xFF22D3EE))

                if (permissionLines.isNotEmpty()) {
                    Text("পারমিশন", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    permissionLines.forEach { (label, allowed) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (allowed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (allowed) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }

                Text("বিস্তারিত", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                features.forEach { feature ->
                    val ok = feature.icon != "cross"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (ok) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(feature.text, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onEdit != null) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                            Text("এডিট")
                        }
                    }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("বন্ধ করুন")
                    }
                }
            }
        }
    }
}

fun subscriptionPermissionLines(
    permTemplate: Int,
    permWebsite: Int,
    permDevice: Int,
    permCustomSender: Int,
    permSmartPopup: Int = 0
): List<Pair<String, Boolean>> = listOf(
    "কাস্টম সেন্ডার আইডি" to (permCustomSender == 1),
    "টেমপ্লেট যোগ/সক্রিয়" to (permTemplate == 1),
    "ওয়েবসাইট যোগ" to (permWebsite == 1),
    "ডিভাইস যোগ" to (permDevice == 1),
    "স্মার্ট পপ-আপ" to (permSmartPopup == 1)
)

fun addonPermissionLines(
    maxDevices: Int,
    permCustomSender: Int = 1,
    permDevice: Int = 1,
    permSmartPopup: Int = 0
): List<Pair<String, Boolean>> = listOf(
    "কাস্টম সেন্ডার আইডি" to (permCustomSender == 1),
    "ডিভাইস যোগ (সর্বোচ্চ $maxDevices)" to (permDevice == 1),
    "টেমপ্লেট" to false,
    "ওয়েবসাইট" to false,
    "স্মার্ট পপ-আপ" to (permSmartPopup == 1)
)
