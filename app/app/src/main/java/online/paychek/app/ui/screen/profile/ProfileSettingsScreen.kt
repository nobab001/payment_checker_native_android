package online.paychek.app.ui.screen.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.CredentialItem
import androidx.compose.foundation.BorderStroke
import online.paychek.app.ui.theme.RoyalIndigo
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.autofill.ContentType
import online.paychek.app.utils.disableAutofill
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.zIndex
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically


// =============================================================================
// Design Tokens (matches Dark Gateway theme)
// =============================================================================

private val PsBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val PsCard: Color @Composable get() = MaterialTheme.colorScheme.surface
private val PsCardAlt: Color @Composable get() = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF253349) else Color(0xFFF1F3F5)
private val PsCyan     = Color(0xFF22D3EE)
private val PsGreen    = Color(0xFF10B981)
private val PsAmber    = Color(0xFFF59E0B)
private val PsRed      = Color(0xFFEF4444)
private val TextW: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val TextM: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val GradHeader = Brush.linearGradient(listOf(Color(0xFF1A237E), Color(0xFF0D47A1), Color(0xFF006064)))
private val GradIndigo = Brush.horizontalGradient(listOf(RoyalIndigo, Color(0xFF7C3AED)))

// =============================================================================
// ProfileSettingsScreen — Root
// =============================================================================

@Composable
fun ProfileSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileSettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val isRestricted = remember(context) {
        online.paychek.app.utils.SecurePreferences.decrypt(context, "pcu_device_role") == "restricted"
    }

    val sharedPrefs = remember(context) {
        context.getSharedPreferences(online.paychek.app.config.AppConfig.PREF_NAME, android.content.Context.MODE_PRIVATE)
    }
    var currentTheme by remember {
        mutableStateOf(sharedPrefs.getString("pcu_app_theme", "system") ?: "system")
    }

    DisposableEffect(sharedPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "pcu_app_theme") {
                currentTheme = sharedPrefs.getString("pcu_app_theme", "system") ?: "system"
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (currentTheme) {
        "light" -> false
        "dark"  -> true
        else    -> isSystemDark
    }
    val localAvatarFile = remember { java.io.File(context.filesDir, "profile_avatar.png") }
    var localAvatarPath by remember {
        mutableStateOf<String?>(if (localAvatarFile.exists()) localAvatarFile.absolutePath else null)
    }
    var selectedUriForCrop by remember { mutableStateOf<android.net.Uri?>(null) }
    var bitmapToCrop by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchProfile()
        viewModel.loadCredentials()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(selectedUriForCrop) {
        selectedUriForCrop?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bmp = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bitmapToCrop = bmp
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedUriForCrop = uri
        }
    }

    // Global Snackbar
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { msg ->
            val snackJob = launch {
                snackbarHost.showSnackbar(msg)
            }
            kotlinx.coroutines.delay(3000L)
            snackJob.cancel()
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            val snackJob = launch {
                snackbarHost.showSnackbar("⚠ $msg")
            }
            kotlinx.coroutines.delay(3000L)
            snackJob.cancel()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        containerColor  = PsBg,
        snackbarHost    = { SnackbarHost(snackbarHost) },
        topBar = {
            ProfileTopBar(
                isDark = isDark,
                currentTheme = currentTheme,
                onThemeToggle = {
                    val nextTheme = when (currentTheme) {
                        "system" -> {
                            // Currently following system; override to opposite
                            if (isSystemDark) {
                                android.widget.Toast.makeText(context, "লাইট মোড সক্রিয়", android.widget.Toast.LENGTH_SHORT).show()
                                "light"
                            } else {
                                android.widget.Toast.makeText(context, "ডার্ক মোড সক্রিয়", android.widget.Toast.LENGTH_SHORT).show()
                                "dark"
                            }
                        }
                        else -> {
                            // Already overridden; go back to system-follow mode
                            android.widget.Toast.makeText(context, "সিস্টেম থিম সক্রিয়", android.widget.Toast.LENGTH_SHORT).show()
                            "system"
                        }
                    }
                    sharedPrefs.edit().putString("pcu_app_theme", nextTheme).apply()
                },
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // ── Section 1: Profile Header ──────────────────────────────
                ProfileHeaderCard(
                    userName         = state.userName,
                    primaryPhone     = state.primaryPhone,
                    primaryEmail     = state.primaryEmail,
                    subscriptionPlan = state.subscriptionPlan,
                    avatarUrl        = localAvatarPath ?: state.avatarUrl,
                    onAvatarClick    = { imagePickerLauncher.launch("image/*") },
                    onRenewClick     = onNavigateToSubscription,
                    isRestricted     = isRestricted,
                    modifier         = Modifier.padding(horizontal = 16.dp)
                )

                // ── Section 2: Linked Credentials ─────────────────────────
                ProfileCredentialsCard(
                    isRestricted   = isRestricted,
                    modifier       = Modifier.padding(horizontal = 16.dp)
                )

                // ── Section 3: Security PIN ────────────────────────────────
                SecurityPinCard(
                    onChangePin    = { viewModel.openChangePinDialog() },
                    onForgotPin    = { viewModel.openResetPinDialog() },
                    isRestricted   = isRestricted,
                    modifier       = Modifier.padding(horizontal = 16.dp)
                )

                // Theme is now managed solely via the one-tap top bar icon

                Spacer(Modifier.height(32.dp))
            }

            // Full-screen loading overlay
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PsCyan)
                }
            }
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────
    if (state.showAddCredentialDialog) {
        AddCredentialDialog(state = state, viewModel = viewModel)
    }
    if (state.showChangePinDialog) {
        ChangePinDialog(state = state, viewModel = viewModel)
    }
    if (state.showResetPinDialog) {
        ResetPinDialog(state = state, viewModel = viewModel)
    }

    val bmpCrop = bitmapToCrop
    if (bmpCrop != null) {
        ImageCropperDialog(
            bitmap = bmpCrop,
            onDismiss = {
                selectedUriForCrop = null
                bitmapToCrop = null
            },
            onCropSuccess = { croppedBmp ->
                selectedUriForCrop = null
                bitmapToCrop = null
                
                val localFile = java.io.File(context.filesDir, "profile_avatar.png")
                try {
                    val outputStream = java.io.FileOutputStream(localFile)
                    croppedBmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.close()
                    val localPath = localFile.absolutePath
                    
                    localAvatarPath = localPath
                    viewModel.setLocalAvatar(localPath)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        )
    }
}

// =============================================================================
// Top App Bar
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTopBar(
    isDark: Boolean,
    currentTheme: String,
    onThemeToggle: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val icon = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode
    val iconColor = if (isDark) Color(0xFFF5F7FA) else Color(0xFF12161F)

    TopAppBar(
        modifier = Modifier.height(56.dp),
        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        title = {
            Text(
                "প্রোফাইল সেটিংস",
                color      = TextW,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector        = Icons.Default.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint               = PsCyan,
                    modifier           = Modifier.size(16.dp)
                )
            }
        },
        actions = {
            IconButton(onClick = onThemeToggle, modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Theme Toggle",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PsCard
        )
    )
}

// =============================================================================
// Section 1 — Profile Header Card
// =============================================================================

@Composable
private fun ProfileHeaderCard(
    userName: String,
    primaryPhone: String?,
    primaryEmail: String?,
    subscriptionPlan: String,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    onRenewClick: () -> Unit,
    isRestricted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape    = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GradHeader, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar (Image Picker clickable Box)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(2.dp, if (isRestricted) PsCyan.copy(alpha = 0.5f) else PsCyan, CircleShape)
                        .then(if (!isRestricted) Modifier.clickable { onAvatarClick() } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = avatarUrl,
                        animationSpec = tween(1000),
                        label = "AvatarTransition"
                    ) { currentUrl ->
                        if (!currentUrl.isNullOrEmpty()) {
                            val fullUrl = if (currentUrl.startsWith("http") || currentUrl.startsWith("/") || currentUrl.contains(":/")) {
                                currentUrl
                            } else {
                                "${AppConfig.BASE_URL}${currentUrl.trimStart('/')}"
                            }
                            AsyncImageLoader(
                                url = fullUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                placeholder = {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = PsCyan, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                  Text(
                                      text     = if (userName.isNotEmpty()) userName.first().uppercase() else "M",
                                      color    = Color.White,
                                      fontSize = 26.sp,
                                      fontWeight = FontWeight.Bold
                                  )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text       = userName.ifEmpty { "মার্চেন্ট" },
                        color      = Color.White,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text       = "Plan: $subscriptionPlan",
                            color      = PsCyan,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Renew/Subscription glassmorphic button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PsCyan.copy(alpha = 0.2f))
                                .border(1.dp, PsCyan, RoundedCornerShape(8.dp))
                                .clickable { onRenewClick() }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "রিনিউ/সাবস্ক্রিপশন",
                                color = PsCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Display primary phone contact
                    if (!primaryPhone.isNullOrEmpty()) {
                        Text(
                            text     = primaryPhone,
                            color    = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp
                        )
                    }

                    // Display primary email right below primary phone in header card
                    if (!primaryEmail.isNullOrEmpty()) {
                        Text(
                            text     = primaryEmail,
                            color    = Color.White.copy(alpha = 0.60f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}



// =============================================================================
// Section 3 — Security PIN Card
// =============================================================================

@Composable
private fun SecurityPinCard(
    onChangePin: () -> Unit,
    onForgotPin: () -> Unit,
    isRestricted: Boolean = false,
    modifier: Modifier = Modifier
) {
    PsSection(
        icon      = Icons.Default.Lock,
        iconColor = PsAmber,
        title     = "নিরাপত্তা PIN পরিচালনা",
        modifier  = modifier
    ) {
        Text(
            text     = "আপনার পেমেন্ট গেটওয়ে নিরাপদ রাখতে একটি শক্তিশালী ৬-সংখ্যার PIN ব্যবহার করুন।",
            color    = TextM,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(14.dp))
        // Change PIN button
        Button(
            onClick        = onChangePin,
            enabled        = !isRestricted,
            colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape          = RoundedCornerShape(14.dp),
            border         = androidx.compose.foundation.BorderStroke(1.dp, if (isRestricted) PsAmber.copy(alpha = 0.3f) else PsAmber),
            modifier       = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Icon(Icons.Default.Key, null, tint = if (isRestricted) PsAmber.copy(alpha = 0.4f) else PsAmber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("পিন পরিবর্তন করুন", color = if (isRestricted) PsAmber.copy(alpha = 0.4f) else PsAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        // Forgot PIN button
        TextButton(
            onClick  = onForgotPin,
            enabled  = !isRestricted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = if (isRestricted) TextM.copy(alpha = 0.4f) else TextM, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("পিন ভুলে গেছেন? OTP দিয়ে রিসেট করুন", color = if (isRestricted) TextM.copy(alpha = 0.4f) else TextM, fontSize = 13.sp)
        }
    }
}

// =============================================================================
// Reusable Section Card
// =============================================================================

@Composable
private fun PsSection(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = PsCard),
        shape    = RoundedCornerShape(20.dp),
        border   = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header row
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier             = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
                Text(
                    text       = title,
                    color      = TextW,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f)
                )
                badge?.let {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(iconColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(it, color = iconColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = PsCardAlt)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

// =============================================================================
// Dialog 1 — Add Credential (OTP flow)
// =============================================================================

@Composable
private fun AddCredentialDialog(
    state: ProfileSettingsState,
    viewModel: ProfileSettingsViewModel
) {
    val addCredentialOtpFocusRequester = remember { FocusRequester() }
    val addCredentialOtpInteractionSource = remember { MutableInteractionSource() }

    val isPhone = state.addCredentialType == "phone"
    val label   = if (isPhone) "মোবাইল নম্বর" else "Gmail ঠিকানা"
    val icon    = if (isPhone) Icons.Default.PhoneAndroid else Icons.Default.Email
    val accent  = if (isPhone) RoyalIndigo else Color(0xFF0EA5E9)

    Dialog(
        onDismissRequest = { viewModel.dismissAddCredentialDialog() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            shape          = RoundedCornerShape(20.dp),
            color          = PsCard,
            tonalElevation = 8.dp,
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .wrapContentHeight()
        ) {
            Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                Column {
                // Header band
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GradIndigo, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Column {
                            Text("নতুন $label যোগ করুন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("OTP যাচাইয়ের মাধ্যমে নিরাপদ", color = Color.White.copy(0.75f), fontSize = 11.sp)
                        }
                    }
                }

                val innerScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(innerScrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Contact input
                    PsTextField(
                        value       = state.addCredentialContact,
                        onValueChange = { viewModel.onAddCredentialContactChange(it) },
                        label       = label,
                        icon        = icon,
                        enabled     = !state.addCredentialOtpSent,
                        keyType     = if (isPhone) KeyboardType.Phone else KeyboardType.Email,
                        accent      = accent,
                        contentType = if (isPhone) ContentType.PhoneNumber else ContentType.EmailAddress
                    )

                    // OTP input (visible after send)
                    AnimatedVisibility(visible = state.addCredentialOtpSent) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = addCredentialOtpInteractionSource,
                                    indication = null
                                ) {
                                    addCredentialOtpFocusRequester.requestFocus()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Visual OTP Boxes
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (i in 0 until 6) {
                                    val char = state.addCredentialOtpCode.getOrNull(i)?.toString() ?: ""
                                    val isFocused = state.addCredentialOtpCode.length == i || (i == 5 && state.addCredentialOtpCode.length == 6)

                                    Box(
                                        modifier = Modifier
                                            .size(width = 40.dp, height = 48.dp)
                                            .background(
                                                color = if (char.isNotEmpty()) Color.White.copy(alpha = 0.05f) else PsCardAlt,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .border(
                                                width = if (isFocused) 2.dp else 1.dp,
                                                color = if (isFocused) accent else PsCardAlt,
                                                shape = RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = char,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextW,
                                                textAlign = TextAlign.Center
                                            )
                                            if (isFocused && char.isNotEmpty()) {
                                                BlinkingCursor(color = accent)
                                            }
                                        }
                                        if (isFocused && char.isEmpty()) {
                                            BlinkingCursor(color = accent)
                                        }
                                    }
                                }
                            }

                            // Hidden BasicTextField capturing keyboard & clipboard actions (layered on top)
                            var addCredOtpState by remember {
                                mutableStateOf(
                                    TextFieldValue(
                                        text = state.addCredentialOtpCode,
                                        selection = TextRange(state.addCredentialOtpCode.length)
                                    )
                                )
                            }
                            LaunchedEffect(state.addCredentialOtpCode) {
                                if (state.addCredentialOtpCode != addCredOtpState.text) {
                                    addCredOtpState = TextFieldValue(
                                        text = state.addCredentialOtpCode,
                                        selection = TextRange(state.addCredentialOtpCode.length)
                                    )
                                }
                            }

                            BasicTextField(
                                value = addCredOtpState,
                                onValueChange = { newValue ->
                                    val digits = newValue.text.filter { it.isDigit() }
                                    val sanitized = digits.take(6)
                                    if (sanitized != state.addCredentialOtpCode) {
                                        viewModel.onAddCredentialOtpChange(sanitized)
                                    }
                                    addCredOtpState = newValue.copy(
                                        text = sanitized,
                                        selection = TextRange(sanitized.length)
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.Transparent,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .height(20.dp)
                                    .focusRequester(addCredentialOtpFocusRequester)
                            )
                        }
                    }


                    // Timer / Resend
                    if (state.addCredentialOtpSent && state.addCredentialTimer > 0) {
                        Text(
                            "পুনরায় OTP: ${state.addCredentialTimer}s",
                            color    = TextM,
                            fontSize = 12.sp
                        )
                    }

                    // Error is removed to prevent layout displacement

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.dismissAddCredentialDialog() },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PsCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextM),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("বাতিল")
                        }
                        Button(
                            onClick = {
                                if (!state.addCredentialOtpSent) viewModel.sendCredentialOtp()
                                else viewModel.verifyCredential()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape  = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                if (!state.addCredentialOtpSent) "OTP পাঠান"
                                else "যাচাই ও যোগ করুন",
                                color      = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 13.sp
                            )
                        }
                    }
                }
            } // closes outer Column

                // Floating Error Overlay inside Dialog Box
                AnimatedVisibility(
                    visible = state.errorMessage != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .zIndex(99f)
                ) {
                    state.errorMessage?.let { error ->
                        FloatingErrorBanner(message = error)
                    }
                }
            }
        }
    }
}

// =============================================================================
// Dialog 2 — Change PIN
// =============================================================================

@Composable
private fun ChangePinDialog(
    state: ProfileSettingsState,
    viewModel: ProfileSettingsViewModel
) {
    Dialog(
        onDismissRequest = { viewModel.dismissChangePinDialog() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            shape          = RoundedCornerShape(20.dp),
            color          = PsCard,
            tonalElevation = 8.dp,
            modifier       = Modifier.fillMaxWidth().padding(horizontal = 16.dp).wrapContentHeight()
        ) {
            Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))),
                                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Key, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Column {
                                Text("PIN পরিবর্তন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("পুরানো PIN দিয়ে নতুন PIN সেট করুন", color = Color.White.copy(0.75f), fontSize = 11.sp)
                            }
                        }
                    }

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PinField("পুরানো PIN (৪-৬ ডিজিট)", state.changePinOld, viewModel::onChangePinOldChange)
                        PinField("নতুন PIN (৪-৬ ডিজিট)", state.changePinNew, viewModel::onChangePinNewChange)
                        PinField("নতুন PIN নিশ্চিত করুন", state.changePinConfirm, viewModel::onChangePinConfirmChange)

                        // Error is removed to prevent layout displacement

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.dismissChangePinDialog() },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PsCyan.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextM),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("বাতিল")
                            }
                            Button(
                                onClick = { viewModel.submitChangePin() },
                                colors  = ButtonDefaults.buttonColors(containerColor = PsAmber),
                                shape   = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("সংরক্ষণ করুন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Floating Error Overlay inside Dialog Box
                AnimatedVisibility(
                    visible = state.errorMessage != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .zIndex(99f)
                ) {
                    state.errorMessage?.let { error ->
                        FloatingErrorBanner(message = error)
                    }
                }
            }
        }
    }
}

// =============================================================================
// Dialog 3 — Reset PIN (Forgot PIN)
// =============================================================================

@Composable
private fun ResetPinDialog(
    state: ProfileSettingsState,
    viewModel: ProfileSettingsViewModel
) {
    val resetPinOtpFocusRequester = remember { FocusRequester() }
    val resetPinOtpInteractionSource = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = { viewModel.dismissResetPinDialog() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            shape          = RoundedCornerShape(20.dp),
            color          = PsCard,
            tonalElevation = 8.dp,
            modifier       = Modifier.fillMaxWidth().padding(horizontal = 16.dp).wrapContentHeight()
        ) {
            Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669))),
                            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.LockReset, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Column {
                            Text("PIN রিসেট", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("OTP যাচাইয়ের মাধ্যমে নতুন PIN সেট করুন", color = Color.White.copy(0.75f), fontSize = 11.sp)
                        }
                    }
                }

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PsTextField(
                        value         = state.resetPinContact,
                        onValueChange = { viewModel.onResetPinContactChange(it) },
                        label         = "মোবাইল নম্বর বা Gmail",
                        icon          = Icons.Default.ContactPhone,
                        enabled       = !state.resetPinOtpSent,
                        accent        = PsGreen,
                        contentType   = ContentType.PhoneNumber + ContentType.EmailAddress
                    )

                    AnimatedVisibility(visible = state.resetPinOtpSent) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = resetPinOtpInteractionSource,
                                        indication = null
                                    ) {
                                        resetPinOtpFocusRequester.requestFocus()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Visual OTP Boxes
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    for (i in 0 until 6) {
                                        val char = state.resetPinOtpCode.getOrNull(i)?.toString() ?: ""
                                        val isFocused = state.resetPinOtpCode.length == i || (i == 5 && state.resetPinOtpCode.length == 6)

                                        Box(
                                            modifier = Modifier
                                                .size(width = 40.dp, height = 48.dp)
                                                .background(
                                                    color = if (char.isNotEmpty()) Color.White.copy(0.05f) else PsCardAlt,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .border(
                                                    width = if (isFocused) 2.dp else 1.dp,
                                                    color = if (isFocused) PsGreen else PsCardAlt,
                                                    shape = RoundedCornerShape(10.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                         ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = char,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextW,
                                                    textAlign = TextAlign.Center
                                                )
                                                if (isFocused && char.isNotEmpty()) {
                                                    BlinkingCursor(color = PsGreen)
                                                }
                                            }
                                            if (isFocused && char.isEmpty()) {
                                                BlinkingCursor(color = PsGreen)
                                            }
                                        }
                                    }
                                }

                                // Hidden BasicTextField capturing keyboard & clipboard actions (layered on top)
                                var resetPinOtpState by remember {
                                    mutableStateOf(
                                        TextFieldValue(
                                            text = state.resetPinOtpCode,
                                            selection = TextRange(state.resetPinOtpCode.length)
                                        )
                                    )
                                }
                                LaunchedEffect(state.resetPinOtpCode) {
                                    if (state.resetPinOtpCode != resetPinOtpState.text) {
                                        resetPinOtpState = TextFieldValue(
                                            text = state.resetPinOtpCode,
                                            selection = TextRange(state.resetPinOtpCode.length)
                                        )
                                    }
                                }

                                BasicTextField(
                                    value = resetPinOtpState,
                                    onValueChange = { newValue ->
                                        val digits = newValue.text.filter { it.isDigit() }
                                        val sanitized = digits.take(6)
                                        if (sanitized != state.resetPinOtpCode) {
                                            viewModel.onResetPinOtpChange(sanitized)
                                        }
                                        resetPinOtpState = newValue.copy(
                                            text = sanitized,
                                            selection = TextRange(sanitized.length)
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.Transparent,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    cursorBrush = SolidColor(Color.Transparent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .height(20.dp)
                                        .focusRequester(resetPinOtpFocusRequester)
                                )
                            }
                            PinField("নতুন PIN (৪-৬ ডিজিট)", state.resetPinNewPin, viewModel::onResetPinNewPinChange)
                        }
                    }

                    // Error is removed to prevent layout displacement

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.dismissResetPinDialog() },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PsCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextM),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("বাতিল")
                        }
                        Button(
                            onClick = {
                                if (!state.resetPinOtpSent) viewModel.sendResetPinOtp()
                                else viewModel.submitResetPin()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PsGreen),
                            shape  = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                if (!state.resetPinOtpSent) "OTP পাঠান" else "PIN রিসেট করুন",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                            )
                        }
                    }
                }
            } // closes outer Column

                // Floating Error Overlay inside Dialog Box
                AnimatedVisibility(
                    visible = state.errorMessage != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .zIndex(99f)
                ) {
                    state.errorMessage?.let { error ->
                        FloatingErrorBanner(message = error)
                    }
                }
            }
        }
    }
}

// =============================================================================
// Reusable Input Components
// =============================================================================

@Composable
private fun PsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    keyType: KeyboardType = KeyboardType.Text,
    accent: Color = PsCyan,
    contentType: ContentType? = null
) {
    val fieldModifier = Modifier.fillMaxWidth().run {
        if (contentType != null) {
            semantics { this.contentType = contentType }
        } else {
            this
        }
    }
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label, color = TextM, fontSize = 12.sp) },
        leadingIcon   = { Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp)) },
        enabled       = enabled,
        singleLine    = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyType),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor    = accent,
            unfocusedBorderColor  = PsCardAlt,
            focusedTextColor      = TextW,
            unfocusedTextColor    = TextW,
            disabledTextColor     = TextM,
            disabledBorderColor   = PsCardAlt,
            cursorColor           = accent
        ),
        shape    = RoundedCornerShape(12.dp),
        modifier = fieldModifier
    )
}

@Composable
private fun PinField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var showPin by remember { mutableStateOf(false) }
    OutlinedTextField(
        value         = value,
        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) onValueChange(it) },
        label         = { Text(label, color = TextM, fontSize = 12.sp) },
        leadingIcon   = { Icon(Icons.Default.Pin, null, tint = PsAmber, modifier = Modifier.size(18.dp)) },
        trailingIcon  = {
            IconButton(onClick = { showPin = !showPin }) {
                Icon(
                    imageVector = if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = TextM,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine    = true,
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = PsAmber,
            unfocusedBorderColor = PsCardAlt,
            focusedTextColor     = TextW,
            unfocusedTextColor   = TextW,
            cursorColor          = PsAmber
        ),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .disableAutofill()
    )
}

@Composable
private fun AsyncImageLoader(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = {}
) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url) {
        if (url.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val bmp = if (url.startsWith("http://") || url.startsWith("https://")) {
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    val input = connection.inputStream
                    val decoded = android.graphics.BitmapFactory.decodeStream(input)
                    input.close()
                    decoded
                } else {
                    val cleanPath = url.replace("file://", "")
                    android.graphics.BitmapFactory.decodeFile(cleanPath)
                }
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        placeholder()
    }
}

// =============================================================================
// Image Cropper Component
// =============================================================================

@Composable
private fun ImageCropperDialog(
    bitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit,
    onCropSuccess: (android.graphics.Bitmap) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val viewportSizePx = remember { with(density) { 300.dp.toPx() } }
    val coroutineScope = rememberCoroutineScope()
    var isCropping by remember { mutableStateOf(false) }

    // Fit calculations
    val imgWidth = bitmap.width.toFloat()
    val imgHeight = bitmap.height.toFloat()
    val baseScale = remember(bitmap) { minOf(viewportSizePx / imgWidth, viewportSizePx / imgHeight) }
    val baseTranslateX = remember(bitmap) { (viewportSizePx - imgWidth * baseScale) / 2f }
    val baseTranslateY = remember(bitmap) { (viewportSizePx - imgHeight * baseScale) / 2f }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        "ছবি ক্রপ করুন",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Subtitle
                Text(
                    "আঙ্গুল দিয়ে জুম এবং ড্র্যাগ করে গোল ফ্রেমে ছবি সাজান",
                    color = TextM,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                // Viewport Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // Crop Box size: 300.dp
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .clip(CircleShape) // Rounded cropping mask preview
                            .border(2.dp, PsCyan, CircleShape)
                            .background(Color.Black)
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offset = Offset(
                                        x = offset.x + pan.x,
                                        y = offset.y + pan.y
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "To Crop",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PsCyan),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PsCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("বাতিল", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (!isCropping) {
                                isCropping = true
                                coroutineScope.launch(Dispatchers.Default) {
                                    try {
                                        val cropped = cropBitmap(
                                            source = bitmap,
                                            viewportSizePx = viewportSizePx,
                                            targetSize = 400, // high-res square output
                                            scale = scale,
                                            offset = offset,
                                            baseScale = baseScale,
                                            baseTranslateX = baseTranslateX,
                                            baseTranslateY = baseTranslateY
                                        )
                                        withContext(Dispatchers.Main) {
                                            onCropSuccess(cropped)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isCropping = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PsCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isCropping) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("ক্রপ করুন", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun cropBitmap(
    source: android.graphics.Bitmap,
    viewportSizePx: Float,
    targetSize: Int,
    scale: Float,
    offset: Offset,
    baseScale: Float,
    baseTranslateX: Float,
    baseTranslateY: Float
): android.graphics.Bitmap {
    val cropped = android.graphics.Bitmap.createBitmap(targetSize, targetSize, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(cropped)
    
    val totalScale = baseScale * scale
    val center = viewportSizePx / 2f
    val totalTranslateX = (baseTranslateX - center) * scale + center + offset.x
    val totalTranslateY = (baseTranslateY - center) * scale + center + offset.y
    
    val ratio = targetSize.toFloat() / viewportSizePx
    
    val matrix = android.graphics.Matrix()
    matrix.postScale(totalScale, totalScale)
    matrix.postTranslate(totalTranslateX, totalTranslateY)
    matrix.postScale(ratio, ratio)
    
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, matrix, paint)
    
    return cropped
}

@Composable
private fun BlinkingCursor(color: Color) {
    val transition = rememberInfiniteTransition(label = "BlinkingCursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CursorAlpha"
    )
    Box(
        modifier = Modifier
            .width(2.dp)
            .height(18.dp)
            .alpha(alpha)
            .background(color)
    )
}

@Composable
fun FloatingErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF3D1F1F) else Color(0xFFFFEBEE)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = PsRed,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                color = PsRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// AppThemeSelectionCard has been removed in favor of the 3-state cyclic top bar toggle button

