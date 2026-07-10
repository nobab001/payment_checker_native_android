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
import online.paychek.app.data.remote.dto.AddonPlanDto
import androidx.compose.material.icons.filled.Edit
import online.paychek.app.data.remote.dto.PlanFeatureDto
import online.paychek.app.ui.components.plan.PlanFeaturesDefaults
import online.paychek.app.ui.components.plan.PlanFeaturesEditorDialog
import online.paychek.app.ui.components.plan.PlanPackagePreviewDialog
import online.paychek.app.ui.components.plan.addonPermissionLines
import online.paychek.app.ui.components.plan.subscriptionPermissionLines
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
    var planCategory by remember { mutableStateOf("personal") }
    var planPermTemplate by remember { mutableStateOf(true) }
    var planPermWebsite by remember { mutableStateOf(true) }
    var planPermDevice by remember { mutableStateOf(true) }

    // PIN Verification Dialog states
    var showPinDialog by remember { mutableStateOf(false) }
    var planToDelete by remember { mutableStateOf<SubscriptionPlanDto?>(null) }
    var addonToDelete by remember { mutableStateOf<AddonPlanDto?>(null) }
    var adminPinInput by remember { mutableStateOf("") }
    var pinErrorText by remember { mutableStateOf<String?>(null) }
    var pinVerificationLoading by remember { mutableStateOf(false) }

    // Global welcome trial package states
    var trialDays by remember { mutableStateOf("7") }
    var trialMaxDevices by remember { mutableStateOf("1") }
    var trialMaxSites by remember { mutableStateOf("1") }
    var trialAllowCustomSender by remember { mutableStateOf("0") }

    var showCreateAddonDialog by remember { mutableStateOf(false) }
    var editingAddon by remember { mutableStateOf<AddonPlanDto?>(null) }
    var addonName by remember { mutableStateOf("") }
    var addonPrice by remember { mutableStateOf("") }
    var addonDurationDays by remember { mutableStateOf("30") }
    var addonDescription by remember { mutableStateOf("") }
    var addonIsActive by remember { mutableStateOf(true) }
    var addonMaxDevices by remember { mutableStateOf("2") }
    var planFeatures by remember { mutableStateOf<List<PlanFeatureDto>>(emptyList()) }
    var addonFeatures by remember { mutableStateOf<List<PlanFeatureDto>>(emptyList()) }
    var showPlanFeaturesEditor by remember { mutableStateOf(false) }
    var showAddonFeaturesEditor by remember { mutableStateOf(false) }

    var adminPlanTab by remember { mutableStateOf(0) }
    var previewSubscriptionPlan by remember { mutableStateOf<SubscriptionPlanDto?>(null) }
    var previewAddonPlan by remember { mutableStateOf<AddonPlanDto?>(null) }
    val filteredAdminPlans = remember(uiState.plans, adminPlanTab) {
        when (adminPlanTab) {
            0 -> uiState.plans.filter { it.planCategory == "personal" || it.planCategory.isBlank() }
            1 -> uiState.plans.filter { it.planCategory == "personal_business" }
            else -> uiState.plans.filter { it.planCategory == "payment_gateway" }
        }
    }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingPlan == null) "নতুন সাবস্ক্রিপশন প্ল্যান" else "প্ল্যান সম্পাদনা",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            planFeatures = PlanFeaturesDefaults.subscriptionFeatures(
                                maxSites = planMaxSites.toIntOrNull() ?: 1,
                                maxDevices = planMaxDevices.toIntOrNull() ?: 1,
                                existing = planFeatures.ifEmpty { editingPlan?.features }
                            )
                            showPlanFeaturesEditor = true
                        }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("এডিট", fontSize = 13.sp)
                    }
                }
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
                    OutlinedTextField(
                        value = when (planCategory) {
                            "personal_business" -> "পার্সোনাল বিজনেস"
                            "payment_gateway" -> "পেমেন্ট গেটওয়ে"
                            else -> "পার্সোনাল"
                        },
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("ক্যাটাগরি") },
                        modifier = Modifier.fillMaxWidth().clickable {
                            planCategory = when (planCategory) {
                                "personal" -> "personal_business"
                                "personal_business" -> "payment_gateway"
                                else -> "personal"
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("টেমপ্লেট পারমিশন", color = TextPrimary, fontSize = 13.sp)
                        Switch(checked = planPermTemplate, onCheckedChange = { planPermTemplate = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ওয়েবসাইট পারমিশন", color = TextPrimary, fontSize = 13.sp)
                        Switch(checked = planPermWebsite, onCheckedChange = { planPermWebsite = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ডিভাইস পারমিশন", color = TextPrimary, fontSize = 13.sp)
                        Switch(checked = planPermDevice, onCheckedChange = { planPermDevice = it })
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
                            val featuresToSave = planFeatures.ifEmpty {
                                PlanFeaturesDefaults.subscriptionFeatures(ms, md, editingPlan?.features)
                            }
                            viewModel.savePlan(
                                SubscriptionPlanDto(
                                    id = editingPlan?.id,
                                    planName = planName,
                                    price = p,
                                    maxSites = ms,
                                    maxDevices = md,
                                    isCustomSenderAllowed = if (planIsCustomSenderAllowed) 1 else 0,
                                    durationDays = dd,
                                    planCategory = planCategory,
                                    permTemplate = if (planPermTemplate) 1 else 0,
                                    permWebsite = if (planPermWebsite) 1 else 0,
                                    permDevice = if (planPermDevice) 1 else 0,
                                    features = featuresToSave
                                )
                            ) { success ->
                                if (!success) return@savePlan
                                showCreatePlanDialog = false
                                editingPlan = null
                                planName = ""
                                planPrice = ""
                                planMaxSites = ""
                                planMaxDevices = ""
                                planDurationDays = "365"
                                planIsCustomSenderAllowed = false
                                planFeatures = emptyList()
                            }
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
                        planFeatures = emptyList()
                    }
                ) {
                    Text("বাতিল", color = TextSecondary)
                }
            },
            containerColor = CardBackground,
            modifier = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Modifier else Modifier.border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(28.dp))
        )
    }

    if (showPlanFeaturesEditor) {
        PlanFeaturesEditorDialog(
            planName = planName,
            subtitle = "মেয়াদ: ${planDurationDays.toIntOrNull() ?: 365} দিন",
            price = planPrice.toDoubleOrNull() ?: 0.0,
            highlighted = planName.equals("Premium", ignoreCase = true),
            buyButtonText = "Buy Now",
            initialFeatures = planFeatures.ifEmpty {
                PlanFeaturesDefaults.subscriptionFeatures(
                    maxSites = planMaxSites.toIntOrNull() ?: 1,
                    maxDevices = planMaxDevices.toIntOrNull() ?: 1,
                    existing = editingPlan?.features
                )
            },
            onDismiss = { showPlanFeaturesEditor = false },
            onSave = { updated ->
                planFeatures = updated
                showPlanFeaturesEditor = false
            }
        )
    }

    if (showCreateAddonDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateAddonDialog = false
                editingAddon = null
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingAddon == null) "নতুন অ্যাড-অন প্যাকেজ" else "অ্যাড-অন প্যাকেজ সম্পাদনা",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            addonFeatures = PlanFeaturesDefaults.addonFeatures(
                                durationDays = addonDurationDays.toIntOrNull() ?: 30,
                                description = addonDescription.ifBlank { null },
                                existing = addonFeatures.ifEmpty { editingAddon?.features }
                            )
                            showAddonFeaturesEditor = true
                        }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("এডিট", fontSize = 13.sp)
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = addonName,
                        onValueChange = { addonName = it },
                        label = { Text("প্যাকেজের নাম") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = addonPrice,
                        onValueChange = { addonPrice = it },
                        label = { Text("মূল্য (৳)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = addonDurationDays,
                        onValueChange = { addonDurationDays = it },
                        label = { Text("মেয়াদ (দিন)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = addonDescription,
                        onValueChange = { addonDescription = it },
                        label = { Text("বিবরণ (ঐচ্ছিক)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = addonMaxDevices,
                        onValueChange = { addonMaxDevices = it },
                        label = { Text("সর্বোচ্চ ডিভাইস") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("সক্রিয়", color = TextPrimary, fontSize = 13.sp)
                        Switch(checked = addonIsActive, onCheckedChange = { addonIsActive = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (addonName.isBlank()) return@Button
                        val featuresToSave = addonFeatures.ifEmpty {
                            PlanFeaturesDefaults.addonFeatures(
                                durationDays = addonDurationDays.toIntOrNull() ?: 30,
                                description = addonDescription.ifBlank { null },
                                existing = editingAddon?.features
                            )
                        }
                        viewModel.saveAddonPlan(
                            AddonPlanDto(
                                id = editingAddon?.id,
                                planName = addonName.trim(),
                                price = addonPrice.toDoubleOrNull() ?: 0.0,
                                durationDays = addonDurationDays.toIntOrNull() ?: 30,
                                description = addonDescription.trim().ifEmpty { null },
                                isActive = if (addonIsActive) 1 else 0,
                                maxDevices = addonMaxDevices.toIntOrNull() ?: 2,
                                permCustomSender = 1,
                                permTemplate = 0,
                                permWebsite = 0,
                                permDevice = 1,
                                features = featuresToSave
                            )
                        ) { success ->
                            if (!success) return@saveAddonPlan
                            showCreateAddonDialog = false
                            editingAddon = null
                            addonName = ""
                            addonPrice = ""
                            addonDurationDays = "30"
                            addonDescription = ""
                            addonIsActive = true
                            addonFeatures = emptyList()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("সংরক্ষণ", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateAddonDialog = false
                    editingAddon = null
                    addonName = ""
                    addonPrice = ""
                    addonDurationDays = "30"
                    addonDescription = ""
                    addonIsActive = true
                    addonFeatures = emptyList()
                }) {
                    Text("বাতিল", color = TextSecondary)
                }
            },
            containerColor = CardBackground
        )
    }

    if (showAddonFeaturesEditor) {
        PlanFeaturesEditorDialog(
            planName = addonName.ifBlank { "অ্যাড-অন প্যাকেজ" },
            subtitle = addonDescription.ifBlank { "মেয়াদ: ${addonDurationDays.toIntOrNull() ?: 30} দিন" },
            price = addonPrice.toDoubleOrNull() ?: 0.0,
            highlighted = false,
            buyButtonText = "Buy Now",
            initialFeatures = addonFeatures.ifEmpty {
                PlanFeaturesDefaults.addonFeatures(
                    durationDays = addonDurationDays.toIntOrNull() ?: 30,
                    description = addonDescription.ifBlank { null },
                    existing = editingAddon?.features
                )
            },
            onDismiss = { showAddonFeaturesEditor = false },
            onSave = { updated ->
                addonFeatures = updated
                showAddonFeaturesEditor = false
            }
        )
    }

    // Security PIN Verification Dialog for Deleting Plan
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                planToDelete = null
                addonToDelete = null
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
                    Text(
                        if (addonToDelete != null) "নিরাপত্তার স্বার্থে এই অ্যাড-অন প্যাকেজটি ডিলিট করতে আপনার অ্যাডমিন সিকিউরিটি পিন দিন:"
                        else "নিরাপত্তার স্বার্থে এই প্ল্যানটি ডিলিট করতে আপনার অ্যাডমিন সিকিউরিটি পিন দিন:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
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
                        val addon = addonToDelete
                        if (adminPinInput.isEmpty()) return@Button
                        pinVerificationLoading = true
                        pinErrorText = null
                        when {
                            addon?.id != null -> viewModel.verifyAdminPinAndDeleteAddonPlan(
                                pin = adminPinInput,
                                planId = addon.id!!,
                                onSuccess = {
                                    showPinDialog = false
                                    addonToDelete = null
                                    adminPinInput = ""
                                    pinVerificationLoading = false
                                    Toast.makeText(context, "অ্যাড-অন প্যাকেজ ডিলিট করা হয়েছে।", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    pinErrorText = err
                                    pinVerificationLoading = false
                                }
                            )
                            plan?.id != null -> viewModel.verifyAdminPinAndDeletePlan(
                                pin = adminPinInput,
                                planId = plan.id,
                                onSuccess = {
                                    showPinDialog = false
                                    planToDelete = null
                                    adminPinInput = ""
                                    pinVerificationLoading = false
                                    Toast.makeText(context, "প্ল্যানটি ডিলিট করা হয়েছে।", Toast.LENGTH_SHORT).show()
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
                text = "✨ অ্যাড-অন ফিচার: কাস্টম সেন্ডার",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    editingAddon = null
                    addonName = ""
                    addonPrice = ""
                    addonDurationDays = "30"
                    addonDescription = ""
                    addonIsActive = true
                    addonFeatures = emptyList()
                    showCreateAddonDialog = true
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Addon Plan",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Text(
            text = "ইউজার অ্যাপের \"অ্যাড-অন ফিচার\" ট্যাবে এই প্যাকেজগুলো দেখাবে। ক্রয় করলে has_custom_sender_addon = 1 হবে।",
            color = TextSecondary,
            fontSize = 11.sp
        )

        uiState.addonPlans.firstOrNull()?.let { firstAddon ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { previewAddonPlan = firstAddon }) {
                    Text("বিস্তারিত — ${firstAddon.planName}")
                }
            }
        }

        if (uiState.addonPlans.isEmpty()) {
            Text("কোনো অ্যাড-অন প্যাকেজ নেই। + বাটনে ক্লিক করে তৈরি করুন।", color = TextSecondary, fontSize = 12.sp)
        } else {
            uiState.addonPlans.forEach { addon ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp),
                    border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingAddon = addon
                            addonName = addon.planName
                            addonPrice = addon.price.toString()
                            addonDurationDays = addon.durationDays.toString()
                            addonDescription = addon.description.orEmpty()
                            addonIsActive = addon.isActive == 1
                            addonFeatures = addon.features.orEmpty()
                            showCreateAddonDialog = true
                        }
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(addon.planName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { previewAddonPlan = addon },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) { Text("বিস্তারিত", fontSize = 11.sp) }
                                Text("৳${addon.price}", fontWeight = FontWeight.Bold, color = Color(0xFF22D3EE), fontSize = 15.sp)
                                IconButton(
                                    onClick = {
                                        addonToDelete = addon
                                        planToDelete = null
                                        showPinDialog = true
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRed, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Text("মেয়াদ: ${addon.durationDays} দিন", color = Color(0xFF10B981), fontSize = 12.sp)
                        addon.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = TextSecondary, fontSize = 12.sp)
                        }
                        Text(
                            if (addon.isActive == 1) "স্ট্যাটাস: সক্রিয়" else "স্ট্যাটাস: নিষ্ক্রিয়",
                            color = if (addon.isActive == 1) Color(0xFF10B981) else StatusRed,
                            fontSize = 12.sp
                        )
                    }
                }
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
                onClick = {
                    editingPlan = null
                    planName = ""
                    planPrice = ""
                    planMaxSites = ""
                    planMaxDevices = ""
                    planDurationDays = "365"
                    planIsCustomSenderAllowed = false
                    planCategory = "personal"
                    planPermTemplate = true
                    planPermWebsite = true
                    planPermDevice = true
                    planFeatures = emptyList()
                    showCreatePlanDialog = true
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Plan",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        ScrollableTabRow(
            selectedTabIndex = adminPlanTab,
            containerColor = CardBackground,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp
        ) {
            Tab(selected = adminPlanTab == 0, onClick = { adminPlanTab = 0 }, text = { Text("পার্সোনাল") })
            Tab(selected = adminPlanTab == 1, onClick = { adminPlanTab = 1 }, text = { Text("পার্সোনাল বিজনেস") })
            Tab(selected = adminPlanTab == 2, onClick = { adminPlanTab = 2 }, text = { Text("পেমেন্ট গেটওয়ে") })
        }

        val categoryPreviewPlan = filteredAdminPlans.firstOrNull()
        if (categoryPreviewPlan != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { previewSubscriptionPlan = categoryPreviewPlan }) {
                    Text("বিস্তারিত — ${categoryPreviewPlan.planName}")
                }
            }
        }

        if (filteredAdminPlans.isEmpty()) {
            Text("এই ক্যাটাগরিতে কোনো প্ল্যান নেই।", color = TextSecondary, fontSize = 12.sp)
        } else {
            filteredAdminPlans.forEach { plan ->
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
                            planCategory = plan.planCategory.ifBlank { "personal" }
                            planPermTemplate = plan.permTemplate == 1
                            planPermWebsite = plan.permWebsite == 1
                            planPermDevice = plan.permDevice == 1
                            planFeatures = plan.features.orEmpty()
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
                                TextButton(
                                    onClick = { previewSubscriptionPlan = plan },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) { Text("বিস্তারিত", fontSize = 11.sp) }
                                Text("৳${plan.price}", fontWeight = FontWeight.Bold, color = Color(0xFF22D3EE), fontSize = 15.sp)
                                IconButton(
                                    onClick = {
                                        planToDelete = plan
                                        addonToDelete = null
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

    previewSubscriptionPlan?.let { plan ->
        val features = PlanFeaturesDefaults.subscriptionFeatures(
            maxSites = plan.maxSites,
            maxDevices = plan.maxDevices,
            existing = plan.features
        )
        PlanPackagePreviewDialog(
            title = plan.planName,
            subtitle = "মেয়াদ: ${plan.durationDays} দিন | সাইট: ${plan.maxSites} | ডিভাইস: ${plan.maxDevices}",
            price = plan.price,
            features = features,
            permissionLines = subscriptionPermissionLines(
                permTemplate = plan.permTemplate,
                permWebsite = plan.permWebsite,
                permDevice = plan.permDevice,
                permCustomSender = plan.isCustomSenderAllowed
            ),
            onDismiss = { previewSubscriptionPlan = null },
            onEdit = {
                previewSubscriptionPlan = null
                editingPlan = plan
                planName = plan.planName
                planPrice = plan.price.toString()
                planMaxSites = plan.maxSites.toString()
                planMaxDevices = plan.maxDevices.toString()
                planDurationDays = plan.durationDays.toString()
                planIsCustomSenderAllowed = plan.isCustomSenderAllowed == 1
                planCategory = plan.planCategory.ifBlank { "personal" }
                planPermTemplate = plan.permTemplate == 1
                planPermWebsite = plan.permWebsite == 1
                planPermDevice = plan.permDevice == 1
                planFeatures = plan.features.orEmpty()
                showCreatePlanDialog = true
            }
        )
    }

    previewAddonPlan?.let { addon ->
        val features = PlanFeaturesDefaults.addonFeatures(
            durationDays = addon.durationDays,
            description = addon.description,
            existing = addon.features
        )
        PlanPackagePreviewDialog(
            title = addon.planName,
            subtitle = addon.description ?: "কাস্টম সেন্ডার অ্যাড-অন",
            price = addon.price,
            features = features,
            permissionLines = addonPermissionLines(
                maxDevices = addon.maxDevices,
                permCustomSender = addon.permCustomSender,
                permDevice = addon.permDevice
            ),
            onDismiss = { previewAddonPlan = null },
            onEdit = {
                previewAddonPlan = null
                editingAddon = addon
                addonName = addon.planName
                addonPrice = addon.price.toString()
                addonDurationDays = addon.durationDays.toString()
                addonDescription = addon.description.orEmpty()
                addonIsActive = addon.isActive == 1
                addonMaxDevices = addon.maxDevices.toString()
                addonFeatures = addon.features.orEmpty()
                showCreateAddonDialog = true
            }
        )
    }
}
