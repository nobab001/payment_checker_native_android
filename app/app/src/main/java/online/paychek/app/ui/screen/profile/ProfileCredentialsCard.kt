package online.paychek.app.ui.screen.profile

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import online.paychek.app.data.remote.dto.CredentialItem
import online.paychek.app.data.repository.CredentialRepository
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.border
import androidx.compose.ui.autofill.AutofillType
import online.paychek.app.utils.autofill
import androidx.compose.foundation.shape.CircleShape


// Local color copies matching theme
private val PsCard     = Color(0xFF1E293B)
private val PsCardAlt  = Color(0xFF253349)
private val PsCyan     = Color(0xFF22D3EE)
private val PsRed      = Color(0xFFEF4444)
private val TextW      = Color(0xFFF8FAFC)
private val TextM      = Color(0xFF94A3B8)

@Composable
fun ProfileCredentialsCard(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    viewModel: CredentialViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = remember {
            CredentialViewModel.Factory(CredentialRepository(context.applicationContext))
        }
    )
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var inputType by remember { mutableStateOf("phone") } // "phone" or "email"
    var inputValue by remember { mutableStateOf("") }
    var otpValue by remember { mutableStateOf("") }

    var showPinDeleteDialog by remember { mutableStateOf(false) }
    var credentialIdToDelete by remember { mutableStateOf<Int?>(null) }
    var deletePinValue by remember { mutableStateOf("") }

    // Show error toast if any error is set outside dialog, or clear it
    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let { msg ->
            if (!showAddDialog) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PsCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Login Credentials (${viewModel.linkedPhones.size + viewModel.linkedEmails.size}/10)",
                fontWeight = FontWeight.Bold,
                color = TextW,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // --- মোবাইল নম্বর সেকশন ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "লিঙ্কড মোবাইল নম্বর (${viewModel.linkedPhones.size}/৫)",
                    color = TextW,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                if (viewModel.linkedPhones.size < 5) {
                    IconButton(
                        onClick = {
                            inputType = "phone"
                            inputValue = ""
                            otpValue = ""
                            viewModel.isOtpSentForLinking = false
                            viewModel.clearError()
                            showAddDialog = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Phone", tint = PsCyan)
                    }
                }
            }
            
            // লিঙ্কড নম্বরগুলো রো আকারে দেখানোর লুপ
            if (viewModel.linkedPhones.isEmpty()) {
                Text(
                    text = "কোনো অতিরিক্ত মোবাইল নম্বর লিঙ্ক করা নেই।",
                    color = TextM,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                viewModel.linkedPhones.forEach { item ->
                    CredentialItemRow(
                        item = item,
                        icon = Icons.Default.PhoneAndroid,
                        onRemove = {
                            credentialIdToDelete = item.id
                            deletePinValue = ""
                            showPinDeleteDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = PsCardAlt, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // --- জিমেইল সেকশন ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "লিঙ্কড জিমেইল অ্যাড্রেস (${viewModel.linkedEmails.size}/৫)",
                    color = TextW,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                if (viewModel.linkedEmails.size < 5) {
                    IconButton(
                        onClick = {
                            inputType = "email"
                            inputValue = ""
                            otpValue = ""
                            viewModel.isOtpSentForLinking = false
                            viewModel.clearError()
                            showAddDialog = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Email", tint = PsCyan)
                    }
                }
            }
            
            // লিঙ্কড জিমেইলগুলো রো আকারে দেখানোর লুপ
            if (viewModel.linkedEmails.isEmpty()) {
                Text(
                    text = "কোনো অতিরিক্ত জিমেইল অ্যাড্রেস লিঙ্ক করা নেই।",
                    color = TextM,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                viewModel.linkedEmails.forEach { item ->
                    CredentialItemRow(
                        item = item,
                        icon = Icons.Default.Email,
                        onRemove = {
                            credentialIdToDelete = item.id
                            deletePinValue = ""
                            showPinDeleteDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        Dialog(onDismissRequest = {
            showAddDialog = false
            viewModel.clearError()
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PsCardAlt),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (!viewModel.isOtpSentForLinking) "নতুন ক্রেডেনশিয়াল যোগ" else "ওটিপি যাচাই করুন",
                        fontWeight = FontWeight.Bold,
                        color = TextW,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!viewModel.isOtpSentForLinking) {
                        OutlinedTextField(
                            value = inputValue,
                            onValueChange = { inputValue = it },
                            label = { Text(if (inputType == "phone") "মোবাইল নম্বর" else "জিমেইল অ্যাড্রেস", color = TextM) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (inputType == "phone") KeyboardType.Phone else KeyboardType.Email
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextW,
                                unfocusedTextColor = TextW,
                                focusedBorderColor = PsCyan,
                                unfocusedBorderColor = TextM,
                                cursorColor = PsCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .autofill(
                                    autofillTypes = if (inputType == "phone") listOf(AutofillType.PhoneNumber) else listOf(AutofillType.EmailAddress),
                                    onFill = { inputValue = it }
                                )
                        )
                    } else {
                        Text(
                            text = "আমরা $inputValue এ একটি ৬-সংখ্যার ওটিপি কোড পাঠিয়েছি। কোডটি নিচে প্রবেশ করুন।",
                            color = TextM,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = otpValue,
                                onValueChange = { newValue ->
                                    val sanitized = newValue.filter { it.isDigit() }.take(6)
                                    otpValue = sanitized
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
                                    val char = otpValue.getOrNull(i)?.toString() ?: ""
                                    val isFocused = otpValue.length == i || (i == 5 && otpValue.length == 6)

                                    Box(
                                        modifier = Modifier
                                            .size(width = 40.dp, height = 48.dp)
                                            .background(
                                                color = if (char.isNotEmpty()) Color.White.copy(0.05f) else PsCardAlt,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .border(
                                                width = if (isFocused) 2.dp else 1.dp,
                                                color = if (isFocused) PsCyan else PsCardAlt,
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

                    viewModel.errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = PsRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showAddDialog = false
                            viewModel.clearError()
                        }) {
                            Text("বাতিল", color = TextM)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (!viewModel.isOtpSentForLinking) {
                                    viewModel.sendOtpForNewCredential(inputValue, inputType)
                                } else {
                                    viewModel.verifyAndLinkCredential(inputValue, inputType, otpValue) {
                                        showAddDialog = false
                                        Toast.makeText(context, "ক্রেডেনশিয়াল সফলভাবে লিঙ্ক করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PsCyan)
                        ) {
                            Text(
                                text = if (!viewModel.isOtpSentForLinking) "কোড পাঠান" else "যাচাই করুন",
                                color = PsCard
                            )
                        }
                    }
                }
            }
        }
    }
    if (showPinDeleteDialog) {
        Dialog(onDismissRequest = {
            showPinDeleteDialog = false
            viewModel.clearError()
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PsCardAlt),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Enter Security PIN",
                        fontWeight = FontWeight.Bold,
                        color = TextW,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = deletePinValue,
                        onValueChange = { newValue ->
                            if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                                deletePinValue = newValue
                            }
                        },
                        label = { Text("Security PIN", color = TextM) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextW,
                            unfocusedTextColor = TextW,
                            focusedBorderColor = PsCyan,
                            unfocusedBorderColor = TextM,
                            cursorColor = PsCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    viewModel.errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = PsRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showPinDeleteDialog = false
                            viewModel.clearError()
                        }) {
                            Text("বাতিল", color = TextM)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                credentialIdToDelete?.let { id ->
                                    viewModel.removeCredential(id, deletePinValue) {
                                        showPinDeleteDialog = false
                                        Toast.makeText(context, "ক্রেডেনশিয়াল সফলভাবে মুছে ফেলা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PsCyan)
                        ) {
                            Text(
                                text = "নিশ্চিত করুন",
                                color = PsCard
                            )
                        }
                    }
                }
            }
    }
}

}

@Composable
private fun CredentialItemRow(
    item: CredentialItem,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(PsCardAlt, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextM,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = item.value,
            color = TextW,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove",
                tint = PsRed,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
