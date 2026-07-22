package online.paychek.app.ui.screen.apicenter.website

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import online.paychek.app.data.remote.dto.CheckoutTabToggle
import online.paychek.app.data.remote.dto.UpdateWebsiteRequest
import online.paychek.app.ui.common.CropFrameShape
import online.paychek.app.ui.common.ImageCropperDialog
import online.paychek.app.ui.common.RemoteImage
import online.paychek.app.ui.common.bitmapToPngBytes
import online.paychek.app.ui.common.decodeUriToBitmap

private val AccentCyan = Color(0xFF22D3EE)
private val AccentGreen = Color(0xFF10B981)
private val AccentAmber = Color(0xFFF59E0B)

private val PURPOSE_DETAILS = mapOf(
    "add_balance" to (
        "Add Balance (Wallet Top-up)" to
            "কাস্টমার সবসময় চেকআউটের টাকাই পাঠাবে। কমিশন/চার্জ শুধু walletCredit হিসেবে কলব্যাকে যাবে — মার্চেন্ট নিজে ওয়ালেটে যোগ করবে। পেমেন্ট গেটওয়ে ওয়ালেট ক্রেডিট করে না।"
        ),
    "payment" to (
        "Payment (Order Complete)" to
            "অর্ডার সম্পন্ন করতে expectedPayable পরিশোধ করতে হবে। কম দিলে অতিরিক্ত Trx দিয়ে Settlement; বেশি দিলে SUCCESS + overPaid — রিফান্ড গেটওয়ে করে না।"
        ),
    "both" to (
        "Both (Add Balance + Payment)" to
            "একই API। Add Balance বাটনে purpose=add_balance, Buy/Pay বাটনে purpose=payment পাঠাতে হবে। ভুল/খালি purpose → Hard Error।"
        ),
)

private val CHECKOUT_TAB_KEYS = listOf(
    "send_money" to "Send Money",
    "cash_out" to "Cash Out",
    "payment" to "Payment",
    "bank" to "Bank",
    "card" to "Card Payment"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteSettingsScreen(
    websiteId: Int,
    onNavigateBack: () -> Unit,
    onOpenCheckoutNumbers: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WebsiteViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val site = state.selected

    LaunchedEffect(websiteId) { viewModel.loadWebsiteDetail(websiteId) }
    LaunchedEffect(state.error, state.infoMessage) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessages() }
        state.infoMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessages() }
    }

    val bg = MaterialTheme.colorScheme.background
    val card = MaterialTheme.colorScheme.surface
    val isDark = bg == Color(0xFF0B0E14)

    // Editable fields
    var companyName by remember(site?.id) { mutableStateOf(site?.companyName ?: "") }
    LaunchedEffect(site?.companyName) { companyName = site?.companyName ?: "" }
    val logoUrl = site?.logoUrl
    var theme by remember(site?.id) { mutableStateOf(site?.checkoutTheme?.takeIf { it.startsWith("design-") } ?: "design-1") }
    var checkoutMode by remember(site?.id) { mutableStateOf(site?.checkoutMode ?: "transaction") }
    var successUrl by remember(site?.id) { mutableStateOf(site?.successUrl ?: "") }
    var cancelUrl by remember(site?.id) { mutableStateOf(site?.cancelUrl ?: "") }
    var callbackUrl by remember(site?.id) { mutableStateOf(site?.callbackUrl ?: "") }
    var webhookUrl by remember(site?.id) { mutableStateOf(site?.webhookUrl ?: "") }
    var receivePaymentType by remember(site?.id) { mutableStateOf(site?.receivePaymentType ?: false) }
    var receiveCommission by remember(site?.id) { mutableStateOf(site?.receiveCommission ?: false) }
    var websitePurpose by remember(site?.id) {
        mutableStateOf(site?.websitePurpose?.takeIf { it in listOf("add_balance", "payment", "both") } ?: "add_balance")
    }
    LaunchedEffect(site?.websitePurpose) {
        websitePurpose = site?.websitePurpose?.takeIf { it in listOf("add_balance", "payment", "both") } ?: "add_balance"
    }
    var designerTab by remember { mutableIntStateOf(0) }
    var showPurposeDialog by rememberSaveable { mutableStateOf(false) }
    var showPurposeCoach by remember { mutableStateOf(false) }

    // One-time hint pulse toward Flag — does not keep the screen dark.
    LaunchedEffect(site?.id, site?.purposeSelected, site?.purposeLocked) {
        val s = site ?: return@LaunchedEffect
        if (s.purposeLocked || s.purposeSelected) return@LaunchedEffect
        val prefs = context.getSharedPreferences("paychek_ui", android.content.Context.MODE_PRIVATE)
        val key = "purpose_coach_once_${s.id}"
        if (prefs.getBoolean(key, false)) return@LaunchedEffect
        showPurposeCoach = true
        prefs.edit().putBoolean(key, true).apply()
        kotlinx.coroutines.delay(2800)
        showPurposeCoach = false
    }

    val tabStates = remember(site?.id) {
        CHECKOUT_TAB_KEYS.associate { (key, _) -> key to mutableStateOf(key != "bank") }
    }
    LaunchedEffect(state.checkoutTabs) {
        CHECKOUT_TAB_KEYS.forEach { (key, _) ->
            state.checkoutTabs[key]?.let { tabStates[key]?.value = it.enabled }
        }
    }

    var bitmapToCrop by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch { bitmapToCrop = decodeUriToBitmap(context, uri) }
        }
    }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) bitmapToCrop = bmp
    }

    bitmapToCrop?.let { bmp ->
        ImageCropperDialog(
            bitmap = bmp,
            title = "কোম্পানি লোগো সাজান",
            subtitle = "১:১ স্কোয়ার লোগো — জুম, কেন্টার ও ক্রপ করে সেভ করুন",
            frameShape = CropFrameShape.Square,
            onDismiss = { bitmapToCrop = null },
            onCropSuccess = { cropped ->
                bitmapToCrop = null
                if (site != null) {
                    viewModel.uploadWebsiteLogo(site.id, bitmapToPngBytes(cropped))
                }
            }
        )
    }

    val needsPurposeSetup = site != null && !site.purposeLocked && !site.purposeSelected

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text(site?.siteName?.ifBlank { "ওয়েবসাইট সেটিংস" } ?: "ওয়েবসাইট সেটিংস", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showPurposeCoach = false
                            showPurposeDialog = true
                        }
                    ) {
                        BadgedBox(
                            badge = {
                                if (needsPurposeSetup) {
                                    Badge(containerColor = AccentAmber)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "ওয়েবসাইটের উদ্দেশ্য",
                                tint = if (needsPurposeSetup) AccentAmber else AccentCyan
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = card)
            )
        },
    ) { padding ->
        if (site == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Checkout Page Settings ───────────────────────────────────────
            Text(
                "চেকআউট পেজ সেটিংস",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                "এই সাইটের জন্য আলাদা কনফিগ। API ট্যাবের গ্লোবাল চেকআউট সব সাইটে প্রয়োগ হয়।",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            TabRow(
                selectedTabIndex = designerTab,
                containerColor = card,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                divider = {}
            ) {
                Tab(
                    selected = designerTab == 0,
                    onClick = { designerTab = 0 },
                    text = { Text("ডিজাইনার সেটিংস", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = designerTab == 1,
                    onClick = { designerTab = 1 },
                    text = { Text("কাস্টমার প্রিভিউ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }

            val tabMap = CHECKOUT_TAB_KEYS.associate { (key, _) -> key to (tabStates[key]?.value ?: true) }
            val previewNumbers = state.checkoutNumbers.filter { it.enabled }

            when (designerTab) {
                0 -> {
                    WebsiteCheckoutLiveEditor(
                        companyName = companyName,
                        logoUrl = logoUrl,
                        checkoutMode = checkoutMode,
                        onCheckoutModeChange = { checkoutMode = it },
                        design = theme,
                        onDesignChange = { theme = it },
                        tabStates = tabMap,
                        onTabToggle = { key, enabled -> tabStates[key]?.value = enabled },
                        numbers = state.checkoutNumbers,
                        editable = true,
                        onMoveNumber = { from, to ->
                            viewModel.moveCheckoutNumber(from, to, site.id)
                        },
                        onToggleNumber = { id, enabled ->
                            viewModel.toggleCheckoutNumber(id, enabled, site.id)
                        },
                        checkoutTabs = state.checkoutTabs,
                        providerBranding = state.providerBranding,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.updateSettings(
                                site.id,
                                UpdateWebsiteRequest(
                                    companyName = companyName,
                                    checkoutTheme = theme,
                                    checkoutMode = checkoutMode,
                                    checkoutTabs = CHECKOUT_TAB_KEYS.associate { (key, _) ->
                                        key to CheckoutTabToggle(enabled = tabStates[key]?.value ?: true)
                                    }
                                )
                            )
                            viewModel.saveCheckoutNumbers(site.id)
                        },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                    ) {
                        if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("চেকআউট কনফিগ সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                    }
                }
                1 -> {
                    WebsiteCheckoutLiveEditor(
                        companyName = companyName,
                        logoUrl = logoUrl,
                        checkoutMode = checkoutMode,
                        onCheckoutModeChange = {},
                        design = theme,
                        onDesignChange = {},
                        tabStates = tabMap,
                        onTabToggle = { _, _ -> },
                        numbers = previewNumbers,
                        editable = false,
                        checkoutTabs = state.checkoutTabs,
                        providerBranding = state.providerBranding,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "এটি গ্রাহকের দেখতে পাওয়া চেকআউট পেজের প্রিভিউ — এখানে কিছু এডিট করা যাবে না।",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // Branding
            SettingsCard(card, isDark, "ব্র্যান্ডিং", Icons.Default.Palette) {
                EditField("Company Name", companyName) { companyName = it }
                BrandingLogoSection(
                    logoUrl = logoUrl,
                    companyName = companyName,
                    isUploading = state.logoUploading,
                    uploadError = state.logoUploadError,
                    onPickGallery = { galleryPicker.launch("image/*") },
                    onPickCamera = { cameraPicker.launch(null) },
                    onRemoveLogo = { viewModel.deleteWebsiteLogo(site.id) },
                    onRetryUpload = { viewModel.retryLogoUpload(site.id) }
                )
            }

            // Merchant identity (read-only)
            SettingsCard(card, isDark, "মার্চেন্ট পরিচিতি", Icons.Default.Badge) {
                ReadOnlyRow("Merchant ID", site.merchantId ?: "-")
                ReadOnlyRow("API Key", site.apiKey)
                ReadOnlyRow("Secret", "•••• ${site.secretLast4 ?: "----"}  (v${site.secretVersion})")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.regenerateSecret(site.id) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber)
                ) {
                    Icon(Icons.Default.Autorenew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Secret Key রিজেনারেট করুন")
                }
            }

            // URLs
            SettingsCard(card, isDark, "রিডাইরেক্ট ও কলব্যাক URL", Icons.Default.Link) {
                EditField("Success URL", successUrl) { successUrl = it }
                EditField("Cancel URL", cancelUrl) { cancelUrl = it }
                EditField("Callback URL", callbackUrl) { callbackUrl = it }
                EditField("Webhook URL", webhookUrl) { webhookUrl = it }
            }

            // Live merchant accounts (API credentials — multi-account)
            MerchantAccountsSection(
                card = card,
                isDark = isDark,
                accounts = state.merchantAccounts,
                onCreate = { req -> viewModel.createMerchantAccount(site.id, req) },
                onUpdate = { acctId, req -> viewModel.updateMerchantAccount(site.id, acctId, req) },
                onToggle = { acctId, active -> viewModel.toggleMerchantAccount(site.id, acctId, active) },
                onSetDefault = { acctId -> viewModel.setDefaultMerchantAccount(site.id, acctId) },
                onDuplicate = { acctId -> viewModel.duplicateMerchantAccount(site.id, acctId) },
                onDelete = { acctId -> viewModel.deleteMerchantAccount(site.id, acctId) }
            )

            // Callback preferences (gated by admin permission — locked by default)
            SettingsCard(card, isDark, "কলব্যাক অপশন", Icons.Default.CallReceived) {
                Text(
                    "ডিফল্টে লক। নিজের সিস্টেমে provider/কমিশন হিসাব করতে পারলে লক রাখুন। শুধু কলব্যাকের মান দিয়ে ওয়ালেট ক্রেডিট করলে অ্যাডমিন দিয়ে আনলক করুন।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                PermToggle(
                    "Payment Type গ্রহণ", "receive_payment_type",
                    enabled = site.allowPaymentTypeCallback,
                    checked = receivePaymentType && site.allowPaymentTypeCallback
                ) { receivePaymentType = it }
                if (!site.allowPaymentTypeCallback) {
                    Text("🔒 লক — অ্যাডমিন আনলক করলে চালু করা যাবে", color = AccentAmber, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                PermToggle(
                    "Commission গ্রহণ", "receive_commission",
                    enabled = site.allowCommissionCallback,
                    checked = receiveCommission && site.allowCommissionCallback
                ) { receiveCommission = it }
                if (!site.allowCommissionCallback) {
                    Text("🔒 লক — অ্যাডমিন আনলক করলে চালু করা যাবে", color = AccentAmber, fontSize = 11.sp)
                }
            }

            // Commission menu (locked until admin permission)
            CommissionSection(
                enabled = site.commissionEnabled,
                card = card, isDark = isDark,
                commissions = state.commissions,
                campaigns = state.campaigns,
                incentiveTemplates = state.incentiveTemplates,
                onSave = { req -> viewModel.upsertCommission(site.id, req) },
                onDelete = { cid -> viewModel.deleteCommission(site.id, cid) },
                onSaveCampaign = { req -> viewModel.upsertCampaign(site.id, req) },
                onDeleteCampaign = { cid -> viewModel.deleteCampaign(site.id, cid) }
            )

            Button(
                onClick = {
                    viewModel.updateSettings(
                        site.id,
                        UpdateWebsiteRequest(
                            companyName = companyName,
                            successUrl = successUrl,
                            cancelUrl = cancelUrl,
                            callbackUrl = callbackUrl,
                            webhookUrl = webhookUrl,
                            receivePaymentType = receivePaymentType,
                            receiveCommission = receiveCommission,
                            websitePurpose = if (site.purposeLocked) null else websitePurpose
                        )
                    )
                },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("ব্র্যান্ডিং ও ইন্টিগ্রেশন সেটিংস সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    // Brief one-time coach signal (auto-dismisses)
    if (showPurposeCoach && site != null && !showPurposeDialog) {
        PurposeCoachOverlay(
            onDismiss = { showPurposeCoach = false },
            onOpenPurpose = {
                showPurposeCoach = false
                showPurposeDialog = true
            }
        )
    }

    if (showPurposeDialog && site != null) {
        WebsitePurposeDialog(
            locked = site.purposeLocked,
            selected = websitePurpose,
            isSaving = state.isSaving,
            onSelect = { websitePurpose = it },
            onDismiss = {
                showPurposeDialog = false
                showPurposeCoach = false
            },
            onSave = {
                viewModel.updateSettings(
                    site.id,
                    UpdateWebsiteRequest(websitePurpose = websitePurpose)
                ) {
                    showPurposeDialog = false
                    showPurposeCoach = false
                }
            }
        )
    }

    // Regenerated secret reveal
    val secret = state.revealedSecret
    if (secret != null && state.createdWebsite == null) {
        SecretRevealDialog(secret) { viewModel.dismissSecretReveal() }
    }
    } // Box
}

@Composable
private fun PurposeCoachOverlay(
    onDismiss: () -> Unit,
    onOpenPurpose: () -> Unit
) {
    // Soft one-shot signal — tap anywhere to dismiss; does not lock the screen.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 4.dp, end = 6.dp)
                .clickable(onClick = onOpenPurpose),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                Modifier
                    .shadow(16.dp, CircleShape)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentAmber),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Flag, contentDescription = "Purpose", tint = Color(0xFF0B0E14))
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A1F2B),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.6f)),
                modifier = Modifier.widthIn(max = 260.dp).padding(end = 4.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "প্রথমে এখানের সেটিং গুলো ঠিক করুন",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Flag আইকনে Purpose সিলেক্ট করে লক করুন।",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun WebsitePurposeDialog(
    locked: Boolean,
    selected: String,
    isSaving: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val card = MaterialTheme.colorScheme.surface
    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = card,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Flag, null, tint = AccentCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ওয়েবসাইটের উদ্দেশ্য",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (locked) "লক করা আছে — চেঞ্জ করতে সুপার অ্যাডমিনের সাথে যোগাযোগ করুন।"
                    else "একবার Confirm করলে লক হয়ে যাবে। পরে চেঞ্জ করা যাবে না।",
                    color = if (locked) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                WebsitePurposeSelector(
                    selected = selected,
                    onSelect = onSelect,
                    enabled = !locked && !isSaving
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("বন্ধ") }
                    if (!locked) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onSave,
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color(0xFF0B0E14))
                        ) {
                            if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF0B0E14))
                            else Text("লক করে সেভ", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebsitePurposeSelector(
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true
) {
    val options = listOf(
        "add_balance" to "Add Balance",
        "payment" to "Payment",
        "both" to "Both"
    )
    var expandedInfo by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) ->
            val sel = selected == id
            val detail = PURPOSE_DETAILS[id]
            val showInfo = expandedInfo == id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) AccentCyan.copy(alpha = 0.12f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (sel) AccentCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = enabled) { onSelect(id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = sel,
                            onClick = { if (enabled) onSelect(id) },
                            enabled = enabled,
                            colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                when (id) {
                                    "add_balance" -> "ওয়ালেট টপ-আপ — কাস্টমার চেকআউট অ্যামাউন্টই পাঠাবে"
                                    "payment" -> "অর্ডার কমপ্লিট — expectedPayable পরিশোধ"
                                    else -> "দুই মোডই — প্রতি init-এ purpose পাঠাতে হবে"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { expandedInfo = if (showInfo) null else id },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "বিস্তারিত",
                            tint = if (showInfo) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (showInfo && detail != null) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
                            .padding(10.dp)
                    ) {
                        Text(detail.first, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentCyan)
                        Spacer(Modifier.height(4.dp))
                        Text(detail.second, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(card: Color, isDark: Boolean, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun EditField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(value, color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        IconButton(onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipData.newPlainText(label, value).toClipEntry())
                Toast.makeText(context, "$label কপি হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.ContentCopy, "Copy", tint = AccentCyan, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val sel = opt == selected
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (sel) AccentCyan.copy(alpha = 0.2f) else Color.Transparent)
                    .border(1.dp, if (sel) AccentCyan else Color(0xFF3A3F4A), RoundedCornerShape(20.dp))
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) { Text(opt, color = if (sel) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ModeOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) AccentCyan else Color(0xFF3A3F4A), RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = AccentCyan))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PermToggle(title: String, apiField: String, enabled: Boolean, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            Text(
                if (enabled) apiField else "Admin অনুমতি প্রয়োজন",
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else AccentAmber,
                fontSize = 10.sp, fontFamily = if (enabled) FontFamily.Monospace else FontFamily.Default
            )
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
        )
    }
}

private val COMMISSION_PAYMENT_TYPES = listOf(
    "bkash_personal" to "bKash Personal",
    "nagad_personal" to "Nagad Personal",
    "rocket_personal" to "Rocket Personal",
    "upay_personal" to "Upay Personal",
    "bkash_agent" to "bKash Agent",
    "nagad_agent" to "Nagad Agent",
    "bkash_merchant" to "bKash Merchant",
    "nagad_merchant" to "Nagad Merchant",
    "card" to "Card",
    "bank" to "Bank"
)

/**
 * Payment-type chips from all parseable ("one value") templates — official + this
 * account's custom parseable ones. Not gated on active numbers, so commission
 * rules survive number/device changes. Each template is a unique tpl_<id> key.
 */
private fun paymentTypeOptions(
    templates: List<online.paychek.app.data.remote.dto.IncentiveTemplateDto>
): List<Pair<String, String>> {
    if (templates.isEmpty()) return COMMISSION_PAYMENT_TYPES
    return templates.map { t ->
        val key = t.paymentType.ifBlank { "tpl_${t.id}" }
        val label = t.name.ifBlank { key }
        key to label
    }
}

private fun incentiveLabelFor(
    token: String,
    options: List<Pair<String, String>>
): String = options.firstOrNull { it.first == token }?.second
    ?: COMMISSION_PAYMENT_TYPES.firstOrNull { it.first == token }?.second
    ?: token.removePrefix("tpl_")

@Composable
private fun CommissionSection(
    enabled: Boolean,
    card: Color,
    isDark: Boolean,
    commissions: List<online.paychek.app.data.remote.dto.CommissionDto>,
    campaigns: List<online.paychek.app.data.remote.dto.CampaignDto>,
    incentiveTemplates: List<online.paychek.app.data.remote.dto.IncentiveTemplateDto>,
    onSave: (online.paychek.app.data.remote.dto.UpsertCommissionRequest) -> Unit,
    onDelete: (Int) -> Unit,
    onSaveCampaign: (online.paychek.app.data.remote.dto.UpsertCampaignRequest) -> Unit,
    onDeleteCampaign: (Int) -> Unit
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var showCampaignDialog by remember { mutableStateOf(false) }
    val typeOptions = remember(incentiveTemplates) { paymentTypeOptions(incentiveTemplates) }
    Box {
        SettingsCard(card, isDark, "Commission", Icons.Default.Percent) {
            if (enabled) {
                Text("Type অনুযায়ী কমিশন/চার্জ যোগ করুন (Percentage বা Flat)।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                if (commissions.isEmpty()) {
                    Text("কোনো কমিশন সেট করা হয়নি।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    commissions.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    incentiveLabelFor(c.paymentType, typeOptions),
                                    color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                                val comm = if (c.commissionType == "percentage") "${c.commissionValue}%" else "৳${c.commissionValue}"
                                val chg = if (c.chargeType == "percentage") "${c.chargeValue}%" else "৳${c.chargeValue}"
                                Text("Commission: $comm · Charge: $chg", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                            IconButton(onClick = { onDelete(c.id) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("কমিশন যোগ করুন")
                }

                // ── Campaign / Extra incentives (amount-range) ─────────────────
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))
                Text("ক্যাম্পেইন / এক্সট্রা", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "নির্দিষ্ট এমাউন্ট রেঞ্জে (মিন–ম্যাক্স) সব/নির্দিষ্ট টাইপে কমিশন দিন বা চার্জ কাটুন।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                if (campaigns.isEmpty()) {
                    Text("কোনো ক্যাম্পেইন সেট করা হয়নি।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    campaigns.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val scope = if (c.paymentType.isBlank()) "সব টাইপ"
                                    else incentiveLabelFor(c.paymentType, typeOptions)
                                Text(scope, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val range = if (c.maxAmount > 0) "৳${fmtAmt(c.minAmount)}–৳${fmtAmt(c.maxAmount)}" else "৳${fmtAmt(c.minAmount)}+"
                                val v = if (c.valueType == "percentage") "${c.value}%" else "৳${fmtAmt(c.value)}"
                                val modeTxt = if (c.mode == "charge") "চার্জ" else "কমিশন"
                                Text("$range · $modeTxt $v", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                            IconButton(onClick = { onDeleteCampaign(c.id) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showCampaignDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen)
                ) {
                    Icon(Icons.Default.Campaign, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ক্যাম্পেইন / এক্সট্রা")
                }
            } else {
                Column(Modifier.blur(6.dp)) {
                    Text("bKash Personal — 1.5% commission", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Nagad Merchant — flat 5৳ charge", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Card — 2.5% commission", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        if (!enabled) {
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable {
                        Toast.makeText(context, "🔒 Commission ফিচার Admin অনুমতি ছাড়া চালু হবে না।", Toast.LENGTH_LONG).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Locked — Admin অনুমতি প্রয়োজন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }

    if (showDialog) {
        CommissionEditorDialog(
            typeOptions = typeOptions,
            onDismiss = { showDialog = false },
            onSave = { onSave(it); showDialog = false }
        )
    }
    if (showCampaignDialog) {
        CampaignEditorDialog(
            typeOptions = typeOptions,
            onDismiss = { showCampaignDialog = false },
            onSave = { onSaveCampaign(it); showCampaignDialog = false }
        )
    }
}

private fun fmtAmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Composable
private fun CommissionEditorDialog(
    typeOptions: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (online.paychek.app.data.remote.dto.UpsertCommissionRequest) -> Unit
) {
    var paymentType by remember { mutableStateOf(typeOptions.firstOrNull()?.first ?: "") }
    var commissionType by remember { mutableStateOf("percentage") }
    var commissionValue by remember { mutableStateOf("") }
    var chargeType by remember { mutableStateOf("flat") }
    var chargeValue by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text("কমিশন / চার্জ", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                Text("পেমেন্ট টাইপ (টেমপ্লেট)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    typeOptions.forEach { (id, label) ->
                        val sel = id == paymentType
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (sel) AccentCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (sel) AccentCyan else Color(0xFF3A3F4A), RoundedCornerShape(20.dp))
                                .clickable { paymentType = id }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) { Text(label, color = if (sel) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Commission", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ChipRow(listOf("percentage", "flat"), commissionType) { commissionType = it }
                EditField(if (commissionType == "percentage") "Commission (%)" else "Commission (৳)", commissionValue) { commissionValue = it }
                Spacer(Modifier.height(8.dp))
                Text("Charge", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ChipRow(listOf("percentage", "flat"), chargeType) { chargeType = it }
                EditField(if (chargeType == "percentage") "Charge (%)" else "Charge (৳)", chargeValue) { chargeValue = it }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("বাতিল") }
                    Button(
                        onClick = {
                            onSave(
                                online.paychek.app.data.remote.dto.UpsertCommissionRequest(
                                    paymentType = paymentType,
                                    commissionType = commissionType,
                                    commissionValue = commissionValue.toDoubleOrNull() ?: 0.0,
                                    chargeType = chargeType,
                                    chargeValue = chargeValue.toDoubleOrNull() ?: 0.0,
                                    isActive = true
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) { Text("সংরক্ষণ") }
                }
            }
        }
    }
}

@Composable
private fun CampaignEditorDialog(
    typeOptions: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (online.paychek.app.data.remote.dto.UpsertCampaignRequest) -> Unit
) {
    // "" = ALL types. First chip is the All option.
    var paymentType by remember { mutableStateOf("") }
    var minAmount by remember { mutableStateOf("") }
    var maxAmount by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("commission") } // commission | charge
    var valueType by remember { mutableStateOf("flat") }  // flat | percentage
    var value by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text("ক্যাম্পেইন / এক্সট্রা", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                Text("লেনদেন টাইপ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val allSel = paymentType.isBlank()
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (allSel) AccentGreen.copy(alpha = 0.2f) else Color.Transparent)
                            .border(1.dp, if (allSel) AccentGreen else Color(0xFF3A3F4A), RoundedCornerShape(20.dp))
                            .clickable { paymentType = "" }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) { Text("সব (All)", color = if (allSel) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                    typeOptions.forEach { (id, label) ->
                        val sel = id == paymentType
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (sel) AccentGreen.copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (sel) AccentGreen else Color(0xFF3A3F4A), RoundedCornerShape(20.dp))
                                .clickable { paymentType = id }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) { Text(label, color = if (sel) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("এমাউন্ট রেঞ্জ (৳)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { EditField("সর্বনিম্ন", minAmount) { minAmount = it } }
                    Box(Modifier.weight(1f)) { EditField("সর্বোচ্চ (0=সীমাহীন)", maxAmount) { maxAmount = it } }
                }
                Spacer(Modifier.height(8.dp))
                Text("ধরন", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ChipRow(listOf("commission", "charge"), mode) { mode = it }
                Spacer(Modifier.height(6.dp))
                Text("ভ্যালু টাইপ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ChipRow(listOf("flat", "percentage"), valueType) { valueType = it }
                EditField(if (valueType == "percentage") "ভ্যালু (%)" else "ভ্যালু (৳)", value) { value = it }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("বাতিল") }
                    Button(
                        onClick = {
                            val v = value.toDoubleOrNull() ?: 0.0
                            val minV = minAmount.toDoubleOrNull() ?: 0.0
                            val maxV = maxAmount.toDoubleOrNull() ?: 0.0
                            if (v <= 0.0) {
                                Toast.makeText(context, "ভ্যালু দিন।", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (maxV > 0.0 && maxV < minV) {
                                Toast.makeText(context, "সর্বোচ্চ এমাউন্ট সর্বনিম্নের চেয়ে বড় হতে হবে।", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val label = if (paymentType.isBlank()) "সব টাইপ"
                                else incentiveLabelFor(paymentType, typeOptions)
                            onSave(
                                online.paychek.app.data.remote.dto.UpsertCampaignRequest(
                                    paymentType = paymentType,
                                    label = label,
                                    minAmount = minV,
                                    maxAmount = maxV,
                                    mode = mode,
                                    valueType = valueType,
                                    value = v,
                                    isActive = true
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) { Text("সংরক্ষণ") }
                }
            }
        }
    }
}

@Composable
private fun BrandingLogoSection(
    logoUrl: String?,
    companyName: String,
    isUploading: Boolean,
    uploadError: String?,
    onPickGallery: () -> Unit,
    onPickCamera: () -> Unit,
    onRemoveLogo: () -> Unit,
    onRetryUpload: () -> Unit
) {
    val hasLogo = !logoUrl.isNullOrBlank()
    val initial = companyName.trim().take(1).uppercase().ifBlank { "P" }

    Text(
        "কোম্পানি লোগো",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(8.dp))

    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, Color(0xFF3A3F4A), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (hasLogo) {
            RemoteImage(
                url = logoUrl,
                contentDescription = "Company logo preview",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
                fallback = {
                    Text(initial, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                }
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                Text("লোগো আপলোড করুন", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isUploading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentCyan, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        }
    }

    if (isUploading) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentCyan)
        Text("লোগো আপলোড হচ্ছে…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    uploadError?.let { err ->
        Spacer(Modifier.height(6.dp))
        Text(err, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        TextButton(onClick = onRetryUpload, enabled = !isUploading) {
            Text("আবার চেষ্টা করুন", fontSize = 12.sp)
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onPickGallery,
            enabled = !isUploading,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (hasLogo) "পরিবর্তন" else "গ্যালারি", fontSize = 12.sp)
        }
        OutlinedButton(
            onClick = onPickCamera,
            enabled = !isUploading,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("ক্যামেরা", fontSize = 12.sp)
        }
    }
    if (hasLogo) {
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = onRemoveLogo,
            enabled = !isUploading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("লোগো মুছুন", fontSize = 12.sp)
        }
    }
}

@Composable
private fun SecretRevealDialog(secret: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Dialog(onDismissRequest = { }) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(22.dp)) {
                Text("নতুন Secret Key", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(6.dp))
                Text("⚠ এখনই সংরক্ষণ করুন — পরে আর দেখা যাবে না।", color = AccentAmber, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background)
                        .border(1.dp, Color(0xFF2A2F3A), RoundedCornerShape(8.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(secret, color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipData.newPlainText("Secret Key", secret).toClipEntry())
                            Toast.makeText(context, "Secret Key কপি হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = AccentCyan, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                    Text("সংরক্ষণ করেছি", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
