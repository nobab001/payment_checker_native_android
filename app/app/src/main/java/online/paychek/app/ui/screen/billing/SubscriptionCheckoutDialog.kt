package online.paychek.app.ui.screen.billing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.SubscriptionQuoteDto
import online.paychek.app.data.repository.PaymentRepository
import online.paychek.app.utils.SecurePreferences

private fun purchaseTypeLabel(type: String): String = when (type) {
    "renew" -> "রিনিউ (একই প্যাকেজ)"
    "upgrade" -> "আপগ্রেড"
    else -> "নতুন ক্রয়"
}

@Composable
fun SubscriptionCheckoutDialog(
    planName: String,
    planTitle: String,
    onDismiss: () -> Unit,
    onPurchased: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { PaymentRepository() }

    var isLoadingQuote by remember { mutableStateOf(true) }
    var isPurchasing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var quote by remember { mutableStateOf<SubscriptionQuoteDto?>(null) }

    fun loadQuote() {
        scope.launch {
            isLoadingQuote = true
            errorMessage = null
            val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                errorMessage = "লগইন সেশন পাওয়া যায়নি।"
                isLoadingQuote = false
                return@launch
            }
            repository.getSubscriptionQuote(token, planName).fold(
                onSuccess = {
                    quote = it
                    isLoadingQuote = false
                },
                onFailure = { err ->
                    errorMessage = err.message ?: "কোট লোড ব্যর্থ"
                    isLoadingQuote = false
                }
            )
        }
    }

    LaunchedEffect(planName) { loadQuote() }

    Dialog(
        onDismissRequest = { if (!isPurchasing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("চেকআউট", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(planTitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)

                when {
                    isLoadingQuote -> {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    errorMessage != null -> {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { loadQuote() }) { Text("আবার চেষ্টা") }
                            TextButton(onClick = onDismiss) { Text("বন্ধ") }
                        }
                    }
                    quote != null -> {
                        val q = quote!!
                        Text(
                            purchaseTypeLabel(q.purchaseType),
                            fontSize = 12.sp,
                            color = Color(0xFF22D3EE),
                            fontWeight = FontWeight.SemiBold
                        )

                        if (q.purchaseType == "upgrade" && q.creditSourcePlan != null) {
                            Text(
                                "বর্তমান: ${q.creditSourcePlan} • ${q.remainingDays} দিন বাকি (মেয়াদ ${q.currentExpiryDate})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (q.purchaseType == "renew") {
                            Text(
                                "নতুন মেয়াদ আপনার বর্তমান মেয়াদের পরে যোগ হবে (মেয়াদ শেষ: ${q.currentExpiryDate})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider()

                        QuoteRow("প্যাকেজ মূল্য", "৳${q.listPrice.toInt()}")
                        if (q.creditApplied > 0) {
                            QuoteRow(
                                "বাকি ক্রেডিট",
                                "-৳${"%.0f".format(q.creditApplied)}",
                                valueColor = Color(0xFF10B981)
                            )
                        }
                        QuoteRow("পরিশোধযোগ্য", "৳${"%.0f".format(q.payableAmount)}", bold = true)
                        QuoteRow("নতুন মেয়াদ শেষ", q.newExpiryDate)
                        QuoteRow("মেয়াদ", "${q.durationDays} দিন")

                        Text(
                            "পেমেন্ট গেটওয়ে সংযুক্ত হলে এখানে ৳${"%.0f".format(q.payableAmount)} কাটা হবে। আপাতত অ্যাডমিন/টেস্ট মোডে সক্রিয় হবে।",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isPurchasing,
                                modifier = Modifier.weight(1f)
                            ) { Text("বাতিল") }
                            Button(
                                onClick = {
                                    scope.launch {
                                        isPurchasing = true
                                        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
                                        repository.purchaseSubscription(token, planName).fold(
                                            onSuccess = {
                                                online.paychek.app.utils.AccountEntitlementsStore.refresh(context)
                                                isPurchasing = false
                                                onPurchased(it.message ?: "${planTitle} সক্রিয় হয়েছে।")
                                                onDismiss()
                                            },
                                            onFailure = { err ->
                                                isPurchasing = false
                                                errorMessage = err.message ?: "ক্রয় ব্যর্থ"
                                            }
                                        )
                                    }
                                },
                                enabled = !isPurchasing,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isPurchasing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("নিশ্চিত করুন")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = if (bold) 16.sp else 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}
