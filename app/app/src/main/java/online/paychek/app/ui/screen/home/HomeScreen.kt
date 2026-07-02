package online.paychek.app.ui.screen.home

import androidx.activity.compose.BackHandler
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.navigation3.runtime.NavKey
import online.paychek.app.NavKey as AppNavKey
import kotlinx.coroutines.launch
import online.paychek.app.ui.screen.dashboard.DashboardScreen
import online.paychek.app.ui.screen.device.DeviceScreen
import online.paychek.app.ui.screen.profile.ProfileSettingsScreen
import online.paychek.app.ui.screen.transactions.TransactionSearchScreen
import online.paychek.app.ui.screen.apicenter.ApiIntegrationScreen
import online.paychek.app.ui.theme.RoyalIndigo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowBackIosNew
import online.paychek.app.utils.SecurePreferences
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Tab সংজ্ঞা
// ─────────────────────────────────────────────────────────────────────────────
private enum class HomeTab(
    val icon: ImageVector,
    val label: String
) {
    HOME(Icons.Default.Home, "Home"),
    DEVICE(Icons.Default.Build, "Device"),
    SEARCH(Icons.Default.Search, "Search"),
    API(Icons.Default.Code, "API"),
    PROFILE(Icons.Default.Person, "Profile")
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CustomBottomBar(
    selectedTab: HomeTab,
    onTabSelect: (HomeTab) -> Unit
) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp + bottomPadding)
            .padding(bottom = bottomPadding)
    ) {
        HomeTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelect(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onBackground,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxHeight()
            )
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen — Bottom Navigation Hub
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(online.paychek.app.config.AppConfig.PREF_NAME, android.content.Context.MODE_PRIVATE) }
    
    val coroutineScope = rememberCoroutineScope()

    var isAccessibilityEnabled by remember { mutableStateOf(true) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.currentStateFlow.collect { state ->
            if (state == androidx.lifecycle.Lifecycle.State.RESUMED) {
                isAccessibilityEnabled = online.paychek.app.utils.AccessibilityHelper.isAccessibilityServiceEnabled(context)
            }
        }
    }

    var hasNotificationPermissionChecked by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        online.paychek.app.MainActivity.isRequestingPermission = false
        hasNotificationPermissionChecked = true
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) {
                online.paychek.app.MainActivity.isRequestingPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                hasNotificationPermissionChecked = true
            }
        } else {
            hasNotificationPermissionChecked = true
        }
    }

    var showPurchaseDialog by remember { mutableStateOf(false) }

    var plansList by remember { mutableStateOf<List<online.paychek.app.data.remote.dto.SubscriptionPlanDto>>(emptyList()) }
    var purchaseLoading by remember { mutableStateOf(false) }

    var isApproved by remember {
        mutableStateOf(
            SecurePreferences.decrypt(context, "pcu_is_approved") == "true"
        )
    }
    var deviceRole by remember {
        mutableStateOf(
            SecurePreferences.decrypt(context, "pcu_device_role").ifEmpty { "pending" }
        )
    }

    DisposableEffect(context) {
        val sharedPrefs = context.getSharedPreferences("paychek_secure_prefs", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "pcu_is_approved" || key == "pcu_device_role") {
                isApproved = SecurePreferences.decrypt(context, "pcu_is_approved") == "true"
                deviceRole = SecurePreferences.decrypt(context, "pcu_device_role").ifEmpty { "pending" }
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // 5-second polling loop to check if approved
    LaunchedEffect(isApproved) {
        if (!isApproved) {
            while (true) {
                try {
                    val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
                    if (token.isNotEmpty()) {
                        val response = online.paychek.app.data.remote.api.RetrofitClient.gatewayApiService.checkApprovalStatus("Bearer $token")
                        if (response.isSuccessful && response.body() != null) {
                            val body = response.body()!!
                            if (body.isApproved) {
                                SecurePreferences.encrypt(context, "pcu_is_approved", "true")
                                SecurePreferences.encrypt(context, "pcu_device_role", body.deviceRole ?: "pending")
                                SecurePreferences.encrypt(context, online.paychek.app.config.AppConfig.KEY_IS_OWNER_DEVICE, if (body.deviceRole == "owner") "true" else "false")
                                if (!body.deviceSpecificPin.isNullOrEmpty()) {
                                    SecurePreferences.encrypt(context, online.paychek.app.config.AppConfig.KEY_DEVICE_SPECIFIC_PIN, body.deviceSpecificPin)
                                } else {
                                    SecurePreferences.remove(context, online.paychek.app.config.AppConfig.KEY_DEVICE_SPECIFIC_PIN)
                                }
                                isApproved = true
                                deviceRole = body.deviceRole ?: "pending"
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore network error in background polling
                }
                kotlinx.coroutines.delay(5000L)
            }
        }
    }

    // Fetch pending approvals on load or refresh (no 10-second polling)
    var pendingDevices by remember { mutableStateOf<List<online.paychek.app.data.remote.dto.ChildDeviceDto>>(emptyList()) }
    LaunchedEffect(isApproved, deviceRole) {
        if (isApproved && deviceRole == "owner") {
            try {
                val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
                if (token.isNotEmpty()) {
                    val response = online.paychek.app.data.remote.api.RetrofitClient.gatewayApiService.getPendingApprovals("Bearer $token")
                    if (response.isSuccessful && response.body() != null) {
                        pendingDevices = response.body()!!.data.filter { it.isApproved == 0 }
                    }
                }
            } catch (e: Exception) {
                // Ignore error
            }
        } else {
            pendingDevices = emptyList()
        }
    }

    var showPinApprovalDialogForDevice by remember { mutableStateOf<online.paychek.app.data.remote.dto.ChildDeviceDto?>(null) }
    var pinApprovalInput by remember { mutableStateOf("") }
    var pinApprovalError by remember { mutableStateOf<String?>(null) }
    var pinApprovalLoading by remember { mutableStateOf(false) }

    var submittingRole by remember { mutableStateOf(false) }
    var submitRoleError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(showPurchaseDialog) {
        if (showPurchaseDialog && plansList.isEmpty()) {
            val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
            if (token.isNotEmpty()) {
                online.paychek.app.data.repository.PaymentRepository().getPlans(token).onSuccess {
                    plansList = it
                }
            }
        }
    }

    val onNavigateToApiCenter: () -> Unit = {
        val accountLevel = prefs.getString("pcu_account_level", "FREE_LEVEL") ?: "FREE_LEVEL"
        if (accountLevel == "FREE_LEVEL") {
            showPurchaseDialog = true
        } else {
            onNavigate(AppNavKey.ApiCenter)
        }
    }
    var selectedTab by remember { mutableStateOf(HomeTab.HOME) }

    BackHandler(enabled = selectedTab != HomeTab.HOME) {
        selectedTab = HomeTab.HOME
    }

    // 1. Lock Overlay if not approved
    if (!isApproved) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock icon",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "অন্য সচল ডিভাইস থেকে অনুমতি নিন",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "এই ডিভাইসটি এখনও অনুমোদিত নয়। অনুগ্রহ করে আপনার অন্য কোনো সচল ডিভাইস থেকে পিন দিয়ে এটি অনুমোদন করুন।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
            }
        }
        return
    }

    // 2. Role selection Dialog if role is pending
    if (isApproved && deviceRole == "pending") {
        Dialog(
            onDismissRequest = { /* Non-dismissible */ },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .wrapContentHeight()
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF22D3EE).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Person",
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "এই ডিভাইসটি কার?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "ডিভাইসের মালিকানা নিশ্চিত করতে নিচের উপযুক্ত মোডটি সিলেক্ট করুন।",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF253349) else Color(0xFFF1F3F5)),
                        border = BorderStroke(1.dp, if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF22D3EE).copy(0.3f) else Color(0xFFE3E5E8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !submittingRole) {
                                coroutineScope.launch {
                                    submittingRole = true
                                    submitRoleError = null
                                    try {
                                        val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
                                        val response = online.paychek.app.data.remote.api.RetrofitClient.gatewayApiService.submitRole(
                                            "Bearer $token",
                                            online.paychek.app.data.remote.dto.SubmitRoleRequest("owner")
                                        )
                                        if (response.isSuccessful && response.body()?.success == true) {
                                            SecurePreferences.encrypt(context, "pcu_device_role", "owner")
                                            deviceRole = "owner"
                                        } else {
                                            submitRoleError = "অনুমোদন জমা দেওয়া যায়নি"
                                        }
                                    } catch (e: Exception) {
                                        submitRoleError = "নেটওয়ার্ক ত্রুটি: ${e.localizedMessage}"
                                    }
                                    submittingRole = false
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "আমার নিজের ডিভাইস",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "মালিকানা মোড (Full Owner Access)। সব সেটি সেটিংস পরিবর্তন ও অন্য ফোন অ্যাক্সেস সম্ভব।",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF253349) else Color(0xFFF1F3F5)),
                        border = BorderStroke(1.dp, if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF475569).copy(0.3f) else Color(0xFFE3E5E8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !submittingRole) {
                                coroutineScope.launch {
                                    submittingRole = true
                                    submitRoleError = null
                                    try {
                                        val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
                                        val response = online.paychek.app.data.remote.api.RetrofitClient.gatewayApiService.submitRole(
                                            "Bearer $token",
                                            online.paychek.app.data.remote.dto.SubmitRoleRequest("restricted")
                                        )
                                        if (response.isSuccessful && response.body()?.success == true) {
                                            SecurePreferences.encrypt(context, "pcu_device_role", "restricted")
                                            deviceRole = "restricted"
                                        } else {
                                            submitRoleError = "অনুমোদন জমা দেওয়া যায়নি"
                                        }
                                    } catch (e: Exception) {
                                        submitRoleError = "নেটওয়ার্ক ত্রুটি: ${e.localizedMessage}"
                                    }
                                    submittingRole = false
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "অন্য কারো/সহযোগীর ডিভাইস",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "স্টাফ মোড (Restricted Mode)। সেটিংস পরিবর্তন বন্ধ থাকবে ও অন্য কোনো ফোন দেখা যাবে না।",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (submitRoleError != null) {
                        Text(submitRoleError!!, color = Color(0xFFEF4444), fontSize = 12.sp)
                    }

                    if (submittingRole) {
                        CircularProgressIndicator(color = Color(0xFF22D3EE), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }

    // 3. PIN Approval Dialog for Owner Device
    val deviceToApprove = showPinApprovalDialogForDevice
    if (deviceToApprove != null) {
        var selectedRoleToApprove by remember { mutableStateOf<String?>(null) }
        Dialog(
            onDismissRequest = {
                showPinApprovalDialogForDevice = null
                pinApprovalInput = ""
                pinApprovalError = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .wrapContentHeight()
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "ডিভাইস অনুমোদন করুন",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "নতুন ডিভাইস '${deviceToApprove.customDeviceName.ifEmpty { "চাইল্ড ডিভাইস" }}' অনুমোদন করতে ডিভাইস রোল সিলেক্ট করে সিকিউরিটি পিন লিখুন।",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    // Role Selection Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedRoleToApprove == "owner") Color(0xFF22D3EE).copy(alpha = 0.15f) else (if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF253349) else Color(0xFFF1F3F5))
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selectedRoleToApprove == "owner") Color(0xFF22D3EE) else (if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF475569).copy(alpha = 0.5f) else Color(0xFFE3E5E8))
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedRoleToApprove = "owner" }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("আমার নিজের ডিভাইস", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("মালিক মোড (Owner)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center)
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedRoleToApprove == "restricted") Color(0xFF22D3EE).copy(alpha = 0.15f) else (if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF253349) else Color(0xFFF1F3F5))
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selectedRoleToApprove == "restricted") Color(0xFF22D3EE) else (if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF475569).copy(alpha = 0.5f) else Color(0xFFE3E5E8))
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedRoleToApprove = "restricted" }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("অন্য কারো ডিভাইস", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("স্টাফ মোড (Restricted)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }

                    // PIN Text Field: visible and active (enabled = true) only if selectedRoleToApprove != null
                    androidx.compose.animation.AnimatedVisibility(visible = selectedRoleToApprove != null) {
                        OutlinedTextField(
                            value = pinApprovalInput,
                            onValueChange = { newValue ->
                                if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                                    pinApprovalInput = newValue
                                }
                            },
                            label = { Text("পিন কোড", color = Color(0xFF94A3B8)) },
                            singleLine = true,
                            enabled = selectedRoleToApprove != null,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF22D3EE),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (pinApprovalError != null) {
                        Text(pinApprovalError!!, color = Color(0xFFEF4444), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showPinApprovalDialogForDevice = null
                                pinApprovalInput = ""
                                pinApprovalError = null
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            Text("বাতিল")
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val currentRole = selectedRoleToApprove
                                    if (currentRole == null) {
                                        pinApprovalError = "অনুগ্রহ করে একটি ডিভাইস রোল সিলেক্ট করুন"
                                        return@launch
                                    }
                                    if (pinApprovalInput.length < 4) {
                                        pinApprovalError = "কমপক্ষে ৪ ডিজিটের পিন দিন"
                                        return@launch
                                    }
                                    pinApprovalLoading = true
                                    pinApprovalError = null
                                    try {
                                        val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
                                        val response = online.paychek.app.data.remote.api.RetrofitClient.gatewayApiService.approveByPin(
                                            "Bearer $token",
                                            online.paychek.app.data.remote.dto.ApproveDeviceRequest(
                                                deviceId = deviceToApprove.deviceId,
                                                pin = pinApprovalInput,
                                                deviceRole = currentRole
                                            )
                                        )
                                        if (response.isSuccessful && response.body()?.success == true) {
                                            android.widget.Toast.makeText(context, "ডিভাইসটি সফলভাবে অনুমোদন করা হয়েছে।", android.widget.Toast.LENGTH_SHORT).show()
                                            pendingDevices = pendingDevices.filter { it.deviceId != deviceToApprove.deviceId }
                                            showPinApprovalDialogForDevice = null
                                            pinApprovalInput = ""
                                        } else {
                                            pinApprovalError = "ভুল পিন কোড, অনুগ্রহ করে আবার চেষ্টা করুন।"
                                        }
                                    } catch (e: Exception) {
                                        pinApprovalError = "নেটওয়ার্ক ত্রুটি: ${e.localizedMessage}"
                                    }
                                    pinApprovalLoading = false
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                            enabled = !pinApprovalLoading && selectedRoleToApprove != null,
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            if (pinApprovalLoading) {
                                CircularProgressIndicator(color = Color(0xFF0F172A), modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("নিশ্চিত করুন", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            CustomBottomBar(
                selectedTab = selectedTab,
                onTabSelect = { tab ->
                    selectedTab = tab
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top accessibility banner — replaces the old blocking AlertDialog.


            // Top notification banner for owner devices with pending requests
            if (isApproved && deviceRole == "owner" && pendingDevices.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { showPinApprovalDialogForDevice = pendingDevices.first() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Warning",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "অনুমোদনের জন্য ${pendingDevices.size}টি ডিভাইস পেন্ডিং রয়েছে",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "অনুমোদন করুন",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when (selectedTab) {
                    HomeTab.HOME -> DashboardScreen(
                        onNavigateToHistory = { selectedTab = HomeTab.SEARCH },
                        onNavigateToSubscription = { onNavigate(AppNavKey.SubscriptionPackages()) },
                        modifier = Modifier.fillMaxSize()
                    )
                    HomeTab.DEVICE -> DeviceScreen(
                        onNavigateBack = { selectedTab = HomeTab.HOME },
                        onNavigateToSubscription = { tab -> onNavigate(AppNavKey.SubscriptionPackages(tab)) },
                        modifier = Modifier.fillMaxSize()
                    )
                    HomeTab.SEARCH -> TransactionSearchScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                    HomeTab.API -> ApiIntegrationScreen(
                        onNavigateToCheckout = onNavigateToApiCenter,
                        onNavigateToWebsites = { onNavigate(AppNavKey.WebsiteManagement) },
                        onNavigateToDocs = { onNavigate(AppNavKey.ApiDocs) },
                        modifier = Modifier.fillMaxSize()
                    )
                    HomeTab.PROFILE -> ProfileSettingsScreen(
                        onNavigateBack = { selectedTab = HomeTab.HOME },
                        onNavigateToSubscription = { onNavigate(AppNavKey.SubscriptionPackages()) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Placeholder — Transaction History (ধাপ ৫-এ পূর্ণ হবে)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HistoryPlaceholderScreen() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.History,
                contentDescription = "History",
                tint               = Color(0xFF22D3EE),
                modifier           = Modifier.size(56.dp)
            )
            Text(
                text       = "ট্রানজেকশন হিস্টোরি",
                color      = Color.White,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text     = "ধাপ ৫-এ সম্পূর্ণ UI তৈরি হবে...",
                color    = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Sub-page Navigation State
// ─────────────────────────────────────────────────────────────────────────────
// SettingsSubPage enum removed as SETTINGS tab is no longer used

// ─────────────────────────────────────────────────────────────────────────────
// SettingsMenuScreen — Settings Select Menu
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsMenuScreen(
    onNavigateToGateway: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Settings Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF22D3EE).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFF22D3EE),
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = "সেটিংস",
                    color = Color(0xFFF8FAFC),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "অ্যাপ ও অ্যাকাউন্ট কনফিগারেশন",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Option 1: Gateway/Device Settings
        SettingsMenuCard(
            title = "গেটওয়ে ও ডিভাইস সেটিংস",
            description = "সিম স্লট ১/২ ও পেমেন্ট মেথড কনফিগার করুন",
            icon = Icons.Default.Tune,
            iconBgColor = Color(0xFF22D3EE),
            onClick = onNavigateToGateway
        )

        // Option 2: Profile Settings
        SettingsMenuCard(
            title = "মার্চেন্ট প্রোফাইল সেটিংস",
            description = "পিন পরিবর্তন, লিংকড মোবাইল ও জিমেইল যুক্ত করুন",
            icon = Icons.Default.Person,
            iconBgColor = Color(0xFF10B981),
            onClick = onNavigateToProfile
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SettingsMenuCard — Clickable Settings Card Item
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsMenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBgColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(0xFFF8FAFC),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
