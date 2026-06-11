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
import online.paychek.app.ui.theme.RoyalIndigo
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import online.paychek.app.utils.autofill
import online.paychek.app.utils.disableAutofill
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch

// =============================================================================
// Design Tokens (matches Dark Gateway theme)
// =============================================================================

private val PsBg       = Color(0xFF0F172A)
private val PsCard     = Color(0xFF1E293B)
private val PsCardAlt  = Color(0xFF253349)
private val PsCyan     = Color(0xFF22D3EE)
private val PsGreen    = Color(0xFF10B981)
private val PsAmber    = Color(0xFFF59E0B)
private val PsRed      = Color(0xFFEF4444)
private val TextW      = Color(0xFFF8FAFC)
private val TextM      = Color(0xFF94A3B8)
private val GradHeader = Brush.linearGradient(listOf(Color(0xFF1A237E), Color(0xFF0D47A1), Color(0xFF006064)))
private val GradIndigo = Brush.horizontalGradient(listOf(RoyalIndigo, Color(0xFF7C3AED)))

// =============================================================================
// ProfileSettingsScreen — Root
// =============================================================================

@Composable
fun ProfileSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileSettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val context = LocalContext.current
    var selectedUriForCrop by remember { mutableStateOf<android.net.Uri?>(null) }
    var bitmapToCrop by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchProfile()
        viewModel.loadCredentials()
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
        state.successMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHost.showSnackbar("⚠ $it")
            viewModel.clearMessages()
        }
    }

    Scaffold(
        containerColor  = PsBg,
        snackbarHost    = { SnackbarHost(snackbarHost) },
        topBar = {
            ProfileTopBar(onNavigateBack = onNavigateBack)
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
                    subscriptionType = state.subscriptionType,
                    avatarUrl        = state.avatarUrl,
                    isUploadingAvatar = state.isUploadingAvatar,
                    onAvatarClick    = { imagePickerLauncher.launch("image/*") },
                    modifier         = Modifier.padding(horizontal = 16.dp)
                )

                // ── Section 2: Linked Credentials ─────────────────────────
                ProfileCredentialsCard(
                    modifier       = Modifier.padding(horizontal = 16.dp)
                )

                // ── Section 3: Security PIN ────────────────────────────────
                SecurityPinCard(
                    onChangePin    = { viewModel.openChangePinDialog() },
                    onForgotPin    = { viewModel.openResetPinDialog() },
                    modifier       = Modifier.padding(horizontal = 16.dp)
                )

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
                
                val cacheDir = context.cacheDir
                val localFile = java.io.File(cacheDir, "avatar_preview.jpg")
                try {
                    val outputStream = java.io.FileOutputStream(localFile)
                    croppedBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
                    outputStream.close()
                    val localPath = localFile.absolutePath
                    
                    viewModel.setLocalAvatar(localPath)
                    
                    val byteArrayOutputStream = java.io.ByteArrayOutputStream()
                    croppedBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
                    val byteArray = byteArrayOutputStream.toByteArray()
                    val base64 = android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
                    viewModel.uploadAvatar(base64)
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
private fun ProfileTopBar(onNavigateBack: () -> Unit) {
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
    subscriptionType: String,
    avatarUrl: String?,
    isUploadingAvatar: Boolean,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (badgeLabel, badgeColor) = when (subscriptionType.lowercase()) {
        "premium"          -> "প্রিমিয়াম"  to PsGreen
        "merchant_active"  -> "অ্যাক্টিভ"   to PsCyan
        else               -> "ট্রায়াল"     to PsAmber
    }

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
                        .border(2.dp, PsCyan, CircleShape)
                        .clickable { onAvatarClick() },
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

                    if (isUploadingAvatar) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = PsCyan,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text       = userName.ifEmpty { "মার্চেন্ট" },
                        color      = TextW,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Show dynamic subscription plan name below the user's name
                    Text(
                        text       = "Plan: ${subscriptionType.replaceFirstChar { it.uppercase() }}",
                        color      = PsCyan,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text       = "● $badgeLabel অ্যাকাউন্ট",
                            color      = badgeColor,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Display primary phone contact
                    if (!primaryPhone.isNullOrEmpty()) {
                        Text(
                            text     = primaryPhone,
                            color    = TextW.copy(alpha = 0.75f),
                            fontSize = 13.sp
                        )
                    }

                    // Display primary email right below primary phone in header card
                    if (!primaryEmail.isNullOrEmpty()) {
                        Text(
                            text     = primaryEmail,
                            color    = TextW.copy(alpha = 0.60f),
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
            colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape          = RoundedCornerShape(14.dp),
            border         = androidx.compose.foundation.BorderStroke(1.dp, PsAmber),
            modifier       = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Icon(Icons.Default.Key, null, tint = PsAmber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("পিন পরিবর্তন করুন", color = PsAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        // Forgot PIN button
        TextButton(
            onClick  = onForgotPin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.HelpOutline, null, tint = TextM, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("পিন ভুলে গেছেন? OTP দিয়ে রিসেট করুন", color = TextM, fontSize = 13.sp)
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
    val isPhone = state.addCredentialType == "phone"
    val label   = if (isPhone) "মোবাইল নম্বর" else "Gmail ঠিকানা"
    val icon    = if (isPhone) Icons.Default.PhoneAndroid else Icons.Default.Email
    val accent  = if (isPhone) RoyalIndigo else Color(0xFF0EA5E9)

    Dialog(onDismissRequest = { viewModel.dismissAddCredentialDialog() }) {
        Surface(
            shape          = RoundedCornerShape(20.dp),
            color          = PsCard,
            tonalElevation = 8.dp,
            modifier       = Modifier.width(320.dp).wrapContentHeight()
        ) {
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

                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Contact input
                    PsTextField(
                        value       = state.addCredentialContact,
                        onValueChange = { viewModel.onAddCredentialContactChange(it) },
                        label       = label,
                        icon        = icon,
                        enabled     = !state.addCredentialOtpSent,
                        keyType     = if (isPhone) KeyboardType.Phone else KeyboardType.Email,
                        accent      = accent,
                        autofillTypes = if (isPhone) listOf(AutofillType.PhoneNumber) else listOf(AutofillType.EmailAddress),
                        onFill = { viewModel.onAddCredentialContactChange(it) }
                    )

                    // OTP input (visible after send)
                    AnimatedVisibility(visible = state.addCredentialOtpSent) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = state.addCredentialOtpCode,
                                onValueChange = { newValue ->
                                    val sanitized = newValue.filter { it.isDigit() }.take(6)
                                    viewModel.onAddCredentialOtpChange(sanitized)
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
                                cursorBrush = SolidColor(Color.Transparent),
                                modifier = Modifier
                                    .matchParentSize()
                                    .alpha(0.01f)
                            )

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
                                                color = if (char.isNotEmpty()) Color.White.copy(0.05f) else PsCardAlt,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .border(
                                                width = if (isFocused) 2.dp else 1.dp,
                                                color = if (isFocused) accent else PsCardAlt,
                                                shape = RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = char,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextW,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
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

                    // Error
                    state.errorMessage?.let {
                        Text(it, color = PsRed, fontSize = 12.sp)
                    }

                    // Buttons
                    Row(
                        modifier            = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { viewModel.dismissAddCredentialDialog() }) {
                            Text("বাতিল", color = TextM)
                        }
                        Button(
                            onClick = {
                                if (!state.addCredentialOtpSent) viewModel.sendCredentialOtp()
                                else viewModel.verifyCredential()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape  = RoundedCornerShape(10.dp)
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
    Dialog(onDismissRequest = { viewModel.dismissChangePinDialog() }) {
        Surface(
            shape          = RoundedCornerShape(20.dp),
            color          = PsCard,
            tonalElevation = 8.dp,
            modifier       = Modifier.width(320.dp).wrapContentHeight()
        ) {
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

                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PinField("পুরানো PIN (৪-৬ ডিজিট)", state.changePinOld, viewModel::onChangePinOldChange)
                    PinField("নতুন PIN (৪-৬ ডিজিট)", state.changePinNew, viewModel::onChangePinNewChange)
                    PinField("নতুন PIN নিশ্চিত করুন", state.changePinConfirm, viewModel::onChangePinConfirmChange)

                    state.errorMessage?.let { Text(it, color = PsRed, fontSize = 12.sp) }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = { viewModel.dismissChangePinDialog() }) { Text("বাতিল", color = TextM) }
                        Button(
                            onClick = { viewModel.submitChangePin() },
                            colors  = ButtonDefaults.buttonColors(containerColor = PsAmber),
                            shape   = RoundedCornerShape(10.dp)
                        ) {
                            Text("সংরক্ষণ করুন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
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
    Dialog(onDismissRequest = { viewModel.dismissResetPinDialog() }) {
        Surface(
            shape          = RoundedCornerShape(20.dp),
            color          = PsCard,
            tonalElevation = 8.dp,
            modifier       = Modifier.width(320.dp).wrapContentHeight()
        ) {
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

                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PsTextField(
                        value         = state.resetPinContact,
                        onValueChange = { viewModel.onResetPinContactChange(it) },
                        label         = "মোবাইল নম্বর বা Gmail",
                        icon          = Icons.Default.ContactPhone,
                        enabled       = !state.resetPinOtpSent,
                        accent        = PsGreen,
                        autofillTypes = listOf(AutofillType.PhoneNumber, AutofillType.EmailAddress),
                        onFill = { viewModel.onResetPinContactChange(it) }
                    )

                    AnimatedVisibility(visible = state.resetPinOtpSent) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextField(
                                    value = state.resetPinOtpCode,
                                    onValueChange = { newValue ->
                                        val sanitized = newValue.filter { it.isDigit() }.take(6)
                                        viewModel.onResetPinOtpChange(sanitized)
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
                                    cursorBrush = SolidColor(Color.Transparent),
                                    modifier = Modifier
                                        .matchParentSize()
                                        .alpha(0.01f)
                                )

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
                                            Text(
                                                text = char,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextW,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                            PinField("নতুন PIN (৪-৬ ডিজিট)", state.resetPinNewPin, viewModel::onResetPinNewPinChange)
                        }
                    }

                    state.errorMessage?.let { Text(it, color = PsRed, fontSize = 12.sp) }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = { viewModel.dismissResetPinDialog() }) { Text("বাতিল", color = TextM) }
                        Button(
                            onClick = {
                                if (!state.resetPinOtpSent) viewModel.sendResetPinOtp()
                                else viewModel.submitResetPin()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PsGreen),
                            shape  = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                if (!state.resetPinOtpSent) "OTP পাঠান" else "PIN রিসেট করুন",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                            )
                        }
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
    autofillTypes: List<AutofillType>? = null,
    onFill: ((String) -> Unit)? = null
) {
    val fieldModifier = Modifier.fillMaxWidth().run {
        if (autofillTypes != null && onFill != null) {
            autofill(autofillTypes, onFill)
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
    val totalTranslateX = baseTranslateX * scale + offset.x
    val totalTranslateY = baseTranslateY * scale + offset.y
    
    val ratio = targetSize.toFloat() / viewportSizePx
    
    val matrix = android.graphics.Matrix()
    matrix.postScale(totalScale, totalScale)
    matrix.postTranslate(totalTranslateX, totalTranslateY)
    matrix.postScale(ratio, ratio)
    
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, matrix, paint)
    
    return cropped
}
