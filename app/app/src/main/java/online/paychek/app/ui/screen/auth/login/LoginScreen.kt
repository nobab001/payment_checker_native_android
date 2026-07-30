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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.ui.screen.maintenance.MaintenanceScreen
import online.paychek.app.ui.theme.*
import online.paychek.app.utils.adaptivePadding
import online.paychek.app.utils.adaptiveTextSize
import online.paychek.app.utils.screenWidth
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.zIndex
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch

// Screenshot audit palette — 95–98% match target
private val LoginPrimary = Color(0xFF2D4CFF)
private val LoginPrimaryDeep = Color(0xFF1A3DE0)
private val LoginLogoBlue = Color(0xFF1E3A8A)
private val LoginBadgeText = Color(0xFF1D4ED8)
private val LoginBadgeBg = Color(0xFFEEF4FF)
private val LoginBgLight = Color(0xFFF8FAFF)
private val LoginBgLightEnd = Color(0xFFF0F4FF)
private val LoginBgTopDark = Color(0xFF0F172A)
private val LoginBgBottomDark = Color(0xFF1E295B)
private val LoginTextPrimary = Color(0xFF1A1C1E)
private val LoginTextSecondary = Color(0xFF74777F)
private val LoginSurfaceDark = Color(0xFF1A2336)
private val LoginShadowCard = Color(0x280F172A)
private val SocialWhatsApp = Color(0xFF25D366)
private val SocialFacebook = Color(0xFF1877F2)
private val SocialTelegram = Color(0xFF2CA5E0)
private val SocialYouTube = Color(0xFFFF0000)

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
    val coroutineScope = rememberCoroutineScope()
    var isButtonClickable by remember { mutableStateOf(true) }
    var lastClickTime by remember { mutableStateOf(0L) }

    // Observe OTP verification and trigger navigation
    val focusRequester = remember { FocusRequester() }
    val otpInteractionSource = remember { MutableInteractionSource() }
    var verificationResult by remember { mutableStateOf<online.paychek.app.data.remote.dto.VerifyOtpResponse?>(null) }

    val isBypass = uiState.contact == uiState.adminSecretUsername
    var adminBypassOpenedAt by remember { mutableStateOf<Long?>(null) }

    if (uiState.isMaintenanceMode && !isBypass) {
        MaintenanceScreen(modifier = modifier.fillMaxSize())
        return
    }

    // Subtle enter animations only (screenshot reference)
    var logoVisible by remember { mutableStateOf(false) }
    var cardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        logoVisible = true
        kotlinx.coroutines.delay(120)
        cardVisible = true
    }

    val isDarkBg = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val screenW = screenWidth()
    val contentMaxWidth = 560.dp
    // Wider card for long Gmail addresses
    val horizontalPad = 10.dp
    val titleColor = if (isDarkBg) Color.White else LoginTextPrimary
    val subtitleColor = if (isDarkBg) Color(0xFFB0B8C4) else LoginTextSecondary
    val cardSurface = if (isDarkBg) LoginSurfaceDark else Color.White
    val socialCircleSize = when {
        screenW.value < 360f -> 52.dp
        screenW.value < 400f -> 56.dp
        else -> 60.dp
    }
    val socialIconSize = when {
        screenW.value < 360f -> 24.dp
        else -> 26.dp
    }

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
            // Persist display name + server avatar to plain prefs so Home/Profile
            // can show them immediately after a fresh login (before opening Profile).
            context.getSharedPreferences(
                online.paychek.app.config.AppConfig.PREF_NAME,
                android.content.Context.MODE_PRIVATE
            ).edit().apply {
                if (res.user.name.isNotBlank()) putString("pcu_user_name", res.user.name)
                res.user.avatar?.takeIf { it.isNotBlank() }?.let { putString("pcu_server_avatar", it) }
                // Server-backed "device was set up before" flag (survives reinstall).
                // Only upgrade to true so a stale false can't wipe a local completion.
                if (res.device.setupCompleted) putBoolean("pcu_setup_completed", true)
                apply()
            }
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
            online.paychek.app.utils.SecurePreferences.encrypt(
                context,
                online.paychek.app.config.AppConfig.KEY_IS_OWNER_DEVICE,
                if (res.device.deviceRole == "owner") "true" else "false"
            )
            if (!res.device.deviceSpecificPin.isNullOrEmpty()) {
                online.paychek.app.utils.SecurePreferences.encrypt(
                    context,
                    online.paychek.app.config.AppConfig.KEY_DEVICE_SPECIFIC_PIN,
                    res.device.deviceSpecificPin
                )
            } else {
                online.paychek.app.utils.SecurePreferences.remove(
                    context,
                    online.paychek.app.config.AppConfig.KEY_DEVICE_SPECIFIC_PIN
                )
            }
            if (!res.secretKey.isNullOrBlank()) {
                online.paychek.app.utils.SecurePreferences.encrypt(
                    context,
                    online.paychek.app.services.sms.SmsReceiver.KEY_HMAC_SECRET,
                    res.secretKey
                )
            }
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


    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val topPad = if (maxHeight < 640.dp) 40.dp else 64.dp
        val logoSize = if (maxWidth < 360.dp) 68.dp else 76.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isDarkBg) {
                            listOf(LoginBgTopDark, LoginBgBottomDark)
                        } else {
                            listOf(LoginBgLight, LoginBgLightEnd)
                        }
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(x = (-70).dp, y = (-40).dp)
                .align(Alignment.TopStart)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDarkBg) LoginPrimary.copy(alpha = 0.28f) else Color(0xFFB8C9FF).copy(alpha = 0.60f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        // Soft dotted pattern — top-left (audit #6)
        Canvas(
            modifier = Modifier
                .size(180.dp)
                .offset(x = (-20).dp, y = 8.dp)
                .align(Alignment.TopStart)
                .alpha(if (isDarkBg) 0.25f else 0.55f)
        ) {
            val step = 14.dp.toPx()
            val radius = 1.6.dp.toPx()
            val dotColor = if (isDarkBg) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.85f)
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = 40.dp, y = (-20).dp)
                .align(Alignment.TopEnd)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDarkBg) Color.White.copy(alpha = 0.08f) else Color(0xFFC5CDD8).copy(alpha = 0.40f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        // Soft dotted pattern — top-right (audit #6)
        Canvas(
            modifier = Modifier
                .size(150.dp)
                .offset(x = 12.dp, y = 20.dp)
                .align(Alignment.TopEnd)
                .alpha(if (isDarkBg) 0.20f else 0.45f)
        ) {
            val step = 14.dp.toPx()
            val radius = 1.4.dp.toPx()
            val dotColor = if (isDarkBg) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.80f)
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = horizontalPad)
                .padding(top = topPad, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isMaintenanceMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusOrange),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = contentMaxWidth)
                        .padding(bottom = 16.dp),
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

            val logoAlpha by animateFloatAsState(
                targetValue = if (logoVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                label = "LogoFade"
            )
            Box(
                modifier = Modifier
                    .graphicsLayer(alpha = logoAlpha)
                    // Layout = logo only; glow overflows so no huge gap under logo
                    .size(logoSize),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow (does not inflate layout height)
                Box(
                    modifier = Modifier
                        .requiredSize(logoSize + 40.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    LoginPrimary.copy(alpha = if (isDarkBg) 0.50f else 0.40f),
                                    LoginPrimary.copy(alpha = 0.16f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .requiredSize(logoSize + 22.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    LoginPrimary.copy(alpha = if (isDarkBg) 0.32f else 0.24f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(logoSize)
                        .shadow(
                            elevation = 18.dp,
                            shape = RoundedCornerShape(22.dp),
                            clip = false,
                            ambientColor = LoginPrimary.copy(alpha = 0.45f),
                            spotColor = LoginPrimary.copy(alpha = 0.55f)
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.logo_app),
                        contentDescription = "Paychek Logo",
                        modifier = Modifier.size(logoSize * 0.58f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Payment Checker",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer(alpha = logoAlpha)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "SMS Payment Verification System",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = subtitleColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer(alpha = logoAlpha)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Badge — subtle #EEF4FF (~7% blue wash), text #1D4ED8 (audit #3)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isDarkBg) {
                    LoginBadgeText.copy(alpha = 0.08f)
                } else {
                    LoginBadgeBg.copy(alpha = 0.55f)
                },
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = LoginBadgeText.copy(alpha = if (isDarkBg) 0.35f else 0.22f)
                ),
                shadowElevation = 0.dp,
                modifier = Modifier.graphicsLayer(alpha = logoAlpha)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = LoginBadgeText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Secure • Fast • Reliable",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LoginBadgeText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(
                visible = cardVisible,
                enter = fadeIn(animationSpec = tween(380)) +
                    slideInVertically(animationSpec = tween(380), initialOffsetY = { it / 5 }),
                label = "CardFadeUp"
            ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(32.dp),
                        clip = false,
                        ambientColor = LoginShadowCard,
                        spotColor = LoginShadowCard
                    ),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = cardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val contact = uiState.contact.trim()
                        val isValidEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(contact).matches() &&
                            contact.substringAfterLast('.', "").length >= 2
                        val isValidPhone = contact.length == 11 && contact.all { it.isDigit() } && contact.startsWith("01")
                        val fieldFocused = remember { MutableInteractionSource() }
                        val isFieldFocused by fieldFocused.collectIsFocusedAsState()

                        BasicTextField(
                            value = uiState.contact,
                            onValueChange = { newValue ->
                                val filtered = newValue.replace(Regex("^\\+?88"), "").replace(" ", "").replace("-", "")
                                viewModel.onContactChanged(filtered)
                            },
                            singleLine = true,
                            readOnly = uiState.isOtpSent,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Normal,
                                color = titleColor
                            ),
                            cursorBrush = SolidColor(LoginPrimary),
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
                            interactionSource = fieldFocused,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(
                                            width = if (isFieldFocused) 1.5.dp else 1.dp,
                                            color = when {
                                                isFieldFocused -> LoginPrimary.copy(alpha = 0.55f)
                                                isDarkBg -> Color.White.copy(alpha = 0.08f)
                                                else -> Color(0xFFD0D5DD).copy(alpha = 0.55f)
                                            },
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(cardSurface)
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Contact Icon",
                                        tint = LoginLogoBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (uiState.contact.isEmpty()) {
                                            Text(
                                                text = "মোবাইল নাম্বার অথবা জিমেইল এড্রেস",
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = subtitleColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (isValidEmail || isValidPhone) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Valid Input",
                                            tint = StatusGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }

                    // OTP Fields Section (Dynamic Animation)
                    AnimatedVisibility(
                        visible = uiState.isOtpSent || isBypass,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AnimatedVisibility(
                                visible = !isBypass,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                              ) {
                                  Text(
                                      text = "৬ ডিজিটের ওটিপি (OTP) পাঠানো হয়েছে।",
                                      fontSize = 12.sp,
                                      fontWeight = FontWeight.Medium,
                                      color = MaterialTheme.colorScheme.onSurfaceVariant
                                  )
                              }

                              if (isBypass) {
                                  OutlinedTextField(
                                      value = uiState.otpCode,
                                      onValueChange = { viewModel.onOtpChanged(it) },
                                      placeholder = { Text("এডমিন পাসওয়ার্ড লিখুন", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                                      shape = RoundedCornerShape(14.dp),
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
                                      val otpBoxSize = 48.dp
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
                                                      .size(otpBoxSize)
                                                      .background(
                                                          color = Color.Transparent,
                                                          shape = RoundedCornerShape(12.dp)
                                                      )
                                                      .border(
                                                          width = if (isFocused) 2.dp else 1.dp,
                                                          color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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
                                  enter = fadeIn() + expandVertically(),
                                  exit = fadeOut() + shrinkVertically()
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
                                              fontWeight = FontWeight.Medium,
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
                                                  color = MaterialTheme.colorScheme.primary
                                              )
                                          }
                                      }
                                  }
                              }
                          }
                      }


                      // Verify button — screenshot: ~58dp, gradient, soft blue shadow, press scale
                      val verifyInteraction = remember { MutableInteractionSource() }
                      val verifyPressed by verifyInteraction.collectIsPressedAsState()
                      val verifyScale by animateFloatAsState(
                          targetValue = if (verifyPressed) 0.97f else 1f,
                          animationSpec = spring(
                              dampingRatio = Spring.DampingRatioMediumBouncy,
                              stiffness = Spring.StiffnessMedium
                          ),
                          label = "VerifyPressScale"
                      )

                      Box(
                          modifier = Modifier
                              .fillMaxWidth()
                              .graphicsLayer(scaleX = verifyScale, scaleY = verifyScale)
                              .height(62.dp)
                              .shadow(
                                  elevation = if (verifyPressed) 4.dp else 12.dp,
                                  shape = RoundedCornerShape(16.dp),
                                  clip = false,
                                  ambientColor = LoginPrimary.copy(alpha = 0.35f),
                                  spotColor = LoginPrimary.copy(alpha = 0.50f)
                              )
                              .clip(RoundedCornerShape(16.dp))
                              .background(
                                  brush = Brush.horizontalGradient(
                                      colors = listOf(LoginPrimary, LoginPrimaryDeep)
                                  )
                              )
                              .clickable(
                                  interactionSource = verifyInteraction,
                                  indication = ripple(color = Color.White.copy(alpha = 0.35f)),
                                  enabled = !uiState.isTrialBlocked && isButtonClickable && !uiState.isLoading
                              ) {
                                  val currentTime = System.currentTimeMillis()
                                  if (isButtonClickable && currentTime - lastClickTime >= 2000L) {
                                      lastClickTime = currentTime
                                      isButtonClickable = false
                                      coroutineScope.launch {
                                          kotlinx.coroutines.delay(2000L)
                                          isButtonClickable = true
                                      }
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
                                  }
                              },
                          contentAlignment = Alignment.Center
                      ) {
                          if (uiState.isLoading) {
                              CircularProgressIndicator(
                                  color = Color.White,
                                  strokeWidth = 2.5.dp,
                                  modifier = Modifier.size(22.dp)
                              )
                          } else {
                              Text(
                                  text = if (uiState.isOtpSent || isBypass) "লগইন করুন" else "যাচাই করুন",
                                  fontSize = 16.sp,
                                  fontWeight = FontWeight.Bold,
                                  color = Color.White
                              )
                          }
                      }
              }
              }
              }

              // Title slightly higher; icons keep previous vertical position
              Spacer(modifier = Modifier.height(28.dp))

              Column(
                  modifier = Modifier
                      .fillMaxWidth()
                      .widthIn(max = contentMaxWidth)
                      .padding(bottom = 8.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(32.dp)
              ) {
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically
                  ) {
                      HorizontalDivider(
                          modifier = Modifier.weight(1f),
                          color = if (isDarkBg) Color.White.copy(alpha = 0.12f) else Color(0xFFD8DCE3)
                      )
                      Text(
                          text = "আমাদের সাথে থাকুন",
                          fontSize = 14.sp,
                          fontWeight = FontWeight.Medium,
                          color = subtitleColor,
                          modifier = Modifier.padding(horizontal = 12.dp)
                      )
                      HorizontalDivider(
                          modifier = Modifier.weight(1f),
                          color = if (isDarkBg) Color.White.copy(alpha = 0.12f) else Color(0xFFD8DCE3)
                      )
                  }

                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                      verticalAlignment = Alignment.CenterVertically
                  ) {
                      val waLink = uiState.whatsappSupportLink
                      if (waLink.isNotBlank()) {
                          SocialItem(
                              name = "WhatsApp",
                              iconColor = SocialWhatsApp,
                              iconBg = SocialWhatsApp.copy(alpha = 0.12f),
                              icon = ImageVector.vectorResource(id = R.drawable.ic_whatsapp),
                              circleSize = socialCircleSize,
                              iconSize = socialIconSize,
                              labelColor = subtitleColor,
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
                                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)))
                              }
                          )
                      }
                      val fbLink = uiState.facebookSupportLink
                      if (fbLink.isNotBlank()) {
                          SocialItem(
                              name = "Facebook",
                              iconColor = SocialFacebook,
                              iconBg = SocialFacebook.copy(alpha = 0.12f),
                              icon = ImageVector.vectorResource(id = R.drawable.ic_facebook),
                              circleSize = socialCircleSize,
                              iconSize = socialIconSize,
                              labelColor = subtitleColor,
                              onClick = {
                                  val rawLink = fbLink.trim()
                                  val finalUrl = if (rawLink.startsWith("http://") || rawLink.startsWith("https://")) rawLink else "https://$rawLink"
                                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)))
                              }
                          )
                      }
                      val tgLink = uiState.telegramSupportLink
                      if (tgLink.isNotBlank()) {
                          SocialItem(
                              name = "Telegram",
                              iconColor = SocialTelegram,
                              iconBg = SocialTelegram.copy(alpha = 0.12f),
                              icon = ImageVector.vectorResource(id = R.drawable.ic_telegram),
                              circleSize = socialCircleSize,
                              iconSize = socialIconSize,
                              labelColor = subtitleColor,
                              onClick = {
                                  val rawLink = tgLink.trim()
                                  val finalUrl = if (rawLink.startsWith("http://") || rawLink.startsWith("https://")) rawLink else "https://$rawLink"
                                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)))
                              }
                          )
                      }
                      val ytLink = uiState.youtubeSupportLink
                      if (ytLink.isNotBlank()) {
                          SocialItem(
                              name = "YouTube",
                              iconColor = SocialYouTube,
                              iconBg = SocialYouTube.copy(alpha = 0.12f),
                              icon = ImageVector.vectorResource(id = R.drawable.ic_youtube),
                              circleSize = socialCircleSize,
                              iconSize = socialIconSize,
                              labelColor = subtitleColor,
                              onClick = {
                                  val rawLink = ytLink.trim()
                                  val finalUrl = if (rawLink.startsWith("http://") || rawLink.startsWith("https://")) rawLink else "https://$rawLink"
                                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)))
                              }
                          )
                      }
                  }
              }
          }

          AnimatedVisibility(
              visible = uiState.errorMessage != null,
              enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
              exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
              modifier = Modifier
                  .align(Alignment.TopCenter)
                  .windowInsetsPadding(WindowInsets.statusBars)
                  .padding(top = 16.dp)
                  .padding(horizontal = horizontalPad)
                  .fillMaxWidth()
                  .widthIn(max = contentMaxWidth)
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
    icon: ImageVector,
    circleSize: androidx.compose.ui.unit.Dp = 60.dp,
    iconSize: androidx.compose.ui.unit.Dp = 26.dp,
    labelColor: Color = LoginTextSecondary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "SocialItemScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(circleSize)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color(0x14000000),
                    spotColor = Color(0x14000000)
                ),
            shape = CircleShape,
            color = iconBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            interactionSource = interactionSource
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = iconColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        Text(
            text = name,
            fontSize = 12.sp,
            color = labelColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

