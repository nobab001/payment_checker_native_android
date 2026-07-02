package online.paychek.app.ui.screen.apicenter

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.content.ClipData
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

import androidx.compose.foundation.BorderStroke
import online.paychek.app.utils.RefreshCooldown
import online.paychek.app.utils.adaptivePadding
import online.paychek.app.utils.adaptiveTextSize
import online.paychek.app.utils.adaptiveTextSize

// =============================================================================
// Design Tokens (matches Premium Dark theme)
// =============================================================================
private val ApiBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val ApiCard: Color @Composable get() = MaterialTheme.colorScheme.surface
private val ApiCardAlt: Color @Composable get() = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Color(0xFF1B2030) else Color(0xFFF1F3F5)
private val AccentCyan   = Color(0xFF22D3EE)
private val AccentGreen  = Color(0xFF10B981)
private val AccentAmber  = Color(0xFFF59E0B)
private val TextWhite: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

private val GradientBtn  = Brush.horizontalGradient(
    colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiIntegrationScreen(
    onNavigateToCheckout: () -> Unit,
    onNavigateToWebsites: () -> Unit = {},
    onNavigateToDocs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf("pk_live_f893634ca31ce46e784dbc2e2f") }
    var clientSecret by remember { mutableStateOf("cs_live_93634ca31ce46e784dbc2e2f7b") }
    var webhookUrl by remember { mutableStateOf("https://yourdomain.com/paychek/webhook") }
    var showWebhookDialog by remember { mutableStateOf(false) }
    var showRegenDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ApiBg,
        topBar = {
            TopAppBar(
                modifier = Modifier.height(adaptivePadding(48.dp, 56.dp)),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                title = {
                    Text(
                        "API ইন্টিগ্রেশন ড্যাশবোর্ড",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = adaptiveTextSize(14.sp, 16.sp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ApiCard),
                actions = {
                    IconButton(onClick = onNavigateToDocs, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Documentation", tint = AccentCyan, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = {
                        if (RefreshCooldown.tryRefresh {
                            Toast.makeText(context, "ড্যাশবোর্ড রিফ্রেশ করা হয়েছে।", Toast.LENGTH_SHORT).show()
                        }) {
                            // refreshed
                        } else {
                            Toast.makeText(context, "৫ সেকেন্ড পরে আবার রিফ্রেশ করুন", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AccentCyan, modifier = Modifier.size(20.dp))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showWebhookDialog = true },
                containerColor = AccentCyan,
                contentColor = ApiBg,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Webhook", modifier = Modifier.size(24.dp))
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = adaptivePadding(12.dp, 16.dp), vertical = adaptivePadding(10.dp, 12.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ─── ১. Check Out Page Button (সবার ওপরে) ──────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GradientBtn)
                        .clickable { onNavigateToCheckout() }
                        .padding(horizontal = adaptivePadding(16.dp, 20.dp), vertical = adaptivePadding(12.dp, 16.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CreditCard, null, tint = Color.White)
                            }
                            Column {
                                Text(
                                    "Check Out Page",
                                    color = Color.White,
                                    fontSize = adaptiveTextSize(13.sp, 15.sp),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "চেকআউট পেজ ডিজাইন ও ডাইনামিক প্রিভিউ",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateToDocs, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Documentation", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                        }
                    }
                }
            }

            // ─── ১খ. Websites / Merchant Management ────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ApiCard),
                shape = RoundedCornerShape(16.dp),
                border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToWebsites() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = adaptivePadding(14.dp, 18.dp), vertical = adaptivePadding(12.dp, 14.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(AccentCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Storefront, null, tint = AccentCyan) }
                        Column {
                            Text("ওয়েবসাইট / মার্চেন্ট", color = TextWhite, fontSize = adaptiveTextSize(13.sp, 15.sp), fontWeight = FontWeight.Bold)
                            Text("ওয়েবসাইট যোগ করুন, API Key ও Secret ম্যানেজ করুন", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                }
            }

            // ─── ২. API Credentials Card ───────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ApiCard),
                shape = RoundedCornerShape(16.dp),
                border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(adaptivePadding(12.dp, 16.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        Text("ডেভেলপার এপিআই ক্রেডেনশিয়াল", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = adaptiveTextSize(12.sp, 14.sp))
                    }

                    HorizontalDivider(color = ApiCardAlt)

                    // Client ID / API Key
                    Column {
                        Text("API Key (Live)", color = TextMuted, fontSize = 11.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ApiCardAlt, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = apiKey,
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Key",
                                tint = AccentCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        coroutineScope.launch {
                                            clipboard.setClipEntry(ClipData.newPlainText("API Key", apiKey).toClipEntry())
                                        }
                                        Toast.makeText(context, "API Key কপি করা হয়েছে।", Toast.LENGTH_SHORT).show()
                                    }
                            )
                        }
                    }

                    // Client Secret
                    Column {
                        Text("Client Secret", color = TextMuted, fontSize = 11.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ApiCardAlt, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = clientSecret,
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Secret",
                                tint = AccentCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        coroutineScope.launch {
                                            clipboard.setClipEntry(ClipData.newPlainText("Client Secret", clientSecret).toClipEntry())
                                        }
                                        Toast.makeText(context, "Client Secret কপি করা হয়েছে।", Toast.LENGTH_SHORT).show()
                                    }
                            )
                        }
                    }

                    Button(
                        onClick = { showRegenDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("নতুন ক্রেডেনশিয়াল জেনারেট করুন", color = AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ─── ৩. Webhook Config Card ───────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ApiCard),
                shape = RoundedCornerShape(16.dp),
                border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(adaptivePadding(12.dp, 16.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.SettingsInputComponent, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                        Text("ওয়েবহুক কনফিগারেশন (Webhook)", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = adaptiveTextSize(12.sp, 14.sp))
                    }

                    HorizontalDivider(color = ApiCardAlt)

                    Column {
                        Text("Webhook Delivery URL", color = TextMuted, fontSize = 11.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ApiCardAlt, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = webhookUrl,
                                color = TextWhite,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Webhook",
                                tint = AccentCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showWebhookDialog = true }
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentGreen)
                        )
                        Text(
                            "Webhook সচল এবং রেসপন্স স্ট্যাটাস ২০০ OK",
                            color = AccentGreen.copy(alpha = 0.9f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ─── ৪. API Usage Stats ───────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ApiCard),
                shape = RoundedCornerShape(16.dp),
                border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(adaptivePadding(12.dp, 16.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.BarChart, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        Text("এপিআই ব্যবহারের স্ট্যাটস (আজ)", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = adaptiveTextSize(12.sp, 14.sp))
                    }

                    HorizontalDivider(color = ApiCardAlt)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("মোট এপিআই কল", color = TextMuted, fontSize = 11.sp)
                            Text("২,৫৮০ বার", color = TextWhite, fontSize = adaptiveTextSize(14.sp, 16.sp), fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("সফলতার হার", color = TextMuted, fontSize = 11.sp)
                            Text("৯৯.৮%", color = AccentGreen, fontSize = adaptiveTextSize(14.sp, 16.sp), fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("গড় রেসপন্স টাইম", color = TextMuted, fontSize = 11.sp)
                            Text("১৪৫ ms", color = TextWhite, fontSize = adaptiveTextSize(14.sp, 16.sp), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ─── ৫. Integration Code Snippet ──────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = ApiCard),
                shape = RoundedCornerShape(16.dp),
                border = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) null else BorderStroke(1.dp, Color(0xFFE3E5E8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(adaptivePadding(12.dp, 16.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Code, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        Text("কুইক ইন্টিগ্রেশন (Node.js SDK)", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = adaptiveTextSize(12.sp, 14.sp))
                    }

                    HorizontalDivider(color = ApiCardAlt)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ApiBg, RoundedCornerShape(8.dp))
                            .border(1.dp, ApiCardAlt, RoundedCornerShape(8.dp))
                            .padding(adaptivePadding(8.dp, 10.dp))
                    ) {
                        Text(
                            text = """
                                const Paychek = require('paychek-sdk');
                                const client = new Paychek({
                                  apiKey: '$apiKey',
                                  secret: '$clientSecret'
                                });
                                
                                // Create order payment
                                const payment = await client.createOrder({
                                  amount: 500,
                                  currency: 'BDT',
                                  callbackUrl: 'https://callback.com'
                                });
                            """.trimIndent(),
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    // Webhook settings modal dialog
    if (showWebhookDialog) {
        var tempUrl by remember { mutableStateOf(webhookUrl) }
        AlertDialog(
            onDismissRequest = { showWebhookDialog = false },
            title = { Text("ওয়েবহুক URL পরিবর্তন করুন", fontWeight = FontWeight.Bold, color = TextWhite) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("পেমেন্ট সফল হওয়ার সাথে সাথে আমাদের সার্ভার এই URL-এ ইনস্ট্যান্ট পোস্ট ডেটা পাঠাবে:", fontSize = 12.sp, color = TextMuted)
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Webhook Delivery URL") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = TextMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        webhookUrl = tempUrl
                        showWebhookDialog = false
                        Toast.makeText(context, "Webhook URL সফলভাবে আপডেট করা হয়েছে।", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("সংরক্ষণ করুন", color = ApiBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebhookDialog = false }) {
                    Text("বাতিল", color = TextMuted)
                }
            },
            containerColor = ApiCard,
            modifier = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Modifier else Modifier.border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(28.dp))
        )
    }

    // Regenerate keys confirmation dialog
    if (showRegenDialog) {
        AlertDialog(
            onDismissRequest = { showRegenDialog = false },
            title = { Text("নতুন API Credentials জেনারেট করুন?", fontWeight = FontWeight.Bold, color = TextWhite) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("সতর্কতা: নতুন ক্রেডেনশিয়াল জেনারেট করলে আগের এপিআই কী নিষ্ক্রিয় হয়ে যাবে এবং আপনার এক্সিস্টিং ইন্টিগ্রেশন সাময়িকভাবে পেমেন্ট রিকোয়েস্ট এক্সেপ্ট করা বন্ধ করবে।", color = TextMuted, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        apiKey = "pk_live_" + java.util.UUID.randomUUID().toString().replace("-", "").take(25)
                        clientSecret = "cs_live_" + java.util.UUID.randomUUID().toString().replace("-", "").take(26)
                        showRegenDialog = false
                        Toast.makeText(context, "নতুন এপিআই ক্রেডেনশিয়াল জেনারেট করা হয়েছে।", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
                ) {
                    Text("কনফার্ম করুন", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenDialog = false }) {
                    Text("বাতিল", color = TextMuted)
                }
            },
            containerColor = ApiCard,
            modifier = if (MaterialTheme.colorScheme.background == Color(0xFF0B0E14)) Modifier else Modifier.border(1.dp, Color(0xFFE3E5E8), RoundedCornerShape(28.dp))
        )
    }
}
