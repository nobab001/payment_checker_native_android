package online.paychek.app.ui.screen.billing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.*
import online.paychek.app.data.repository.PaymentRepository
import online.paychek.app.ui.components.plan.PlanPackageCard
import online.paychek.app.ui.theme.RoyalIndigo
import online.paychek.app.utils.SecurePreferences

private fun v3TabLabel(key: String): String = when (key) {
    "gateway" -> "Gateway"
    "personal_business" -> "Personal Business"
    "personal" -> "Personal"
    else -> key
}

private fun v3TabInfo(key: String): String = when (key) {
    "gateway" -> "পেমেন্ট গেটওয়ে প্যাকেজ — ওয়েবসাইট সংখ্যা অনুযায়ী API ও চেকআউট অ্যাক্সেস।"
    "personal_business" -> "পার্সোনাল বিজনেস — একক ব্যবসার জন্য মনিটরিং ও টেমপ্লেট।"
    "personal" -> "পার্সোনাল — ব্যক্তিগত ব্যবহারের জন্য সীমিত ফিচার।"
    else -> ""
}

private fun purchaseTypeLabel(type: String): String = when (type) {
    "renew" -> "রিনিউ"
    "upgrade" -> "আপগ্রেড"
    "cross_category" -> "ক্রস-ক্যাটাগরি"
    else -> "নতুন ক্রয়"
}

private fun priceForDuration(pkg: V3PackageDto, durationKey: String): Double = when (durationKey) {
    "1m" -> pkg.price1m
    "6m" -> pkg.price6m
    else -> pkg.price12m
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionV3PackagesScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { PaymentRepository() }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var catalog by remember { mutableStateOf<SubscriptionV3CatalogResponse?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var durationIndex by remember { mutableIntStateOf(2) }
    var selectedPkg by remember { mutableStateOf<V3PackageDto?>(null) }
    var selectedAddons by remember { mutableStateOf(setOf<String>()) }
    var showTabInfo by remember { mutableStateOf<String?>(null) }
    var quoteResponse by remember { mutableStateOf<V3QuoteResponse?>(null) }
    var showSummary by remember { mutableStateOf(false) }
    var isQuoting by remember { mutableStateOf(false) }
    var isCheckingOut by remember { mutableStateOf(false) }
    var pendingOrderId by remember { mutableStateOf<String?>(null) }
  var checkoutError by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    fun token(): String = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)

    fun reload() {
        scope.launch {
            isLoading = true
            errorMessage = null
            if (token().isEmpty()) {
                errorMessage = "অনুগ্রহ করে লগইন করুন।"
                isLoading = false
                return@launch
            }
            repository.getV3BillingCatalog(token()).fold(
                onSuccess = {
                    catalog = it
                    isLoading = false
                },
                onFailure = { err ->
                    errorMessage = err.message
                    isLoading = false
                }
            )
        }
    }

    fun pollOrder(orderId: String) {
        scope.launch {
            repeat(12) {
                repository.getSubscriptionCheckoutStatus(token(), orderId).fold(
                    onSuccess = { st ->
                        if (st.activated || st.status == "activated") {
                            online.paychek.app.utils.AccountEntitlementsStore.refresh(context)
                            pendingOrderId = null
                            showSummary = false
                            quoteResponse = null
                            reload()
                            android.widget.Toast.makeText(
                                context,
                                st.message ?: "প্যাকেজ সক্রিয় হয়েছে।",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                    },
                    onFailure = { }
                )
                delay(2500)
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    DisposableEffect(lifecycleOwner, pendingOrderId) {
        val orderId = pendingOrderId
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !orderId.isNullOrBlank()) {
                pollOrder(orderId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cat = catalog
    val tabOrder = cat?.tabOrder ?: listOf("gateway", "personal_business", "personal")
    val durationSegments = cat?.durationSegments ?: listOf(
        DurationSegmentDto("monthly", "1m", "Monthly"),
        DurationSegmentDto("annually", "6m", "Annually"),
        DurationSegmentDto("yearly", "12m", "Yearly")
    )
    val currentTabKey = tabOrder.getOrElse(selectedTab) { "gateway" }
    val durationKey = durationSegments.getOrElse(durationIndex) { durationSegments.last() }.durationKey
    val packages = cat?.categories?.get(currentTabKey).orEmpty()
    val addonCatalog = cat?.addons.orEmpty()
    val allowedAddonKeys = packages.firstOrNull()?.allowedAddons
        ?: when (currentTabKey) {
            "gateway" -> listOf("smart_popup", "custom_sender")
            "personal_business" -> listOf("custom_sender", "gateway_permission")
            else -> listOf("smart_popup", "custom_sender", "gateway_permission")
        }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            cat?.sharedExpiry?.let { exp ->
                Text(
                    "শেয়ার্ড মেয়াদ: $exp",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab.coerceIn(0, (tabOrder.size - 1).coerceAtLeast(0)),
                containerColor = RoyalIndigo,
                contentColor = Color.White,
                edgePadding = 8.dp,
                indicator = { positions ->
                    val idx = selectedTab.coerceIn(0, (positions.size - 1).coerceAtLeast(0))
                    if (positions.isNotEmpty()) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(positions[idx]),
                            color = Color.White
                        )
                    }
                }
            ) {
                tabOrder.forEachIndexed { index, key ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            selectedPkg = null
                            selectedAddons = emptySet()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(v3TabLabel(key), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                IconButton(
                                    onClick = { showTabInfo = key },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(16.dp)) {
                durationSegments.forEachIndexed { index, seg ->
                    SegmentedButton(
                        selected = durationIndex == index,
                        onClick = { durationIndex = index },
                        shape = SegmentedButtonDefaults.itemShape(index, durationSegments.size)
                    ) {
                        val disc = packages.firstOrNull()?.discounts?.get(seg.durationKey.removeSuffix("m") + "m")
                        val badge = if (index > 0 && (disc ?: 0) > 0) " -${disc}%" else ""
                        Text(seg.label + badge, fontSize = 11.sp)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color(0xFF22D3EE))
                    errorMessage != null -> Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { reload() }) { Text("পুনরায় চেষ্টা") }
                    }
                    packages.isEmpty() -> Text(
                        "এই ক্যাটাগরিতে কোনো প্যাকেজ নেই।",
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(packages, key = { it.skuKey }) { pkg ->
                            val price = priceForDuration(pkg, durationKey)
                            val features = buildList {
                                pkg.websiteDisplay?.let { add(it) }
                                add(pkg.deviceDisplay ?: "Unlimited Devices")
                                add("রিফান্ড: ${pkg.refundDays} দিন")
                            }
                            PlanPackageCard(
                                planName = pkg.displayName,
                                subtitle = durationSegments.getOrElse(durationIndex) { durationSegments.last() }.label,
                                price = price,
                                features = features.map { PlanFeatureDto(text = it) },
                                highlighted = selectedPkg?.skuKey == pkg.skuKey,
                                buyButtonText = if (selectedPkg?.skuKey == pkg.skuKey) "নির্বাচিত ✓" else "নির্বাচন",
                                buyButtonTextColor = Color.White,
                                onBuyClick = {
                                    selectedPkg = pkg
                                    selectedAddons = emptySet()
                                },
                                onDetailsClick = { selectedPkg = pkg }
                            )
                        }

                        if (selectedPkg != null && allowedAddonKeys.isNotEmpty()) {
                            item {
                                Text("অ্যাড-অন", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            items(allowedAddonKeys) { key ->
                                val addon = addonCatalog.find { it.addonKey == key } ?: return@items
                                val addonPrice = when (durationKey) {
                                    "1m" -> addon.price1m
                                    "6m" -> addon.price6m
                                    else -> addon.price12m
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedAddons = if (selectedAddons.contains(key)) {
                                                selectedAddons - key
                                            } else {
                                                selectedAddons + key
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedAddons.contains(key),
                                        onCheckedChange = { checked ->
                                            selectedAddons = if (checked) selectedAddons + key else selectedAddons - key
                                        }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(addon.displayName, fontWeight = FontWeight.SemiBold)
                                        Text("৳${addonPrice.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        if (selectedPkg != null) {
                            item {
                                Button(
                                    onClick = {
                                        val pkg = selectedPkg ?: return@Button
                                        scope.launch {
                                            isQuoting = true
                                            checkoutError = null
                                            repository.postV3Quote(
                                                token(),
                                                V3QuoteRequest(
                                                    category = currentTabKey,
                                                    skuKey = pkg.skuKey,
                                                    durationKey = durationKey,
                                                    addons = selectedAddons.toList()
                                                )
                                            ).fold(
                                                onSuccess = {
                                                    quoteResponse = it
                                                    showSummary = true
                                                    isQuoting = false
                                                },
                                                onFailure = { err ->
                                                    checkoutError = err.message
                                                    isQuoting = false
                                                }
                                            )
                                        }
                                    },
                                    enabled = !isQuoting,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                                ) {
                                    if (isQuoting) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("কোট দেখুন ও চেকআউট")
                                    }
                                }
                                checkoutError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }

                        cat?.extensionHistory?.takeIf { it.isNotEmpty() }?.let { extensions ->
                            item {
                                Spacer(Modifier.height(8.dp))
                                Text("সাবস্ক্রিপশন এক্সটেনশন", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            items(extensions, key = { it.id }) { ext ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF22D3EE).copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "Subscription Extended",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = RoyalIndigo
                                        )
                                        Text(
                                            "Your subscription has been extended by ${ext.daysAdded} days.",
                                            fontSize = 12.sp
                                        )
                                        ext.reason?.let {
                                            Text("Reason: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        val by = ext.adminName ?: "Administrator"
                                        Text("Extended By: $by", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        ext.newExpiry?.let {
                                            Text("New Expiry: $it", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        ext.createdAt?.take(10)?.let { date ->
                                            Text(date, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        cat?.purchaseHistory?.takeIf { it.isNotEmpty() }?.let { history ->
                            item {
                                Spacer(Modifier.height(8.dp))
                                Text("ক্রয় ইতিহাস", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            items(history, key = { it.id }) { h ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(h.packageFullName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            "${h.invoiceNo ?: "—"} • ৳${h.paidAmount?.toInt() ?: 0} • ${h.purchasedAt?.take(10) ?: ""}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        h.refundStatus?.let { rs ->
                                            Text("রিফান্ড: $rs", fontSize = 11.sp, color = Color(0xFF22D3EE))
                                        }
                                        if (h.refundStatus.isNullOrBlank() || h.refundStatus == "none") {
                                            TextButton(onClick = {
                                                scope.launch {
                                                    repository.postV3RefundRequest(token(), h.id, "User requested refund").fold(
                                                        onSuccess = {
                                                            android.widget.Toast.makeText(context, "রিফান্ড রিকোয়েস্ট পাঠানো হয়েছে", android.widget.Toast.LENGTH_SHORT).show()
                                                            reload()
                                                        },
                                                        onFailure = { err ->
                                                            android.widget.Toast.makeText(context, err.message, android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                    )
                                                }
                                            }) { Text("রিফান্ড রিকোয়েস্ট", fontSize = 12.sp) }
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

    showTabInfo?.let { key ->
        AlertDialog(
            onDismissRequest = { showTabInfo = null },
            title = { Text(v3TabLabel(key)) },
            text = { Text(v3TabInfo(key)) },
            confirmButton = { TextButton(onClick = { showTabInfo = null }) { Text("বন্ধ") } }
        )
    }

    if (showSummary && quoteResponse != null) {
        val q = quoteResponse!!.quote!!
        val quoteToken = quoteResponse!!.quoteToken!!
        Dialog(onDismissRequest = { if (!isCheckingOut) showSummary = false }) {
            Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(0.95f)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("চেকআউট সারাংশ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(q.packageFullName, fontSize = 14.sp)
                    Text(purchaseTypeLabel(q.purchaseType), color = Color(0xFF22D3EE), fontSize = 12.sp)
                    HorizontalDivider()
                    SummaryRow("প্যাকেজ মূল্য", "৳${q.listPrice.toInt()}")
                    if (q.addonTotal > 0) SummaryRow("অ্যাড-অন", "৳${q.addonTotal.toInt()}")
                    SummaryRow("পরিশোধযোগ্য", "৳${q.payableAmount.toInt()}", bold = true)
                    SummaryRow("নতুন মেয়াদ শেষ", q.finalExpiry)
                    q.sharedExpiry?.let { SummaryRow("শেয়ার্ড মেয়াদ", it) }
                    q.peerUpgradeLines?.forEach { line ->
                        Text(line.message ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showSummary = false }, enabled = !isCheckingOut, modifier = Modifier.weight(1f)) {
                            Text("বাতিল")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isCheckingOut = true
                                    repository.postV3CheckoutInit(token(), quoteToken).fold(
                                        onSuccess = { res ->
                                            isCheckingOut = false
                                            if (res.activated) {
                                                online.paychek.app.utils.AccountEntitlementsStore.refresh(context)
                                                showSummary = false
                                                reload()
                                                android.widget.Toast.makeText(context, res.message ?: "সক্রিয় হয়েছে", android.widget.Toast.LENGTH_LONG).show()
                                            } else {
                                                val url = res.checkoutUrl
                                                if (url.isNullOrBlank()) {
                                                    checkoutError = "চেকআউট URL পাওয়া যায়নি"
                                                } else {
                                                    pendingOrderId = res.orderId
                                                    online.paychek.app.ui.checkout.CheckoutActivity.open(context, url)
                                                    res.orderId?.let { pollOrder(it) }
                                                }
                                            }
                                        },
                                        onFailure = { err ->
                                            isCheckingOut = false
                                            checkoutError = err.message
                                        }
                                    )
                                }
                            },
                            enabled = !isCheckingOut,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                        ) {
                            if (isCheckingOut) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("পেমেন্ট করুন")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, fontSize = if (bold) 16.sp else 13.sp)
    }
}
