package online.paychek.app.ui.components.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import online.paychek.app.data.remote.dto.PlanFeatureDto
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

private data class EditablePlanFeature(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val icon: String = PlanFeatureDto.ICON_CHECK
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanFeaturesEditorDialog(
    planName: String,
    subtitle: String,
    price: Double,
    highlighted: Boolean,
    buyButtonText: String,
    initialFeatures: List<PlanFeatureDto>,
    onDismiss: () -> Unit,
    onSave: (List<PlanFeatureDto>) -> Unit
) {
    val features = remember {
        mutableStateListOf<EditablePlanFeature>().apply {
            addAll(
                initialFeatures.map {
                    EditablePlanFeature(text = it.text, icon = it.icon)
                }.ifEmpty {
                    listOf(EditablePlanFeature(text = ""))
                }
            )
        }
    }
    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
        val item = features.removeAt(from.index)
        features.add(to.index, item)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "প্যাকেজ ডিটেইল এডিট",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "বন্ধ")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ইউজার প্রিভিউ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    PlanPackageCard(
                        planName = planName.ifBlank { "প্যাকেজের নাম" },
                        subtitle = subtitle.ifBlank { "সাবটাইটেল" },
                        price = price,
                        features = features.map {
                            PlanFeatureDto(it.text, it.icon)
                        }.ifEmpty {
                            listOf(PlanFeatureDto("নতুন লাইন যোগ করুন", PlanFeatureDto.ICON_CHECK))
                        },
                        highlighted = highlighted,
                        buyButtonText = buyButtonText,
                        onBuyClick = {}
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ডিটেইল লাইনসমূহ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        TextButton(
                            onClick = {
                                features.add(EditablePlanFeature(text = ""))
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("লাইন যোগ")
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(features, key = { _, item -> item.id }) { index, feature ->
                            ReorderableItem(reorderState, key = feature.id) { isDragging ->
                                FeatureEditRow(
                                    feature = feature,
                                    isDragging = isDragging,
                                    onTextChange = { features[index] = feature.copy(text = it) },
                                    onIconToggle = {
                                        val next = if (feature.icon == PlanFeatureDto.ICON_CHECK) {
                                            PlanFeatureDto.ICON_CROSS
                                        } else {
                                            PlanFeatureDto.ICON_CHECK
                                        }
                                        features[index] = feature.copy(icon = next)
                                    },
                                    onRemove = {
                                        if (features.size > 1) features.removeAt(index)
                                    },
                                    dragHandleModifier = Modifier.draggableHandle()
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("বাতিল")
                    }
                    Button(
                        onClick = {
                            onSave(
                                features
                                    .map { PlanFeatureDto(it.text.trim(), it.icon) }
                                    .filter { it.text.isNotEmpty() }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("সেভ করুন")
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureEditRow(
    feature: EditablePlanFeature,
    isDragging: Boolean,
    onTextChange: (String) -> Unit,
    onIconToggle: () -> Unit,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    val isCheck = feature.icon != PlanFeatureDto.ICON_CROSS
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "ড্র্যাগ",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandleModifier
            )
            OutlinedTextField(
                value = feature.text,
                onValueChange = onTextChange,
                placeholder = { Text("লাইনের টেক্সট লিখুন") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(onClick = onIconToggle) {
                Icon(
                    imageVector = if (isCheck) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = "আইকন টগল",
                    tint = if (isCheck) Color(0xFF10B981) else Color(0xFFEF4444)
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "মুছুন",
                    tint = Color(0xFFEF4444)
                )
            }
        }
    }
}
