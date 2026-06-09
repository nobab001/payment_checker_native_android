package online.paychek.app.ui.screen.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
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
import online.paychek.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingConfigScreen(
    viewModel: AdminDashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

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
            text = "⚙️ গ্লোবাল বিলিং রেট সেটিংস",
            color = Color.White,
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
            colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                Spacer(modifier = Modifier.width(8.dp))
                Text("বিলিং সেটিংস সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = description, color = Color.Gray, fontSize = 11.sp)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoyalIndigo,
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
