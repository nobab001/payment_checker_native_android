package online.paychek.app.ui.screen.apicenter.website

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import online.paychek.app.data.remote.dto.CheckoutHelplineConfigDto
import online.paychek.app.data.remote.dto.SaveCheckoutHelplineRequest

private val AccentCyan = Color(0xFF22D3EE)
private val AccentGreen = Color(0xFF10B981)
private val AccentAmber = Color(0xFFF59E0B)

private data class HelplineIconOption(
    val key: String,
    val emoji: String,
    val color: Color,
    val prefix: String,
)

private val HELPLINE_ICONS = listOf(
    HelplineIconOption("whatsapp", "💬", Color(0xFF25D366), "https://wa.me/"),
    HelplineIconOption("telegram", "✈️", Color(0xFF229ED9), "https://t.me/"),
    HelplineIconOption("facebook", "👤", Color(0xFF1877F2), "https://facebook.com/"),
    HelplineIconOption("messenger", "💭", Color(0xFF0084FF), "https://m.me/"),
    HelplineIconOption("instagram", "📷", Color(0xFFE4405F), "https://instagram.com/"),
    HelplineIconOption("phone", "📞", Color(0xFF0D9488), ""),
    HelplineIconOption("mail", "✉️", Color(0xFF64748B), "mailto:"),
    HelplineIconOption("support", "🆘", Color(0xFF0F766E), ""),
)

private fun iconOption(key: String) = HELPLINE_ICONS.firstOrNull { it.key == key } ?: HELPLINE_ICONS.first()

@Composable
fun CheckoutHelplineFabOverlay(
    config: CheckoutHelplineConfigDto,
    isSaving: Boolean,
    onSave: (SaveCheckoutHelplineRequest) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    val icon = iconOption(config.icon)
    val isActive = config.enabled && config.value.isNotBlank()

    FloatingActionButton(
        onClick = { showDialog = true },
        modifier = modifier
            .padding(end = 16.dp, bottom = 20.dp)
            .size(56.dp)
            .alpha(if (isActive) 1f else 0.38f)
            .then(if (!isActive) Modifier.blur(1.5.dp) else Modifier),
        containerColor = icon.color,
        contentColor = Color.White,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
    ) {
        Text(icon.emoji, fontSize = 24.sp)
    }

    if (showDialog) {
        CheckoutHelplineDialog(
            config = config,
            isSaving = isSaving,
            onDismiss = { showDialog = false },
            onSave = { draft ->
                onSave(draft)
                showDialog = false
            },
            onDelete = {
                onDelete()
                showDialog = false
            },
        )
    }
}

@Composable
private fun CheckoutHelplineDialog(
    config: CheckoutHelplineConfigDto,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (SaveCheckoutHelplineRequest) -> Unit,
    onDelete: () -> Unit,
) {
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var iconKey by remember(config) { mutableStateOf(config.icon.ifBlank { "whatsapp" }) }
    var label by remember(config) { mutableStateOf(config.label) }
    var value by remember(config) { mutableStateOf(config.value) }

    val selected = iconOption(iconKey)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SupportAgent, null, tint = AccentCyan, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("চেকআউট হেল্পলাইন", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "চেকআউট পেজের নিচে ডান দিকে এই আইকন দেখা যাবে। শুধু একটি হেল্পলাইন রাখা যাবে।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(14.dp))

                Text("আইকন সিলেক্ট করুন", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 180.dp),
                ) {
                    items(HELPLINE_ICONS, key = { it.key }) { opt ->
                        val selectedNow = opt.key == iconKey
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(opt.color.copy(alpha = if (selectedNow) 1f else 0.75f))
                                .border(
                                    width = if (selectedNow) 2.dp else 0.dp,
                                    color = if (selectedNow) AccentCyan else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { iconKey = opt.key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(opt.emoji, fontSize = 22.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("লেবেল (ঐচ্ছিক)") },
                    placeholder = { Text("খালি রাখলে দেখাবে না", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                )
                Spacer(Modifier.height(10.dp))

                if (selected.prefix.isNotBlank()) {
                    Text(
                        selected.prefix,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = {
                        Text(
                            when (iconKey) {
                                "phone" -> "মোবাইল নাম্বার"
                                "mail" -> "ইমেইল"
                                "support" -> "লিংক"
                                else -> "নাম্বার / ইউজারনেম"
                            }
                        )
                    },
                    placeholder = {
                        Text(
                            when (iconKey) {
                                "whatsapp", "phone" -> "8801XXXXXXXXX"
                                "telegram" -> "username"
                                "mail" -> "help@example.com"
                                else -> "username বা লিংক"
                            },
                            fontSize = 11.sp,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("চালু রাখুন", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        onSave(
                            SaveCheckoutHelplineRequest(
                                enabled = enabled,
                                icon = iconKey,
                                label = label.trim(),
                                value = value.trim(),
                            )
                        )
                    },
                    enabled = !isSaving && value.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                    }
                }

                if (config.value.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                        border = BorderStroke(1.dp, AccentAmber),
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("হেল্পলাইন মুছুন")
                    }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("বাতিল")
                }
            }
        }
    }
}
