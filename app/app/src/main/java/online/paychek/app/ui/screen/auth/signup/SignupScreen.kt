package online.paychek.app.ui.screen.auth.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

private val ErrorContainerLight = Color(0xFFFFEBEE)
private val ErrorContainerDark = Color(0xFF3D1F1F)
private val ErrorTextColor = Color(0xFFC62828)
private val SignupIndigo = Color(0xFF1F2A8A)
private val SignupIndigoSoft = Color(0xFF3949AB)
private val VerifiedGreen = Color(0xFF059669)
private val SoftSurface = Color(0xFFF8F9FC)

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
    val isEmailLogin = remember(contact) { contact.contains("@") }

    LaunchedEffect(contact, token) {
        viewModel.initData(contact, token)
    }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0E14)
    val pageBg = if (isDark) MaterialTheme.colorScheme.background else SoftSurface
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBg)
    ) {
        // Soft top wash — matches login premium feel without clutter
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SignupIndigo.copy(alpha = if (isDark) 0.35f else 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header badge
            Box(
                Modifier
                    .size(64.dp)
                    .shadow(8.dp, CircleShape, spotColor = SignupIndigo.copy(0.35f))
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(SignupIndigo, SignupIndigoSoft))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "প্রোফাইল সম্পন্ন করুন",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDark) Color.White else SignupIndigo,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "অ্যাকাউন্ট সক্রিয় করতে কয়েকটি তথ্য দিন",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            // Form card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isDark) 0.dp else 4.dp
                ),
                border = if (isDark) {
                    androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.08f))
                } else null
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.errorMessage?.let { error ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) ErrorContainerDark else ErrorContainerLight)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, null, tint = ErrorTextColor, modifier = Modifier.size(16.dp))
                            Text(
                                error,
                                color = ErrorTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                    }

                    SectionLabel("পরিচয়")

                    CompactField(
                        value = uiState.name,
                        onValueChange = { if (it.length <= 50) viewModel.onNameChanged(it) },
                        label = "পূর্ণ নাম",
                        placeholder = "আপনার পূর্ণ নাম",
                        icon = Icons.Default.Person,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                        modifier = Modifier.semantics { contentType = ContentType.PersonFullName }
                    )

                    // Verified contact — compact chip-style row (no bulky supportingText)
                    VerifiedContactRow(
                        icon = if (isEmailLogin) Icons.Default.Email else Icons.Default.PhoneAndroid,
                        label = if (isEmailLogin) "জিমেইল" else "মোবাইল",
                        value = if (isEmailLogin) (uiState.email ?: contact) else (uiState.phone ?: contact),
                        isDark = isDark
                    )

                    if (isEmailLogin) {
                        CompactField(
                            value = uiState.phone ?: "",
                            onValueChange = { viewModel.onPhoneChanged(it) },
                            label = "মোবাইল নম্বর *",
                            placeholder = "01XXXXXXXXX",
                            icon = Icons.Default.PhoneAndroid,
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next,
                            modifier = Modifier.semantics { contentType = ContentType.PhoneNumber }
                        )
                    } else {
                        CompactField(
                            value = uiState.email ?: "",
                            onValueChange = { viewModel.onEmailChanged(it) },
                            label = "জিমেইল (ঐচ্ছিক)",
                            placeholder = "example@gmail.com",
                            icon = Icons.Default.Email,
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                            modifier = Modifier.semantics { contentType = ContentType.EmailAddress }
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    SectionLabel("নিরাপত্তা")

                    CompactField(
                        value = uiState.pin,
                        onValueChange = { viewModel.onPinChanged(it) },
                        label = "নিরাপত্তা পিন",
                        placeholder = "৪–৬ ডিজিট",
                        icon = Icons.Default.Lock,
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next,
                        isPassword = true,
                        modifier = Modifier
                            .semantics { contentDescription = "Security PIN" }
                            .disableAutofill()
                    )

                    CompactField(
                        value = uiState.confirmPin,
                        onValueChange = { viewModel.onConfirmPinChanged(it) },
                        label = "পিন নিশ্চিত করুন",
                        placeholder = "পিন আবার লিখুন",
                        icon = Icons.Default.Lock,
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                        isPassword = true,
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.submitSignup(context, onSignupComplete)
                        },
                        modifier = Modifier
                            .semantics { contentDescription = "Confirm PIN" }
                            .disableAutofill()
                    )

                    Spacer(Modifier.height(8.dp))

                    if (uiState.isLoading) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SignupIndigo, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.submitSignup(context, onSignupComplete)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SignupIndigo),
                            shape = RoundedCornerShape(14.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                "অ্যাকাউন্ট তৈরি করুন",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 0.dp)
    )
}

@Composable
private fun VerifiedContactRow(
    icon: ImageVector,
    label: String,
    value: String,
    isDark: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDark) VerifiedGreen.copy(alpha = 0.12f)
                else Color(0xFFECFDF5)
            )
            .border(
                1.dp,
                VerifiedGreen.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = VerifiedGreen, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 10.sp, color = VerifiedGreen, fontWeight = FontWeight.SemiBold)
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
        Icon(Icons.Default.CheckCircle, null, tint = VerifiedGreen, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    isPassword: Boolean = false,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        leadingIcon = {
            Icon(icon, null, tint = SignupIndigo, modifier = Modifier.size(20.dp))
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = { onDone?.invoke() }
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = signupFieldColors(),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
    )
}

@Composable
private fun signupFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    focusedBorderColor = SignupIndigo,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLabelColor = SignupIndigo,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)
