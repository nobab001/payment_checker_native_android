package online.paychek.app.ui.screen.auth.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.ui.theme.*
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.autofill.ContentType
import online.paychek.app.utils.disableAutofill

// ─────────────────────────────────────────────────────────────────────────────
// Fix 1: Hardcoded error colors — always visible on both light and dark mode.
// background = #FFEBEE (light) / #3D1F1F (dark)
// text       = #C62828 (dark red, high contrast on both backgrounds)
// ─────────────────────────────────────────────────────────────────────────────
private val ErrorContainerLight = Color(0xFFFFEBEE)
private val ErrorContainerDark  = Color(0xFF3D1F1F)
private val ErrorTextColor      = Color(0xFFC62828) // গাঢ় লাল — visible on both backgrounds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    contact: String,
    token: String,
    onSignupComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignupViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // Fix 4: Detect login type from contact passed into this screen
    // contact is the verified identifier from OTP — email or phone
    val isEmailLogin = remember(contact) { contact.contains("@") }

    // Initialize verified credentials from login/OTP
    LaunchedEffect(contact, token) {
        viewModel.initData(contact, token)
    }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0E14)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "প্রোফাইল সম্পন্ন করুন",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "আপনার পেমেন্ট চেকার অ্যাকাউন্টটি সক্রিয় করতে নিচের তথ্যগুলো পূরণ করুন।",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Fix 1: Error Message — always readable ────────────────────
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) ErrorContainerDark else ErrorContainerLight
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector        = Icons.Default.Lock,
                            contentDescription = null,
                            tint               = ErrorTextColor,
                            modifier           = Modifier.size(18.dp)
                        )
                        Text(
                            text       = error,
                            color      = ErrorTextColor,  // #C62828 — গাঢ় লাল, সর্বদা দৃশ্যমান
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 18.sp,
                            modifier   = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Field 1: Full Name — Fix 2: maxLength=50, Bangla placeholder ──
            OutlinedTextField(
                value       = uiState.name,
                onValueChange = { if (it.length <= 50) viewModel.onNameChanged(it) },
                label       = { Text("পূর্ণ নাম") },
                placeholder = { Text("আপনার পূর্ণ নাম") },  // Fix 2: Bangla placeholder
                leadingIcon = {
                    Icon(
                        imageVector    = Icons.Default.Person,
                        contentDescription = "Name Icon",
                        tint           = RoyalIndigo
                    )
                },
                supportingText = {
                    // Show character count when user is typing (> 30 chars)
                    if (uiState.name.length > 30) {
                        Text(
                            text  = "${uiState.name.length}/50",
                            color = if (uiState.name.length >= 50) ErrorTextColor
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction    = ImeAction.Next
                ),
                singleLine = true,
                shape      = RoundedCornerShape(12.dp),
                colors     = signupFieldColors(),
                modifier   = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.PersonFullName }
            )

            // ── Verified contact from login (read-only) ────────────────────
            if (isEmailLogin) {
                OutlinedTextField(
                    value = uiState.email ?: contact,
                    onValueChange = {},
                    label = { Text("জিমেইল এড্রেস") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    },
                    supportingText = {
                        Text(
                            text = "লগইন থেকে ভেরিফাইড — পরিবর্তন করা যাবে না",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    readOnly = true,
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    colors = signupFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = uiState.phone ?: contact,
                    onValueChange = {},
                    label = { Text("মোবাইল নম্বর") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    },
                    supportingText = {
                        Text(
                            text = "লগইন থেকে ভেরিফাইড — পরিবর্তন করা যাবে না",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    readOnly = true,
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    colors = signupFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Complementary field — other contact type ──────────────────
            // Email login → need phone (mandatory)
            // Phone login → email optional
            if (isEmailLogin) {
                OutlinedTextField(
                    value       = uiState.phone ?: "",
                    onValueChange = { viewModel.onPhoneChanged(it) },
                    label       = { Text("মোবাইল নম্বর") },
                    placeholder = { Text("01XXXXXXXXX") },
                    leadingIcon = {
                        Icon(
                            imageVector    = Icons.Default.PhoneAndroid,
                            contentDescription = "Phone Icon",
                            tint           = RoyalIndigo
                        )
                    },
                    supportingText = {
                        Text(
                            text     = "বাধ্যতামূলক — Gmail দিয়ে অ্যাকাউন্ট খোলার জন্য",
                            fontSize = 11.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction    = ImeAction.Next
                    ),
                    singleLine = true,
                    shape      = RoundedCornerShape(12.dp),
                    colors     = signupFieldColors(),
                    modifier   = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.PhoneNumber }
                )
            } else {
                OutlinedTextField(
                    value       = uiState.email ?: "",
                    onValueChange = { viewModel.onEmailChanged(it) },
                    label       = { Text("জিমেইল এড্রেস") },
                    placeholder = { Text("example@gmail.com") },
                    leadingIcon = {
                        Icon(
                            imageVector    = Icons.Default.Email,
                            contentDescription = "Email Icon",
                            tint           = RoyalIndigo
                        )
                    },
                    supportingText = {
                        Text(
                            text     = "ঐচ্ছিক — পরেও যোগ করা যাবে",
                            fontSize = 11.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction    = ImeAction.Next
                    ),
                    singleLine = true,
                    shape      = RoundedCornerShape(12.dp),
                    colors     = signupFieldColors(),
                    modifier   = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.EmailAddress }
                )
            }

            // ── Field: PIN (4–6 digits) ───────────────────────────────────
            OutlinedTextField(
                value       = uiState.pin,
                onValueChange = { viewModel.onPinChanged(it) },
                label       = { Text("নিরাপত্তা পিন") },
                placeholder = { Text("৪-৬ ডিজিটের নিরাপত্তা পিন (PIN)") },
                leadingIcon = {
                    Icon(
                        imageVector    = Icons.Default.Lock,
                        contentDescription = "PIN Icon",
                        tint           = RoyalIndigo
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction    = ImeAction.Next
                ),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape      = RoundedCornerShape(12.dp),
                colors     = signupFieldColors(),
                modifier   = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Security PIN" }
                    .disableAutofill()
            )

            // ── Field: Confirm PIN ────────────────────────────────────────
            OutlinedTextField(
                value       = uiState.confirmPin,
                onValueChange = { viewModel.onConfirmPinChanged(it) },
                label       = { Text("পিন নিশ্চিত করুন") },
                placeholder = { Text("নিরাপত্তা পিনটি পুনরায় লিখুন") },
                leadingIcon = {
                    Icon(
                        imageVector    = Icons.Default.Lock,
                        contentDescription = "Confirm PIN Icon",
                        tint           = RoyalIndigo
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.submitSignup(context, onSignupComplete)
                    }
                ),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape      = RoundedCornerShape(12.dp),
                colors     = signupFieldColors(),
                modifier   = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Confirm PIN" }
                    .disableAutofill()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Submit Button ─────────────────────────────────────────────
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color    = RoyalIndigo,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submitSignup(context, onSignupComplete)
                    },
                    colors  = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                    shape   = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text       = "অ্যাকাউন্ট তৈরি করুন",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared field colors — extracted to avoid repetition
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun signupFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor      = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor    = MaterialTheme.colorScheme.surface,
    focusedBorderColor         = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor       = MaterialTheme.colorScheme.outlineVariant,
    focusedTextColor           = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor         = MaterialTheme.colorScheme.onSurface,
    focusedPlaceholderColor    = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor  = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLabelColor          = RoyalIndigo,
    unfocusedLabelColor        = MaterialTheme.colorScheme.onSurfaceVariant
)
