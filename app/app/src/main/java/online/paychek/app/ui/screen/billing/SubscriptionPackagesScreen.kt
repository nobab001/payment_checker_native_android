package online.paychek.app.ui.screen.billing

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.paychek.app.data.remote.dto.SubscriptionPlanDto
import online.paychek.app.data.repository.PaymentRepository
import online.paychek.app.ui.theme.RoyalIndigo
import online.paychek.app.utils.SecurePreferences
import kotlinx.coroutines.launch

private val PackBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val PackCard: Color @Composable get() = MaterialTheme.colorScheme.surface
private val TextW: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val TextM: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

private val GradIndigo = Brush.linearGradient(
    colors = listOf(Color(0xFF1A237E), Color(0xFF0D47A1), Color(0xFF006064))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionPackagesScreen(
    onNavigateToPaymentMock: () -> Unit,
    onNavigateBack: () -> Unit,
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { PaymentRepository() }
    
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var plans by remember { mutableStateOf<List<SubscriptionPlanDto>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(initialTab.coerceIn(0, 1)) }

    LaunchedEffect(Unit) {
        val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
        if (token.isNotEmpty()) {
            repository.getPlans(token).fold(
                onSuccess = { list ->
                    plans = list
                    isLoading = false
                },
                onFailure = { err ->
                    errorMessage = err.message ?: "প্যাকেজ তালিকা লোড করতে ব্যর্থ হয়েছে।"
                    isLoading = false
                }
            )
        } else {
            errorMessage = "অনুগ্রহ করে লগইন করুন।"
            isLoading = false
        }
    }

    Scaffold(
        containerColor = PackBg,
        topBar = {
            TopAppBar(
                modifier = Modifier.height(56.dp),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                title = {
                    Text(
                        text = "সাবস্ক্রিপশন প্যাকেজসমূহ",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowBackIosNew,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RoyalIndigo
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PackBg)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = PackBg,
                contentColor = Color(0xFF22D3EE),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF22D3EE)
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("মূল প্যাকেজসমূহ", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("অ্যাড-অন ফিচার", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (selectedTab == 0) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(color = Color(0xFF22D3EE))
                        }
                        errorMessage != null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = errorMessage ?: "ত্রুটি দেখা দিয়েছে",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        isLoading = true
                                        errorMessage = null
                                        coroutineScope.launch {
                                            val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
                                            repository.getPlans(token).fold(
                                                onSuccess = { list ->
                                                    plans = list
                                                    isLoading = false
                                                },
                                                onFailure = { err ->
                                                    errorMessage = err.message
                                                    isLoading = false
                                                }
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                                ) {
                                    Text("পুনরায় চেষ্টা করুন")
                                }
                            }
                        }
                        plans.isEmpty() -> {
                            Text(
                                text = "কোনো প্যাকেজ পাওয়া যায়নি।",
                                color = TextM,
                                fontSize = 14.sp
                            )
                        }
                        else -> {
                            val mainPlans = plans.filter { !it.planName.contains("custom sender", ignoreCase = true) }
                            if (mainPlans.isEmpty()) {
                                Text(
                                    text = "কোনো প্যাকেজ পাওয়া যায়নি।",
                                    color = TextM,
                                    fontSize = 14.sp
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(mainPlans, key = { it.planName }) { plan ->
                                        PackageCard(
                                            plan = plan,
                                            onBuyNowClick = onNavigateToPaymentMock
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Render Custom Sender Add-on Card
                    var isAddonPurchasing by remember { mutableStateOf(false) }
                    val addonPlan = plans.find { it.planName.contains("custom sender", ignoreCase = true) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PackCard),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(2.dp, Color(0xFF22D3EE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = addonPlan?.planName ?: "কাস্টম সেন্ডার আইডি অ্যাড-অন",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            color = TextW
                                        )
                                        Text(
                                            text = "Custom Sender ID Integration",
                                            fontSize = 11.sp,
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    Text(
                                        text = "৳${addonPlan?.price?.toInt() ?: 250}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 24.sp,
                                        color = Color(0xFF22D3EE)
                                    )
                                }
                                
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PlanLimitItem(label = "আনলিমিটেড নিজস্ব কাস্টম সেন্ডার আইডি সেট করুন")
                                    PlanLimitItem(label = "নন-পার্সেবল বার্তার জন্য সার্ভার সাইড জিরো ওভারহেড স্টোরেজ")
                                    PlanLimitItem(label = "কাস্টম আর্কাইভ ট্যাবে সর্বশেষ ২৫০০টি ডেটা রেকর্ডস")
                                    PlanLimitItem(label = "মেয়াদ: ${addonPlan?.durationDays ?: 365} দিন")
                                    PlanLimitItem(label = "২৪/৭ রিয়েল-টাইম অটোমেটেড এসএমএস ট্র্যাকিং")
                                }
                                
                                Button(
                                    onClick = {
                                        isAddonPurchasing = true
                                        coroutineScope.launch {
                                            val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
                                            repository.purchaseSubscriptionAddon(token).fold(
                                                onSuccess = { res ->
                                                    isAddonPurchasing = false
                                                    android.widget.Toast.makeText(context, "অ্যাড-অন সফলভাবে সক্রিয় হয়েছে! ✓", android.widget.Toast.LENGTH_LONG).show()
                                                },
                                                onFailure = { err ->
                                                    isAddonPurchasing = false
                                                    android.widget.Toast.makeText(context, err.message ?: "ক্রয় ব্যর্থ হয়েছে।", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                    },
                                    enabled = !isAddonPurchasing,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    if (isAddonPurchasing) {
                                        CircularProgressIndicator(
                                            color = Color.Black,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            text = "এখনই কিনুন (Buy Now)",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
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
private fun PackageCard(
    plan: SubscriptionPlanDto,
    onBuyNowClick: () -> Unit
) {
    val isPremium = plan.planName.equals("Premium", ignoreCase = true)
    
    Card(
        colors = CardDefaults.cardColors(containerColor = PackCard),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = if (isPremium) 2.dp else 1.dp,
            color = if (isPremium) Color(0xFF22D3EE) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = plan.planName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = TextW
                    )
                    Text(
                        text = "মেয়াদ: ${plan.durationDays} দিন",
                        fontSize = 12.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Text(
                    text = "৳${plan.price}",
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = if (isPremium) Color(0xFF22D3EE) else RoyalIndigo
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanLimitItem(label = "সর্বোচ্চ ${plan.maxSites} টি ওয়েবসাইট সংযুক্ত করুন")
                PlanLimitItem(label = "সর্বোচ্চ ${plan.maxDevices} টি চাইল্ড ডিভাইস যুক্ত করুন")
                PlanLimitItem(label = "২৪/৭ লাইভ এডমিন ও হোয়াটসঅ্যাপ সাপোর্ট")
            }
            
            Button(
                onClick = onBuyNowClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPremium) Color(0xFF22D3EE) else RoyalIndigo
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Buy Now",
                    color = if (isPremium) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun PlanLimitItem(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextM
        )
    }
}
