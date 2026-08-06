package online.paychek.app.ui.screen.billing

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.*
import online.paychek.app.data.repository.PaymentRepository
import online.paychek.app.ui.components.plan.DurationTab
import online.paychek.app.ui.components.plan.PremiumDurationSelector
import online.paychek.app.ui.components.plan.PremiumPackageCard
import online.paychek.app.ui.components.plan.PremiumPromoBanner
import online.paychek.app.ui.components.plan.PremiumTabRow
import online.paychek.app.ui.components.plan.cardAccentColor
import online.paychek.app.utils.AccountEntitlementsStore
import online.paychek.app.utils.BanglaDateTimeFormat
import online.paychek.app.utils.MoneyFormat
import online.paychek.app.utils.SecurePreferences

// ─── Pure helpers ─────────────────────────────────────────────────────────────

private fun v3TabLabel(key: String): String = when (key) {
    "gateway"           -> "গেটওয়ে"
    "personal_business" -> "বিজনেস"
    "personal"          -> "পার্সোনাল"
    else                -> key
}

private fun v3TabInfo(key: String): String = when (key) {
    "gateway"           -> "পেমেন্ট গেটওয়ে প্যাকেজ — ওয়েবসাইট সংখ্যা অনুযায়ী API ও চেকআউট অ্যাক্সেস।"
    "personal_business" -> "পার্সোনাল বিজনেস — একক ব্যবসার জন্য মনিটরিং ও টেমপ্লেট।"
    "personal"          -> "পার্সোনাল — ব্যক্তিগত ব্যবহারের জন্য সীমিত ফিচার।"
    else                -> ""
}

private fun purchaseTypeLabel(type: String): String = when (type) {
    "renew"          -> "রিনিউ / এক্সটেন্ড"
    "upgrade"        -> "আপগ্রেড"
    "downgrade"      -> "ডাউনগ্রেড (পরবর্তী মেয়াদ)"
    "cross_category" -> "ক্রস-ক্যাটাগরি"
    else             -> "নতুন ক্রয়"
}

private fun priceForDuration(pkg: V3PackageDto, durationKey: String): Double = when (durationKey) {
    "1m"  -> pkg.price1m
    "6m"  -> pkg.price6m
    else  -> pkg.price12m
}

private fun banglaDurationLabel(durationKey: String): String = when (durationKey) {
    "1m"  -> "১ মাস"
    "6m"  -> "৬ মাস"
    "12m" -> "১২ মাস"
    else  -> durationKey
}

private fun durationMonths(durationKey: String): Int = when (durationKey) {
    "1m"  -> 1
    "6m"  -> 6
    "12m" -> 12
    else  -> 1
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun SubscriptionV3PackagesScreen(
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val repository = remember { PaymentRepository() }

    var isLoading      by remember { mutableStateOf(true) }
    var errorMessage   by remember { mutableStateOf<String?>(null) }
    var catalog        by remember { mutableStateOf<SubscriptionV3CatalogResponse?>(null) }
    var selectedTab    by remember { mutableIntStateOf(initialTab.coerceAtLeast(0)) }
    var durationIndex  by remember { mutableIntStateOf(2) }
    var selectedPkg    by remember { mutableStateOf<V3PackageDto?>(null) }
    var selectedAddons by remember { mutableStateOf(setOf<String>()) }
    var quoteResponse  by remember { mutableStateOf<V3QuoteResponse?>(null) }
    var showSummary    by remember { mutableStateOf(false) }
    var isQuoting      by remember { mutableStateOf(false) }
    var isCheckingOut  by remember { mutableStateOf(false) }
    var pendingOrderId by remember { mutableStateOf<String?>(null) }
    var checkoutError  by remember { mutableStateOf<String?>(null) }
    var showDetails    by remember { mutableStateOf<V3PackageDto?>(null) }
    var refundTarget   by remember { mutableStateOf<V3PurchaseHistoryDto?>(null) }
    var isRefunding    by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
    var addonInfoPopup by remember { mutableStateOf<Pair<String, String>?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    fun token() = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)

    fun reload() {
        scope.launch {
            isLoading = true; errorMessage = null
            if (token().isEmpty()) { errorMessage = "অনুগ্রহ করে লগইন করুন।"; isLoading = false; return@launch }
            repository.getV3BillingCatalog(token()).fold(
                onSuccess = { catalog = it; isLoading = false },
                onFailure = { err -> errorMessage = err.message; isLoading = false }
            )
        }
    }

    fun pollOrder(orderId: String) {
        scope.launch {
            repeat(12) {
                repository.getSubscriptionCheckoutStatus(token(), orderId).fold(
                    onSuccess = { st ->
                        if (st.activated || st.status == "activated") {
                            AccountEntitlementsStore.refresh(context)
                            online.paychek.app.utils.SubscriptionLockState.refresh(context)
                            online.paychek.app.utils.SubscriptionLockState.notifyBillingRefresh(context)
                            pendingOrderId = null; showSummary = false; quoteResponse = null
                            reload()
                            Toast.makeText(context, st.message ?: "প্যাকেজ সক্রিয় হয়েছে।", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                    },
                    onFailure = {}
                )
                delay(2500)
            }
        }
    }

    fun updateQuote(pkg: V3PackageDto, addons: Set<String>, tabKey: String, durKey: String) {
        isQuoting = true; checkoutError = null
        scope.launch {
            repository.postV3Quote(
                token(),
                V3QuoteRequest(category = tabKey, skuKey = pkg.skuKey, durationKey = durKey, addons = addons.toList())
            ).fold(
                onSuccess = { quoteResponse = it; showSummary = true; isQuoting = false },
                onFailure = { err -> checkoutError = err.message; isQuoting = false }
            )
        }
    }

    /** Silent quote update for addon toggles — no loading state, no layout shift */
    fun silentQuoteUpdate(pkg: V3PackageDto, addons: Set<String>, tabKey: String, durKey: String) {
        scope.launch {
            repository.postV3Quote(
                token(),
                V3QuoteRequest(category = tabKey, skuKey = pkg.skuKey, durationKey = durKey, addons = addons.toList())
            ).fold(
                onSuccess = { quoteResponse = it },
                onFailure = { err -> checkoutError = err.message }
            )
        }
    }

    LaunchedEffect(Unit) { reload() }

    DisposableEffect(lifecycleOwner, pendingOrderId) {
        val orderId = pendingOrderId
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !orderId.isNullOrBlank()) pollOrder(orderId)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // ─── Derived display state ──────────────────────────────────────────────

    val cat = catalog
    val tabOrder = cat?.tabOrder ?: listOf("gateway", "personal_business", "personal")
    val durationSegments = cat?.durationSegments ?: listOf(
        DurationSegmentDto("monthly", "1m", "Monthly"),
        DurationSegmentDto("annually", "6m", "Annually"),
        DurationSegmentDto("yearly",   "12m", "Yearly")
    )
    val safeTabIndex   = selectedTab.coerceIn(tabOrder.indices)
    val currentTabKey  = tabOrder[safeTabIndex]
    val currentSeg     = durationSegments.getOrElse(durationIndex) { durationSegments.last() }
    val durationKey    = currentSeg.durationKey
    val packages       = cat?.categories?.get(currentTabKey).orEmpty()
    val addonCatalog   = cat?.addons.orEmpty()

    // Highest discount across the current module (drives the promo banner headline)
    val maxDiscount = remember(packages) {
        packages.flatMap { pkg -> pkg.discounts?.values.orEmpty() }.maxOrNull() ?: 0
    }

    // Duration tabs: Bengali primary + backend secondary tag + per-duration discount pill
    val durationTabs = remember(durationSegments, packages) {
        durationSegments.map { seg ->
            val maxDisc = packages.mapNotNull { pkg ->
                pkg.discounts?.get(seg.durationKey)?.takeIf { it > 0 }
            }.maxOrNull() ?: 0
            DurationTab(
                primary = banglaDurationLabel(seg.durationKey),
                secondary = seg.label,
                discountPercent = maxDisc
            )
        }
    }

    val activePlan = cat?.activeSubscriptions?.firstOrNull()

    // Determine "popular" package for badge tag text
    val popularSkuKey = remember(packages, durationKey) {
        if (packages.isEmpty()) null
        else {
            val withDiscount = packages.mapNotNull { pkg ->
                val disc = pkg.discounts?.get(durationKey) ?: 0
                if (disc > 0) pkg.skuKey to disc else null
            }
            if (withDiscount.isNotEmpty()) withDiscount.maxByOrNull { it.second }?.first
            else if (packages.size > 2) packages.getOrNull(packages.size / 2)?.skuKey
            else null
        }
    }

    // ─── UI Layout ─────────────────────────────────────────────────────────

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // ── Thin Status Strip (replaces big header card) ──────────────────
            ThinStatusStrip(activePlan, cat?.sharedExpiry, cat?.settings?.trialDays)

            // ── Category Tabs (new design) ───────────────────────────────────
            PremiumTabRow(
                tabs = tabOrder.map { v3TabLabel(it) },
                selectedIndex = safeTabIndex,
                onTabSelected = { idx ->
                    selectedTab = idx
                    selectedPkg = null
                    selectedAddons = emptySet()
                }
            )

            // ── Gradient Promo Banner (between tabs and duration) ─────────────
            PremiumPromoBanner(maxDiscount = maxDiscount)

            // ── Duration Selector (divider style, icon + 2 lines + pill) ─────
            PremiumDurationSelector(
                segments = durationTabs,
                selectedIndex = durationIndex,
                onSelect = { durationIndex = it }
            )

            Spacer(Modifier.height(8.dp))

            // ── Package List / Loading / Error states ────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> SkeletonPackageList()

                    errorMessage != null -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Button(onClick = { reload() }) { Text("পুনরায় চেষ্টা") }
                    }

                    packages.isEmpty() -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📦", fontSize = 32.sp, textAlign = TextAlign.Center)
                        Text(
                            text = "এই ক্যাটাগরিতে কোনো প্যাকেজ নেই।",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(packages.size, key = { packages[it].skuKey }) { index ->
                            val pkg = packages[index]
                            val price   = priceForDuration(pkg, durationKey)
                            val disc    = pkg.discounts?.get(durationKey) ?: 0
                            val badge   = if (disc > 0) "-$disc%" else null
                            val months  = durationMonths(durationKey)

                            val originalPrice = if (disc > 0 && price > 0) {
                                price / (1.0 - disc / 100.0)
                            } else null

                            val perMonthText = if (months > 1 && price > 0) {
                                MoneyFormat.perMonth(price, months)
                            } else null

                            val savingsText = if (originalPrice != null && originalPrice > price) {
                                "${MoneyFormat.taka(originalPrice - price)} সঞ্চয়"
                            } else null

                            // Badge tag text for the popular/recommended card
                            val badgeTagText = when {
                                pkg.skuKey == popularSkuKey && disc > 0 -> "সেরা অফার"
                                pkg.skuKey == popularSkuKey -> "রিকমেন্ডেড"
                                else -> null
                            }

                            // Multi-color: each card gets a unique accent by index
                            val accent = cardAccentColor(index)

                            val featureList = buildFeatureList(pkg)

                            PremiumPackageCard(
                                planName      = pkg.displayName,
                                subtitle      = banglaDurationLabel(durationKey),
                                price         = price,
                                features      = featureList,
                                accentColor   = accent,
                                discountBadge = badge,
                                originalPrice = originalPrice,
                                perMonthText  = perMonthText,
                                savingsText   = savingsText,
                                badgeTagText  = badgeTagText,
                                onBuyClick    = {
                                    selectedPkg    = pkg
                                    selectedAddons = emptySet()
                                    updateQuote(pkg, emptySet(), currentTabKey, durationKey)
                                },
                                onDetailsClick = { showDetails = pkg }
                            )
                        }

                        // ── Collapsible History Section ──────────────────────
                        val hasHistory = !cat?.extensionHistory.isNullOrEmpty() || !cat?.purchaseHistory.isNullOrEmpty()
                        if (hasHistory) {
                            item {
                                Spacer(Modifier.height(8.dp))
                                HistorySection(
                                    extensionHistory = cat?.extensionHistory.orEmpty(),
                                    purchaseHistory = cat?.purchaseHistory.orEmpty(),
                                    expanded = historyExpanded,
                                    onToggle = { historyExpanded = !historyExpanded },
                                    onRefundRequest = { refundTarget = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Package Details Dialog ─────────────────────────────────────────────

    showDetails?.let { pkg ->
        PackageDetailsDialog(
            pkg = pkg,
            durationKey = durationKey,
            addonCatalog = addonCatalog,
            onDismiss = { showDetails = null },
            onBuy = {
                showDetails = null
                selectedPkg = pkg
                selectedAddons = emptySet()
                updateQuote(pkg, emptySet(), currentTabKey, durationKey)
            }
        )
    }

    // ─── Refund Confirmation Dialog ─────────────────────────────────────────

    refundTarget?.let { h ->
        AlertDialog(
            onDismissRequest = { if (!isRefunding) refundTarget = null },
            title = { Text("রিফান্ড রিকোয়েস্ট", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "আপনি কি নিশ্চিত যে \"${h.packageFullName}\" প্যাকেজের জন্য রিফান্ড রিকোয়েস্ট পাঠাতে চান?",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "ইনভয়েস: ${h.invoiceNo ?: "—"} • মূল্য: ${MoneyFormat.taka(h.paidAmount ?: 0.0)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isRefunding = true
                        scope.launch {
                            repository.postV3RefundRequest(token(), h.id, "User requested refund").fold(
                                onSuccess = {
                                    isRefunding = false; refundTarget = null
                                    Toast.makeText(context, "রিফান্ড রিকোয়েস্ট পাঠানো হয়েছে", Toast.LENGTH_SHORT).show()
                                    reload()
                                },
                                onFailure = { err ->
                                    isRefunding = false; refundTarget = null
                                    Toast.makeText(context, err.message, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    enabled = !isRefunding,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isRefunding) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                    } else {
                        Text("রিকোয়েস্ট পাঠান", color = MaterialTheme.colorScheme.onError)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { refundTarget = null }, enabled = !isRefunding) {
                    Text("বাতিল")
                }
            }
        )
    }

    // ─── Addon info popup ───────────────────────────────────────────────────

    addonInfoPopup?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { addonInfoPopup = null },
            title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    body,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { addonInfoPopup = null }) { Text("বুঝেছি") }
            }
        )
    }

    // ─── Checkout Summary Dialog ────────────────────────────────────────────

    if (showSummary && quoteResponse != null) {
        val q          = quoteResponse!!.quote!!
        val quoteToken = quoteResponse!!.quoteToken!!

        val allowedAddons = selectedPkg?.allowedAddons
            ?: when (currentTabKey) {
                "gateway"           -> listOf("smart_popup", "custom_sender")
                "personal_business" -> listOf("custom_sender", "gateway_permission")
                else                -> listOf("gateway_permission", "smart_popup")
            }

        Dialog(onDismissRequest = { if (!isCheckingOut && !isQuoting) showSummary = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("চেকআউট সারাংশ", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)

                    Text(q.packageFullName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)

                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            purchaseTypeLabel(q.purchaseType),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    CheckoutSummaryRow("প্যাকেজ মূল্য", MoneyFormat.taka(q.listPrice))
                    CheckoutSummaryRow("অ্যাড-অন", MoneyFormat.taka(q.addonTotal))
                    if (q.creditApplied > 0) {
                        CheckoutSummaryRow("অবশিষ্ট ক্রেডিট (−)", "−${MoneyFormat.taka(q.creditApplied)}")
                    }
                    if (!q.packageCode.isNullOrBlank() || q.packageSku.isNotBlank()) {
                        CheckoutSummaryRow("প্যাকেজ কোড", q.packageCode ?: q.packageSku)
                    }
                    if (q.deferred && !q.deferredStartsAt.isNullOrBlank()) {
                        Text(
                            "ডাউনগ্রেড ${formatExpiryDate(q.deferredStartsAt)} থেকে সক্রিয় হবে। ততদিন বর্তমান প্যাকেজ চলবে।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.tertiary,
                            lineHeight = 16.sp
                        )
                    }

                    // Highlighted payable amount
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("পরিশোধযোগ্য", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                MoneyFormat.taka(q.payableAmount),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    CheckoutSummaryRow("নতুন মেয়াদ শেষ", formatExpiryDate(q.finalExpiry))
                    q.sharedExpiry?.let { CheckoutSummaryRow("শেয়ার্ড মেয়াদ", formatExpiryDate(it)) }
                    q.peerUpgradeLines?.forEach { line ->
                        Text(line.message ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (allowedAddons.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("অ্যাড-অন সুবিধা", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        allowedAddons.forEach { addonKey ->
                            val addon = addonCatalog.find { it.addonKey == addonKey } ?: return@forEach
                            val addonLabel = addon.displayName.ifBlank { addonKey }
                            val addonPrice = priceForAddon(addon, durationKey)
                            val isChecked = selectedAddons.contains(addonKey)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = !isCheckingOut) {
                                        val newAddons = if (isChecked) selectedAddons - addonKey else selectedAddons + addonKey
                                        selectedAddons = newAddons
                                        selectedPkg?.let { silentQuoteUpdate(it, newAddons, currentTabKey, durationKey) }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        val newAddons = if (checked) selectedAddons + addonKey else selectedAddons - addonKey
                                        selectedAddons = newAddons
                                        selectedPkg?.let { silentQuoteUpdate(it, newAddons, currentTabKey, durationKey) }
                                    },
                                    enabled = !isCheckingOut
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(addonLabel, fontSize = 13.sp)
                                    Text(
                                        text = "+${MoneyFormat.taka(addonPrice)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!addon.infoText.isNullOrBlank()) {
                                    IconButton(
                                        onClick = { addonInfoPopup = addonLabel to addon.infoText!! },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "অ্যাড-অন তথ্য",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    checkoutError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { showSummary = false },
                            enabled = !isCheckingOut && !isQuoting,
                            modifier = Modifier.weight(1f)
                        ) { Text("বাতিল") }

                        Button(
                            onClick = {
                                scope.launch {
                                    isCheckingOut = true; checkoutError = null
                                    repository.postV3CheckoutInit(token(), quoteToken).fold(
                                        onSuccess = { res ->
                                            isCheckingOut = false
                                            if (res.activated) {
                                                AccountEntitlementsStore.refresh(context)
                                                online.paychek.app.utils.SubscriptionLockState.refresh(context)
                                                online.paychek.app.utils.SubscriptionLockState.notifyBillingRefresh(context)
                                                showSummary = false; reload()
                                                Toast.makeText(context, res.message ?: "সক্রিয় হয়েছে", Toast.LENGTH_LONG).show()
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
                                        onFailure = { err -> isCheckingOut = false; checkoutError = err.message }
                                    )
                                }
                            },
                            enabled = !isCheckingOut && !isQuoting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(2f).height(44.dp)
                        ) {
                            if (isCheckingOut) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("পেমেন্ট করুন", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Thin Status Strip ───────────────────────────────────────────────────────

@Composable
private fun ThinStatusStrip(
    activePlan: V3ActiveSubscriptionDto?,
    sharedExpiry: String?,
    trialDays: Int?
) {
    val hasActivePlan = activePlan != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    if (hasActivePlan) Color(0xFF16A34A)
                    else Color(0xFFFF9800)
                )
        )

        // Plan name or trial message
        Text(
            text = if (hasActivePlan) {
                activePlan!!.packageFullName
            } else {
                val days = trialDays ?: 7
                "${BanglaDateTimeFormat.toBanglaDigits(days.toString())} দিনের ফ্রি ট্রায়াল উপভোগ করছেন"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hasActivePlan) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Expiry (compact)
        sharedExpiry?.let { exp ->
            Text(
                text = "মেয়াদ: ${formatExpiryDate(exp)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ─── Skeleton Loading ────────────────────────────────────────────────────────

@Composable
private fun SkeletonPackageList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(3) { index ->
            SkeletonPackageCard(seed = index)
        }
    }
}

@Composable
private fun SkeletonPackageCard(seed: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (seed == 0) 0.45f else 0.55f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(muted.copy(alpha = 0.12f))
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(muted.copy(alpha = 0.10f))
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.2f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(muted.copy(alpha = 0.07f))
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = muted.copy(alpha = 0.08f), thickness = 0.5.dp)
            Spacer(Modifier.height(14.dp))
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(when (i) { 0 -> 0.7f; 1 -> 0.55f; else -> 0.6f })
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(muted.copy(alpha = (0.09f - i * 0.02f)))
                )
                if (i < 2) Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.weight(1f).height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(muted.copy(alpha = 0.06f))
                )
                Box(
                    modifier = Modifier.weight(1.4f).height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(muted.copy(alpha = 0.10f))
                )
            }
        }
    }
}

// ─── Collapsible History Section ─────────────────────────────────────────────

@Composable
private fun HistorySection(
    extensionHistory: List<V3ExtensionHistoryDto>,
    purchaseHistory: List<V3PurchaseHistoryDto>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRefundRequest: (V3PurchaseHistoryDto) -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(300),
        label = "historyChevron"
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggle() }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "ক্রয় ও এক্সটেনশন ইতিহাস",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "সংকুচিত করুন" else "বিস্তৃত করুন",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(chevronRotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                extensionHistory.forEach { ext ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("সাবস্ক্রিপশন এক্সটেন্ড হয়েছে", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("${BanglaDateTimeFormat.toBanglaDigits(ext.daysAdded.toString())} দিন যোগ করা হয়েছে",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            ext.reason?.let {
                                Text("কারণ: $it", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                            }
                            Text("অ্যাডমিন: ${ext.adminName ?: "Administrator"}", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                            ext.newExpiry?.let {
                                Text("নতুন মেয়াদ: ${formatDateBangla(it)}", fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            ext.createdAt?.let {
                                Text(formatDateBangla(it), fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                purchaseHistory.forEach { h ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(h.packageFullName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                "${h.invoiceNo ?: "—"} • ${MoneyFormat.taka(h.paidAmount ?: 0.0)} • ${formatDateBangla(h.purchasedAt ?: "")}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            h.refundStatus?.takeIf { it != "none" && it.isNotBlank() }?.let { rs ->
                                Text("রিফান্ড: $rs", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            if (h.refundStatus.isNullOrBlank() || h.refundStatus == "none") {
                                TextButton(onClick = { onRefundRequest(h) }) {
                                    Text("রিফান্ড রিকোয়েস্ট", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Package Details Dialog ──────────────────────────────────────────────────

@Composable
private fun PackageDetailsDialog(
    pkg: V3PackageDto,
    durationKey: String,
    addonCatalog: List<V3AddonCatalogDto>,
    onDismiss: () -> Unit,
    onBuy: () -> Unit
) {
    val price = priceForDuration(pkg, durationKey)
    val disc = pkg.discounts?.get(durationKey) ?: 0

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(pkg.displayName, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)

                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(MoneyFormat.taka(price), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary)
                    Text("/ ${banglaDurationLabel(durationKey)}", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }

                if (disc > 0) {
                    Text("$disc% ছাড় প্রযোজ্য", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text("পারমিশন ও সুবিধা", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                buildPermissionLines(pkg).forEach { (label, allowed) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (allowed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (allowed) Color(0xFF16A34A) else MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(label, fontSize = 13.sp,
                            color = if (allowed) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }

                val allowedAddonKeys = pkg.allowedAddons.orEmpty()
                if (allowedAddonKeys.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("অ্যাড-অন (ঐচ্ছিক)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    allowedAddonKeys.forEach { key ->
                        val addon = addonCatalog.find { it.addonKey == key }
                        if (addon != null) {
                            val addonPrice = priceForAddon(addon, durationKey)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    when (key) {
                                        "custom_sender" -> "কাস্টম সেন্ডার আইডি"
                                        "smart_popup" -> "স্মার্ট পপআপ"
                                        "gateway_permission" -> "গেটওয়ে পারমিশন"
                                        else -> addon.displayName
                                    },
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("+${MoneyFormat.taka(addonPrice)}", fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                    Text(
                        "🛡️ ${BanglaDateTimeFormat.toBanglaDigits(pkg.refundDays.toString())} দিনের রিফান্ড গ্যারান্টি",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("বন্ধ") }
                    Button(onClick = onBuy, modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text("কিনুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Private helpers ─────────────────────────────────────────────────────────

@Composable
private fun CheckoutSummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = if (bold) 17.sp else 13.sp,
            color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

private fun buildFeatureList(pkg: V3PackageDto): List<PlanFeatureDto> {
    return buildList {
        pkg.websiteDisplay?.let { add(PlanFeatureDto(it)) }
        add(PlanFeatureDto(pkg.deviceDisplay ?: "আনলিমিটেড ডিভাইস"))
        if (pkg.permSmartPopup > 0) add(PlanFeatureDto("স্মার্ট পপআপ স্ক্যান"))
        if (pkg.permManualTransaction > 0) add(PlanFeatureDto("ম্যানুয়াল ট্রানজেকশন"))
        if (pkg.isCustomSenderAllowed > 0) add(PlanFeatureDto("কাস্টম সেন্ডার আইডি"))
        add(PlanFeatureDto("${BanglaDateTimeFormat.toBanglaDigits(pkg.refundDays.toString())} দিন রিফান্ড গ্যারান্টি"))
    }
}

private fun buildPermissionLines(pkg: V3PackageDto): List<Pair<String, Boolean>> {
    return buildList {
        add("টেমপ্লেট অ্যাক্সেস" to (pkg.permTemplate > 0))
        add("ওয়েবসাইট সংযোগ (${pkg.websiteDisplay ?: "—"})" to (pkg.permWebsite > 0))
        add("ডিভাইস মনিটরিং (${pkg.deviceDisplay ?: "আনলিমিটেড"})" to (pkg.permDevice > 0))
        add("কাস্টম সেন্ডার আইডি" to (pkg.isCustomSenderAllowed > 0))
        add("স্মার্ট পপআপ" to (pkg.permSmartPopup > 0))
        add("ম্যানুয়াল ট্রানজেকশন" to (pkg.permManualTransaction > 0))
    }
}

private fun priceForAddon(addon: V3AddonCatalogDto, durationKey: String): Double = when (durationKey) {
    "1m"  -> addon.price1m
    "6m"  -> addon.price6m
    else  -> addon.price12m
}

private fun formatDateBangla(raw: String): String {
    if (raw.isBlank()) return "—"
    return BanglaDateTimeFormat.formatTrxCard(raw).ifBlank {
        BanglaDateTimeFormat.toBanglaDigits(raw.take(10))
    }
}

/** Format expiry date with YEAR: "২২ জুলাই, ২০২৬" — no time component */
private fun formatExpiryDate(raw: String): String {
    if (raw.isBlank()) return "—"
    return try {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        val bnMonths = arrayOf(
            "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
            "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
        )
        var date: java.util.Date? = null
        val trimmed = raw.trim()
        for (pattern in patterns) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                if (pattern.contains("'Z'") || pattern.contains("T")) {
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                date = sdf.parse(trimmed)
                if (date != null) break
            } catch (_: Exception) { }
        }
        if (date != null) {
            val cal = java.util.Calendar.getInstance().apply { time = date }
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val month = bnMonths.getOrElse(cal.get(java.util.Calendar.MONTH)) { "" }
            val year = cal.get(java.util.Calendar.YEAR)
            BanglaDateTimeFormat.toBanglaDigits("$day $month, $year")
        } else {
            // Fallback: show raw first 10 chars with Bangla digits
            BanglaDateTimeFormat.toBanglaDigits(raw.take(10))
        }
    } catch (_: Exception) {
        BanglaDateTimeFormat.toBanglaDigits(raw.take(10))
    }
}
