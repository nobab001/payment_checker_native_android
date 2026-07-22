package online.paychek.app.ui.screen.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import online.paychek.app.data.remote.dto.SmsTemplateDto
import online.paychek.app.ui.screen.device.ReadyMadeTabs
import online.paychek.app.ui.theme.RoyalIndigo
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCheckoutTemplateOrderDialog(
    templates: List<SmsTemplateDto>,
    onDismiss: () -> Unit,
    onSave: (List<SmsTemplateDto>) -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val card = MaterialTheme.colorScheme.surface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var local by remember(templates) {
        mutableStateOf(
            templates
                .filter { it.isParseable == 1 }
                .sortedWith(compareBy({ it.displayOrder }, { it.id ?: 0 }))
                .toMutableList()
        )
    }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        local = local.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = bg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text("Checkout টেমপ্লেট অর্ডার", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = { onSave(local.toList()) }) {
                            Text("সেভ", fontWeight = FontWeight.Bold, color = RoyalIndigo)
                        }
                    }
                )
                Text(
                    text = "ড্রাগ করে উপরে/নিচে সাজান — কাস্টমার চেকআউটে এই অর্ডার দেখাবে (মার্চেন্ট ওভাররাইড থাকলে সেটা প্রাধান্য পাবে)।",
                    color = muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                ) {
                    itemsIndexed(local, key = { _, t -> t.id ?: t.hashCode() }) { index, item ->
                        ReorderableItem(reorderState, key = item.id ?: item.hashCode()) { _ ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(card, RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Drag",
                                    tint = muted,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .draggableHandle()
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${item.templateName}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${item.category ?: "SEND_MONEY"} · ${item.senderId}",
                                        color = muted,
                                        fontSize = 12.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCustomSenderTemplateOrderDialog(
    templates: List<SmsTemplateDto>,
    onDismiss: () -> Unit,
    onSave: (List<SmsTemplateDto>) -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val card = MaterialTheme.colorScheme.surface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ReadyMadeTabs.size })
    var byCategory by remember(templates) {
        mutableStateOf(
            ReadyMadeTabs.associate { (key, _) ->
                key to templates
                    .filter { it.isParseable == 0 && (it.category ?: "OTHERS").uppercase() == key }
                    .sortedWith(compareBy({ it.displayOrder }, { it.id ?: 0 }))
                    .toMutableList()
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text("কাস্টম সেন্ডার অর্ডার", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val flat = ReadyMadeTabs.flatMap { (key, _) ->
                                    byCategory[key].orEmpty()
                                }
                                onSave(flat)
                            }
                        ) {
                            Text("সেভ", fontWeight = FontWeight.Bold, color = RoyalIndigo)
                        }
                    }
                )
                Text(
                    text = "মার্চেন্টের রেডিমেট পেজের মতো ট্যাব — ড্রাগ করে অর্ডার বদলান, সোয়াইপ করে ট্যাব বদলান।",
                    color = muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 12.dp,
                    containerColor = bg,
                    contentColor = RoyalIndigo
                ) {
                    ReadyMadeTabs.forEachIndexed { index, pair ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(pair.second, fontSize = 13.sp) }
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val cat = ReadyMadeTabs[page].first
                    val list = byCategory[cat].orEmpty()
                    val listState = rememberLazyListState()
                    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
                        val updated = list.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                        byCategory = byCategory.toMutableMap().apply { put(cat, updated) }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (list.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("এই ট্যাবে টেমপ্লেট নেই", color = muted)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                        ) {
                            itemsIndexed(list, key = { _, t -> t.id ?: t.hashCode() }) { index, item ->
                                ReorderableItem(reorderState, key = item.id ?: item.hashCode()) { _ ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(card, RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Drag",
                                            tint = muted,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .draggableHandle()
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${index + 1}. ${item.templateName}",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = item.senderId,
                                                color = muted,
                                                fontSize = 12.sp
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
