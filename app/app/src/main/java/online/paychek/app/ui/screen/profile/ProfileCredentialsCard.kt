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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import online.paychek.app.data.remote.dto.CredentialItem
import online.paychek.app.data.repository.CredentialRepository
import online.paychek.app.utils.adaptivePadding
import online.paychek.app.utils.adaptiveTextSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.zIndex
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically



// Local color copies matching theme
private val PsCard: Color @Composable get() = MaterialTheme.colorScheme.surface
private val PsCardAlt: Color @Composable get() = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF253349) else Color(0xFFF1F3F5)
private val PsCyan     = Color(0xFF22D3EE)
private val PsRed      = Color(0xFFEF4444)
private val TextW: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val TextM: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun ProfileCredentialsCard(
    modifier: Modifier = Modifier,
    isRestricted: Boolean = false,
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

    val otpFocusRequester = remember { FocusRequester() }
    val otpInteractionSource = remember { MutableInteractionSource() }


    // Show error toast if any error is set outside dialog, or clear it
    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let { msg ->
            if (!showAddDialog) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            } else {
                kotlinx.coroutines.delay(3000L)
                viewModel.clearError()
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        colors = CardDefaults.cardColors(containerColor = PsCard)
    ) {
        Column(modifier = Modifier.padding(adaptivePadding(10.dp, 14.dp))) {
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
                    fontSize = adaptiveTextSize(12.sp, 14.sp)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!isRestricted && viewModel.linkedPhones.size < 5) {
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
                    val isPrimary = isSamePhone(item.value, viewModel.primaryPhone)
                    CredentialItemRow(
                        item = item,
                        icon = Icons.Default.PhoneAndroid,
                        isRestricted = isRestricted || isPrimary,
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
                    fontSize = adaptiveTextSize(12.sp, 14.sp)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!isRestricted && viewModel.linkedEmails.size < 5) {
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
                    val isPrimary = isSameEmail(item.value, viewModel.primaryEmail)
                    CredentialItemRow(
                        item = item,
                        icon = Icons.Default.Email,
                        isRestricted = isRestricted || isPrimary,
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
        Dialog(
            onDismissRequest = {
                showAddDialog = false
                inputValue = ""
                otpValue = ""
                viewModel.isOtpSentForLinking = false
                viewModel.clearError()
            },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PsCardAlt),
                border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .wrapContentHeight()
            ) {
                Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (!viewModel.isOtpSentForLinking) "নতুন ক্রেডেনশিয়াল যোগ" else "ওটিপি যাচাই করুন",
                        fontWeight = FontWeight.Bold,
                        color = TextW,
                        fontSize = 18.sp
                    )

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
                                .semantics {
                                    contentType = if (inputType == "phone") ContentType.PhoneNumber else ContentType.EmailAddress
                                }
                        )
                    } else {
                        Text(
                            text = "আমরা $inputValue এ একটি ৬-সংখ্যার ওটিপি কোড পাঠিয়েছি। কোডটি নিচে প্রবেশ করুন।",
                            color = TextM,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                        // Hidden BasicTextField state (declared outside / above the Box modifier so it is in scope)
                        var otpValueState by remember {
                            val padded = otpValue.padEnd(6, ' ')
                            val firstEmpty = otpValue.indexOf(' ')
                            val selIndex = if (firstEmpty != -1) firstEmpty else minOf(otpValue.length, 5)
                            mutableStateOf(
                                TextFieldValue(
                                    text = padded,
                                    selection = TextRange(selIndex, selIndex + 1)
                                )
                            )
                        }
                        LaunchedEffect(otpValue) {
                            val padded = otpValue.padEnd(6, ' ')
                            if (padded != otpValueState.text) {
                                val firstEmpty = otpValue.indexOf(' ')
                                val selIndex = if (firstEmpty != -1) firstEmpty else minOf(otpValue.length, 5)
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
                                    otpFocusRequester.requestFocus()
                                    keyboardController?.show()
                                    val firstEmpty = otpValue.indexOf(' ')
                                    val selIndex = if (firstEmpty != -1) firstEmpty else minOf(otpValue.length, 5)
                                    otpValueState = otpValueState.copy(
                                        selection = TextRange(selIndex, selIndex + 1)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Visual OTP Boxes
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (i in 0 until 6) {
                                    val char = otpValue.getOrNull(i)?.toString() ?: " "
                                    val isFocused = (otpValueState.selection.start == i) || (i == 5 && otpValueState.selection.start == 6)

                                    Box(
                                        modifier = Modifier
                                            .size(width = 40.dp, height = 48.dp)
                                            .background(
                                                color = if (char.isNotBlank()) Color.White.copy(alpha = 0.05f) else PsCardAlt,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .border(
                                                width = if (isFocused) 2.dp else 1.dp,
                                                color = if (isFocused) PsCyan else PsCardAlt,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                otpFocusRequester.requestFocus()
                                                keyboardController?.show()
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
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextW,
                                                textAlign = TextAlign.Center
                                            )
                                            if (isFocused && (otpValueState.selection.start == i || (i == 5 && otpValueState.selection.start == 6)) && char.isNotBlank()) {
                                                BlinkingCursor(color = PsCyan)
                                            }
                                        }
                                        if (isFocused && char.isBlank()) {
                                            BlinkingCursor(color = PsCyan)
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

                                        if (sanitized != otpValue) {
                                            otpValue = sanitized
                                        }
                                        otpValueState = TextFieldValue(
                                            text = sanitized,
                                            selection = targetSelection
                                        )
                                        if (newText.length < oldText.length) {
                                            otpFocusRequester.requestFocus()
                                            keyboardController?.show()
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
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
                                        .focusRequester(otpFocusRequester)
                                )
                            }
                        }
                    }

                    // Error is removed to prevent layout displacement

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                showAddDialog = false
                                inputValue = ""
                                otpValue = ""
                                viewModel.isOtpSentForLinking = false
                                viewModel.clearError()
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PsCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextM),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("বাতিল")
                        }
                        Button(
                            onClick = {
                                if (!viewModel.isOtpSentForLinking) {
                                    viewModel.sendOtpForNewCredential(inputValue, inputType)
                                } else {
                                    viewModel.verifyAndLinkCredential(inputValue, inputType, otpValue) {
                                        showAddDialog = false
                                        inputValue = ""
                                        otpValue = ""
                                        Toast.makeText(context, "ক্রেডেনশিয়াল সফলভাবে লিঙ্ক করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PsCyan),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                text = if (!viewModel.isOtpSentForLinking) "কোড পাঠান" else "যাচাই করুন",
                                color = PsCard,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Floating Error Overlay inside Dialog Box
                androidx.compose.animation.AnimatedVisibility(
                    visible = viewModel.errorMessage != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .zIndex(99f)
                ) {
                    viewModel.errorMessage?.let { error ->
                        FloatingErrorBanner(message = error)
                    }
                }
            }
        }
    }
}
    if (showPinDeleteDialog) {
        Dialog(
            onDismissRequest = {
                showPinDeleteDialog = false
                viewModel.clearError()
            },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PsCardAlt),
                border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                showPinDeleteDialog = false
                                viewModel.clearError()
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            Text("বাতিল", color = TextM)
                        }
                        Button(
                            onClick = {
                                credentialIdToDelete?.let { id ->
                                    viewModel.removeCredential(id, deletePinValue) {
                                        showPinDeleteDialog = false
                                        Toast.makeText(context, "ক্রেডেনশিয়াল সফলভাবে মুছে ফেলা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PsCyan),
                            modifier = Modifier.weight(1f).fillMaxWidth()
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
    isRestricted: Boolean = false,
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
        if (!isRestricted) {
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



private fun isSamePhone(p1: String?, p2: String?): Boolean {
    if (p1 == null || p2 == null) return false
    val clean1 = p1.replace(Regex("[^0-9]"), "")
    val clean2 = p2.replace(Regex("[^0-9]"), "")
    return clean1.isNotEmpty() && clean1.takeLast(10) == clean2.takeLast(10)
}

private fun isSameEmail(e1: String?, e2: String?): Boolean {
    if (e1 == null || e2 == null) return false
    return e1.trim().lowercase() == e2.trim().lowercase()
}
