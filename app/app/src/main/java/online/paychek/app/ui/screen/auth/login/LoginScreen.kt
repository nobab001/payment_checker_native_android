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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.ui.theme.*

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
    var verificationResult by remember { mutableStateOf<online.paychek.app.data.remote.dto.VerifyOtpResponse?>(null) }

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

    // ── "ডিভাইস লিংক নোটিশ" প্রিমিয়াম কাস্টম ডায়ালগ ─────────────
    if (uiState.showDeviceBoundDialog) {
        Dialog(
            onDismissRequest = { viewModel.dismissDeviceBoundDialog() }
        ) {
            Surface(
                shape    = RoundedCornerShape(20.dp),
                color    = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .width(300.dp)
                    .wrapContentHeight()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {

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
                            color      = Color(0xFF475569),
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
                                        color = Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text       = "লিংকড অ্যাকাউন্ট:",
                                    fontWeight = FontWeight.SemiBold,
                                    color      = Color(0xFF334155),
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
                                            color      = Color(0xFF1E293B),
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
                                            color      = Color(0xFF1E293B),
                                            fontSize   = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text       = "নতুন করে অন্য কোনো অ্যাকাউন্ট এই ডিভাইসে যুক্ত করা সম্ভব নয়। অ্যাপের সেবা উপভোগ করতে দয়া করে আপনার ওপরের লিংক করা অ্যাকাউন্টটি ব্যবহার করুন।",
                            color      = Color(0xFF64748B),
                            fontSize   = 12.sp,
                            lineHeight = 18.sp
                        )

                        // ── বাটন রো (ঠিক আছে) ───────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick        = { viewModel.dismissDeviceBoundDialog() },
                                colors         = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                                shape          = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
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
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(50.dp))
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

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Logo Header
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(RoyalIndigo),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = "Wallet Logo",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Payment Checker",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RoyalIndigo,
                textAlign = TextAlign.Center
            )

            Text(
                text = "SMS পেমেন্ট ট্র্যাকার",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Error Message display
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = StatusRed,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Contact Input Box
            OutlinedTextField(
                value = uiState.contact,
                onValueChange = { viewModel.onContactChanged(it) },
                placeholder = { Text("মোবাইল নম্বর অথবা Gmail এড্রেস") },
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
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFFBFBFC),
                    focusedBorderColor = RoyalIndigo,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedPlaceholderColor = Color(0xFF94A3B8),
                    unfocusedPlaceholderColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // OTP Fields Section (Dynamic Animation)
            AnimatedVisibility(
                visible = uiState.isOtpSent,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "আমরা আপনার দেওয়া ঠিকানায় ৬ ডিজিটের ওটিপি পাঠিয়েছি।",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    val isBypass = uiState.contact == "admin"
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
                                    viewModel.verifyOtp(context) { res ->
                                        verificationResult = res
                                    }
                                }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFFBFBFC),
                                focusedBorderColor = RoyalIndigo,
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Custom 6-digit OTP input boxes (centered text, auto-paste, backspace traversal built-in via hidden field)
                        val clipboardManager = LocalClipboardManager.current
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Hidden BasicTextField capturing keyboard & clipboard actions
                            BasicTextField(
                                value = uiState.otpCode,
                                onValueChange = { newValue ->
                                    val sanitized = newValue.filter { it.isDigit() }.take(6)
                                    viewModel.onOtpChanged(sanitized)
                                    if (sanitized.length == 6) {
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
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
                                cursorBrush = SolidColor(Color.Transparent),
                                modifier = Modifier
                                    .matchParentSize()
                                    .alpha(0.01f)
                            )

                            // Visual OTP Boxes
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (i in 0 until 6) {
                                    val char = uiState.otpCode.getOrNull(i)?.toString() ?: ""
                                    val isFocused = uiState.otpCode.length == i || (i == 5 && uiState.otpCode.length == 6)

                                    Box(
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 52.dp)
                                            .background(
                                                color = if (char.isNotEmpty()) Color.White else Color(0xFFFBFBFC),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = if (isFocused) 2.dp else 1.dp,
                                                color = if (isFocused) RoyalIndigo else Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = char,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RoyalIndigo,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Timer & Resend Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.timerSeconds > 0) {
                            Text(
                                text = "${uiState.timerSeconds} সেকেন্ড পর আবার পাঠান",
                                fontSize = 12.sp,
                                color = TextSecondary
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

            Spacer(modifier = Modifier.height(4.dp))

            // Main Action Buttons
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = RoyalIndigo,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (!uiState.isOtpSent) {
                            viewModel.checkContactAndRequestOtp(context)
                        } else {
                            viewModel.verifyOtp(context) { res ->
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
                ) {
                    Text(
                        text = if (uiState.isOtpSent) "লগইন করুন" else "যাচাই করুন",
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
                    color = Color(0xFFE2E8F0)
                )
                Text(
                    text = "আমাদের সাথে থাকুন",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFE2E8F0)
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
                .size(48.dp)
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
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PremiumRegisterDialog(
    onDismiss: () -> Unit,
    onRegisterClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFFAF9F6)) // সফট অফ-হোয়াইট ব্যাকগ্রাউন্ড
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // ১. শিরোনাম (Title) - একদম ছিমছাম ও প্রফেশনাল
                Text(
                    text = "অ্যাকাউন্ট পাওয়া যায়নি",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1C1E),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // ২. মূল লেখা (Body Micro-copy) - টু দ্য পয়েন্ট, কোনো বাড়তি লেখা নেই
                Text(
                    text = "এই জিমেইল/নম্বরটি আমাদের সিস্টেমে নিবন্ধিত নেই। আপনি কি একটি নতুন অ্যাকাউন্ট তৈরি করতে চান?",
                    fontSize = 14.sp,
                    color = Color(0xFF42474E),
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 28.dp)
                )

                // ৩. বাটন লেআউট (দুই কোণায় ছোট আকারে চাপানো রো)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // বাম পাশের বাটন (ধূসর/বর্ডার টেক্সট: বাতিল করুন)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFC2C7CE), RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "বাতিল করুন",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF616161) // দৃষ্টিনন্দন ধূসর শেড
                        )
                    }

                    // ডান পাশের বাটন (ছোট গাঢ় নীল/বেগুনী বাটন: নতুন অ্যাকাউন্ট)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF3F51B5)) // রাজকীয় গাঢ় নীল/বেগুনী কালার টোন
                            .clickable { onRegisterClick() }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "নতুন অ্যাকাউন্ট",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White // হোয়াইট টেক্সট কন্ট্রাস্ট
                        )
                    }
                }
            }
        }
    }
}
