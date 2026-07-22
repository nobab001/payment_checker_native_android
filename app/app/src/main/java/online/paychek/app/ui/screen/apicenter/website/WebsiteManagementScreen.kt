package online.paychek.app.ui.screen.apicenter.website

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import online.paychek.app.data.remote.dto.WebsiteDto
import online.paychek.app.ui.common.RemoteImage

private val AccentCyan = Color(0xFF22D3EE)
private val AccentGreen = Color(0xFF10B981)
private val AccentAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteManagementScreen(
    onNavigateBack: () -> Unit,
    onOpenWebsite: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebsiteViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showWizard by remember { mutableStateOf(false) }
    var editSite by remember { mutableStateOf<WebsiteDto?>(null) }
    var deleteSite by remember { mutableStateOf<WebsiteDto?>(null) }
    var deletePinError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadWebsites() }

    LaunchedEffect(state.error, deleteSite?.id) {
        if (deleteSite == null) {
            state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessages() }
        } else if (state.error != null) {
            deletePinError = state.error
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(state.error, state.infoMessage) {
        state.infoMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessages() }
    }

    val bg = MaterialTheme.colorScheme.background
    val card = MaterialTheme.colorScheme.surface
    val isDark = bg == Color(0xFF0B0E14)

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("ওয়েবসাইট / মার্চেন্ট", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = card)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showWizard = true },
                containerColor = AccentCyan,
                contentColor = Color(0xFF0B0E14),
                icon = { Icon(Icons.Default.Add, "Add Website") },
                text = { Text("নতুন ওয়েবসাইট", fontWeight = FontWeight.Bold) }
            )
        },
        modifier = modifier
    ) { padding ->
        if (state.isLoading && state.websites.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
        } else if (state.websites.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("এখনো কোনো ওয়েবসাইট যুক্ত করা হয়নি।", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("\"নতুন ওয়েবসাইট\" চাপুন — শুধু ডোমেইন দিলেই তৈরি হবে।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.websites, key = { it.id }) { site ->
                    WebsiteCard(
                        site, card, isDark,
                        onClick = { onOpenWebsite(site.id) },
                        onEdit = { editSite = site },
                        onDelete = { deleteSite = site; deletePinError = null }
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showWizard) {
        AddWebsiteWizard(
            isCreating = state.isCreating,
            onDismiss = { showWizard = false },
            onCreate = { domain, name, purpose -> viewModel.createWebsite(domain, name, purpose) }
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

    // Secret reveal (shown once, on create or regenerate)
    val created = state.createdWebsite
    val secret = state.revealedSecret
    if (created != null && secret != null) {
        showWizard = false
        CredentialRevealDialog(
            website = created,
            apiSecret = secret,
            onDismiss = { viewModel.dismissSecretReveal() }
        )
    }
}

@Composable
internal fun WebsiteCard(
    site: WebsiteDto,
    card: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(AccentCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                RemoteImage(
                    url = site.logoUrl,
                    contentDescription = site.siteName.ifBlank { site.domain ?: "Website" },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    fallback = { Icon(Icons.Default.Storefront, null, tint = AccentCyan) }
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    site.companyName?.ifBlank { null } ?: site.siteName.ifBlank { site.domain ?: "Website" },
                    color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    site.domain ?: "-",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(if (site.isActive) "সচল" else "বন্ধ", if (site.isActive) AccentGreen else Color(0xFFF44336))
                    Spacer(Modifier.width(6.dp))
                    StatusChip(
                        when {
                            !site.purposeSelected && !site.purposeLocked -> "Purpose?"
                            site.websitePurpose == "payment" -> "Pay"
                            site.websitePurpose == "both" -> "Both"
                            else -> "Add Balance"
                        },
                        AccentAmber
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusChip(if (site.checkoutMode == "merchant_vibe") "Vibe Mode" else "Transaction", AccentCyan)
                }
            }
            if (onEdit != null || onDelete != null) {
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        onEdit?.let {
                            DropdownMenuItem(
                                text = { Text("এডিট করুন") },
                                onClick = { showMenu = false; it() },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                        }
                        onDelete?.let {
                            DropdownMenuItem(
                                text = { Text("মুছে ফেলুন", color = Color(0xFFEF4444)) },
                                onClick = { showMenu = false; it() },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) }
                            )
                        }
                    }
                }
            } else {
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp)
    ) { Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun EditWebsiteDialog(
    site: WebsiteDto,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (domain: String, name: String?) -> Unit
) {
    var domain by remember(site.id) { mutableStateOf(site.domain ?: "") }
    var name by remember(site.id) { mutableStateOf(site.siteName.ifBlank { site.companyName ?: "" }) }
    val card = MaterialTheme.colorScheme.surface

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(shape = RoundedCornerShape(24.dp), color = card) {
            Column(Modifier.padding(22.dp)) {
                Text("ওয়েবসাইট এডিট", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = domain, onValueChange = { domain = it },
                    label = { Text("ডোমেইন") }, singleLine = true, enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("ওয়েবসাইট নাম") }, singleLine = true, enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("বাতিল") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(domain, name) },
                        enabled = !isSaving && domain.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color(0xFF0B0E14))
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("সংরক্ষণ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeleteWebsitePinDialog(
    siteName: String,
    isDeleting: Boolean,
    pinError: String?,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val card = MaterialTheme.colorScheme.surface

    Dialog(onDismissRequest = { if (!isDeleting) onDismiss() }) {
        Surface(shape = RoundedCornerShape(24.dp), color = card) {
            Column(Modifier.padding(22.dp)) {
                Text("ওয়েবসাইট মুছে ফেলুন", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFEF4444))
                Spacer(Modifier.height(8.dp))
                Text(
                    "\"$siteName\" স্থায়ীভাবে মুছে যাবে। নিরাপত্তার জন্য PIN দিন।",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("নিরাপত্তা PIN") },
                    singleLine = true,
                    enabled = !isDeleting,
                    modifier = Modifier.fillMaxWidth(),
                    isError = pinError != null,
                    supportingText = pinError?.let { { Text(it, color = Color(0xFFEF4444)) } }
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("বাতিল") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(pin) },
                        enabled = !isDeleting && pin.length >= 4,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        if (isDeleting) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("মুছে ফেলুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddWebsiteWizard(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String?, String) -> Unit
) {
    var domain by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("add_balance") }
    var step by remember { mutableIntStateOf(0) }
    var confirmLock by remember { mutableStateOf(false) }
    val card = MaterialTheme.colorScheme.surface

    Dialog(onDismissRequest = { if (!isCreating) onDismiss() }) {
        Surface(shape = RoundedCornerShape(24.dp), color = card) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    if (step == 0) "নতুন ওয়েবসাইট যুক্ত করুন" else "উদ্দেশ্য সিলেক্ট ও লক",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (step == 0) "ডোমেইন দিন — পরের ধাপে Purpose লক হবে।"
                    else "একবার Confirm করলে Purpose আর চেঞ্জ করা যাবে না (শুধু সুপার অ্যাডমিন আনলক করতে পারে)।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(18.dp))

                if (step == 0) {
                    OutlinedTextField(
                        value = domain, onValueChange = { domain = it },
                        label = { Text("ডোমেইন (আবশ্যক)") },
                        placeholder = { Text("example.com") },
                        singleLine = true, enabled = !isCreating,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("ওয়েবসাইট নাম (ঐচ্ছিক)") },
                        singleLine = true, enabled = !isCreating,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss, enabled = !isCreating) { Text("বাতিল") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { step = 1 },
                            enabled = !isCreating && domain.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color(0xFF0B0E14))
                        ) { Text("পরবর্তী", fontWeight = FontWeight.Bold) }
                    }
                } else {
                    listOf(
                        "add_balance" to "Add Balance",
                        "payment" to "Pay",
                        "both" to "Both"
                    ).forEach { (key, label) ->
                        val selected = purpose == key
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AccentCyan.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable(enabled = !isCreating) { purpose = key }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = { purpose = key }, enabled = !isCreating)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                                Text(
                                    when (key) {
                                        "add_balance" -> "ওয়ালেট টপ-আপ — কাস্টমার চেকআউট অ্যামাউন্টই পাঠাবে"
                                        "payment" -> "অর্ডার কমপ্লিট — expectedPayable পরিশোধ"
                                        else -> "দুই বাটন — purpose=add_balance / payment"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { confirmLock = !confirmLock },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = confirmLock, onCheckedChange = { confirmLock = it }, enabled = !isCreating)
                        Text("লক করে Confirm করছি — পরে চেঞ্জ করা যাবে না", fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { step = 0 }, enabled = !isCreating) { Text("পেছনে") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onCreate(domain, name.ifBlank { null }, purpose) },
                            enabled = !isCreating && confirmLock,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color(0xFF0B0E14))
                        ) {
                            if (isCreating) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF0B0E14), strokeWidth = 2.dp)
                            else Text("লক করে তৈরি", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CredentialRevealDialog(
    website: WebsiteDto,
    apiSecret: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val card = MaterialTheme.colorScheme.surface

    fun copy(label: String, value: String) {
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText(label, value).toClipEntry())
            Toast.makeText(context, "$label কপি হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = { }) {
        Surface(shape = RoundedCornerShape(24.dp), color = card) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = AccentGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("ওয়েবসাইট তৈরি হয়েছে", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(AccentAmber.copy(alpha = 0.15f)).padding(10.dp)) {
                    Text(
                        "⚠ Secret Key শুধু এখনই দেখা যাবে। এটি এখনই নিরাপদে সংরক্ষণ করুন — পরে আর দেখা যাবে না।",
                        color = AccentAmber, fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                CredRow("Merchant ID", website.merchantId ?: "-") { copy("Merchant ID", website.merchantId ?: "") }
                Spacer(Modifier.height(10.dp))
                CredRow("API Key", website.apiKey) { copy("API Key", website.apiKey) }
                Spacer(Modifier.height(10.dp))
                CredRow("Secret Key", apiSecret) { copy("Secret Key", apiSecret) }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) { Text("সংরক্ষণ করেছি, বন্ধ করুন", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CredRow(label: String, value: String, onCopy: () -> Unit) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color(0xFF2A2F3A), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value, color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace,
                fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ContentCopy, "Copy", tint = AccentCyan, modifier = Modifier.size(16.dp))
            }
        }
    }
}
