package online.paychek.app.ui.screen.auth.login

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Observe OTP verification and trigger navigation
    var verificationResult by remember { mutableStateOf<online.paychek.app.data.remote.dto.VerifyOtpResponse?>(null) }

    LaunchedEffect(verificationResult) {
        verificationResult?.let { res ->
            if (res.user.role == "admin") {
                onNavigateToAdminDashboard(res.token)
            } else if (!res.user.profileComplete) {
                onNavigateToSignup(uiState.contact, res.token)
            } else {
                onNavigateToHome(res.token)
            }
        }
    }

    if (uiState.showRegisterDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRegisterDialog() },
            title = {
                Text(
                    text = "অ্যাকাউন্ট পাওয়া যায়নি",
                    fontWeight = FontWeight.Bold,
                    color = RoyalIndigo,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "আপনার মোবাইল নম্বর বা ইমেইল ঠিকানাটি নিবন্ধিত নেই। আপনি কি নতুন একটি ট্রায়াল অ্যাকাউন্ট তৈরি করতে চান?",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.proceedToRegister(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "নতুন অ্যাকাউন্ট তৈরি করুন",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissRegisterDialog() }
                ) {
                    Text(
                        text = "বাতিল করুন",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

                    OutlinedTextField(
                        value = uiState.otpCode,
                        onValueChange = { viewModel.onOtpChanged(it) },
                        placeholder = { Text("৬-ডিজিটের ওটিপি কোড (OTP)") },
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

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // WhatsApp
                SocialItem(
                    name = "WhatsApp",
                    iconColor = Color(0xFF25D366),
                    iconBg = Color(0xFFE8F9EE),
                    icon = Icons.Default.Support,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/8801700000000"))
                        context.startActivity(intent)
                    }
                )

                // Facebook
                SocialItem(
                    name = "Facebook",
                    iconColor = Color(0xFF1877F2),
                    iconBg = Color(0xFFE8F1FF),
                    icon = Icons.Default.Person,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://facebook.com"))
                        context.startActivity(intent)
                    }
                )

                // Telegram
                SocialItem(
                    name = "Telegram",
                    iconColor = Color(0xFF24A1DE),
                    iconBg = Color(0xFFE5F6FD),
                    icon = Icons.AutoMirrored.Filled.Send,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/paychek_support"))
                        context.startActivity(intent)
                    }
                )

                // YouTube
                SocialItem(
                    name = "YouTube",
                    iconColor = Color(0xFFFF0000),
                    iconBg = Color(0xFFFFEBEE),
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com"))
                        context.startActivity(intent)
                    }
                )
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
