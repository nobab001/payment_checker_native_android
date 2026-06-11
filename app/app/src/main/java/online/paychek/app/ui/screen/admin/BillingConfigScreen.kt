package online.paychek.app.ui.screen.admin

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.paychek.app.data.remote.dto.BillingSettingDto
import online.paychek.app.ui.theme.RoyalIndigo
import online.paychek.app.ui.theme.RoyalIndigoLight
import online.paychek.app.ui.theme.BkashPink
import online.paychek.app.ui.theme.NagadOrange
import online.paychek.app.ui.theme.RocketPurple
import online.paychek.app.ui.theme.UpayTeal
import online.paychek.app.ui.theme.StatusGreen
import online.paychek.app.ui.theme.StatusOrange
import online.paychek.app.ui.theme.StatusRed
import online.paychek.app.ui.theme.SoldOutRed
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

    var showCreatePlanDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<online.paychek.app.data.remote.dto.SubscriptionPlanDto?>(null) }
    var planName by remember { mutableStateOf("") }
    var planPrice by remember { mutableStateOf("") }
    var planMaxSites by remember { mutableStateOf("") }
    var planMaxDevices by remember { mutableStateOf("") }
    var planDurationDays by remember { mutableStateOf("365") }

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
                        enabled = editingPlan == null,
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
                                online.paychek.app.data.remote.dto.SubscriptionPlanDto(
                                    id = editingPlan?.id,
                                    planName = planName,
                                    price = p,
                                    maxSites = ms,
                                    maxDevices = md,
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
                    }
                ) {
                    Text("বাতিল", color = TextSecondary)
                }
            },
            containerColor = CardBackground,
            modifier = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Modifier else Modifier.border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(28.dp))
        )
    }

    // Key-Value states for global billing settings
    var signupBonus by remember { mutableStateOf("") }
    var dailyRate by remember { mutableStateOf("") }
    var siteFee by remember { mutableStateOf("") }
    var deviceFee by remember { mutableStateOf("") }

    // Sync input fields with model when loaded
    LaunchedEffect(uiState.billingSettings) {
        uiState.billingSettings.forEach { setting ->
            when (setting.settingKey) {
                "default_signup_bonus" -> signupBonus = setting.settingValue
                "daily_maintenance_rate" -> dailyRate = setting.settingValue
                "one_time_site_fee" -> siteFee = setting.settingValue
                "one_time_device_fee" -> deviceFee = setting.settingValue
            }
        }
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
            text = "⚙️ গ্লোবাল বিলিং রেট সেটিিংস",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        // Default Signup Bonus
        BillingSettingField(
            label = "সাইনআপ বোনাস (৳)",
            description = "নতুন ইউজার জয়েন করলে অটোমেটিক ক্রেডিট দেওয়া হবে।",
            value = signupBonus,
            onValueChange = { signupBonus = it }
        )

        // Daily Maintenance Rate
        BillingSettingField(
            label = "দৈনিক সার্ভিস ফি (৳)",
            description = "প্রতিদিন মধ্যরাতে ইউজার অ্যাকাউন্ট থেকে কাটা হবে।",
            value = dailyRate,
            onValueChange = { dailyRate = it }
        )

        // One-time Site Fee
        BillingSettingField(
            label = "ওয়েবসাইট যুক্তকরণ ফি (৳)",
            description = "নতুন পেমেন্ট লেআউট/সাইট তৈরি করার ওয়ান-টাইম চার্জ।",
            value = siteFee,
            onValueChange = { siteFee = it }
        )

        // One-time Device Fee
        BillingSettingField(
            label = "চাইল্ড ডিভাইস অ্যাড ফি (৳)",
            description = "নতুন চাইল্ড ডিভাইস যুক্ত করার ওয়ান-টাইম চার্জ।",
            value = deviceFee,
            onValueChange = { deviceFee = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val updatedSettings = listOf(
                    BillingSettingDto(settingKey = "default_signup_bonus", settingValue = signupBonus),
                    BillingSettingDto(settingKey = "daily_maintenance_rate", settingValue = dailyRate),
                    BillingSettingDto(settingKey = "one_time_site_fee", settingValue = siteFee),
                    BillingSettingDto(settingKey = "one_time_device_fee", settingValue = deviceFee)
                )
                viewModel.saveBillingSettings(updatedSettings)
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
                Text("বিলিং সেটিংস সংরক্ষণ করুন", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
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
                            Text("৳${plan.price}", fontWeight = FontWeight.Bold, color = Color(0xFF22D3EE), fontSize = 15.sp)
                        }
                        Text("সর্বোচ্চ সাইট: ${plan.maxSites} | সর্বোচ্চ ডিভাইস: ${plan.maxDevices}", color = TextSecondary, fontSize = 12.sp)
                        Text("মেয়াদ: ${plan.durationDays} দিন", color = Color(0xFF10B981), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BillingSettingField(
    label: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = description, color = TextSecondary, fontSize = 11.sp)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
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
    }
}
