package online.paychek.app.ui.screen.apicenter

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.ui.screen.apicenter.website.AddWebsiteWizard
import online.paychek.app.ui.screen.apicenter.website.CredentialRevealDialog
import online.paychek.app.ui.screen.apicenter.website.DeleteWebsitePinDialog
import online.paychek.app.ui.screen.apicenter.website.EditWebsiteDialog
import online.paychek.app.ui.screen.apicenter.website.WebsiteCard
import online.paychek.app.ui.screen.apicenter.website.WebsiteViewModel
import online.paychek.app.utils.RefreshCooldown
import online.paychek.app.utils.adaptivePadding
import online.paychek.app.utils.adaptiveTextSize

private val ApiBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val ApiCard: Color @Composable get() = MaterialTheme.colorScheme.surface
private val AccentCyan = Color(0xFF22D3EE)
private val TextWhite: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val GradientBtn = Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)))

/**
 * Main API Tab — global Checkout banner + inline website list.
 * Credentials, webhook, stats and SDK moved to Documentation / per-website settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiIntegrationScreen(
    onNavigateToCheckout: () -> Unit,
    onOpenWebsite: (Int) -> Unit,
    onNavigateToDocs: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WebsiteViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var entitlements by remember { mutableStateOf(online.paychek.app.utils.AccountEntitlementsStore.readCached(context)) }
    var showWizard by remember { mutableStateOf(false) }
    var editSite by remember { mutableStateOf<online.paychek.app.data.remote.dto.WebsiteDto?>(null) }
    var deleteSite by remember { mutableStateOf<online.paychek.app.data.remote.dto.WebsiteDto?>(null) }
    var deletePinError by remember { mutableStateOf<String?>(null) }
    val isDark = ApiBg == Color(0xFF0B0E14)

    LaunchedEffect(Unit) {
        viewModel.loadWebsites()
        entitlements = online.paychek.app.utils.AccountEntitlementsStore.refresh(context) ?: entitlements
    }
    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessages() }
    }
    LaunchedEffect(state.error, deleteSite?.id) {
        if (deleteSite == null) {
            state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessages() }
        } else if (state.error != null) {
            deletePinError = state.error
            viewModel.clearMessages()
        }
    }

    val created = state.createdWebsite
    val secret = state.revealedSecret
    if (created != null && secret != null) {
        showWizard = false
        CredentialRevealDialog(website = created, apiSecret = secret, onDismiss = { viewModel.dismissSecretReveal() })
    }

    Scaffold(
        containerColor = ApiBg,
        topBar = {
            TopAppBar(
                modifier = Modifier.height(adaptivePadding(48.dp, 56.dp)),
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text("API ইন্টিগ্রেশন", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = adaptiveTextSize(14.sp, 16.sp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ApiCard),
                actions = {
                    IconButton(onClick = onNavigateToDocs) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, "Documentation", tint = AccentCyan)
                    }
                    IconButton(onClick = {
                        if (RefreshCooldown.tryRefresh { viewModel.loadWebsites() }) { /* ok */ }
                        else Toast.makeText(context, "৫ সেকেন্ড পরে আবার রিফ্রেশ করুন", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = AccentCyan)
                    }
                }
            )
        },
        floatingActionButton = {
            if (entitlements.hasWebsite) {
                FloatingActionButton(
                    onClick = { showWizard = true },
                    containerColor = AccentCyan,
                    contentColor = Color(0xFF0B0E14)
                ) { Icon(Icons.Default.Add, "Add Website") }
            }
        },
        modifier = modifier
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── Global Checkout Page (all sites default) ──────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = adaptivePadding(12.dp, 16.dp), vertical = 10.dp)
                    .border(1.dp, AccentCyan.copy(0.3f), RoundedCornerShape(16.dp))
            ) {
                Box(
                    Modifier.fillMaxWidth().background(GradientBtn).clickable { onNavigateToCheckout() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(0.2f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CreditCard, null, tint = Color.White)
                            }
                            Column {
                                Text("Check Out Page", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("গ্লোবাল — সব ওয়েবসাইটের ডিফল্ট চেকআউট", color = Color.White.copy(0.85f), fontSize = 11.sp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateToDocs, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                        }
                    }
                }
            }

            if (!entitlements.hasWebsite) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = adaptivePadding(12.dp, 16.dp), vertical = 8.dp)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Lock, null, tint = Color(0xFFF59E0B))
                        Column(Modifier.weight(1f)) {
                            Text("ওয়েবসাইট পারমিশন নেই", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("নতুন ওয়েবসাইট যোগ করতে সাবস্ক্রিপশন প্যাকেজ কিনুন।", fontSize = 11.sp, color = TextMuted)
                        }
                        TextButton(onClick = onNavigateToSubscription) { Text("প্যাকেজ") }
                    }
                }
            }

            Text(
                "ওয়েবসাইট তালিকা",
                Modifier.padding(horizontal = adaptivePadding(14.dp, 18.dp), vertical = 4.dp),
                color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )

            if (state.isLoading && state.websites.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan)
                }
            } else if (state.websites.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Language, null, tint = TextMuted, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("এখনো কোনো ওয়েবসাইট নেই", color = TextMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (entitlements.hasWebsite) "নিচের + বাটনে ক্লিক করে যোগ করুন"
                            else "ওয়েবসাইট যোগ করতে সাবস্ক্রিপশন প্যাকেজ প্রয়োজন",
                            color = TextMuted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (!entitlements.hasWebsite) {
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onNavigateToSubscription, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
                                Text("প্যাকেজ দেখুন", color = Color(0xFF0B0E14))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = adaptivePadding(12.dp, 16.dp), vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.websites, key = { it.id }) { site ->
                        WebsiteCard(
                            site = site,
                            card = ApiCard,
                            isDark = isDark,
                            onClick = { onOpenWebsite(site.id) },
                            onEdit = { editSite = site },
                            onDelete = { deleteSite = site; deletePinError = null }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showWizard) {
        AddWebsiteWizard(
            isCreating = state.isCreating,
            onDismiss = { showWizard = false },
            onCreate = { domain, name -> viewModel.createWebsite(domain, name) }
        )
    }

    editSite?.let { site ->
        EditWebsiteDialog(
            site = site,
            isSaving = state.isSaving,
            onDismiss = { editSite = null },
            onSave = { domain, name ->
                viewModel.updateWebsiteInfo(site.id, domain, name) { editSite = null }
            }
        )
    }

    deleteSite?.let { site ->
        DeleteWebsitePinDialog(
            siteName = site.siteName.ifBlank { site.domain ?: "Website" },
            isDeleting = state.isSaving,
            pinError = deletePinError,
            onDismiss = { deleteSite = null; deletePinError = null },
            onConfirm = { pin ->
                viewModel.deleteWebsiteWithPin(site.id, pin) {
                    deleteSite = null
                    deletePinError = null
                }
            }
        )
    }
}
