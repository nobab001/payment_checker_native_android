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

    // Initialize verified credentials from login/OTP
    LaunchedEffect(contact, token) {
        viewModel.initData(contact, token)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "প্রোফাইল সম্পন্ন করুন",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoyalIndigo,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "আপনার পেমেন্ট চেকার অ্যাকাউন্টটি সক্রিয় করতে নিচের তথ্যগুলো পূরণ করুন।",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Error Message
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
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 1. Full Name Field
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.onNameChanged(it) },
                    label = { Text("পূর্ণ নাম (Full Name)") },
                    placeholder = { Text("আপনার পুরো নাম লিখুন") },
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalIndigo,
                        focusedLabelColor = RoyalIndigo
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 2. Mobile Phone (Read-Only if prefilled)
                OutlinedTextField(
                    value = uiState.phone ?: "",
                    onValueChange = { viewModel.onPhoneChanged(it) },
                    label = { Text("মোবাইল নম্বর") },
                    placeholder = { Text("যেমন: 017xxxxxxxx") },
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalIndigo,
                        focusedLabelColor = RoyalIndigo
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. Email (Read-Only if prefilled)
                OutlinedTextField(
                    value = uiState.email ?: "",
                    onValueChange = { viewModel.onEmailChanged(it) },
                    label = { Text("ইমেইল ঠিকানা") },
                    placeholder = { Text("যেমন: yourname@gmail.com") },
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalIndigo,
                        focusedLabelColor = RoyalIndigo
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 4. PIN Field (6-digits, numeric)
                OutlinedTextField(
                    value = uiState.pin,
                    onValueChange = { viewModel.onPinChanged(it) },
                    label = { Text("৬-ডিজিটের নিরাপত্তা পিন (PIN)") },
                    placeholder = { Text("যেমন: ১২৩৪৫৬") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "PIN Icon",
                            tint = RoyalIndigo
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalIndigo,
                        focusedLabelColor = RoyalIndigo
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 5. Confirm PIN Field (6-digits, numeric)
                OutlinedTextField(
                    value = uiState.confirmPin,
                    onValueChange = { viewModel.onConfirmPinChanged(it) },
                    label = { Text("নিরাপত্তা পিনটি পুনরায় লিখুন") },
                    placeholder = { Text("যেমন: ১২৩৪৫৬") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Confirm PIN Icon",
                            tint = RoyalIndigo
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.submitSignup(onSignupComplete)
                        }
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalIndigo,
                        focusedLabelColor = RoyalIndigo
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Submit Button
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = RoyalIndigo)
                } else {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.submitSignup(onSignupComplete)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "অ্যাকাউন্ট তৈরি করুন",
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
