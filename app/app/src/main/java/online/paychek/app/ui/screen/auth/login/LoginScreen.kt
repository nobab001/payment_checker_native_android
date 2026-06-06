package online.paychek.app.ui.screen.auth.login

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Send
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
            if (!res.user.profileComplete) {
                onNavigateToSignup(uiState.contact, res.token)
            } else {
                onNavigateToHome(res.token)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp),
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
                    shape = RoundedCornerShape(8.dp)
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

            // 2. Logo Header
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(RoyalIndigo),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = "Wallet Logo",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = "Paychek.online",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RoyalIndigo,
                textAlign = TextAlign.Center
            )

            Text(
                text = "স্বয়ংক্রিয় পেমেন্ট নোটিফিকেশন ট্র্যাকার গেটওয়ে অ্যাপ",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Login Card Container
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (uiState.isOtpSent) "ওটিপি যাচাই করুন" else "লগইন / রেজিস্ট্রেশন",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Error Message display
                    uiState.errorMessage?.let { error ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error,
                                color = StatusRed,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Contact Input Box
                    OutlinedTextField(
                        value = uiState.contact,
                        onValueChange = { viewModel.onContactChanged(it) },
                        label = { Text("মোবাইল নম্বর অথবা ইমেইল") },
                        placeholder = { Text("যেমন: 017xxxxxxxx অথবা user@gmail.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoyalIndigo,
                            focusedLabelColor = RoyalIndigo
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
                                label = { Text("৬-ডিজিটের ওটিপি কোড (OTP)") },
                                placeholder = { Text("xxxxxx") },
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
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RoyalIndigo,
                                    focusedLabelColor = RoyalIndigo
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

                    // Main Action Buttons
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color = RoyalIndigo,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(8.dp)
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
                            shape = RoundedCornerShape(8.dp),
                            enabled = !uiState.isTrialBlocked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = if (uiState.isOtpSent) "লগইন করুন" else "যাচাই করুন",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Social / Support buttons
            Text(
                text = "কোনো সমস্যা হচ্ছে? সরাসরি সাপোর্ট নিন:",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Telegram
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/paychek_support"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24A1DE)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Telegram Support",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Telegram", fontSize = 12.sp, color = Color.White)
                }

                // WhatsApp
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/8801700000000"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Support,
                        contentDescription = "WhatsApp Support",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}
