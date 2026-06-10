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
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import online.paychek.app.utils.autofill
import online.paychek.app.utils.disableAutofill

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

    // Initialize verified credentials from login/OTP
    LaunchedEffect(contact, token) {
        viewModel.initData(contact, token)
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
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "প্রোফাইল সম্পন্ন করুন",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RoyalIndigo,
                textAlign = TextAlign.Center
            )

            Text(
                text = "আপনার পেমেন্ট চেকার অ্যাকাউন্টটি সক্রিয় করতে নিচের তথ্যগুলো পূরণ করুন।",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Error Message
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

            // 1. Full Name Field
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.onNameChanged(it) },
                placeholder = { Text("পূর্ণ নাম (Full Name)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Name Icon",
                        tint = RoyalIndigo
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFFBFBFC),
                    focusedBorderColor = RoyalIndigo,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedPlaceholderColor = Color(0xFF94A3B8),
                    unfocusedPlaceholderColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .autofill(
                        autofillTypes = listOf(AutofillType.PersonFullName),
                        onFill = { viewModel.onNameChanged(it) }
                    )
            )

            // 2. Mobile Phone (Read-Only if prefilled)
            OutlinedTextField(
                value = uiState.phone ?: "",
                onValueChange = { viewModel.onPhoneChanged(it) },
                placeholder = { Text("মোবাইল নম্বর") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Phone Icon",
                        tint = if (uiState.isPhonePreFilled) Color.Gray else RoyalIndigo
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                readOnly = uiState.isPhonePreFilled,
                enabled = !uiState.isPhonePreFilled,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFFBFBFC),
                    focusedBorderColor = RoyalIndigo,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedPlaceholderColor = Color(0xFF94A3B8),
                    unfocusedPlaceholderColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .autofill(
                        autofillTypes = listOf(AutofillType.PhoneNumber),
                        onFill = { viewModel.onPhoneChanged(it) }
                    )
            )

            // 3. Email (Read-Only if prefilled)
            OutlinedTextField(
                value = uiState.email ?: "",
                onValueChange = { viewModel.onEmailChanged(it) },
                placeholder = { Text("ইমেইল ঠিকানা") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email Icon",
                        tint = if (uiState.isEmailPreFilled) Color.Gray else RoyalIndigo
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                readOnly = uiState.isEmailPreFilled,
                enabled = !uiState.isEmailPreFilled,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFFBFBFC),
                    focusedBorderColor = RoyalIndigo,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedPlaceholderColor = Color(0xFF94A3B8),
                    unfocusedPlaceholderColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .autofill(
                        autofillTypes = listOf(AutofillType.EmailAddress),
                        onFill = { viewModel.onEmailChanged(it) }
                    )
            )

            // 4. PIN Field (4-6 digits, numeric)
            OutlinedTextField(
                value = uiState.pin,
                onValueChange = { viewModel.onPinChanged(it) },
                placeholder = { Text("৪-৬ ডিজিটের নিরাপত্তা পিন (PIN)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "PIN Icon",
                        tint = RoyalIndigo
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                ),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFFBFBFC),
                    focusedBorderColor = RoyalIndigo,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedPlaceholderColor = Color(0xFF94A3B8),
                    unfocusedPlaceholderColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Security PIN" }
                    .disableAutofill()
            )

            // 5. Confirm PIN Field (6-digits, numeric)
            OutlinedTextField(
                value = uiState.confirmPin,
                onValueChange = { viewModel.onConfirmPinChanged(it) },
                placeholder = { Text("নিরাপত্তা পিনটি পুনরায় লিখুন") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Confirm PIN Icon",
                        tint = RoyalIndigo
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.submitSignup(context, onSignupComplete)
                    }
                ),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFFBFBFC),
                    focusedBorderColor = RoyalIndigo,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedPlaceholderColor = Color(0xFF94A3B8),
                    unfocusedPlaceholderColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Confirm PIN" }
                    .disableAutofill()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Submit Button
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = RoyalIndigo,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submitSignup(context, onSignupComplete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "অ্যাকাউন্ট তৈরি করুন",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
