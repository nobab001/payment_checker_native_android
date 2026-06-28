package online.paychek.app.ui.screen.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.paychek.app.ui.theme.RoyalIndigo
import online.paychek.app.ui.theme.StatusRed
import online.paychek.app.data.remote.dto.SubscriptionPlanDto
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border

private val AppBackground: Color @Composable get() = MaterialTheme.colorScheme.background
private val CardBackground: Color @Composable get() = MaterialTheme.colorScheme.surface
private val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingConfigScreen(
    viewModel: AdminDashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var showCreatePlanDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<SubscriptionPlanDto?>(null) }
    var planName by remember { mutableStateOf("") }
    var planPrice by remember { mutableStateOf("") }
    var planMaxSites by remember { mutableStateOf("") }
    var planMaxDevices by remember { mutableStateOf("") }
    var planIsCustomSenderAllowed by remember { mutableStateOf(false) }
    var planDurationDays by remember { mutableStateOf("365") }

    // PIN Verification Dialog states
    var showPinDialog by remember { mutableStateOf(false) }
    var planToDelete by remember { mutableStateOf<SubscriptionPlanDto?>(null) }
    var adminPinInput by remember { mutableStateOf("") }
    var pinErrorText by remember { mutableStateOf<String?>(null) }
    var pinVerificationLoading by remember { mutableStateOf(false) }

    // Global welcome trial package states
    var trialDays by remember { mutableStateOf("7") }
    var trialMaxDevices by remember { mutableStateOf("1") }
    var trialMaxSites by remember { mutableStateOf("1") }
    var trialAllowCustomSender by remember { mutableStateOf("0") }

    LaunchedEffect(uiState.configs) {
        trialDays = uiState.configs["trial_days"] ?: "7"
        trialMaxDevices = uiState.configs["trial_max_devices"] ?: "1"
        trialMaxSites = uiState.configs["trial_max_sites"] ?: "1"
        trialAllowCustomSender = uiState.configs["trial_allow_custom_sender"] ?: "0"
    }

    // Edit/Create Plan Dialog
    if (showCreatePlanDialog) {
        AlertDialog(
            onDismissRequest = { 
                showCreatePlanDialog = false
                editingPlan = null
            },
            title = {
                Text(
                    text = if (editingPlan == null) "নতুন সাবস্ক্রিপশন প্ল্যান তৈরি করুন" else "প্ল্যান সম্পাদন করুন",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = planName,
                        onValueChange = { planName = it },
                        label = { Text("প্ল্যানের নাম (যেমন: Basic, Standard)") },
                        enabled = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = TextSecondary,
                            unfocusedLabelColor = TextSecondary.copy(alpha = 0.6f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = planPrice,
                        onValueChange = { planPrice = it },
                        label = { Text("মূল্য (৳)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = TextSecondary,
                            unfocusedLabelColor = TextSecondary.copy(alpha = 0.6f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = planMaxSites,
                        onValueChange = { planMaxSites = it },
                        label = { Text("সর্বোচ্চ সাইট সংখ্যা") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = TextSecondary,
                            unfocusedLabelColor = TextSecondary.copy(alpha = 0.6f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = planMaxDevices,
                        onValueChange = { planMaxDevices = it },
                        label = { Text("সর্বোচ্চ ডিভাইস সংখ্যা") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = TextSecondary,
                            unfocusedLabelColor = TextSecondary.copy(alpha = 0.6f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = planDurationDays,
                        onValueChange = { planDurationDays = it },
                        label = { Text("প্যাকেজের মেয়াদ (দিন)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = TextSecondary,
                            unfocusedLabelColor = TextSecondary.copy(alpha = 0.6f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("কাস্টম সেন্ডার আইডি ব্যবহারের অনুমতি", color = TextPrimary, fontSize = 13.sp)
                        Switch(
                            checked = planIsCustomSenderAllowed,
                            onCheckedChange = { planIsCustomSenderAllowed = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = planPrice.toDoubleOrNull() ?: 0.0
                        val ms = planMaxSites.toIntOrNull() ?: 1
                        val md = planMaxDevices.toIntOrNull() ?: 1
                        val dd = planDurationDays.toIntOrNull() ?: 365
                        
                        if (planName.isNotEmpty()) {
                            viewModel.savePlan(
                                SubscriptionPlanDto(
                                    id = editingPlan?.id,
                                    planName = planName,
                                    price = p,
                                    maxSites = ms,
                                    maxDevices = md,
                                    isCustomSenderAllowed = if (planIsCustomSenderAllowed) 1 else 0,
                                    durationDays = dd
                                )
                            )
                            showCreatePlanDialog = false
                            editingPlan = null
                            planName = ""
                            planPrice = ""
                            planMaxSites = ""
                            planMaxDevices = ""
                            planDurationDays = "365"
                            planIsCustomSenderAllowed = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("সংরক্ষণ", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showCreatePlanDialog = false
                        editingPlan = null
                        planName = ""
                        planPrice = ""
                        planMaxSites = ""
                        planMaxDevices = ""
                        planDurationDays = "365"
                        planIsCustomSenderAllowed = false
                    }
                ) {
                    Text("বাতিল", color = TextSecondary)
                }
            },
            containerColor = CardBackground,
            modifier = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Modifier else Modifier.border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(28.dp))
        )
    }

    // Security PIN Verification Dialog for Deleting Plan
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                planToDelete = null
                adminPinInput = ""
                pinErrorText = null
                pinVerificationLoading = false
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, null, tint = StatusRed)
                    Text("পাস সিকিউরিটি পিন (Enter Admin PIN)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("নিরাপত্তার স্বার্থে এই প্ল্যানটি ডিলিট করতে আপনার অ্যাডমিন সিকিউরিটি পিন দিন:", color = TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = adminPinInput,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                adminPinInput = it
                            }
                        },
                        label = { Text("অ্যাডমিন পিন") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = StatusRed,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinErrorText?.let {
                        Text(it, color = StatusRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val plan = planToDelete
                        if (plan?.id != null && adminPinInput.isNotEmpty()) {
                            pinVerificationLoading = true
                            pinErrorText = null
                            viewModel.verifyAdminPinAndDeletePlan(
                                pin = adminPinInput,
                                planId = plan.id,
                                onSuccess = {
                                    showPinDialog = false
                                    planToDelete = null
                                    adminPinInput = ""
                                    pinVerificationLoading = false
                                    Toast.makeText(context, "প্ল্যানটি ডিলিট করা হয়েছে।", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    pinErrorText = err
                                    pinVerificationLoading = false
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                    enabled = !pinVerificationLoading
                ) {
                    if (pinVerificationLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("ডিলিট কনফার্ম", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        planToDelete = null
                        adminPinInput = ""
                        pinErrorText = null
                    },
                    enabled = !pinVerificationLoading
                ) {
                    Text("বাতিল", color = TextSecondary)
                }
            },
            containerColor = CardBackground,
            modifier = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Modifier else Modifier.border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(28.dp))
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🎁 ওয়েলকাম ট্রায়াল প্যাকেজ সেটিংস",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        // Global Free Trial settings card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp),
            border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Trial Days
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "মেয়াদ দিনসংখ্যা (trial_days)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "নতুন নিবন্ধিত ডিভাইসের জন্য ফ্রি ট্রায়াল দিনসংখ্যা (যেমন: 7, 3, 0)।", color = TextSecondary, fontSize = 11.sp)
                    OutlinedTextField(
                        value = trialDays,
                        onValueChange = { trialDays = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2. Max Devices
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "সর্বোচ্চ ডিভাইস (trial_max_devices)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "ট্রায়াল প্যাকেজে সর্বোচ্চ কয়টি ডিভাইস যুক্ত করা যাবে।", color = TextSecondary, fontSize = 11.sp)
                    OutlinedTextField(
                        value = trialMaxDevices,
                        onValueChange = { trialMaxDevices = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. Max Sites
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "সর্বোচ্চ সাইট (trial_max_sites)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "ট্রায়াল প্যাকেজে সর্বোচ্চ কয়টি গেটওয়ে সাইট বা ল্যান্ডিং পেইজ যুক্ত করা যাবে।", color = TextSecondary, fontSize = 11.sp)
                    OutlinedTextField(
                        value = trialMaxSites,
                        onValueChange = { trialMaxSites = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 4. Allow Custom Sender
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "কাস্টম সেন্ডার আইডি সাপোর্ট (Custom Sender ID)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "ট্রায়াল প্যাকেজে ইউজারদের কাস্টম সেন্ডার আইডি তৈরি করতে দেওয়া হবে কিনা।", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = trialAllowCustomSender == "1",
                        onCheckedChange = { trialAllowCustomSender = if (it) "1" else "0" }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                viewModel.updateConfigs(
                    mapOf(
                        "trial_days" to trialDays,
                        "trial_max_devices" to trialMaxDevices,
                        "trial_max_sites" to trialMaxSites,
                        "trial_allow_custom_sender" to trialAllowCustomSender
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            } else {
                Icon(imageVector = Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ওয়েলকাম ট্রায়াল সেটিংস সংরক্ষণ করুন", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋 Manage Subscription Plans",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { showCreatePlanDialog = true },
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Plan",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        if (uiState.plans.isEmpty()) {
            Text("কোনো সাবস্ক্রিপশন প্ল্যান পাওয়া যায়নি।", color = TextSecondary, fontSize = 12.sp)
        } else {
            uiState.plans.forEach { plan ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp),
                    border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingPlan = plan
                            planName = plan.planName
                            planPrice = plan.price.toString()
                            planMaxSites = plan.maxSites.toString()
                            planMaxDevices = plan.maxDevices.toString()
                            planDurationDays = plan.durationDays.toString()
                            planIsCustomSenderAllowed = plan.isCustomSenderAllowed == 1
                            showCreatePlanDialog = true
                        }
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(plan.planName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("৳${plan.price}", fontWeight = FontWeight.Bold, color = Color(0xFF22D3EE), fontSize = 15.sp)
                                IconButton(
                                    onClick = {
                                        planToDelete = plan
                                        showPinDialog = true
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Plan",
                                        tint = StatusRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Text("সর্বোচ্চ সাইট: ${plan.maxSites} | সর্বোচ্চ ডিভাইস: ${plan.maxDevices}${if (plan.isCustomSenderAllowed == 1) " | কাস্টম সেন্ডার: হ্যাঁ" else ""}", color = TextSecondary, fontSize = 12.sp)
                        Text("মেয়াদ: ${plan.durationDays} দিন", color = Color(0xFF10B981), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
