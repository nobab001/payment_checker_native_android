package online.paychek.app.ui.screen.billing

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.paychek.app.data.remote.dto.SubscriptionPlanDto
import online.paychek.app.data.remote.dto.AddonPlanDto
import online.paychek.app.data.repository.PaymentRepository
import online.paychek.app.ui.components.plan.PlanFeaturesDefaults
import online.paychek.app.ui.components.plan.PlanPackageCard
import online.paychek.app.ui.theme.RoyalIndigo
import online.paychek.app.utils.SecurePreferences
import kotlinx.coroutines.launch

private val PackBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val TextM: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

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
    var addonPlans by remember { mutableStateOf<List<AddonPlanDto>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(initialTab.coerceIn(0, 3)) }
    var purchasingAddonId by remember { mutableStateOf<Int?>(null) }

    fun reloadPlans() {
        coroutineScope.launch {
            val token = SecurePreferences.decrypt(context, online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                errorMessage = "অনুগ্রহ করে লগইন করুন।"
                isLoading = false
                return@launch
            }
            isLoading = true
            errorMessage = null
            repository.getPlans(token).fold(
                onSuccess = { list ->
                    plans = list
                    isLoading = false
                },
                onFailure = { err ->
                    errorMessage = err.message ?: "প্যাকেজ তালিকা লোড করতে ব্যর্থ হয়েছে।"
                    isLoading = false
                }
            )
            repository.getAddonPlans(token).fold(
                onSuccess = { list -> addonPlans = list },
                onFailure = { }
            )
        }
    }

    LaunchedEffect(Unit) {
        reloadPlans()
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalIndigo)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PackBg)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = PackBg,
                contentColor = Color(0xFF22D3EE),
                edgePadding = 8.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFF22D3EE)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("পার্সোনাল", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("পার্সোনাল বিজনেস", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("পেমেন্ট গেটওয়ে", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("কাস্টম সেন্ডার", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            val categoryPlans = when (selectedTab) {
                0 -> plans.filter { it.planCategory == "personal" || it.planCategory.isBlank() }
                1 -> plans.filter { it.planCategory == "personal_business" }
                2 -> plans.filter { it.planCategory == "payment_gateway" }
                else -> emptyList()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (selectedTab < 3) {
                    when {
                        isLoading -> CircularProgressIndicator(color = Color(0xFF22D3EE))
                        errorMessage != null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = errorMessage ?: "ত্রুটি দেখা দিয়েছে",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { reloadPlans() },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                                ) { Text("পুনরায় চেষ্টা করুন") }
                            }
                        }
                        categoryPlans.isEmpty() -> {
                            Text("এই ক্যাটাগরিতে কোনো প্যাকেজ নেই।", color = TextM, fontSize = 14.sp)
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(categoryPlans, key = { it.planName }) { plan ->
                                    val isPremium = plan.planName.equals("Premium", ignoreCase = true)
                                    val features = PlanFeaturesDefaults.subscriptionFeatures(
                                        maxSites = plan.maxSites,
                                        maxDevices = plan.maxDevices,
                                        existing = plan.features
                                    )
                                    PlanPackageCard(
                                        planName = plan.planName,
                                        subtitle = "মেয়াদ: ${plan.durationDays} দিন",
                                        price = plan.price,
                                        features = features,
                                        highlighted = isPremium,
                                        buyButtonText = "Buy Now",
                                        buyButtonTextColor = if (isPremium) Color.Black else Color.White,
                                        onBuyClick = onNavigateToPaymentMock
                                    )
                                }
                            }
                        }
                    }
                } else {
                    when {
                        isLoading -> CircularProgressIndicator(color = Color(0xFF22D3EE))
                        addonPlans.isEmpty() -> {
                            Text(
                                text = "কোনো অ্যাড-অন প্যাকেজ পাওয়া যায়নি।",
                                color = TextM,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(addonPlans, key = { it.id ?: it.planName }) { addon ->
                                    val features = PlanFeaturesDefaults.addonFeatures(
                                        durationDays = addon.durationDays,
                                        description = addon.description,
                                        existing = addon.features
                                    )
                                    PlanPackageCard(
                                        planName = addon.planName,
                                        subtitle = "কাস্টম সেন্ডার আইডি পারমিশন",
                                        price = addon.price,
                                        features = features,
                                        highlighted = true,
                                        buyButtonText = "এখনই কিনুন (Buy Now)",
                                        buyButtonTextColor = Color.Black,
                                        isPurchasing = purchasingAddonId == addon.id,
                                        onBuyClick = {
                                            val planId = addon.id ?: return@PlanPackageCard
                                            purchasingAddonId = planId
                                            coroutineScope.launch {
                                                val token = SecurePreferences.decrypt(
                                                    context,
                                                    online.paychek.app.config.AppConfig.KEY_AUTH_TOKEN
                                                )
                                                repository.purchaseSubscriptionAddon(token, planId).fold(
                                                    onSuccess = {
                                                        purchasingAddonId = null
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            it.message ?: "অ্যাড-অন সফলভাবে সক্রিয় হয়েছে! ✓",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    },
                                                    onFailure = { err ->
                                                        purchasingAddonId = null
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            err.message ?: "ক্রয় ব্যর্থ হয়েছে।",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                )
                                            }
                                        }
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
