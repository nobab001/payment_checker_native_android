package online.paychek.app.ui.screen.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.data.remote.dto.*
import online.paychek.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminDashboardViewModel = viewModel()
) {
    val uiState by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialog trigger states
    var showTemplateDialog by remember { mutableStateOf<SmsTemplateDto?>(null) }
    var showEmailDialog by remember { mutableStateOf<EmailAccountDto?>(null) }
    var showGatewayDialog by remember { mutableStateOf<SmsSettingsDto?>(null) }
    var showUserDialog by remember { mutableStateOf<AdminUserDto?>(null) }
    var showCheckoutDialog by remember { mutableStateOf<CheckoutTemplateDto?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Admin Panel Console",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAllData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalIndigo)
            )
        },
        bottomBar = {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("গেটওয়ে ও টেমপ্লেট", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Build, "Gateways") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("ইউজার ও ডিভাইস", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.People, "Users") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("অ্যাপ সেটিংস", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Settings, "Config") }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = RoyalIndigo,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                when (selectedTab) {
                    0 -> GatewaysAndTemplatesTab(
                        uiState = uiState,
                        onEditTemplate = { showTemplateDialog = it },
                        onEditEmail = { showEmailDialog = it },
                        onEditGateway = { showGatewayDialog = it },
                        onEditCheckout = { showCheckoutDialog = it },
                        onDeleteTemplate = { viewModel.deleteSmsTemplate(it) },
                        onDeleteEmail = { viewModel.deleteEmailAccount(it) }
                    )
                    1 -> UsersAndDevicesTab(
                        uiState = uiState,
                        onUserClick = { showUserDialog = it }
                    )
                    2 -> GlobalSettingsTab(
                        uiState = uiState,
                        onUpdateConfig = { key, valStr -> viewModel.updateConfig(key, valStr) }
                    )
                }
            }
        }
    }

    // Dialog implementations
    showTemplateDialog?.let { template ->
        SmsTemplateEditDialog(
            template = template,
            onDismiss = { showTemplateDialog = null },
            onSave = {
                viewModel.saveSmsTemplate(it)
                showTemplateDialog = null
            }
        )
    }

    showEmailDialog?.let { email ->
        EmailAccountEditDialog(
            account = email,
            onDismiss = { showEmailDialog = null },
            onSave = {
                viewModel.saveEmailAccount(it)
                showEmailDialog = null
            }
        )
    }

    showGatewayDialog?.let { gateway ->
        SmsSettingsEditDialog(
            settings = gateway,
            onDismiss = { showGatewayDialog = null },
            onSave = {
                viewModel.saveSmsSettings(it)
                showGatewayDialog = null
            }
        )
    }

    showCheckoutDialog?.let { checkout ->
        CheckoutTemplateEditDialog(
            checkout = checkout,
            onDismiss = { showCheckoutDialog = null },
            onSave = {
                viewModel.saveCheckoutTemplate(it)
                showCheckoutDialog = null
            }
        )
    }

    showUserDialog?.let { user ->
        UserDetailAndTrialDialog(
            user = user,
            onDismiss = { showUserDialog = null },
            onToggleBlock = { blocked ->
                viewModel.toggleUserBlock(user.id, blocked)
                showUserDialog = null
            },
            onUpdateTrial = { devId, expires, locked, reason ->
                viewModel.updateDeviceTrial(devId, expires, locked, reason)
                showUserDialog = null
            }
        )
    }
}

@Composable
private fun GatewaysAndTemplatesTab(
    uiState: AdminUiState,
    onEditTemplate: (SmsTemplateDto) -> Unit,
    onEditEmail: (EmailAccountDto) -> Unit,
    onEditGateway: (SmsSettingsDto) -> Unit,
    onEditCheckout: (CheckoutTemplateDto) -> Unit,
    onDeleteTemplate: (Int) -> Unit,
    onDeleteEmail: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Section 1: SMTP Profiles
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SMTP Mailer Accounts", fontWeight = FontWeight.Bold, color = RoyalIndigo, fontSize = 16.sp)
                    IconButton(onClick = { onEditEmail(EmailAccountDto(null, "", "", "smtp.gmail.com", 465, 1, 500, 0, 1)) }) {
                        Icon(Icons.Default.AddCircle, "Add Email", tint = RoyalIndigo)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.emailAccounts.isEmpty()) {
                    Text("কোনো SMTP অ্যাকাউন্ট যুক্ত নেই।", fontSize = 13.sp, color = TextSecondary)
                } else {
                    uiState.emailAccounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { onEditEmail(acc) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.email, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Sent: ${acc.sentToday ?: 0}/${acc.dailyLimit} | Active: ${if (acc.isActive == 1) "Yes" else "No"}", fontSize = 12.sp, color = TextSecondary)
                            }
                            IconButton(onClick = { acc.id?.let { onDeleteEmail(it) } }) {
                                Icon(Icons.Default.Delete, "Delete", tint = StatusRed)
                            }
                        }
                    }
                }
            }
        }

        // Section 2: SMS Gateway Configs
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SMS Gateway Settings", fontWeight = FontWeight.Bold, color = RoyalIndigo, fontSize = 16.sp)
                    if (uiState.smsSettings.isEmpty()) {
                        IconButton(onClick = { onEditGateway(SmsSettingsDto(null, "", "GET", null, null, null, null, 1)) }) {
                            Icon(Icons.Default.AddCircle, "Add Gateway", tint = RoyalIndigo)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.smsSettings.isEmpty()) {
                    Text("কোনো গেটওয়ে কনফিগার করা নেই।", fontSize = 13.sp, color = TextSecondary)
                } else {
                    uiState.smsSettings.forEach { setting ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { onEditGateway(setting) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Gateway URL:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(setting.gatewayUrl, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                                Text("Method: ${setting.httpMethod} | Active: ${if (setting.isActive == 1) "Yes" else "No"}", fontSize = 12.sp, color = TextSecondary)
                            }
                            Icon(Icons.Default.Edit, "Edit", tint = RoyalIndigo)
                        }
                    }
                }
            }
        }

        // Section 3: SMS Templates (Official)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SMS Templates (Official)", fontWeight = FontWeight.Bold, color = RoyalIndigo, fontSize = 16.sp)
                    IconButton(onClick = { onEditTemplate(SmsTemplateDto(null, "", "", "", "", 1, 1)) }) {
                        Icon(Icons.Default.AddCircle, "Add Template", tint = RoyalIndigo)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.smsTemplates.isEmpty()) {
                    Text("কোনো অফিসিয়াল টেমপ্লেট নেই।", fontSize = 13.sp, color = TextSecondary)
                } else {
                    uiState.smsTemplates.forEach { temp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { onEditTemplate(temp) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(temp.templateName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Sender: ${temp.senderId} | Active: ${if (temp.isActive == 1) "Yes" else "No"}", fontSize = 12.sp, color = TextSecondary)
                            }
                            IconButton(onClick = { temp.id?.let { onDeleteTemplate(it) } }) {
                                Icon(Icons.Default.Delete, "Delete", tint = StatusRed)
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Checkout Instructions
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Checkout Instructions Mapping", fontWeight = FontWeight.Bold, color = RoyalIndigo, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.checkoutTemplates.isEmpty()) {
                    Text("কোনো নির্দেশিকা ম্যাপিং নেই।", fontSize = 13.sp, color = TextSecondary)
                } else {
                    uiState.checkoutTemplates.forEach { check ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { onEditCheckout(check) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(check.templateName ?: "Template #${check.smsTemplateId}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Instruction: ${check.singleInstruction}", fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                            }
                            Icon(Icons.Default.Edit, "Edit", tint = RoyalIndigo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersAndDevicesTab(
    uiState: AdminUiState,
    onUserClick: (AdminUserDto) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredUsers = remember(uiState.users, searchQuery) {
        uiState.users.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    (it.phone?.contains(searchQuery) == true) ||
                    (it.email?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("নাম বা ফোন/ইমেইল সার্চ করুন") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (filteredUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো ব্যবহারকারী পাওয়া যায়নি।", color = TextSecondary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                filteredUsers.forEach { user ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUserClick(user) }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name.ifEmpty { "Pending User" }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Contact: ${user.phone ?: user.email ?: "Unknown"}", fontSize = 12.sp, color = TextSecondary)
                                Text("Role: ${user.role} | Devices: ${user.devices.size}", fontSize = 12.sp, color = TextSecondary)
                            }
                            val chipBg = if (user.blocked) Color(0xFFFFEBEE) else Color(0xFFE8F9EE)
                            val chipText = if (user.blocked) StatusRed else Color(0xFF1B5E20)
                            Box(
                                modifier = Modifier
                                    .background(chipBg, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (user.blocked) "Blocked" else "Active",
                                    color = chipText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSettingsTab(
    uiState: AdminUiState,
    onUpdateConfig: (String, String) -> Unit
) {
    val scrollState = rememberScrollState()

    val isMaintenance = uiState.configs["maintenance_mode"] == "true"
    val isRegistration = uiState.configs["registration_enabled"] == "true"
    
    var waLink by remember(uiState.configs) { mutableStateOf(uiState.configs["whatsapp_support_link"] ?: "") }
    var tgLink by remember(uiState.configs) { mutableStateOf(uiState.configs["telegram_support_link"] ?: "") }
    var fbLink by remember(uiState.configs) { mutableStateOf(uiState.configs["facebook_support_link"] ?: "") }
    var ytLink by remember(uiState.configs) { mutableStateOf(uiState.configs["youtube_support_link"] ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("মাস্টার কন্ট্রোল টগলস", fontWeight = FontWeight.Bold, color = RoyalIndigo, fontSize = 16.sp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("সিস্টেম মেইনটেনেন্স মোড (Maintenance Mode)", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("সক্রিয় করলে গ্রাহকদের মেইনটেনেন্স ব্যানার দেখাবে", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = isMaintenance,
                        onCheckedChange = { onUpdateConfig("maintenance_mode", it.toString()) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("নতুন ইউজার রেজিস্ট্রেশন (Registration Enabled)", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("বন্ধ করলে নতুন অ্যাকাউন্ট খোলা বন্ধ থাকবে", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = isRegistration,
                        onCheckedChange = { onUpdateConfig("registration_enabled", it.toString()) }
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("সোশ্যাল মিডিয়া ও সাপোর্ট লিংকসমূহ", fontWeight = FontWeight.Bold, color = RoyalIndigo, fontSize = 16.sp)

                OutlinedTextField(
                    value = waLink,
                    onValueChange = { waLink = it },
                    label = { Text("WhatsApp Support Link") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tgLink,
                    onValueChange = { tgLink = it },
                    label = { Text("Telegram Support Link") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fbLink,
                    onValueChange = { fbLink = it },
                    label = { Text("Facebook Link") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ytLink,
                    onValueChange = { ytLink = it },
                    label = { Text("YouTube Link") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        onUpdateConfig("whatsapp_support_link", waLink)
                        onUpdateConfig("telegram_support_link", tgLink)
                        onUpdateConfig("facebook_support_link", fbLink)
                        onUpdateConfig("youtube_support_link", ytLink)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("সংরক্ষণ করুন (Save Links)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SmsTemplateEditDialog(
    template: SmsTemplateDto,
    onDismiss: () -> Unit,
    onSave: (SmsTemplateDto) -> Unit
) {
    var name by remember { mutableStateOf(template.templateName) }
    var sender by remember { mutableStateOf(template.senderId) }
    var keywords by remember { mutableStateOf(template.matchingKeyword) }
    var regex by remember { mutableStateOf(template.regexPattern) }
    var isActive by remember { mutableIntStateOf(template.isActive) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (template.id == null) "নতুন এসএমএস টেমপ্লেট" else "টেমপ্লেট এডিট করুন", fontWeight = FontWeight.Bold, color = RoyalIndigo, fontSize = 16.sp)
                
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Template Name") })
                OutlinedTextField(value = sender, onValueChange = { sender = it }, label = { Text("Sender ID") })
                OutlinedTextField(value = keywords, onValueChange = { keywords = it }, label = { Text("Matching Keywords") })
                OutlinedTextField(value = regex, onValueChange = { regex = it }, label = { Text("Regex Pattern") })
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Active Status")
                    Switch(checked = isActive == 1, onCheckedChange = { isActive = if (it) 1 else 0 })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("বাতিল", color = TextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(template.copy(templateName = name, senderId = sender, matchingKeyword = keywords, regexPattern = regex, isActive = isActive)) },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                    ) { Text("সংরক্ষণ", color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun EmailAccountEditDialog(
    account: EmailAccountDto,
    onDismiss: () -> Unit,
    onSave: (EmailAccountDto) -> Unit
) {
    var email by remember { mutableStateOf(account.email) }
    var password by remember { mutableStateOf(account.password) }
    var host by remember { mutableStateOf(account.host) }
    var port by remember { mutableStateOf(account.port.toString()) }
    var secure by remember { mutableIntStateOf(account.secure) }
    var dailyLimit by remember { mutableStateOf(account.dailyLimit.toString()) }
    var isActive by remember { mutableIntStateOf(account.isActive) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (account.id == null) "নতুন SMTP প্রোফাইল" else "SMTP প্রোফাইল এডিট", fontWeight = FontWeight.Bold, color = RoyalIndigo, fontSize = 16.sp)
                
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("SMTP Email Address") })
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("App Specific Password") })
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("SMTP Host") })
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("SMTP Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = dailyLimit, onValueChange = { dailyLimit = it }, label = { Text("Daily Send Limit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Secure SSL (Port 465)")
                    Switch(checked = secure == 1, onCheckedChange = { secure = if (it) 1 else 0 })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Active Status")
                    Switch(checked = isActive == 1, onCheckedChange = { isActive = if (it) 1 else 0 })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("বাতিল", color = TextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(account.copy(email = email, password = password, host = host, port = port.toIntOrNull() ?: 465, secure = secure, dailyLimit = dailyLimit.toIntOrNull() ?: 500, isActive = isActive)) },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                    ) { Text("সংরক্ষণ", color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun SmsSettingsEditDialog(
    settings: SmsSettingsDto,
    onDismiss: () -> Unit,
    onSave: (SmsSettingsDto) -> Unit
) {
    var gatewayUrl by remember { mutableStateOf(settings.gatewayUrl) }
    var httpMethod by remember { mutableStateOf(settings.httpMethod) }
    var postBodyTemplate by remember { mutableStateOf(settings.postBodyTemplate ?: "") }
    var apiKey by remember { mutableStateOf(settings.apiKey ?: "") }
    var username by remember { mutableStateOf(settings.username ?: "") }
    var senderId by remember { mutableStateOf(settings.senderId ?: "") }
    var isActive by remember { mutableIntStateOf(settings.isActive) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("SMS Gateway সেটিংস", fontWeight = FontWeight.Bold, color = RoyalIndigo, fontSize = 16.sp)
                
                OutlinedTextField(value = gatewayUrl, onValueChange = { gatewayUrl = it }, label = { Text("Gateway URL") })
                OutlinedTextField(value = httpMethod, onValueChange = { httpMethod = it }, label = { Text("HTTP Method (GET/POST)") })
                OutlinedTextField(value = postBodyTemplate, onValueChange = { postBodyTemplate = it }, label = { Text("POST Body Template (Optional)") })
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key (Optional)") })
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username (Optional)") })
                OutlinedTextField(value = senderId, onValueChange = { senderId = it }, label = { Text("Sender ID (Optional)") })
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Active Status")
                    Switch(checked = isActive == 1, onCheckedChange = { isActive = if (it) 1 else 0 })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("বাতিল", color = TextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(settings.copy(gatewayUrl = gatewayUrl, httpMethod = httpMethod, postBodyTemplate = postBodyTemplate.ifEmpty { null }, apiKey = apiKey.ifEmpty { null }, username = username.ifEmpty { null }, senderId = senderId.ifEmpty { null }, isActive = isActive)) },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                    ) { Text("সংরক্ষণ", color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun CheckoutTemplateEditDialog(
    checkout: CheckoutTemplateDto,
    onDismiss: () -> Unit,
    onSave: (CheckoutTemplateDto) -> Unit
) {
    var singleInstruction by remember { mutableStateOf(checkout.singleInstruction) }
    var multipleInstruction by remember { mutableStateOf(checkout.multipleInstruction) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("চেকআউট নির্দেশিকা এডিট", fontWeight = FontWeight.Bold, color = RoyalIndigo, fontSize = 16.sp)
                Text("Template: ${checkout.templateName ?: checkout.smsTemplateId}", fontSize = 13.sp, color = TextSecondary)

                OutlinedTextField(
                    value = singleInstruction,
                    onValueChange = { singleInstruction = it },
                    label = { Text("Single Number Instruction") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = multipleInstruction,
                    onValueChange = { multipleInstruction = it },
                    label = { Text("Multiple Numbers Instruction") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("বাতিল", color = TextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(checkout.copy(singleInstruction = singleInstruction, multipleInstruction = multipleInstruction)) },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                    ) { Text("সংরক্ষণ", color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun UserDetailAndTrialDialog(
    user: AdminUserDto,
    onDismiss: () -> Unit,
    onToggleBlock: (Boolean) -> Unit,
    onUpdateTrial: (Int, String?, Boolean, String?) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("ব্যবহারকারী ও ডিভাইস তথ্য", fontWeight = FontWeight.Bold, color = RoyalIndigo, fontSize = 17.sp)
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("নাম: ${user.name.ifEmpty { "Pending Profile" }}", fontWeight = FontWeight.Bold)
                    Text("ফোন: ${user.phone ?: "None"}")
                    Text("ইমেইল: ${user.email ?: "None"}")
                    Text("রোল: ${user.role}")
                    Text("ব্যালেন্স: ${user.balance} Tk")
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                Button(
                    onClick = { onToggleBlock(!user.blocked) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (user.blocked) Color(0xFF1B5E20) else StatusRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (user.blocked) "অ্যাকাউন্ট আনব্লক করুন" else "অ্যাকাউন্ট ব্লক করুন", color = Color.White)
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                Text("নিবন্ধিত ডিভাইসসমূহ (${user.devices.size}):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                if (user.devices.isEmpty()) {
                    Text("কোনো ডিভাইস রেজিস্টার করা নেই।", fontSize = 12.sp, color = TextSecondary)
                } else {
                    user.devices.forEach { dev ->
                        var isLocked by remember { mutableStateOf(dev.isTrialLocked) }
                        var daysStr by remember { mutableStateOf("") }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${dev.deviceName} (${dev.deviceModel})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Android: ${dev.androidVersion} | Status: ${dev.status}", fontSize = 12.sp, color = TextSecondary)
                                Text("Trial Expires: ${dev.trialExpiresAt ?: "N/A"}", fontSize = 12.sp, color = TextSecondary)
                                Text("Last seen: ${dev.lastSeenAt ?: "Never"} | Battery: ${dev.lastBatteryPercent ?: 0}%", fontSize = 12.sp, color = TextSecondary)
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Trial Lock State", fontSize = 13.sp)
                                    Switch(checked = isLocked, onCheckedChange = { isLocked = it })
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = daysStr,
                                        onValueChange = { daysStr = it },
                                        placeholder = { Text("দিন বাড়ান/কমান") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            val addDays = daysStr.toIntOrNull() ?: 0
                                            val currentExp = dev.trialExpiresAt?.let {
                                                try {
                                                    val parts = it.substring(0, 10).split("-")
                                                    java.util.Calendar.getInstance().apply {
                                                        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                                                    }.time
                                                } catch (e: Exception) {
                                                    java.util.Date()
                                                }
                                            } ?: java.util.Date()
                                            
                                            val newExp = java.util.Calendar.getInstance().apply {
                                                time = currentExp
                                                add(java.util.Calendar.DAY_OF_YEAR, addDays)
                                            }.time
                                            
                                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                                            val formatted = sdf.format(newExp)
                                            
                                            onUpdateTrial(dev.id, formatted, isLocked, if (isLocked) "trial_expired" else null)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Update", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("বন্ধ করুন", color = RoyalIndigo)
                }
            }
        }
    }
}
