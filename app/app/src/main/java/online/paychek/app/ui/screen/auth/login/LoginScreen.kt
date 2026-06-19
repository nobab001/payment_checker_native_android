package online.paychek.app.ui.screen.auth.login

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import online.paychek.app.R
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Support
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.ui.theme.*
import online.paychek.app.utils.adaptivePadding
import online.paychek.app.utils.adaptiveTextSize
import online.paychek.app.utils.screenWidth
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.zIndex
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateToSignup: (String, String) -> Unit, // passes (contact, token)
    onNavigateToHome: (String) -> Unit, // passes token
    onNavigateToAdminDashboard: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Observe OTP verification and trigger navigation
    val focusRequester = remember { FocusRequester() }
    val otpInteractionSource = remember { MutableInteractionSource() }
    var verificationResult by remember { mutableStateOf<online.paychek.app.data.remote.dto.VerifyOtpResponse?>(null) }

    val isBypass = uiState.contact == uiState.adminSecretUsername
    var adminBypassOpenedAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(isBypass) {
        if (isBypass) {
            if (adminBypassOpenedAt == null) {
                adminBypassOpenedAt = System.currentTimeMillis()
            }
        } else {
            adminBypassOpenedAt = null
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            kotlinx.coroutines.delay(3000L)
            viewModel.clearError()
        }
    }

    LaunchedEffect(verificationResult) {
        verificationResult?.let { res ->
            online.paychek.app.utils.SecurePreferences.encrypt(
                context,
                online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN,
                res.token
            )
            online.paychek.app.utils.SecurePreferences.encrypt(
                context,
                "pcu_user_role",
                res.user.role
            )
            online.paychek.app.utils.SecurePreferences.encrypt(
                context,
                "pcu_contact",
                uiState.contact
            )
            online.paychek.app.utils.SecurePreferences.encrypt(
                context,
                "pcu_is_approved",
                if (res.device.isApproved) "true" else "false"
            )
            online.paychek.app.utils.SecurePreferences.encrypt(
                context,
                "pcu_device_role",
                res.device.deviceRole
            )
            if (res.user.role == "admin") {
                online.paychek.app.utils.SecurePreferences.encrypt(context, "pcu_profile_complete", "true")
                onNavigateToAdminDashboard(res.token)
            } else if (!res.user.profileComplete) {
                online.paychek.app.utils.SecurePreferences.encrypt(context, "pcu_profile_complete", "false")
                onNavigateToSignup(uiState.contact, res.token)
            } else {
                online.paychek.app.utils.SecurePreferences.encrypt(context, "pcu_profile_complete", "true")
                onNavigateToHome(res.token)
            }
        }
    }

    // ── "অ্যাকাউন্ট খুঁজে পাওয়া যায়নি" প্রিমিয়াম কাস্টম ডায়ালগ ─────────────
    if (uiState.showRegisterDialog) {
        PremiumRegisterDialog(
            onDismiss = { viewModel.dismissRegisterDialog() },
            onRegisterClick = { viewModel.proceedToRegister(context) }
        )
    }

    // ── "👑 লিমিট শেষ" প্রিমিয়াম কাস্টম ডায়ালগ ─────────────
    if (uiState.showLimitExceededDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLimitExceededDialog() },
            title = {
                Text(
                    text = "👑 লিমিট শেষ!",
                    color = Color(0xFFF59E0B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "আপনি আপনার বর্তমান প্যাকেজের সর্বোচ্চ সীমা অতিক্রম করেছেন। আরও সাইট বা ডিভাইস যুক্ত করতে অনুগ্রহ করে আপনার প্যাকেজটি আপগ্রেড করুন।",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissLimitExceededDialog() }
                ) {
                    Text("ঠিক আছে", color = RoyalIndigo, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            modifier = if (isSystemInDarkTheme()) Modifier else Modifier.border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(20.dp))
        )
    }

    // ── "ডিভাইস লিংক নোটিশ" প্রিমিয়াম কাস্টম ডায়ালগ ─────────────
    if (uiState.showDeviceBoundDialog) {
        Dialog(
            onDismissRequest = { viewModel.dismissDeviceBoundDialog() },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Surface(
                shape    = RoundedCornerShape(20.dp),
                color    = MaterialTheme.colorScheme.surface,
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
                ) {

                    // ── রয়্যাল ইন্ডিগো শীর্ষ ব্যান্ড ─────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(RoyalIndigo, Color(0xFF7C3AED))
                                ),
                                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text      = "ডিভাইস লিংক নোটিশ",
                                fontWeight = FontWeight.Bold,
                                color     = Color.White,
                                fontSize  = 16.sp
                            )
                            Text(
                                text    = "নিরাপত্তা বিধিনিষেধ সক্রিয়",
                                color   = Color.White.copy(alpha = 0.80f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // ── মূল বার্তা ও বাউন্ড অ্যাকাউন্ট তালিকা ────────────────────────────
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // মূল সিকিউরিটি বার্তা
                        Text(
                            text       = "নিরাপত্তা নিশ্চিতকরণ ও অ্যাকাউন্ট পলিসির কারণে একটি ডিভাইসে কেবল একটি অ্যাকাউন্টই সক্রিয় রাখা অনুমোদিত। আপনার এই ডিভাইসটি ইতিমধ্যে নিচের অ্যাকাউন্টের সাথে লিংক করা রয়েছে:",
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize   = 13.sp,
                            lineHeight = 19.sp
                        )

                        // ── বাউন্ড কন্টাক্ট তালিকা ──────────────────
                        val hasCredentials = uiState.boundPhones.isNotEmpty() || uiState.boundEmails.isNotEmpty()
                        if (hasCredentials) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text       = "লিংকড অ্যাকাউন্ট:",
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize   = 11.sp
                                )
                                // ── ফোন নম্বরগুলো ──
                                uiState.boundPhones.forEach { maskedPhone ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(RoyalIndigo.copy(alpha = 0.12f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PhoneAndroid,
                                                contentDescription = null,
                                                tint   = RoyalIndigo,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text       = maskedPhone,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = MaterialTheme.colorScheme.onSurface,
                                            fontSize   = 13.sp
                                        )
                                    }
                                }
                                // ── ইমেইলগুলো ──
                                uiState.boundEmails.forEach { maskedEmail ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Color(0xFF0EA5E9).copy(alpha = 0.12f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Person,
                                                contentDescription = null,
                                                tint   = Color(0xFF0EA5E9),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text       = maskedEmail,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = MaterialTheme.colorScheme.onSurface,
                                            fontSize   = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text       = "নতুন করে অন্য কোনো অ্যাকাউন্ট এই ডিভাইসে যুক্ত করা সম্ভব নয়। অ্যাপের সেবা উপভোগ করতে দয়া করে আপনার ওপরের লিংক করা অ্যাকাউন্টটি ব্যবহার করুন।",
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize   = 12.sp,
                            lineHeight = 18.sp
                        )

                        // ── বাটন রো (ঠিক আছে) ───────────────────
                        Button(
                            onClick        = { viewModel.dismissDeviceBoundDialog() },
                            colors         = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                            shape          = RoundedCornerShape(10.dp),
                            modifier       = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                text       = "ঠিক আছে",
                                color      = Color.White,
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = adaptivePadding(16.dp, 24.dp), vertical = adaptivePadding(12.dp, 16.dp)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            // 1. Maintenance Banner
            if (uiState.isMaintenanceMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusOrange),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Maintenance Warning",
                            tint = Color.White
                        )
                        Text(
                            text = "সিস্টেম রক্ষণাবেক্ষণ চলছে। কিছু সার্ভিস সাময়িক ডাউন থাকতে পারে।",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Logo Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(RoyalIndigo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = "Wallet Logo",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Payment Checker",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "SMS পেমেন্ট ট্র্যাকার",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "পার্সোনাল এবং বিজনেস সলিউশন",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Error Message display is removed from layout Column to prevent displacement

            // Contact Input Box
            OutlinedTextField(
                value = uiState.contact,
                onValueChange = { viewModel.onContactChanged(it) },
                placeholder = { Text(
                    text = "মোবাইল নাম্বার অথবা জিমেইল এড্রেসটি লিখুন।",
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                ) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Contact Icon",
                        tint = RoyalIndigo
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = if (uiState.isOtpSent) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.checkContactAndRequestOtp(context)
                    }
                ),
                singleLine = true,
                readOnly = uiState.isOtpSent,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // OTP Fields Section (Dynamic Animation)
            AnimatedVisibility(
                visible = uiState.isOtpSent || isBypass,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedVisibility(
                        visible = !isBypass,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = "৬ ডিজিটের ওটিপি (OTP) পাঠানো হয়েছে।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isBypass) {
                        OutlinedTextField(
                            value = uiState.otpCode,
                            onValueChange = { viewModel.onOtpChanged(it) },
                            placeholder = { Text("এডমিন পাসওয়ার্ড লিখুন") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    val duration = if (adminBypassOpenedAt != null) {
                                        (System.currentTimeMillis() - adminBypassOpenedAt!!) / 1000
                                    } else {
                                        null
                                    }
                                    viewModel.verifyOtp(context, duration) { res ->
                                        verificationResult = res
                                    }
                                }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                        val coroutineScope = rememberCoroutineScope()
                        // Hidden BasicTextField state (declared outside / above the Box modifier so it is in scope)
                        var otpValueState by remember {
                            val padded = uiState.otpCode.padEnd(6, ' ')
                            val firstEmpty = uiState.otpCode.indexOf(' ')
                            val selIndex = if (firstEmpty != -1) firstEmpty else minOf(uiState.otpCode.length, 5)
                            mutableStateOf(
                                TextFieldValue(
                                    text = padded,
                                    selection = TextRange(selIndex, selIndex + 1)
                                )
                            )
                        }
                        LaunchedEffect(uiState.otpCode) {
                            val padded = uiState.otpCode.padEnd(6, ' ')
                            if (padded != otpValueState.text) {
                                val firstEmpty = uiState.otpCode.indexOf(' ')
                                val selIndex = if (firstEmpty != -1) firstEmpty else minOf(uiState.otpCode.length, 5)
                                otpValueState = TextFieldValue(
                                    text = padded,
                                    selection = TextRange(selIndex, selIndex + 1)
                                )
                            }
                        }

                        // Custom 6-digit OTP input boxes (centered text, auto-paste, backspace traversal built-in via hidden field)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = otpInteractionSource,
                                    indication = null
                                ) {
                                    coroutineScope.launch {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                    val firstEmpty = uiState.otpCode.indexOf(' ')
                                    val selIndex = if (firstEmpty != -1) firstEmpty else minOf(uiState.otpCode.length, 5)
                                    otpValueState = otpValueState.copy(
                                        selection = TextRange(selIndex, selIndex + 1)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Visual OTP Boxes
                            val otpBoxWidth = screenWidth() / 7.5f
                            val otpBoxHeight = otpBoxWidth * 1.1f
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(adaptivePadding(4.dp, 6.dp), Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (i in 0 until 6) {
                                    val char = uiState.otpCode.getOrNull(i)?.toString() ?: " "
                                    val isFocused = (otpValueState.selection.start == i) || (i == 5 && otpValueState.selection.start == 6)

                                    Box(
                                        modifier = Modifier
                                            .size(width = otpBoxWidth, height = otpBoxHeight)
                                            .background(
                                                color = if (char.isNotBlank()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = if (isFocused) 2.dp else 1.dp,
                                                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                coroutineScope.launch {
                                                    focusRequester.requestFocus()
                                                    keyboardController?.show()
                                                }
                                                otpValueState = otpValueState.copy(
                                                    selection = TextRange(i, i + 1)
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = char,
                                                fontSize = adaptiveTextSize(16.sp, 20.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.Center
                                            )
                                            if (isFocused && (otpValueState.selection.start == i || (i == 5 && otpValueState.selection.start == 6)) && char.isNotBlank()) {
                                                BlinkingCursor(color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        if (isFocused && char.isBlank()) {
                                            BlinkingCursor(color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }

                            val emptyTextToolbar = object : androidx.compose.ui.platform.TextToolbar {
                                override fun showMenu(
                                    rect: androidx.compose.ui.geometry.Rect,
                                    onCopy: (() -> Unit)?,
                                    onPaste: (() -> Unit)?,
                                    onCut: (() -> Unit)?,
                                    onSelectAll: (() -> Unit)?
                                ) {}
                                override fun hide() {}
                                override val status: androidx.compose.ui.platform.TextToolbarStatus = androidx.compose.ui.platform.TextToolbarStatus.Hidden
                            }

                            CompositionLocalProvider(androidx.compose.ui.platform.LocalTextToolbar provides emptyTextToolbar) {
                                BasicTextField(
                                    value = otpValueState,
                                    onValueChange = { newValue ->
                                        val oldText = otpValueState.text
                                        val newText = newValue.text
                                        val oldSelection = otpValueState.selection

                                        val (sanitized, targetSelection) = if (newText.length < oldText.length) {
                                            val i = oldSelection.start
                                            val isBoxEmpty = oldSelection.collapsed || i >= oldText.length || oldText[i] == ' '

                                            if (!isBoxEmpty) {
                                                val sb = StringBuilder(oldText)
                                                if (i >= 0 && i < oldText.length) {
                                                    sb.setCharAt(i, ' ')
                                                }
                                                val updatedText = sb.toString()
                                                val sel = TextRange(i, i + 1)
                                                Pair(updatedText, sel)
                                            } else {
                                                val deleteIndex = i - 1
                                                val sb = StringBuilder(oldText)
                                                if (deleteIndex >= 0 && deleteIndex < oldText.length) {
                                                    sb.setCharAt(deleteIndex, ' ')
                                                }
                                                val updatedText = sb.toString()
                                                val newCursor = maxOf(0, deleteIndex)
                                                val sel = TextRange(newCursor, newCursor + 1)
                                                Pair(updatedText, sel)
                                            }
                                        } else if (newText != oldText) {
                                            val insertedLength = newText.length - oldText.length + (oldSelection.end - oldSelection.start)
                                            if (insertedLength > 0 && oldSelection.start < 6) {
                                                val insertedText = newText.substring(oldSelection.start, minOf(oldSelection.start + insertedLength, newText.length))
                                                val digitsOnly = insertedText.filter { it.isDigit() }
                                                if (digitsOnly.isNotEmpty()) {
                                                    val sb = StringBuilder(oldText)
                                                    for (idx in 0 until digitsOnly.length) {
                                                        val targetIdx = oldSelection.start + idx
                                                        if (targetIdx < 6) {
                                                            sb.setCharAt(targetIdx, digitsOnly[idx])
                                                        }
                                                    }
                                                    val updatedText = sb.toString()
                                                    val nextIndex = oldSelection.start + digitsOnly.length
                                                    val sel = if (nextIndex < 6) {
                                                        TextRange(nextIndex, nextIndex + 1)
                                                    } else {
                                                        TextRange(5, 6)
                                                    }
                                                    Pair(updatedText, sel)
                                                } else {
                                                    Pair(oldText, oldSelection)
                                                }
                                            } else {
                                                Pair(oldText, oldSelection)
                                            }
                                        } else {
                                            Pair(oldText, oldSelection)
                                        }

                                        if (sanitized != uiState.otpCode) {
                                            viewModel.onOtpChanged(sanitized)
                                        }
                                        otpValueState = TextFieldValue(
                                            text = sanitized,
                                            selection = targetSelection
                                        )
                                        if (newText.length < oldText.length) {
                                            coroutineScope.launch {
                                                focusRequester.requestFocus()
                                                keyboardController?.show()
                                            }
                                        }
                                        if (sanitized.all { it.isDigit() } && sanitized.length == 6) {
                                            focusManager.clearFocus()
                                            viewModel.verifyOtp(context) { res ->
                                                verificationResult = res
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            focusManager.clearFocus()
                                            viewModel.verifyOtp(context) { res ->
                                                verificationResult = res
                                            }
                                        }
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.Transparent,
                                        fontSize = 1.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    cursorBrush = SolidColor(Color.Transparent),
                                    modifier = Modifier
                                        .size(1.dp)
                                        .alpha(0f)
                                        .focusRequester(focusRequester)
                                )
                            }
                        }
                    }

                    // Timer & Resend Row
                    AnimatedVisibility(
                        visible = !isBypass,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.timerSeconds > 0) {
                                Text(
                                    text = "${uiState.timerSeconds} সেকেন্ড পর আবার পাঠান",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                TextButton(
                                    onClick = { viewModel.resendOtp(context) },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = "কোড আবার পাঠান",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoyalIndigo
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Action Buttons
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = RoyalIndigo,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (!uiState.isOtpSent && !isBypass) {
                            viewModel.checkContactAndRequestOtp(context)
                        } else {
                            val duration = if (isBypass && adminBypassOpenedAt != null) {
                                (System.currentTimeMillis() - adminBypassOpenedAt!!) / 1000
                            } else {
                                null
                            }
                            viewModel.verifyOtp(context, duration) { res ->
                                verificationResult = res
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isTrialBlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = if (uiState.isOtpSent || isBypass) "লগইন করুন" else "যাচাই করুন",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 4. Social / Support divider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = "আমাদের সাথে থাকুন",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // WhatsApp — only shown when link is configured
                val waLink = uiState.whatsappSupportLink
                if (waLink.isNotBlank()) {
                    SocialItem(
                        name = "WhatsApp",
                        iconColor = Color(0xFF25D366),
                        iconBg = Color(0xFFE8F9EE),
                        icon = ImageVector.vectorResource(id = R.drawable.ic_whatsapp),
                        onClick = {
                            val rawLink = waLink.trim()
                            val finalUrl = when {
                                rawLink.startsWith("http://") || rawLink.startsWith("https://") -> rawLink
                                rawLink.all { it.isDigit() || it == '+' || it == ' ' || it == '-' } -> {
                                    val cleanNumber = rawLink.filter { it.isDigit() }
                                    "https://wa.me/$cleanNumber"
                                }
                                else -> "https://wa.me/$rawLink"
                            }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                            context.startActivity(intent)
                        }
                    )
                }

                // Facebook
                val fbLink = uiState.facebookSupportLink
                if (fbLink.isNotBlank()) {
                    SocialItem(
                        name = "Facebook",
                        iconColor = Color(0xFF1877F2),
                        iconBg = Color(0xFFE8F1FF),
                        icon = Icons.Default.Person,
                        onClick = {
                            val rawLink = fbLink.trim()
                            val finalUrl = if (rawLink.startsWith("http://") || rawLink.startsWith("https://")) rawLink else "https://$rawLink"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                            context.startActivity(intent)
                        }
                    )
                }

                // Telegram
                val tgLink = uiState.telegramSupportLink
                if (tgLink.isNotBlank()) {
                    SocialItem(
                        name = "Telegram",
                        iconColor = Color(0xFF24A1DE),
                        iconBg = Color(0xFFE5F6FD),
                        icon = Icons.AutoMirrored.Filled.Send,
                        onClick = {
                            val rawLink = tgLink.trim()
                            val finalUrl = if (rawLink.startsWith("http://") || rawLink.startsWith("https://")) rawLink else "https://$rawLink"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                            context.startActivity(intent)
                        }
                    )
                }

                // YouTube
                val ytLink = uiState.youtubeSupportLink
                if (ytLink.isNotBlank()) {
                    SocialItem(
                        name = "YouTube",
                        iconColor = Color(0xFFFF0000),
                        iconBg = Color(0xFFFFEBEE),
                        icon = Icons.Default.PlayArrow,
                        onClick = {
                            val rawLink = ytLink.trim()
                            val finalUrl = if (rawLink.startsWith("http://") || rawLink.startsWith("https://")) rawLink else "https://$rawLink"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        // Floating Error Message overlay (Top-overlay banner)
        AnimatedVisibility(
            visible = uiState.errorMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .zIndex(99f)
        ) {
            uiState.errorMessage?.let { error ->
                FloatingErrorBanner(message = error)
            }
        }
    }
}

@Composable
fun SocialItem(
    name: String,
    iconColor: Color,
    iconBg: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(adaptivePadding(48.dp, 56.dp))
                .clip(CircleShape)
                .background(iconBg)
                .border(
                    width = 1.dp,
                    color = iconColor.copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = name,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PremiumRegisterDialog(
    onDismiss: () -> Unit,
    onRegisterClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
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
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "অ্যাকাউন্ট পাওয়া যায়নি",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "এই জিমেইল/নম্বরটি আমাদের সিস্টেমে নিবন্ধিত নেই। আপনি কি একটি নতুন অ্যাকাউন্ট তৈরি করতে চান?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 28.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC2C7CE)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF616161)),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            text = "বাতিল করুন",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = onRegisterClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5)),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            text = "নতুন অ্যাকাউন্ট",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
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
private fun FloatingErrorBanner(
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
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = StatusRed,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                color = StatusRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

